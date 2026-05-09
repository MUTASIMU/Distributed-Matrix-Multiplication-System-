package com.distributed.matmul.common;

import java.io.Serializable;

/**
 * The partial result returned by a worker after completing its Task.
 *
 * Contains:
 *  - the computed rows (result[i] = global row startRow + i)
 *  - the original row range so the master can place them correctly
 */
public class TaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The row range this result corresponds to. */
    private final int startRow;
    private final int endRow;

    /**
     * Computed rows.
     * rows[i] is the result for global row (startRow + i).
     * Dimensions: (endRow - startRow) x colsB
     */
    private final double[][] rows;

    public TaskResult(int startRow, int endRow, double[][] rows) {
        this.startRow = startRow;
        this.endRow   = endRow;
        this.rows     = rows;
    }

    public int getStartRow() { return startRow; }
    public int getEndRow()   { return endRow;   }
    public double[][] getRows() { return rows; }

    @Override
    public String toString() {
        return String.format("TaskResult[rows %d..%d)]", startRow, endRow);
    }
}
