package com.antech.tasktracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.antech.tasktracker.domain.Task;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired
    private TaskRepository repo;

    private Task save(String title, boolean done, boolean deleted) {
        Task t = new Task(UUID.randomUUID());
        t.setTitle(title);
        t.setDone(done);
        t.setDeleted(deleted);
        return repo.saveAndFlush(t);
    }

    @Test
    void filtersByDoneAndExcludesDeleted() {
        save("open", false, false);
        save("done", true, false);
        save("gone", true, true);
        assertThat(repo.findByDeletedFalse(PageRequest.of(0, 10)).getContent()).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("open", "done");
        assertThat(repo.findByDeletedFalseAndDone(true, PageRequest.of(0, 10)).getContent()).extracting(Task::getTitle)
                .containsExactly("done");
    }

    @Test
    void changesIncludeDeletedInUpdateOrder() throws InterruptedException {
        Instant before = Instant.now().minusMillis(1);
        save("first", false, false);
        Thread.sleep(5);
        save("second", false, true);
        assertThat(repo.findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(before)).extracting(Task::getTitle)
                .containsExactly("first", "second");
    }

    @Test
    void versionStartsAtZeroAndIncrementsOnUpdate() {
        Task t = save("v", false, false);
        assertThat(t.getVersion()).isZero();
        t.setTitle("v2");
        assertThat(repo.saveAndFlush(t).getVersion()).isEqualTo(1L);
    }
}
