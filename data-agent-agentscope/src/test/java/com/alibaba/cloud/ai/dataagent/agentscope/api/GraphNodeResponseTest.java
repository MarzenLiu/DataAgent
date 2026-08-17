package com.alibaba.cloud.ai.dataagent.agentscope.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphNodeResponseTest {

	@Test
	void factoriesPreserveExistingWireSemantics() {
		GraphNodeResponse output = GraphNodeResponse.output("1", "run-1", "step-1", "ReportGeneratorNode",
				TextType.MARK_DOWN, "report");
		assertThat(output.eventType()).isEqualTo(GraphEventType.NODE_OUTPUT);
		assertThat(output.error()).isFalse();
		assertThat(output.complete()).isFalse();

		GraphNodeResponse complete = GraphNodeResponse.complete("1", "run-1");
		assertThat(complete.complete()).isTrue();
		assertThat(complete.textType()).isEqualTo(TextType.TEXT);
	}

}
