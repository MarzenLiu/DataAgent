/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.agent;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.internal.node.Node;
import com.alibaba.cloud.ai.graph.internal.node.SubCompiledGraphNode;

import java.util.Objects;

/**
 * A business-capability agent backed by a graph of one or more existing workflow nodes.
 */
public final class WorkflowCapabilityAgent extends BaseAgent {

	private final GraphFactory graphFactory;

	public WorkflowCapabilityAgent(String name, String description, GraphFactory graphFactory) {
		super(name, description, true, true, null, null);
		this.graphFactory = Objects.requireNonNull(graphFactory, "graphFactory cannot be null");
	}

	@Override
	protected StateGraph initGraph() throws GraphStateException {
		return graphFactory.create();
	}

	@Override
	public Node asNode(boolean includeContents, boolean returnReasoningContents) {
		return new SubCompiledGraphNode(name, getAndCompileGraph());
	}

	@FunctionalInterface
	public interface GraphFactory {

		StateGraph create() throws GraphStateException;

	}

}
