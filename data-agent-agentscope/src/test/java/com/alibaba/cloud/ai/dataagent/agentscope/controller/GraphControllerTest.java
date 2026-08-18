package com.alibaba.cloud.ai.dataagent.agentscope.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.dataagent.agentscope.api.AgentStreamRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.ConfirmationDecision;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentScopeSearchService;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

class GraphControllerTest {

	@Test
	void streamSearchUsesAgentEventAndConfirmationContracts() {
		AtomicReference<AgentStreamRequest> captured = new AtomicReference<>();
		AgentScopeSearchService service = new AgentScopeSearchService() {
			@Override
			public Flux<ServerSentEvent<AgentEvent>> streamSearch(AgentStreamRequest request) {
				captured.set(request);
				return Flux.just(ServerSentEvent
					.<AgentEvent>builder(new CustomEvent("stream_completed", Map.of("runId", "run-1")))
					.id("run-1")
					.build());
			}

			@Override
			public void stop(String conversationId, String runId) {
			}
		};
		WebTestClient client = WebTestClient.bindToController(new GraphController(service)).build();

		client.get()
			.uri("/api/stream/search?agentId=7&conversationId=conversation-1&runId=run-old&query=revenue"
					+ "&hitl=true&confirmation=REJECT&nl2sqlOnly=true")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith("text/event-stream")
			.expectBody(String.class)
			.value(body -> {
				assertThat(body).contains("id:run-1");
				assertThat(body).contains("\"type\":\"CUSTOM\"");
				assertThat(body).contains("\"name\":\"stream_completed\"");
				assertThat(body).doesNotContain("nodeName");
			});

		AgentStreamRequest request = captured.get();
		assertThat(request.conversationId()).isEqualTo("conversation-1");
		assertThat(request.runId()).isEqualTo("run-old");
		assertThat(request.confirmation()).isEqualTo(ConfirmationDecision.REJECT);
		assertThat(request.nl2sqlOnly()).isTrue();
	}

}
