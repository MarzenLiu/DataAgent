/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.entity;

/** Serialized AgentScope remote-filesystem entry. */
public record WorkspaceStoreRow(String storeKey, String namespaceKey, String itemKey, String valueJson, long version) {
}
