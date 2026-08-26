/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.controller;

import com.alibaba.cloud.ai.dataagent.agentscope.service.MemoryManagementService;
import com.alibaba.cloud.ai.dataagent.agentscope.service.MemoryManagementService.MemoryDocument;
import com.alibaba.cloud.ai.dataagent.agentscope.service.MemoryManagementService.MemoryOverview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP management API for AgentScope long-term memories. */
@Validated
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/memories")
public class MemoryController {

	private final MemoryManagementService memoryService;

	public MemoryController(MemoryManagementService memoryService) {
		this.memoryService = memoryService;
	}

	@GetMapping
	public MemoryOverview getMemories(@RequestParam @Positive long agentId) {
		return memoryService.getMemories(agentId);
	}

	@PutMapping("/consolidated")
	public MemoryDocument saveConsolidated(@Valid @RequestBody SaveMemoryRequest request) {
		return memoryService.saveConsolidated(request.agentId(), request.content());
	}

	@DeleteMapping("/consolidated")
	public ResponseEntity<Void> deleteConsolidated(@RequestParam @Positive long agentId) {
		memoryService.deleteConsolidated(agentId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/daily/{fileName}")
	public ResponseEntity<Void> deleteDaily(@RequestParam @Positive long agentId, @PathVariable String fileName) {
		memoryService.deleteDaily(agentId, fileName);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> clearAll(@RequestParam @Positive long agentId) {
		memoryService.clearAll(agentId);
		return ResponseEntity.noContent().build();
	}

	public record SaveMemoryRequest(@Positive long agentId, @NotNull String content) {
	}

}
