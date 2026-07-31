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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.*;
import com.alibaba.cloud.ai.dataagent.workflow.node.*;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Builds capability-oriented agents from the existing deterministic business nodes.
 */
public final class DataAnalysisAgentFactory {

	private static final String HANDOFF_FINISH = "handoff_finish";

	private static final String HANDOFF_DATA_PREPARATION = "handoff_data_preparation";

	private static final String HANDOFF_PLANNING = "handoff_planning";

	private static final String HANDOFF_SQL = "handoff_sql";

	private static final String HANDOFF_PYTHON = "handoff_python";

	private static final String HANDOFF_REPORT = "handoff_report";

	private static final String HANDOFF_HUMAN_REVIEW = "handoff_human_review";

	private static final String HANDOFF_PLANNING_EXECUTE = "handoff_planning_execute";

	private static final String PLANNING_ENTRY = "planning_entry";

	private static final String PLAN_MODE = "plan";

	private static final String EXECUTE_MODE = "execute";

	private final NodeBeanUtil nodeBeanUtil;

	private final CodeExecutorProperties codeExecutorProperties;

	private final KeyStrategyFactory keyStrategyFactory;

	public DataAnalysisAgentFactory(NodeBeanUtil nodeBeanUtil, CodeExecutorProperties codeExecutorProperties,
			KeyStrategyFactory keyStrategyFactory) {
		this.nodeBeanUtil = nodeBeanUtil;
		this.codeExecutorProperties = codeExecutorProperties;
		this.keyStrategyFactory = keyStrategyFactory;
	}

	public List<Agent> createAgents() {
		return List.of(
				new WorkflowCapabilityAgent(REQUEST_UNDERSTANDING_AGENT,
						"Builds the user profile, recognizes intent, recalls evidence and enhances the request",
						this::requestUnderstandingGraph),
				new WorkflowCapabilityAgent(DATA_PREPARATION_AGENT,
						"Recalls schema, resolves table relationships and assesses feasibility",
						this::dataPreparationGraph),
				new WorkflowCapabilityAgent(PLANNING_AGENT,
						"Creates, repairs and advances the execution plan",
						this::planningGraph),
				new WorkflowCapabilityAgent(SQL_AGENT,
						"Generates, validates and executes SQL, including retries",
						this::sqlGraph),
				new WorkflowCapabilityAgent(PYTHON_AGENT,
						"Generates, executes and analyzes Python, including retries",
						this::pythonGraph),
				new WorkflowCapabilityAgent(REPORT_AGENT,
						"Produces the final Markdown analysis report",
						this::reportGraph),
				new WorkflowCapabilityAgent(HUMAN_FEEDBACK_NODE,
						"Processes the human plan approval or rejection boundary",
						this::humanReviewGraph));
	}

