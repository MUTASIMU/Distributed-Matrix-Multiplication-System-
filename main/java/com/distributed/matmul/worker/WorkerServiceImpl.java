package com.distributed.matmul.worker;

import com.distributed.matmul.common.MatrixUtils;
import com.distributed.matmul.common.Task;
import com.distributed.matmul.common.TaskResult;
import com.distributed.matmul.common.WorkerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Worker implementation.
 *
 * Responsibilities:
 *   - Extend UnicastRemoteObject to be exportable over RMI.
 *   - Implement the single remote method: compute(Task).
 *   - Do the actual row-range matrix multiplication using MatrixUtils.
 *   - Log activity for observability.
 *
 * Design notes:
 *   - Workers are stateless between calls; they hold no matrix state.
 *   - compute() is thread-safe because all state lives in the Task parameter.
 *   - The worker does NOT know the master's address; it just waits for work.
 */
public class WorkerServiceImpl extends UnicastRemoteObject implements WorkerService {

    private static final long serialVersionUID = 1L;

    private final String workerId;

    public WorkerServiceImpl(String workerId) throws RemoteException {
        super(); // exports the object on an anonymous port
        this.workerId = workerId;
    }

    /**
     * Compute the matrix rows described in the task.
     *
     * Steps:
     *   1. Log receipt of task.
     *   2. Delegate the math to MatrixUtils.multiplyRows().
     *   3. Wrap result in TaskResult and return.
     *
     * RemoteException is declared but only thrown by the RMI layer itself
     * (e.g., serialization failure); normal computation errors become
     * RuntimeExceptions which RMI wraps in RemoteException automatically.
     */
    @Override
    public TaskResult compute(Task task) throws RemoteException {
        System.out.printf("[%s] Received %s (%d rows, colsA=%d, colsB=%d)%n",
                workerId, task, task.rowCount(), task.getColsA(), task.getColsB());

        long start = System.currentTimeMillis();

        double[][] resultRows = MatrixUtils.multiplyRows(
                task.getASubset(),
                task.getMatrixB(),
                task.getColsA(),
                task.getColsB()
        );

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[%s] Completed %s in %d ms%n", workerId, task, elapsed);

        return new TaskResult(task.getStartRow(), task.getEndRow(), resultRows);
    }
}
