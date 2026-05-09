package com.distributed.matmul.common;

/**
 * Stores the network address of a single worker.
 *
 * Worker addresses are kept in an in-memory array inside the Master; no config
 * files or databases are used for discovery.
 */
public class WorkerAddress {

    private final String host;
    private final int    port;
    private final String serviceName;

    public WorkerAddress(String host, int port, String serviceName) {
        this.host        = host;
        this.port        = port;
        this.serviceName = serviceName;
    }

    public String getHost()        { return host;        }
    public int    getPort()        { return port;        }
    public String getServiceName() { return serviceName; }

    /** Builds the canonical RMI URL: rmi://host:port/serviceName */
    public String toRmiUrl() {
        return String.format("rmi://%s:%d/%s", host, port, serviceName);
    }

    @Override
    public String toString() {
        return toRmiUrl();
    }
}
