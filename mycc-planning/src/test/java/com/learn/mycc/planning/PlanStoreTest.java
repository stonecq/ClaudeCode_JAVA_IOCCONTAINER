package com.learn.mycc.planning;

import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlanStoreTest {

    @TempDir
    Path tempDir;

    PlanStore store;

    @BeforeEach
    void setUp() {
        store = new PlanStore(new FileStorage(tempDir));
    }

    @Test
    void savesAndLoadsPlanRoundTrip() {
        Plan plan = Plan.of("实现X", List.of("设计", "编码"));
        Plan advanced = plan.markDone(0);

        store.save("sess-1", advanced);
        Optional<Plan> loaded = store.load("sess-1");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().goal()).isEqualTo("实现X");
        assertThat(loaded.get().steps().get(0).done()).isTrue();
        assertThat(loaded.get().steps().get(1).done()).isFalse();
        assertThat(loaded.get().nextIndex()).isEqualTo(1);
    }

    @Test
    void saveOverwritesPreviousPlanPerSession() {
        store.save("sess-1", Plan.of("旧目标", List.of("旧步骤")));
        store.save("sess-1", Plan.of("新目标", List.of("新步骤")));

        assertThat(store.load("sess-1").get().goal()).isEqualTo("新目标");
    }

    @Test
    void plansAreIsolatedPerSession() {
        store.save("sess-1", Plan.of("A", List.of("a")));
        assertThat(store.load("sess-2")).isEmpty();
    }

    @Test
    void loadMissingReturnsEmpty() {
        assertThat(store.load("nope")).isEmpty();
    }

    @Test
    void deleteRemovesPlanForSession() {
        store.save("sess-1", Plan.of("实现X", List.of("设计")));
        store.delete("sess-1");
        assertThat(store.load("sess-1")).isEmpty();
    }

    @Test
    void deleteMissingSessionDoesNotThrow() {
        store.delete("no-such-session");
    }
}