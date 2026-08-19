/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.controller;

import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentScopeAgentFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ModelSettings;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import java.time.Duration;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Internal management operations that must execute through AgentScope model adapters. */
@RestController
@RequestMapping("/api/internal/model")
public class ModelOperationsController {

	private final AgentScopeAgentFactory modelFactory;

	public ModelOperationsController(AgentScopeAgentFactory modelFactory) {
		this.modelFactory = modelFactory;
	}

	@PostMapping("/test")
	public Mono<ModelTextResponse> test(@RequestBody ModelTextRequest request) {
		ModelSettings settings = new ModelSettings(request.provider(), request.baseUrl(), request.apiKey(),
				request.modelName(), request.temperature(), request.maxTokens(), request.endpointPath());
		return generate(modelFactory.createModel(settings), "Reply with OK only.").map(ModelTextResponse::new);
	}

	@PostMapping("/title")
	public Mono<ModelTextResponse> title(@RequestBody TitleRequest request) {
		String prompt = "请为以下用户问题生成不超过20个字的中文会话标题，只输出标题：\n" + request.message();
		return generate(modelFactory.createActiveModel(), prompt).map(ModelTextResponse::new);
	}

	@DeleteMapping("/state/{agentId}/{sessionId}")
	public void deleteState(@PathVariable long agentId, @PathVariable String sessionId) {
		modelFactory.deleteSessionState(agentId, sessionId);
	}

	private Mono<String> generate(Model model, String prompt) {
		Msg message = Msg.builder().role(MsgRole.USER).textContent(prompt).build();
		return model.stream(List.of(message), List.of(), GenerateOptions.builder().stream(false).build())
			.flatMapIterable(ChatResponse::getContent)
			.filter(TextBlock.class::isInstance)
			.map(TextBlock.class::cast)
			.map(TextBlock::getText)
			.collectList()
			.map(parts -> String.join("", parts))
			.filter(StringUtils::hasText)
			.switchIfEmpty(Mono.error(new IllegalStateException("AgentScope model returned empty content")))
			.timeout(Duration.ofSeconds(30));
	}

	public record ModelTextRequest(String provider, String baseUrl, String apiKey, String modelName,
			Double temperature, Integer maxTokens, String endpointPath) { }

	public record TitleRequest(String message) { }

	public record ModelTextResponse(String text) { }

}
