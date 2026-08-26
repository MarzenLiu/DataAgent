/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.WorkspaceStoreRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseWorkspaceStoreTest {

	@Mock
	private WorkspaceStoreMapper mapper;

	private DatabaseWorkspaceStore store;

	@BeforeEach
	void setUp() {
		store = new DatabaseWorkspaceStore(mapper, new ObjectMapper());
	}

	@Test
	void readsSerializedStoreItem() {
		when(mapper.findByStoreKey(anyString())).thenReturn(new WorkspaceStoreRow("hash", "namespace", "/MEMORY.md",
				"{\"content\":\"remember me\",\"encoding\":\"utf-8\"}", 3));

		var item = store.get(List.of("agents", "data-agent-1", "users", "1", "root"), "/MEMORY.md");

		assertThat(item.key()).isEqualTo("/MEMORY.md");
		assertThat(item.version()).isEqualTo(3);
		assertThat(item.value()).containsEntry("content", "remember me");
	}

	@Test
	void insertsNewValueWhenUpdateFindsNoRow() {
		when(mapper.update(anyString(), anyString())).thenReturn(0);

		store.put(List.of("memory"), "/2026-08-19.md", Map.of("content", "fact"));

		verify(mapper).insert(anyString(), anyString(), eq("[\"memory\"]"), eq("/2026-08-19.md"),
				anyString());
	}

	@Test
	void performsCompareAndSetForExistingValue() {
		when(mapper.updateIfVersion(anyString(), anyString(), eq(4L))).thenReturn(1);

		boolean updated = store.putIfVersion(List.of("memory"), "/2026-08-19.md", Map.of("content", "new"),
				4);

		assertThat(updated).isTrue();
	}

}
