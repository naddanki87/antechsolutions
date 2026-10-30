package com.antech.tasktracker.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antech.tasktracker.domain.Priority;
import com.antech.tasktracker.domain.Task;
import com.antech.tasktracker.repository.TaskRepository;
import com.antech.tasktracker.web.dto.CreateTaskRequest;
import com.antech.tasktracker.web.dto.UpsertTaskRequest;

@Service
@Transactional
public class TaskService {

    /** Result of an idempotent PUT: the saved task and whether it was newly created. */
    public record UpsertResult(Task task, boolean created) {
    }

    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public Task create(CreateTaskRequest req) {
        Task task = new Task(UUID.randomUUID());
        apply(task, req.title(), req.notes(), req.dueDate(), req.priority(), false);
        return tasks.saveAndFlush(task);
    }

    @Transactional(readOnly = true)
    public Page<Task> list(Boolean done, Pageable pageable) {
        return done == null ? tasks.findByDeletedFalse(pageable) : tasks.findByDeletedFalseAndDone(done, pageable);
    }

    @Transactional(readOnly = true)
    public Task get(UUID id) {
        return tasks.findById(id).filter(t -> !t.isDeleted()).orElseThrow(() -> new NotFoundException("Task", id));
    }

    public UpsertResult upsert(UUID id, UpsertTaskRequest req) {
        Optional<Task> existing = tasks.findById(id);
        boolean done = Boolean.TRUE.equals(req.done());
        if (existing.isEmpty()) {
            Task task = new Task(id);
            apply(task, req.title(), req.notes(), req.dueDate(), req.priority(), done);
            return new UpsertResult(tasks.saveAndFlush(task), true);
        }
        Task task = existing.get();
        if (task.isDeleted()) {
            throw new ConflictException("Task " + id + " was deleted");
        }
        if (req.version() == null || !req.version().equals(task.getVersion())) {
            throw new ConflictException("Task " + id + " was changed by someone else; reload it and retry");
        }
        apply(task, req.title(), req.notes(), req.dueDate(), req.priority(), done);
        return new UpsertResult(tasks.saveAndFlush(task), false);
    }

    public void delete(UUID id) {
        Task task = get(id);
        task.setDeleted(true);
        tasks.saveAndFlush(task);
    }

    @Transactional(readOnly = true)
    public List<Task> changesSince(Instant since) {
        return tasks.findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(since);
    }

    private static void apply(Task task, String title, String notes, LocalDate dueDate, Priority priority,
            boolean done) {
        task.setTitle(title.trim());
        task.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        task.setDueDate(dueDate);
        task.setPriority(priority == null ? Priority.MEDIUM : priority);
        task.setDone(done);
    }
}
