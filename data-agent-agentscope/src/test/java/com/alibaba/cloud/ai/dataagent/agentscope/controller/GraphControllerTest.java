package com.alibaba.cloud.ai.dataagent.agentscope.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentScopeSearchService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

class GraphControllerTest {

	@Test
	void streamSearchKeepsRequestAndSseContracts() {
		AtomicReference<GraphRequest> captured = new AtomicReference<>();
		AgentScopeSearchService service = new AgentScopeSearchService() {
			@Override
			public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(GraphRequest request) {
				captured.set(request);
				return Flux.just(ServerSentEvent.builder(GraphNodeResponse.complete(request.agentId(), "run-1"))
					.event("complete")
					.build());
			}

			@Override
			public void stop(String conversationId, String threadId) {
			}
		};
		WebTestClient client = WebTestClient.bindToController(new GraphController(service)).build();

		client.get()
			.uri("/api/stream/search?agentId=7&conversationId=conversation-1&threadId=run-old&query=revenue"
					+ "&humanFeedback=true&humanFeedbackContent=accept&rejectedPlan=false&nl2sqlOnly=true")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith("text/event-stream")
			.expectBody(String.class)
			.value(body -> {
				assertThat(body).contains("event:complete");
				assertThat(body).contains("\"agentId\":\"7\"");
				assertThat(body).contains("\"complete\":true");
			});

		GraphRequest request = captured.get();
		assertThat(request.conversationId()).isEqualTo("conversation-1");
		assertThat(request.humanFeedbackContent()).isEqualTo("accept");
		assertThat(request.nl2sqlOnly()).isTrue();
	}

}
