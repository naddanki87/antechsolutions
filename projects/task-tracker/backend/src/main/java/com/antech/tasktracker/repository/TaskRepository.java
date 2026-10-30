package com.antech.tasktracker.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.antech.tasktracker.domain.Task;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByDeletedFalse(Pageable pageable);

    Page<Task> findByDeletedFalseAndDone(boolean done, Pageable pageable);

    /** Sync feed, capped so one call stays small; the client repeats until it receives fewer rows. */
    List<Task> findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(Instant since);
}
