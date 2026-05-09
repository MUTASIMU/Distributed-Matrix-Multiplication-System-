package com.distributed.matmul.master;

import com.distributed.matmul.common.Task;
import com.distributed.matmul.common.TaskResult;
import com.distributed.matmul.common.WorkerAddress;
import com.distributed.matmul.common.WorkerService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.*;

public class WorkerProxy {

    private static final long TIMEOUT_SECONDS = 10;

    private final WorkerAddress address;
    private volatile WorkerService stub;
    private final ExecutorService executor;

    public WorkerProxy(WorkerAddress address) {
        this.address = address;
        // Initialized in constructor AFTER address is assigned - fixes compile error
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rmi-" + address.getServiceName());
            t.setDaemon(true);
            return t;
        });
    }

    public TaskResult compute(Task task) throws Exception {
        WorkerService worker = getStub();
        Future<TaskResult> future = executor.submit(() -> worker.compute(task));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            stub = null;
            throw new Exception("Worker " + address + " timed out on " + task, e);
        } catch (ExecutionException e) {
            stub = null;
            throw new Exception("Worker " + address + " failed on " + task, e.getCause());
        }
    }

    private synchronized WorkerService getStub() throws Exception {
        if (stub == null) {
            Registry registry = LocateRegistry.getRegistry(address.getHost(), address.getPort());
            stub = (WorkerService) registry.lookup(address.getServiceName());
        }
        return stub;
    }

    public WorkerAddress getAddress() { return address; }

    public void shutdown() { executor.shutdownNow(); }

    @Override
    public String toString() {
        return "WorkerProxy[" + address + "]";
    }
}
