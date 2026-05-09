package com.distributed.matmul.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface exposed by every Worker node.
 *
 * Design decision: workers expose exactly ONE operation as required by the spec.
 * The master calls this method; the worker does the math and returns its partial
 * result. Workers know nothing about the master or each other.
 */
public interface WorkerService extends Remote {

    /**
     * Compute the matrix-multiplication rows described by the task.
     *
     * @param task  contains aSubset, matrixB, row range, and dimensions
     * @return      the computed rows for [task.startRow, task.endRow)
     * @throws RemoteException if the network or worker fails
     */
    TaskResult compute(Task task) throws RemoteException;
}
