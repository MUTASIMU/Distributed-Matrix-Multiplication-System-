package com.distributed.matmul.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface exposed by the Master.
 * - ping()     : used by the Backup to check if master is alive
 * - multiply() : used by the Client to submit a matrix job
 */
public interface MasterService extends Remote {

    boolean ping() throws RemoteException;

    double[][] multiply(double[][] matrixA, double[][] matrixB) throws RemoteException;
}
