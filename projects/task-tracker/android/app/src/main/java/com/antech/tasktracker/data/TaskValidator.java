package com.antech.tasktracker.data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/** Same rules as the backend's Bean Validation, checked before anything is saved. */
public final class TaskValidator {

    public static final int MAX_TITLE = 120;
    public static final int MAX_NOTES = 1000;

    private TaskValidator() {
    }

    /** @return an error message, or null when the input is valid. */
    public static String validate(String title, String notes, String dueDate, String priority) {
        if (title == null || title.trim().isEmpty()) return "Title is required";
        if (title.trim().length() > MAX_TITLE) return "Title must be at most " + MAX_TITLE + " characters";
        if (notes != null && notes.length() > MAX_NOTES) return "Notes must be at most " + MAX_NOTES + " characters";
        if (dueDate != null && !dueDate.isEmpty()) {
            try {
                LocalDate.parse(dueDate);
            } catch (DateTimeParseException e) {
                return "Due date must be yyyy-MM-dd";
            }
        }
        if (priority == null || !Arrays.asList(Task.PRIORITIES).contains(priority)) return "Unknown priority";
        return null;
    }
}
