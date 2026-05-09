package com.distributed.matmul.master;

import com.distributed.matmul.common.MatrixUtils;
import com.distributed.matmul.common.Task;
import com.distributed.matmul.common.TaskResult;

/**
 * Local fallback computation engine used by the master.
 *
 * When a remote worker fails, times out, or is unreachable, the master
 * invokes this class to compute the missing rows in-process.
 *
 * Design:
 *   - Delegates to MatrixUtils.multiplyRows() – the same code path workers use.
 *   - Stateless; all inputs arrive through the Task.
 *   - The master calls this synchronously inside the worker-dispatch thread,
 *     so no additional synchronization is needed here.
 *
 * Guarantee: as long as the master process is alive, every task will eventually
 * complete via this fallback, ensuring correctness regardless of worker failures.
 */
public class LocalFallback {

    /**
     * Compute the task locally, mirroring exactly what a remote worker would do.
     *
     * @param task the failed task
     * @return     a TaskResult identical in shape to what the worker would return
     */
    public static TaskResult compute(Task task) {
        System.out.printf("[LocalFallback] Computing %s locally (worker failed)%n", task);

        long start = System.currentTimeMillis();

        double[][] resultRows = MatrixUtils.multiplyRows(
                task.getASubset(),
                task.getMatrixB(),
                task.getColsA(),
                task.getColsB()
        );

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[LocalFallback] Completed %s in %d ms%n", task, elapsed);

        return new TaskResult(task.getStartRow(), task.getEndRow(), resultRows);
    }
}
