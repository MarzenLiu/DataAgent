/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** Stops a rejected HITL operation before a later user message asks the model to revise it. */
final class StopOnAllDeniedMiddleware implements MiddlewareBase {

	@Override
	public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
			Function<ActingInput, Flux<AgentEvent>> next) {
		return next.apply(input).flatMap(event -> event instanceof AllToolsDeniedEvent
				? Flux.just(event,
						new RequestStopEvent("Tool execution rejected by user", GenerateReason.ALL_TOOLS_DENIED))
				: Flux.just(event));
	}

}
