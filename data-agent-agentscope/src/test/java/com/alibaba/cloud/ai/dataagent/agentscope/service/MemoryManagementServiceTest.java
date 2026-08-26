/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryManagementServiceTest {

	private static final long AGENT_ID = 7L;

	private InMemoryStore store;

	private MemoryManagementService service;

	@BeforeEach
	void setUp() {
		store = new InMemoryStore();
		service = new MemoryManagementService(store);
	}

	@Test
	void savesAndReturnsConsolidatedMemoryForGlobalUser() {
		service.saveConsolidated(AGENT_ID, "用户偏好中文回答");

		var overview = service.getMemories(AGENT_ID);

		assertThat(overview.userId()).isEqualTo("1");
		assertThat(overview.consolidated().path()).isEqualTo("MEMORY.md");
		assertThat(overview.consolidated().content()).isEqualTo("用户偏好中文回答");
	}

	@Test
	void deletesOneDailyMemoryWithoutAffectingOthers() {
		List<String> namespace = List.of("agents", "data-agent-7", "users", "1", "memory");
		store.put(namespace, "/2026-08-18.md", Map.of("content", "old"));
		store.put(namespace, "/2026-08-19.md", Map.of("content", "new"));

		service.deleteDaily(AGENT_ID, "2026-08-18.md");

		assertThat(service.getMemories(AGENT_ID).daily()).extracting(MemoryManagementService.MemoryDocument::path)
			.containsExactly("2026-08-19.md");
	}

	@Test
	void rejectsUnsafeDailyMemoryPath() {
		assertThatThrownBy(() -> service.deleteDaily(AGENT_ID, "../MEMORY.md"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Invalid daily memory");
	}

	@Test
	void clearsConsolidatedAndDailyMemories() {
		service.saveConsolidated(AGENT_ID, "remember");
		store.put(List.of("agents", "data-agent-7", "users", "1", "memory"), "/2026-08-19.md",
				Map.of("content", "daily"));

		service.clearAll(AGENT_ID);

		var overview = service.getMemories(AGENT_ID);
		assertThat(overview.consolidated()).isNull();
		assertThat(overview.daily()).isEmpty();
	}

}
