/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.WorkspaceStoreRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Database-backed KV store for AgentScope workspace and long-term memory files. */
@Repository
public class DatabaseWorkspaceStore implements BaseStore {

	private static final TypeReference<Map<String, Object>> VALUE_TYPE = new TypeReference<>() {
	};

	private final WorkspaceStoreMapper mapper;

	private final ObjectMapper objectMapper;

	public DatabaseWorkspaceStore(WorkspaceStoreMapper mapper, ObjectMapper objectMapper) {
		this.mapper = mapper;
		this.objectMapper = objectMapper;
	}

	@Override
	public StoreItem get(List<String> namespace, String key) {
		return toStoreItem(mapper.findByStoreKey(storeKey(namespace, key)));
	}

	@Override
	public void put(List<String> namespace, String key, Map<String, Object> value) {
		String storeKey = storeKey(namespace, key);
		String valueJson = writeJson(value);
		if (mapper.update(storeKey, valueJson) > 0) {
			return;
		}
		try {
			mapper.insert(storeKey, namespaceHash(namespace), namespaceKey(namespace), key, valueJson);
		}
		catch (DuplicateKeyException ex) {
			mapper.update(storeKey, valueJson);
		}
	}

	@Override
	public boolean putIfVersion(List<String> namespace, String key, Map<String, Object> value, long expectedVersion) {
		String storeKey = storeKey(namespace, key);
		String valueJson = writeJson(value);
		if (expectedVersion > 0) {
			return mapper.updateIfVersion(storeKey, valueJson, expectedVersion) > 0;
		}
		try {
			return mapper.insert(storeKey, namespaceHash(namespace), namespaceKey(namespace), key, valueJson) > 0;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	@Override
	public List<StoreItem> search(List<String> namespace, int limit, int offset) {
		if (limit <= 0) {
			return List.of();
		}
		return mapper.findByNamespace(namespaceHash(namespace), namespaceKey(namespace), limit, Math.max(0, offset))
			.stream()
			.map(this::toStoreItem)
			.toList();
	}

	@Override
	public void delete(List<String> namespace, String key) {
		mapper.delete(storeKey(namespace, key));
	}

	private StoreItem toStoreItem(WorkspaceStoreRow row) {
		if (row == null) {
			return null;
		}
		try {
			return new StoreItem(row.itemKey(), objectMapper.readValue(row.valueJson(), VALUE_TYPE), row.version());
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to deserialize AgentScope workspace value", ex);
		}
	}

	private String writeJson(Map<String, Object> value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize AgentScope workspace value", ex);
		}
	}

	private String storeKey(List<String> namespace, String key) {
		return sha256(namespaceKey(namespace) + "\n" + key);
	}

	private String namespaceHash(List<String> namespace) {
		return sha256(namespaceKey(namespace));
	}

	private String namespaceKey(List<String> namespace) {
		try {
			return objectMapper.writeValueAsString(namespace);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize AgentScope namespace", ex);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

}
