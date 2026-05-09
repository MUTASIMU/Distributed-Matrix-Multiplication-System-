package com.distributed.matmul.client;

import com.distributed.matmul.common.MasterService;
import com.distributed.matmul.common.MatrixUtils;
import com.distributed.matmul.master.MasterNode;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

/**
 * Interactive client - connects to the already-running Master over RMI
 * and submits matrix multiplication jobs.
 *
 * The client does NOT create or start the master. The master must already
 * be running (make master) before the client is launched.
 *
 * Because the client talks to the master over RMI, the time spent typing
 * matrices has no effect on the master's heartbeat with the backup.
 *
 * Usage (on master machine, after master is running):
 *   java com.distributed.matmul.client.MatrixClient <masterIP>
 *
 * Example:
 *   java com.distributed.matmul.client.MatrixClient 192.168.1.5
 *
 * Single machine testing:
 *   java com.distributed.matmul.client.MatrixClient 127.0.0.1
 */
public class MatrixClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MatrixClient <masterIP>");
            System.err.println("Example: MatrixClient 192.168.1.5");
            System.err.println("Single machine: MatrixClient 127.0.0.1");
            System.exit(1);
        }

        String masterIP = args[0];
        Scanner sc = new Scanner(System.in);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Distributed Matrix Multiplication - Client     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("  Connecting to master at " + masterIP + ":" + MasterNode.MASTER_RMI_PORT + "...");

        // Connect to the already-running master over RMI
        Registry registry = LocateRegistry.getRegistry(masterIP, MasterNode.MASTER_RMI_PORT);
        MasterService master = (MasterService) registry.lookup(MasterNode.MASTER_SERVICE);

        System.out.println("  Connected successfully. Master is ready.");
        System.out.println();

        // Multiply loop - client just sends jobs and receives results
        while (true) {
            System.out.println("--------------------------------------------------");

            // Matrix A
            System.out.println("Enter Matrix A:");
            System.out.print("  Rows of A: ");
            int rowsA = Integer.parseInt(sc.nextLine().trim());
            System.out.print("  Columns of A: ");
            int colsA = Integer.parseInt(sc.nextLine().trim());
            double[][] matrixA = readMatrix(sc, "A", rowsA, colsA);

            // Matrix B
            System.out.println("\nEnter Matrix B:");
            System.out.printf("  Rows of B (must be %d): ", colsA);
            int rowsB = Integer.parseInt(sc.nextLine().trim());
            if (rowsB != colsA) {
                System.out.printf("  [Error] Rows of B must be %d but got %d. Try again.%n", colsA, rowsB);
                continue;
            }
            System.out.print("  Columns of B: ");
            int colsB = Integer.parseInt(sc.nextLine().trim());
            double[][] matrixB = readMatrix(sc, "B", rowsB, colsB);

            // Show what was entered
            System.out.println("\nMatrix A (" + rowsA + "x" + colsA + "):");
            System.out.print(MatrixUtils.format(matrixA));
            System.out.println("Matrix B (" + rowsB + "x" + colsB + "):");
            System.out.print(MatrixUtils.format(matrixB));

            // Send job to master over RMI - master distributes to workers
            System.out.println("\n[Client] Sending job to master...");
            long start = System.currentTimeMillis();

            double[][] result = master.multiply(matrixA, matrixB);

            long elapsed = System.currentTimeMillis() - start;

            // Show result
            System.out.println("\nResult A x B (" + rowsA + "x" + colsB + "):");
            System.out.print(MatrixUtils.format(result));
            System.out.printf("[Client] Done in %d ms%n", elapsed);

            System.out.print("\nMultiply another pair? (yes/no): ");
            String again = sc.nextLine().trim().toLowerCase();
            if (!again.equals("yes") && !again.equals("y")) break;
        }

        System.out.println("[Client] Goodbye.");
        sc.close();
    }

    private static double[][] readMatrix(Scanner sc, String name, int rows, int cols) {
        double[][] matrix = new double[rows][cols];
        System.out.printf("Enter Matrix %s row by row (space or comma separated):%n", name);
        for (int i = 0; i < rows; i++) {
            while (true) {
                System.out.printf("  Row %d: ", i + 1);
                String line = sc.nextLine().trim().replaceAll(",", " ");
                String[] tokens = line.split("\\s+");
                if (tokens.length != cols) {
                    System.out.printf("  [Error] Expected %d values, got %d. Try again.%n", cols, tokens.length);
                    continue;
                }
                try {
                    for (int j = 0; j < cols; j++)
                        matrix[i][j] = Double.parseDouble(tokens[j]);
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("  [Error] Invalid number. Try again.");
                }
            }
        }
        return matrix;
    }
}
