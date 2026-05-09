package com.distributed.matmul.common;

/**
 * Shared utility methods for matrix operations.
 *
 * Used by:
 *   - WorkerServiceImpl  → for the distributed path
 *   - DistributedMaster  → for the local fallback when a worker fails
 */
public final class MatrixUtils {

    private MatrixUtils() {}

    /**
     * Multiply a subset of rows of A (aSubset) against full matrix B.
     *
     * aSubset has dimensions  (endRow - startRow) x colsA
     * matrixB has dimensions  colsA x colsB
     * Result has dimensions   (endRow - startRow) x colsB
     *
     * Standard O(n³) triple loop; can be replaced with BLAS/EJML for production.
     */
    public static double[][] multiplyRows(double[][] aSubset,
                                          double[][] matrixB,
                                          int colsA, int colsB) {
        int numRows = aSubset.length;
        double[][] result = new double[numRows][colsB];

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < colsB; j++) {
                double sum = 0.0;
                for (int k = 0; k < colsA; k++) {
                    sum += aSubset[i][k] * matrixB[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }

    /**
     * Extracts rows [startRow, endRow) from matrix A.
     * The returned array is a fresh copy, safe for RMI serialization.
     */
    public static double[][] extractRows(double[][] matrix, int startRow, int endRow) {
        int count = endRow - startRow;
        double[][] subset = new double[count][];
        for (int i = 0; i < count; i++) {
            subset[i] = matrix[startRow + i].clone();
        }
        return subset;
    }

    /**
     * Pretty-print a matrix for debugging.
     */
    public static String format(double[][] m) {
        if (m == null) return "[null]";
        StringBuilder sb = new StringBuilder();
        for (double[] row : m) {
            sb.append("  [");
            for (int j = 0; j < row.length; j++) {
                sb.append(String.format("%8.0f", row[j]));
                if (j < row.length - 1) sb.append(", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    /**
     * Validate that A × B is legal (colsA == rowsB).
     */
    public static void validateDimensions(double[][] a, double[][] b) {
        if (a == null || b == null) throw new IllegalArgumentException("Matrices must not be null");
        if (a.length == 0 || b.length == 0) throw new IllegalArgumentException("Matrices must not be empty");
        if (a[0].length != b.length) {
            throw new IllegalArgumentException(String.format(
                "Dimension mismatch: A is %dx%d but B is %dx%d",
                a.length, a[0].length, b.length, b[0].length));
        }
    }
}