	private StateGraph requestUnderstandingGraph() throws GraphStateException {
		return new StateGraph("request_understanding_agent_graph", keyStrategyFactory)
			.addNode(USER_PROFILE_NODE, nodeBeanUtil.getNodeBeanAsync(UserProfileNode.class))
			.addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.getNodeBeanAsync(IntentRecognitionNode.class))
			.addNode(EVIDENCE_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(EvidenceRecallNode.class))
			.addNode(QUERY_ENHANCE_NODE, nodeBeanUtil.getNodeBeanAsync(QueryEnhanceNode.class))
			.addNode(HANDOFF_DATA_PREPARATION, handoff(DATA_PREPARATION_AGENT, null))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, USER_PROFILE_NODE)
			.addConditionalEdges(USER_PROFILE_NODE, edge_async(new UserProfileDispatcher()),
					Map.of(INTENT_RECOGNITION_NODE, INTENT_RECOGNITION_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(INTENT_RECOGNITION_NODE, edge_async(new IntentRecognitionDispatcher()),
					Map.of(EVIDENCE_RECALL_NODE, EVIDENCE_RECALL_NODE, END, HANDOFF_FINISH))
			.addEdge(EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE)
			.addConditionalEdges(QUERY_ENHANCE_NODE, edge_async(new QueryEnhanceDispatcher()),
					Map.of(SCHEMA_RECALL_NODE, HANDOFF_DATA_PREPARATION, END, HANDOFF_FINISH))
			.addEdge(HANDOFF_DATA_PREPARATION, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph dataPreparationGraph() throws GraphStateException {
		return new StateGraph("data_preparation_agent_graph", keyStrategyFactory)
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(TABLE_RELATION_NODE, nodeBeanUtil.getNodeBeanAsync(TableRelationNode.class))
			.addNode(FEASIBILITY_ASSESSMENT_NODE,
					nodeBeanUtil.getNodeBeanAsync(FeasibilityAssessmentNode.class))
			.addNode(HANDOFF_PLANNING, handoff(PLANNING_AGENT, PLAN_MODE))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, SCHEMA_RECALL_NODE)
			.addConditionalEdges(SCHEMA_RECALL_NODE, edge_async(new SchemaRecallDispatcher()),
					Map.of(TABLE_RELATION_NODE, TABLE_RELATION_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(TABLE_RELATION_NODE, edge_async(new TableRelationDispatcher()),
					Map.of(FEASIBILITY_ASSESSMENT_NODE, FEASIBILITY_ASSESSMENT_NODE, TABLE_RELATION_NODE,
							TABLE_RELATION_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(FEASIBILITY_ASSESSMENT_NODE,
					edge_async(new FeasibilityAssessmentDispatcher()),
					Map.of(PLANNER_NODE, HANDOFF_PLANNING, END, HANDOFF_FINISH))
			.addEdge(HANDOFF_PLANNING, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph planningGraph() throws GraphStateException {
		return new StateGraph("planning_agent_graph", keyStrategyFactory)
			.addNode(PLANNING_ENTRY, node_async(state -> Map.of()))
			.addNode(PLANNER_NODE, nodeBeanUtil.getNodeBeanAsync(PlannerNode.class))
			.addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.getNodeBeanAsync(PlanExecutorNode.class))
			.addNode(HANDOFF_SQL, handoff(SQL_AGENT, null))
			.addNode(HANDOFF_PYTHON, handoff(PYTHON_AGENT, null))
			.addNode(HANDOFF_REPORT, handoff(REPORT_AGENT, null))
			.addNode(HANDOFF_HUMAN_REVIEW, handoff(HUMAN_FEEDBACK_NODE, null))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, PLANNING_ENTRY)
			.addConditionalEdges(PLANNING_ENTRY, edge_async(state -> {
				String mode = state.value(MULTI_AGENT_PLANNING_MODE, PLAN_MODE);
				return EXECUTE_MODE.equals(mode) ? PLAN_EXECUTOR_NODE : PLANNER_NODE;
			}), Map.of(PLANNER_NODE, PLANNER_NODE, PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE))
			.addEdge(PLANNER_NODE, PLAN_EXECUTOR_NODE)
			.addConditionalEdges(PLAN_EXECUTOR_NODE, edge_async(new PlanExecutorDispatcher()), Map.of(
					PLANNER_NODE, PLANNER_NODE,
					SQL_GENERATE_NODE, HANDOFF_SQL,
					PYTHON_GENERATE_NODE, HANDOFF_PYTHON,
					REPORT_GENERATOR_NODE, HANDOFF_REPORT,
					HUMAN_FEEDBACK_NODE, HANDOFF_HUMAN_REVIEW,
					END, HANDOFF_FINISH))
			.addEdge(HANDOFF_SQL, END)
			.addEdge(HANDOFF_PYTHON, END)
			.addEdge(HANDOFF_REPORT, END)
			.addEdge(HANDOFF_HUMAN_REVIEW, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph sqlGraph() throws GraphStateException {
		return new StateGraph("sql_agent_graph", keyStrategyFactory)
			.addNode(SQL_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlGenerateNode.class))
			.addNode(SEMANTIC_CONSISTENCY_NODE,
					nodeBeanUtil.getNodeBeanAsync(SemanticConsistencyNode.class))
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(HANDOFF_PLANNING_EXECUTE, handoff(PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, SQL_GENERATE_NODE)
			.addConditionalEdges(SQL_GENERATE_NODE,
					nodeBeanUtil.getEdgeBeanAsync(SqlGenerateDispatcher.class),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SEMANTIC_CONSISTENCY_NODE,
							SEMANTIC_CONSISTENCY_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(SEMANTIC_CONSISTENCY_NODE,
					edge_async(new SemanticConsistenceDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
			.addConditionalEdges(SQL_EXECUTE_NODE, edge_async(new SQLExecutorDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, PLAN_EXECUTOR_NODE,
							HANDOFF_PLANNING_EXECUTE))
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph pythonGraph() throws GraphStateException {
		return new StateGraph("python_agent_graph", keyStrategyFactory)
			.addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonGenerateNode.class))
			.addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonExecuteNode.class))
			.addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonAnalyzeNode.class))
			.addNode(HANDOFF_PLANNING_EXECUTE, handoff(PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, PYTHON_GENERATE_NODE)
			.addEdge(PYTHON_GENERATE_NODE, PYTHON_EXECUTE_NODE)
			.addConditionalEdges(PYTHON_EXECUTE_NODE,
					edge_async(new PythonExecutorDispatcher(codeExecutorProperties)),
					Map.of(PYTHON_ANALYZE_NODE, PYTHON_ANALYZE_NODE, PYTHON_GENERATE_NODE,
							PYTHON_GENERATE_NODE, END, HANDOFF_FINISH))
			.addEdge(PYTHON_ANALYZE_NODE, HANDOFF_PLANNING_EXECUTE)
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph reportGraph() throws GraphStateException {
		return new StateGraph("report_agent_graph", keyStrategyFactory)
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, REPORT_GENERATOR_NODE)
			.addEdge(REPORT_GENERATOR_NODE, HANDOFF_FINISH)
			.addEdge(HANDOFF_FINISH, END);
	}

	private StateGraph humanReviewGraph() throws GraphStateException {
		return new StateGraph("human_review_agent_graph", keyStrategyFactory)
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class))
			.addNode(HANDOFF_PLANNING, handoff(PLANNING_AGENT, PLAN_MODE))
			.addNode(HANDOFF_PLANNING_EXECUTE, handoff(PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(FINISH, null))
			.addEdge(START, HUMAN_FEEDBACK_NODE)
			.addConditionalEdges(HUMAN_FEEDBACK_NODE, edge_async(new HumanFeedbackDispatcher()), Map.of(
					PLANNER_NODE, HANDOFF_PLANNING,
					PLAN_EXECUTOR_NODE, HANDOFF_PLANNING_EXECUTE,
					HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE,
					END, HANDOFF_FINISH))
			.addEdge(HANDOFF_PLANNING, END)
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END);
	}

	private AsyncNodeAction handoff(String nextAgent, String planningMode) {
		return node_async(state -> {
			Map<String, Object> update = new HashMap<>();
			update.put(MULTI_AGENT_NEXT, nextAgent);
			update.put("messages", new UserMessage(DataAnalysisSupervisorAgent.handoffMessage(nextAgent)));
			if (planningMode != null) {
				update.put(MULTI_AGENT_PLANNING_MODE, planningMode);
			}
			return update;
		});
	}

}
