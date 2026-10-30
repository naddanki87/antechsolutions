package com.antech.tasktracker.data;

/** Local task model. Field names match the REST contract's JSON. */
public final class Task {

    public static final String[] PRIORITIES = { "LOW", "MEDIUM", "HIGH" };

    public String id;
    public String title;
    public String notes;
    /** ISO date yyyy-MM-dd, or null. */
    public String dueDate;
    public String priority = "MEDIUM";
    public boolean done;
    public boolean deleted;
    /** Server version; null until the task has been synced once. */
    public Long version;
    /** Epoch millis of the last change (local or server). */
    public long updatedAt;
    /** True when the row has local changes not yet uploaded. */
    public boolean dirty;
}
