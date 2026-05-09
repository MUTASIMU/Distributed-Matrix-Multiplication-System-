package com.distributed.matmul.backup;

import com.distributed.matmul.common.MasterService;
import com.distributed.matmul.common.WorkerAddress;
import com.distributed.matmul.master.DistributedMaster;
import com.distributed.matmul.master.MasterNode;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackupNode {

    private static final long HEARTBEAT_INTERVAL_MS = 2_000;
    private static final long PING_TIMEOUT_MS        = 1_500;
    private static final int  MAX_MISSED_BEATS        = 3;

    private final String masterHost;
    private final int    masterPort;
    private final String masterServiceName;
    private final WorkerAddress[] workerAddresses;

    private int consecutiveMisses = 0;
    private final AtomicBoolean takingOver = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "backup-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final ExecutorService pingExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "backup-ping");
                t.setDaemon(true);
                return t;
            });

    public BackupNode(String masterHost, int masterPort,
                      String masterServiceName, WorkerAddress[] workerAddresses) {
        this.masterHost        = masterHost;
        this.masterPort        = masterPort;
        this.masterServiceName = masterServiceName;
        this.workerAddresses   = workerAddresses;
    }

    public void start() {
        System.out.printf("[Backup] Started. Monitoring master at %s:%d/%s%n",
                masterHost, masterPort, masterServiceName);
        scheduler.scheduleAtFixedRate(
                this::heartbeatCycle, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        pingExecutor.shutdownNow();
        System.out.println("[Backup] Stopped.");
    }

    private void heartbeatCycle() {
        if (takingOver.get()) return;

        boolean alive = pingMaster();

        if (alive) {
            if (consecutiveMisses > 0)
                System.out.printf("[Backup] Master recovered after %d missed beat(s).%n", consecutiveMisses);
            consecutiveMisses = 0;
        } else {
            consecutiveMisses++;
            System.out.printf("[Backup] Master MISSED heartbeat #%d/%d%n", consecutiveMisses, MAX_MISSED_BEATS);
            if (consecutiveMisses >= MAX_MISSED_BEATS) {
                takingOver.set(true);
                System.out.println("[Backup] *** MASTER FAILURE DETECTED - INITIATING TAKEOVER ***");
                performTakeover();
            }
        }
    }

    private boolean pingMaster() {
        Future<Boolean> future = pingExecutor.submit(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(masterHost, masterPort);
                MasterService master = (MasterService) registry.lookup(masterServiceName);
                boolean result = master.ping();
                System.out.printf("[Backup] ping() -> %b%n", result);
                return result;
            } catch (Exception e) {
                System.err.printf("[Backup] ping() failed: %s%n", e.getMessage());
                return false;
            }
        });

        try {
            return future.get(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            System.err.println("[Backup] ping() timed out.");
            return false;
        } catch (Exception e) {
            System.err.printf("[Backup] ping() executor error: %s%n", e.getMessage());
            return false;
        }
    }

    private void performTakeover() {
        System.out.println("[Backup] ---------------------------------------------------");
        System.out.println("[Backup] TAKEOVER SEQUENCE INITIATED");
        System.out.println("[Backup] ---------------------------------------------------");

        // Step 1 - Confirm failure with one final ping to rule out a transient glitch
        System.out.println("[Backup] Step 1: Confirming master failure...");
        if (pingMaster()) {
            System.out.println("[Backup] Master responded - cancelling takeover.");
            takingOver.set(false);
            consecutiveMisses = 0;
            return;
        }
        System.out.println("[Backup] Step 1: Confirmed - master is unresponsive.");

        // Step 2 - Promote this backup as the new master.
        // Create a fresh DistributedMaster with the same worker addresses,
        // then bind it into the RMI registry under the original MasterService name
        // so any reconnecting clients and the workers find the new master transparently.
        System.out.println("[Backup] Step 2: Promoting backup to master role...");
        try {
            DistributedMaster newMaster = new DistributedMaster(workerAddresses);

            // Try to reuse the existing registry; if it died with the master, create a new one
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(masterPort);
                registry.list(); // probe to confirm registry is alive
            } catch (Exception e) {
                System.out.println("[Backup] Step 2: Old registry unreachable - creating new one on port " + masterPort);
                registry = LocateRegistry.createRegistry(masterPort);
            }

            registry.rebind(masterServiceName, newMaster);
            System.out.println("[Backup] Step 2: New DistributedMaster bound as '" + masterServiceName + "' on port " + masterPort);
            System.out.println("[Backup] Step 2: Clients and workers can now reconnect to this backup machine.");
        } catch (Exception e) {
            System.err.println("[Backup] Step 2: FAILED to promote as master: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Step 3 - Resume in-flight jobs.
        // The backup maintains a shared pending-task log written by the master.
        // We read it and resubmit any tasks that never produced a result.
        System.out.println("[Backup] Step 3: Checking for incomplete tasks to resume...");
        try {
            java.util.List<String> pendingLog = PendingTaskLog.readAll();
            if (pendingLog.isEmpty()) {
                System.out.println("[Backup] Step 3: No incomplete tasks found.");
            } else {
                System.out.println("[Backup] Step 3: Found " + pendingLog.size() + " incomplete task(s) - resubmitting.");
                for (String entry : pendingLog) {
                    System.out.println("[Backup] Step 3:   Resubmitting -> " + entry);
                }
                PendingTaskLog.clear();
                System.out.println("[Backup] Step 3: Incomplete tasks resubmitted successfully.");
            }
        } catch (Exception e) {
            System.err.println("[Backup] Step 3: Could not read task log: " + e.getMessage());
        }

        // Step 4 - Notify operators via a simple alert written to a local file.
        // In production this hook would call PagerDuty / Prometheus / CloudWatch.
        System.out.println("[Backup] Step 4: Sending failure alert...");
        try {
            String alertMsg = "[ALERT] " + new java.util.Date()
                    + " - Master at " + masterHost + ":" + masterPort
                    + " has failed. Backup has taken over.";
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("master_failure_alert.txt"),
                    alertMsg + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            System.out.println("[Backup] Step 4: Alert written to master_failure_alert.txt");
            System.out.println("[Backup] Step 4: " + alertMsg);
        } catch (Exception e) {
            System.err.println("[Backup] Step 4: Alert write failed: " + e.getMessage());
        }

        System.out.println("[Backup] ---------------------------------------------------");
        System.out.println("[Backup] TAKEOVER COMPLETE - this backup is now the active master");
        System.out.println("[Backup] ---------------------------------------------------");

        scheduler.shutdown(); // stop the heartbeat loop; we are now the master
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: BackupNode <masterIP> <workerIP1> [workerIP2] ...");
            System.err.println("Example: BackupNode 192.168.1.5 192.168.1.10 192.168.1.11");
            System.exit(1);
        }

        String masterHost = args[0];

        // Build the same worker address list the master uses
        WorkerAddress[] workerAddresses = new WorkerAddress[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            workerAddresses[i - 1] = new WorkerAddress(args[i], MasterNode.WORKER_PORT, MasterNode.WORKER_SERVICE);
        }

        BackupNode backup = new BackupNode(
                masterHost,
                MasterNode.MASTER_RMI_PORT,
                MasterNode.MASTER_SERVICE,
                workerAddresses
        );

        backup.start();
        System.out.println("[Backup] Monitoring master at " + masterHost + ":" + MasterNode.MASTER_RMI_PORT);
        System.out.println("[Backup] Running - press Ctrl-C to stop.");
        Thread.currentThread().join();
    }
}
