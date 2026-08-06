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
package com.alibaba.cloud.ai.dataagent.workflow.agent.capability;

import com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_AGENT_NEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_AGENT_PLANNING_MODE;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

final class CapabilityAgentSupport {

	static final String HANDOFF_FINISH = "handoff_finish";

	static final String HANDOFF_PLANNING = "handoff_planning";

	static final String HANDOFF_PLANNING_EXECUTE = "handoff_planning_execute";

	static final String PLAN_MODE = "plan";

	static final String EXECUTE_MODE = "execute";

	private CapabilityAgentSupport() {
	}

	static AsyncNodeAction handoff(String completedAgent, String nextHint, String planningMode) {
		return node_async(state -> {
			Map<String, Object> update = new HashMap<>();
			update.put(MULTI_AGENT_NEXT, nextHint);
			update.put("messages",
					new UserMessage(DataAnalysisSupervisorAgent.capabilityResultMessage(completedAgent, nextHint)));
			if (planningMode != null) {
				update.put(MULTI_AGENT_PLANNING_MODE, planningMode);
			}
			return update;
		});
	}

}
