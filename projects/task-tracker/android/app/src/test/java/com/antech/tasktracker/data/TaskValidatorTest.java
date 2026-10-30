package com.antech.tasktracker.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TaskValidatorTest {

    @Test
    public void acceptsValidTask() {
        assertNull(TaskValidator.validate("Buy milk", null, null, "MEDIUM"));
        assertNull(TaskValidator.validate("Buy milk", "2 litres", "2026-11-05", "HIGH"));
    }

    @Test
    public void rejectsBlankOrLongTitle() {
        assertEquals("Title is required", TaskValidator.validate("   ", null, null, "LOW"));
        assertEquals("Title must be at most 120 characters",
                TaskValidator.validate(new String(new char[121]).replace('\0', 'x'), null, null, "LOW"));
    }

    @Test
    public void rejectsLongNotesBadDateAndUnknownPriority() {
        assertEquals("Notes must be at most 1000 characters",
                TaskValidator.validate("t", new String(new char[1001]).replace('\0', 'n'), null, "LOW"));
        assertEquals("Due date must be yyyy-MM-dd", TaskValidator.validate("t", null, "05/11/2026", "LOW"));
        assertEquals("Unknown priority", TaskValidator.validate("t", null, null, "URGENT"));
    }
}
