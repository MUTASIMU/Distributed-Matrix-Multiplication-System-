package com.distributed.matmul.worker;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.InetAddress;

/**
 * Entry point for a Worker machine.
 *
 * Run this on EACH worker machine. No arguments needed.
 * The worker registers itself on port 2001 under the name "WorkerService".
 *
 * Usage (on each worker machine):
 *   java com.distributed.matmul.worker.WorkerNode
 *
 * Then give the IP of this machine to the Client when it asks.
 *
 * To find your machine's IP on Windows:  ipconfig
 * To find your machine's IP on Linux:    hostname -I
 */
public class WorkerNode {

    public static final int    PORT         = 2001;
    public static final String SERVICE_NAME = "WorkerService";

    public static void main(String[] args) throws Exception {
        String hostname = InetAddress.getLocalHost().getHostAddress();

        // Tell RMI to advertise this machine's real IP so the master can call back
        System.setProperty("java.rmi.server.hostname", hostname);

        Registry registry = LocateRegistry.createRegistry(PORT);

        WorkerServiceImpl worker = new WorkerServiceImpl("Worker@" + hostname);
        registry.rebind(SERVICE_NAME, worker);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║            Worker Node Started                   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("  This machine IP : " + hostname);
        System.out.println("  Listening on    : port " + PORT);
        System.out.println("  Service name    : " + SERVICE_NAME);
        System.out.println();
        System.out.println("  Give this IP to the Client when it asks for a worker address.");
        System.out.println("  Waiting for tasks... (Ctrl-C to stop)");

        Thread.currentThread().join();
    }
}
