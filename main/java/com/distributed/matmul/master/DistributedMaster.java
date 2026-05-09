package com.distributed.matmul.master;

import com.distributed.matmul.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @auther Mutasimu Ali
 * Core master - registers on RMI immediately on startup so the backup
 * can ping it right away. The client connects over RMI and calls multiply()
 * remotely, so typing time does not affect the heartbeat at all.
 */
public class DistributedMaster extends UnicastRemoteObject implements MasterService {

    private static final long serialVersionUID = 1L;

    private final WorkerAddress[] workerAddresses;
    private final WorkerProxy[]   workerProxies;

    public DistributedMaster(WorkerAddress[] workerAddresses) throws RemoteException {
        super();
        this.workerAddresses = workerAddresses;
        this.workerProxies   = new WorkerProxy[workerAddresses.length];
        for (int i = 0; i < workerAddresses.length; i++) {
            workerProxies[i] = new WorkerProxy(workerAddresses[i]);
        }
        System.out.println("[Master] Initialised with " + workerAddresses.length + " worker(s):");
        for (WorkerAddress a : workerAddresses)
            System.out.println("  -> " + a.toRmiUrl());
    }

    // ── Heartbeat (called by Backup every 2 s) ────────────────────────────────
    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    // ── Computation (called by Client over RMI) ───────────────────────────────
    @Override
    public double[][] multiply(double[][] matrixA, double[][] matrixB) throws RemoteException {
        try {
            MatrixUtils.validateDimensions(matrixA, matrixB);

            int rowsA = matrixA.length;
            int colsB = matrixB[0].length;
            int effectiveWorkers = Math.max(1, Math.min(workerProxies.length, rowsA));

            System.out.printf("%n[Master] Job received: A(%dx%d) x B(%dx%d), workers=%d%n",
                    rowsA, matrixA[0].length, matrixB.length, colsB, workerProxies.length);

            // 1. Build task queue
            TaskQueue queue = new TaskQueue(matrixA, matrixB, effectiveWorkers);
            int totalTasks  = queue.size();

            // 2. Result collector
            BlockingQueue<TaskResult> results = new LinkedBlockingQueue<>();
            AtomicInteger completed = new AtomicInteger(0);

            // 3. One dispatcher thread per worker
            int threadCount = Math.min(workerProxies.length, totalTasks);
            if (threadCount == 0) threadCount = 1;

            ExecutorService pool = Executors.newFixedThreadPool(threadCount,
                    r -> new Thread(r, "dispatcher-" + UUID.randomUUID().toString().substring(0, 6)));

            for (int w = 0; w < threadCount; w++) {
                final int idx = w % workerProxies.length;
                pool.submit(() -> dispatchLoop(queue, idx, results, completed, totalTasks));
            }

            // 4. Merge results
            double[][] result = new double[rowsA][colsB];
            int merged = 0;
            while (merged < totalTasks) {
                TaskResult tr = results.poll(30, TimeUnit.SECONDS);
                if (tr == null)
                    throw new RemoteException("[Master] Timed out waiting for results.");
                for (int i = 0; i < tr.getRows().length; i++)
                    result[tr.getStartRow() + i] = tr.getRows()[i];
                merged++;
                System.out.printf("[Master] Merged %s (%d/%d)%n", tr, merged, totalTasks);
            }

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("[Master] Job complete.");
            return result;

        } catch (InterruptedException e) {
            throw new RemoteException("Master interrupted during computation", e);
        }
    }

    // ── Dispatcher loop (one per worker thread) ───────────────────────────────
    private void dispatchLoop(TaskQueue queue, int workerIndex,
                              BlockingQueue<TaskResult> results,
                              AtomicInteger completed, int totalTasks) {
        WorkerProxy proxy = workerProxies[workerIndex];
        String name = Thread.currentThread().getName();

        while (true) {
            Task task = queue.poll();
            if (task == null) break;

            TaskResult result;
            try {
                System.out.printf("[%s] Sending %s to %s%n", name, task, proxy);
                result = proxy.compute(task);
                System.out.printf("[%s] Got result for %s%n", name, task);
            } catch (Exception ex) {
                System.err.printf("[%s] Worker %s FAILED on %s: %s%n", name, proxy, task, ex.getMessage());
                System.err.printf("[%s] Falling back to local computation for %s%n", name, task);
                result = LocalFallback.compute(task);
            }

            results.add(result);
            completed.incrementAndGet();
        }

        System.out.printf("[%s] Dispatcher done.%n", name);
    }

    public void shutdown() {
        for (WorkerProxy p : workerProxies) p.shutdown();
    }
}
