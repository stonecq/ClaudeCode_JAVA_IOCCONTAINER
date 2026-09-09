package com.learn.mycc.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryIndexTest {

    @Test
    void upsertAddsNewEntry() {
        String result = MemoryIndex.upsert(null, "prefs", "偏好中文回复");
        assertThat(result).isEqualTo("- prefs: 偏好中文回复");
    }

    @Test
    void upsertAppendsMultipleEntries() {
        String first = MemoryIndex.upsert(null, "prefs", "偏好中文回复");
        String second = MemoryIndex.upsert(first, "build", "常用 Maven 命令");
        assertThat(second).isEqualTo("""
                - prefs: 偏好中文回复
                - build: 常用 Maven 命令""");
    }

    @Test
    void upsertReplacesDescriptionOfSameId() {
        String first = MemoryIndex.upsert(null, "prefs", "旧描述");
        String updated = MemoryIndex.upsert(first, "prefs", "新描述");
        assertThat(updated).isEqualTo("- prefs: 新描述");
        assertThat(updated.lines().count()).isEqualTo(1);
    }

    @Test
    void upsertUpdatesDescriptionWithoutMovingPosition() {
        String a = MemoryIndex.upsert(null, "a", "A");
        String b = MemoryIndex.upsert(a, "b", "B");
        String updated = MemoryIndex.upsert(b, "a", "A2");
        assertThat(updated).isEqualTo("""
                - a: A2
                - b: B""");
    }

    @Test
    void removeRemovesMatchingId() {
        String idx = MemoryIndex.upsert(MemoryIndex.upsert(null, "a", "A"), "b", "B");
        String result = MemoryIndex.remove(idx, "a");
        assertThat(result).isEqualTo("- b: B");
    }

    @Test
    void removeUnmatchedIdKeepsIndexUnchanged() {
        String idx = MemoryIndex.upsert(null, "a", "A");
        String result = MemoryIndex.remove(idx, "nope");
        assertThat(result).isEqualTo("- a: A");
    }

    @Test
    void removeLastEntryReturnsEmpty() {
        String idx = MemoryIndex.upsert(null, "a", "A");
        assertThat(MemoryIndex.remove(idx, "a")).isEmpty();
    }
}