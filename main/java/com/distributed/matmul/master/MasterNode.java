package com.distributed.matmul.master;

import com.distributed.matmul.common.WorkerAddress;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Starts the Master process.
 *
 * The master registers on RMI IMMEDIATELY on startup so:
 *   - The Backup can start pinging right away (ping() is live)
 *   - The Client can connect at any time and call multiply() over RMI
 *
 * The master never waits for user input - it just sits and serves requests.
 *
 * Usage (on master machine):
 *   java com.distributed.matmul.master.MasterNode <workerIP1> [workerIP2] ...
 *
 * Example:
 *   java com.distributed.matmul.master.MasterNode 192.168.1.10 192.168.1.11
 *
 * Single machine testing:
 *   java com.distributed.matmul.master.MasterNode 127.0.0.1
 */
public class MasterNode {

    public static final int    MASTER_RMI_PORT = 1099;
    public static final String MASTER_SERVICE  = "MasterService";
    public static final int    WORKER_PORT     = 2001;
    public static final String WORKER_SERVICE  = "WorkerService";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MasterNode <workerIP1> [workerIP2] ...");
            System.err.println("Example: MasterNode 192.168.1.10 192.168.1.11");
            System.err.println("Single machine: MasterNode 127.0.0.1");
            System.exit(1);
        }

        // Build worker address list from command-line IPs
        WorkerAddress[] addresses = new WorkerAddress[args.length];
        for (int i = 0; i < args.length; i++) {
            addresses[i] = new WorkerAddress(args[i], WORKER_PORT, WORKER_SERVICE);
        }

        // Create master and register on RMI immediately
        DistributedMaster master = new DistributedMaster(addresses);
        Registry registry = LocateRegistry.createRegistry(MASTER_RMI_PORT);
        registry.rebind(MASTER_SERVICE, master);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║             Master Node Started                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("  RMI port    : " + MASTER_RMI_PORT);
        System.out.println("  Service     : " + MASTER_SERVICE);
        System.out.println("  Status      : READY - backup can ping, client can connect");
        System.out.println();
        System.out.println("  Now start the backup, then run the client to submit jobs.");
        System.out.println("  Waiting for jobs... (Ctrl-C to stop)");

        // Keep alive - master serves RMI calls indefinitely
        Thread.currentThread().join();
    }
}
