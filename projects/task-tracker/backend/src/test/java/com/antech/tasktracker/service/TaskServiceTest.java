package com.antech.tasktracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.antech.tasktracker.domain.Priority;
import com.antech.tasktracker.domain.Task;
import com.antech.tasktracker.repository.TaskRepository;
import com.antech.tasktracker.web.dto.CreateTaskRequest;
import com.antech.tasktracker.web.dto.UpsertTaskRequest;

class TaskServiceTest {

    private TaskRepository repo;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repo = mock(TaskRepository.class);
        when(repo.saveAndFlush(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new TaskService(repo);
    }

    private static Task existing(UUID id, long version) {
        Task t = new Task(id);
        t.setTitle("Existing");
        ReflectionTestUtils.setField(t, "version", version);
        return t;
    }

    @Test
    void createTrimsTitleBlankNotesAndDefaultsPriority() { // AC-1
        Task t = service.create(new CreateTaskRequest("  Write report  ", "   ", null, null));
        assertThat(t.getId()).isNotNull();
        assertThat(t.getTitle()).isEqualTo("Write report");
        assertThat(t.getNotes()).isNull();
        assertThat(t.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(t.isDone()).isFalse();
    }

    @Test
    void upsertCreatesWhenIdIsUnknown() { // AC-9
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        TaskService.UpsertResult r = service.upsert(id, new UpsertTaskRequest("Offline", null, null, Priority.HIGH, true, 7L));
        assertThat(r.created()).isTrue();
        assertThat(r.task().getId()).isEqualTo(id);
        assertThat(r.task().isDone()).isTrue();
        assertThat(r.task().getVersion()).isNull(); // client version ignored on create
    }

    @Test
    void upsertUpdatesWithCurrentVersion() { // AC-7
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(existing(id, 2)));
        TaskService.UpsertResult r = service.upsert(id, new UpsertTaskRequest("Renamed", "n", null, Priority.LOW, true, 2L));
        assertThat(r.created()).isFalse();
        assertThat(r.task().getTitle()).isEqualTo("Renamed");
        assertThat(r.task().isDone()).isTrue();
    }

    @Test
    void upsertRejectsStaleOrMissingVersion() { // AC-8
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(existing(id, 3)));
        assertThatThrownBy(() -> service.upsert(id, new UpsertTaskRequest("x", null, null, null, false, 2L)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.upsert(id, new UpsertTaskRequest("x", null, null, null, false, null)))
                .isInstanceOf(ConflictException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void upsertRejectsDeletedTask() { // AC-10
        UUID id = UUID.randomUUID();
        Task deleted = existing(id, 1);
        deleted.setDeleted(true);
        when(repo.findById(id)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.upsert(id, new UpsertTaskRequest("x", null, null, null, false, 1L)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("deleted");
    }

    @Test
    void getHidesDeletedTasks() { // AC-6
        UUID id = UUID.randomUUID();
        Task deleted = existing(id, 0);
        deleted.setDeleted(true);
        when(repo.findById(id)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteIsSoft() { // AC-11
        UUID id = UUID.randomUUID();
        Task t = existing(id, 0);
        when(repo.findById(id)).thenReturn(Optional.of(t));
        service.delete(id);
        assertThat(t.isDeleted()).isTrue();
        verify(repo).saveAndFlush(t);
    }
}
