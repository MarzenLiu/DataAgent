/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import static com.alibaba.cloud.ai.dataagent.agentscope.constant.DataAgentRuntimeConstants.GLOBAL_USER_ID;

import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Manages the AgentScope long-term memory files exposed to the single global user. */
@Service
public class MemoryManagementService {

	private static final String CONSOLIDATED_MEMORY_KEY = "/MEMORY.md";

	private static final Pattern DAILY_MEMORY_FILE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.md");

	private static final int MAX_MEMORY_FILES = 1_000;

	private final BaseStore workspaceStore;

	public MemoryManagementService(BaseStore workspaceStore) {
		this.workspaceStore = workspaceStore;
	}

	public MemoryOverview getMemories(long agentId) {
		validateAgentId(agentId);
		MemoryDocument consolidated = toDocument(workspaceStore.get(rootNamespace(agentId), CONSOLIDATED_MEMORY_KEY));
		List<MemoryDocument> daily = workspaceStore.search(memoryNamespace(agentId), MAX_MEMORY_FILES, 0)
			.stream()
			.map(this::toDocument)
			.toList();
		return new MemoryOverview(GLOBAL_USER_ID, agentId, consolidated, daily);
	}

	public MemoryDocument saveConsolidated(long agentId, String content) {
		validateAgentId(agentId);
		if (content == null) {
			throw new IllegalArgumentException("Memory content must not be null");
		}
		List<String> namespace = rootNamespace(agentId);
		StoreItem current = workspaceStore.get(namespace, CONSOLIDATED_MEMORY_KEY);
		workspaceStore.put(namespace, CONSOLIDATED_MEMORY_KEY, fileValue(content, current));
		return toDocument(workspaceStore.get(namespace, CONSOLIDATED_MEMORY_KEY));
	}

	public void deleteConsolidated(long agentId) {
		validateAgentId(agentId);
		workspaceStore.delete(rootNamespace(agentId), CONSOLIDATED_MEMORY_KEY);
	}

	public void deleteDaily(long agentId, String fileName) {
		validateAgentId(agentId);
		if (!StringUtils.hasText(fileName) || !DAILY_MEMORY_FILE.matcher(fileName).matches()) {
			throw new IllegalArgumentException("Invalid daily memory file name");
		}
		workspaceStore.delete(memoryNamespace(agentId), "/" + fileName);
	}

	public void clearAll(long agentId) {
		validateAgentId(agentId);
		deleteConsolidated(agentId);
		workspaceStore.search(memoryNamespace(agentId), MAX_MEMORY_FILES, 0)
			.forEach(item -> workspaceStore.delete(memoryNamespace(agentId), item.key()));
	}

	private Map<String, Object> fileValue(String content, StoreItem current) {
		String now = Instant.now().toString();
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("content", content);
		value.put("encoding", "utf-8");
		value.put("created_at", current == null ? now : current.value().getOrDefault("created_at", now));
		value.put("modified_at", now);
		return value;
	}

	private MemoryDocument toDocument(StoreItem item) {
		if (item == null) {
			return null;
		}
		Object content = item.value().get("content");
		Object modifiedAt = item.value().get("modified_at");
		return new MemoryDocument(stripLeadingSlash(item.key()), content == null ? "" : String.valueOf(content),
				item.version(), modifiedAt == null ? null : String.valueOf(modifiedAt));
	}

	private List<String> rootNamespace(long agentId) {
		return List.of("agents", agentName(agentId), "users", GLOBAL_USER_ID, "root");
	}

	private List<String> memoryNamespace(long agentId) {
		return List.of("agents", agentName(agentId), "users", GLOBAL_USER_ID, "memory");
	}

	private String agentName(long agentId) {
		return "data-agent-%d".formatted(agentId);
	}

	private String stripLeadingSlash(String path) {
		return path != null && path.startsWith("/") ? path.substring(1) : path;
	}

	private void validateAgentId(long agentId) {
		if (agentId <= 0) {
			throw new IllegalArgumentException("agentId must be positive");
		}
	}

	public record MemoryOverview(String userId, long agentId, MemoryDocument consolidated, List<MemoryDocument> daily) {
	}

	public record MemoryDocument(String path, String content, long version, String modifiedAt) {
	}

}
