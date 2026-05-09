package com.distributed.matmul.common;

import java.io.Serializable;

/**
 * Encapsulates everything a worker needs to compute its assigned row range.
 * Serializable so it can be transmitted over Java RMI.
 *
 * Design note: we ship only the rows of A that this worker needs, not the
 * full matrix, to reduce network traffic. Matrix B is always sent in full
 * because every row of A needs every column of B.
 */
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Global row index (inclusive) in matrix A where this task begins. */
    private final int startRow;

    /** Global row index (exclusive) in matrix A where this task ends. */
    private final int endRow;

    /**
     * The relevant rows of A for this task.
     * aSubset[i] corresponds to global row (startRow + i).
     * Dimensions: (endRow - startRow) x colsA
     */
    private final double[][] aSubset;

    /**
     * Full matrix B.
     * Dimensions: colsA x colsB
     */
    private final double[][] matrixB;

    /** Number of columns in A (== number of rows in B). */
    private final int colsA;

    /** Number of columns in B (== number of columns in the result). */
    private final int colsB;

    public Task(int startRow, int endRow,
                double[][] aSubset, double[][] matrixB,
                int colsA, int colsB) {
        this.startRow = startRow;
        this.endRow   = endRow;
        this.aSubset  = aSubset;
        this.matrixB  = matrixB;
        this.colsA    = colsA;
        this.colsB    = colsB;
    }

    public int getStartRow() { return startRow; }
    public int getEndRow()   { return endRow;   }
    public double[][] getASubset()  { return aSubset;  }
    public double[][] getMatrixB()  { return matrixB;  }
    public int getColsA() { return colsA; }
    public int getColsB() { return colsB; }

    /** Number of rows assigned to this task. */
    public int rowCount() { return endRow - startRow; }

    @Override
    public String toString() {
        return String.format("Task[rows %d..%d)", startRow, endRow);
    }
}
