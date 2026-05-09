package com.distributed.matmul.backup;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Simple file-based log of tasks that the master dispatched but whose
 * results had not yet been confirmed when the master died.
 *
 * The master writes an entry when it sends a task to a worker.
 * It removes the entry when the result arrives.
 * If the master crashes before removing an entry, the backup finds it here
 * and resubmits it.
 *
 * File: pending_tasks.log  (same directory as the running JVM)
 * Format: one line per pending task, e.g.  "rows 0-2 | worker 192.168.1.10"
 */
public class PendingTaskLog {

    private static final Path LOG_FILE = Path.of("pending_tasks.log");

    /** Write a task entry when the master dispatches it. */
    public static synchronized void add(String entry) {
        try {
            Files.writeString(LOG_FILE, entry + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[PendingTaskLog] Failed to write entry: " + e.getMessage());
        }
    }

    /** Remove a task entry when its result is successfully received. */
    public static synchronized void remove(String entry) {
        try {
            if (!Files.exists(LOG_FILE)) return;
            List<String> lines = Files.readAllLines(LOG_FILE);
            lines.remove(entry);
            Files.write(LOG_FILE, lines);
        } catch (IOException e) {
            System.err.println("[PendingTaskLog] Failed to remove entry: " + e.getMessage());
        }
    }

    /** Read all pending (unconfirmed) task entries. */
    public static synchronized List<String> readAll() {
        try {
            if (!Files.exists(LOG_FILE)) return Collections.emptyList();
            return new ArrayList<>(Files.readAllLines(LOG_FILE));
        } catch (IOException e) {
            System.err.println("[PendingTaskLog] Failed to read log: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Clear the log after backup has resubmitted all pending tasks. */
    public static synchronized void clear() {
        try {
            Files.deleteIfExists(LOG_FILE);
        } catch (IOException e) {
            System.err.println("[PendingTaskLog] Failed to clear log: " + e.getMessage());
        }
    }
}
