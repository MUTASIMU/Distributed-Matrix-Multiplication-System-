package com.distributed.matmul.master;

import com.distributed.matmul.common.Task;
import com.distributed.matmul.common.MatrixUtils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe queue of Tasks to be distributed across workers.
 *
 * Handles two edge cases required by the spec:
 *
 *   (A) rows > workers:
 *       Tasks are split into chunks of size Math.ceil(rows / workers).
 *       The last chunk may be smaller.
 *
 *   (B) workers > rows:
 *       Each row becomes its own task; excess workers simply get nothing.
 *       poll() returns null when the queue is drained.
 *
 * Design: we use a simple Deque protected by synchronized methods.
 * For very large task counts a ConcurrentLinkedDeque would avoid contention,
 * but for typical matrix sizes this is sufficient.
 */
public class TaskQueue {

    private final Deque<Task> pending = new ArrayDeque<>();

    /**
     * Build and enqueue all tasks for an A×B multiplication.
     *
     * @param matrixA    full matrix A
     * @param matrixB    full matrix B
     * @param numWorkers number of available workers
     */
    public TaskQueue(double[][] matrixA, double[][] matrixB, int numWorkers) {
        int rowsA = matrixA.length;
        int colsA = matrixA[0].length;
        int colsB = matrixB[0].length;

        // How many rows per task? At least 1; at most rowsA.
        int chunkSize = Math.max(1, (int) Math.ceil((double) rowsA / numWorkers));

        int row = 0;
        while (row < rowsA) {
            int start = row;
            int end   = Math.min(row + chunkSize, rowsA);

            double[][] aSubset = MatrixUtils.extractRows(matrixA, start, end);

            // Note: matrixB is shared across tasks. Each task holds a reference
            // to the same array; this is safe because tasks only READ matrixB.
            pending.addLast(new Task(start, end, aSubset, matrixB, colsA, colsB));

            row = end;
        }

        System.out.printf("[TaskQueue] Created %d tasks for %d rows across %d workers (chunkSize=%d)%n",
                pending.size(), rowsA, numWorkers, chunkSize);
    }

    /** Retrieve and remove the next pending task, or null if the queue is empty. */
    public synchronized Task poll() {
        return pending.pollFirst();
    }

    /** Return a task to the front of the queue (used when a worker fails). */
    public synchronized void returnTask(Task task) {
        pending.addFirst(task); // front = highest priority
        System.out.printf("[TaskQueue] Returned %s to queue (queue size now %d)%n",
                task, pending.size());
    }

    /** How many tasks are still waiting. */
    public synchronized int size() {
        return pending.size();
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }
}
