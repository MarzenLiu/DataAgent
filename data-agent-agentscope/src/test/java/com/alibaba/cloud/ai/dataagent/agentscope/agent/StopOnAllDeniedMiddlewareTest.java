package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.middleware.ActingInput;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class StopOnAllDeniedMiddlewareTest {

	@Test
	void stopsTheCurrentCallAfterUserRejectsAllPendingTools() {
		StopOnAllDeniedMiddleware middleware = new StopOnAllDeniedMiddleware();
		AllToolsDeniedEvent denied = new AllToolsDeniedEvent(List.of());

		List<?> events = middleware
			.onActing(mock(Agent.class), RuntimeContext.empty(), new ActingInput(List.of()),
					ignored -> Flux.just(denied))
			.collectList()
			.block();

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isSameAs(denied);
		assertThat(events.get(1)).isInstanceOfSatisfying(RequestStopEvent.class,
				stop -> assertThat(stop.getGenerateReason()).isEqualTo(GenerateReason.ALL_TOOLS_DENIED));
	}

}
