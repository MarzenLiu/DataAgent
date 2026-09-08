/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { defineStore } from 'pinia';
import chatService, {
	type ChatSession,
	type ChatMessage,
} from '~/services/chat/index';
import graphService, {
	type AgentStreamRequest,
	type AgentStreamEvent,
	type AgentToolActivity,
	type AgentToolCall,
	ConfirmationDecision,
} from '~/services/graph/index';
import agentDatasourceService from '~/services/agentDatasource/index';
import { useSessionStateManager } from '~/services/sessionStateManager/index';
import modelConfigService, {
	type ModelConfig,
} from '~/services/modelConfig/index';
import datasourceService, {
	type Datasource as BaseDatasource,
} from '~/services/datasource/index';
import { resolveActiveDatasource } from '~/utils/datasourceSelection';
import { agentResultText, visibleToolLabel } from '~/utils/agentEvents';

export type Datasource = BaseDatasource & { isActive?: boolean };

export interface ExtendedChatSession extends ChatSession {
	editing?: boolean;
	editingTitle?: string;
}

export interface ChatRequestOptions {
	humanFeedback: boolean;
	nl2sqlOnly: boolean;
	showSqlResults: boolean;
	pageSize: number;
}

export const useChatStore = defineStore('chat', () => {
	// ── Session list state ──────────────────────────────────────────────────────
	const sessions = ref<ExtendedChatSession[]>([]);
	const currentSession = ref<ChatSession | null>(null);
	const currentMessages = ref<ChatMessage[]>([]);

	// ── Streaming state ─────────────────────────────────────────────────────────
	const isStreaming = ref(false);
	const toolActivities = ref<AgentToolActivity[]>([]);

	// ── Human feedback state ────────────────────────────────────────────────────
	const showHumanFeedback = ref(false);
	const lastRequest = ref<AgentStreamRequest | null>(null);
	const pendingConfirmations = ref<AgentToolCall[]>([]);

	// ── Request options ─────────────────────────────────────────────────────────
	const requestOptions = ref<ChatRequestOptions>({
		humanFeedback: false,
		nl2sqlOnly: false,
		showSqlResults: false,
		pageSize: 20,
	});

	// ── Report state ────────────────────────────────────────────────────────────
	const reportFormat = ref<'markdown' | 'html'>('markdown');
	const showReportFullscreen = ref(false);
	const fullscreenReportContent = ref('');
	const streamingReportContent = ref('');
	const isReportStreaming = ref(false);

	// ── Chat sidebar collapse state ─────────────────────────────────────────────
	const chatSidebarCollapsed = ref(false);

	// ── Agent info (set by layout) ──────────────────────────────────────────────
	const currentAgentId = ref<number | undefined>(undefined);
	const activeChatModel = ref('');
	const currentAgentName = ref('');
	const currentAgentAvatar = ref('');
	const currentAgentDescription = ref('');

	// ── Datasource state ──────────────────────────────────────────────────────────
	const allDatasources = ref<Datasource[]>([]);
	const activeDatasource = ref<Datasource | null>(null);

	// ── Model state ──────────────────────────────────────────────────────────────
	const chatModels = ref<ModelConfig[]>([]);
	const activeModelConfig = ref<ModelConfig | null>(null);

	// ── SSE session stream refs (not reactive) ──────────────────────────────────
	let sessionEventSource: EventSource | null = null;
	let sessionReconnectTimer: ReturnType<typeof setTimeout> | null = null;
	let isStoreActive = true;

	const {
		getSessionState,
		syncStateToView,
		saveViewToState,
		deleteSessionState,
	} = useSessionStateManager();

	// ── Session stream ──────────────────────────────────────────────────────────
	function connectSessionStream(agentId: number) {
		if (sessionReconnectTimer) {
			clearTimeout(sessionReconnectTimer);
			sessionReconnectTimer = null;
		}
		if (sessionEventSource) sessionEventSource.close();

		const source = new EventSource(`/api/agent/${agentId}/sessions/stream`);
		source.addEventListener('title-updated', (event) => {
			try {
				const data = JSON.parse((event as MessageEvent<string>).data) as {
					sessionId: string;
					title: string;
				};
				const target = sessions.value.find((s) => s.id === data.sessionId);
				if (target) {
					target.title = data.title;
					target.editingTitle = data.title;
				}
				if (currentSession.value?.id === data.sessionId)
					currentSession.value.title = data.title;
			} catch {
				/* ignore */
			}
		});
		source.onerror = () => {
			source.close();
			sessionEventSource = null;
			if (isStoreActive)
				sessionReconnectTimer = setTimeout(
					() => connectSessionStream(agentId),
					3000,
				);
		};
		sessionEventSource = source;
	}

	function disconnectSessionStream() {
		isStoreActive = false;
		if (sessionReconnectTimer) clearTimeout(sessionReconnectTimer);
		if (sessionEventSource) {
			sessionEventSource.close();
			sessionEventSource = null;
		}
	}

	// ── Session operations ──────────────────────────────────────────────────────
	async function loadSessions(agentId: number) {
		sessions.value = await chatService.getAgentSessions(agentId);
		const firstSession = sessions.value[0];
		if (firstSession) {
			await selectSession(firstSession);
		} else {
			await createNewSession(agentId);
		}
		// Load active global datasources, then resolve the binding for this agent.
		try {
			const list = await datasourceService.getAllDatasource('active');
			let activeDatasourceId: number | undefined;
			try {
				const agentDatasource =
					await agentDatasourceService.getActiveAgentDatasource(agentId);
				activeDatasourceId = agentDatasource.datasourceId;
			} catch {
				// An unbound agent can still select from the global datasource list.
			}
			const resolved = resolveActiveDatasource(list, activeDatasourceId);
			allDatasources.value = resolved.datasources;
			activeDatasource.value = resolved.activeDatasource;
		} catch {
			allDatasources.value = [];
			activeDatasource.value = null;
		}
		// Load chat models
		try {
			const models = await modelConfigService.list();
			chatModels.value = models.filter((m) => m.modelType === 'CHAT');
			const active = chatModels.value.find((m) => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
			}
		} catch {
			/* ignore */
		}
	}

	async function switchDatasource(ds: Datasource) {
		const agentId = currentAgentId.value;
		const nextDatasourceId = ds?.id;
		if (!agentId || !nextDatasourceId) {
			activeDatasource.value = ds;
			return;
		}
		if (activeDatasource.value?.id === nextDatasourceId) {
			activeDatasource.value = { ...ds, isActive: true };
			return;
		}
		try {
			// 全局数据源列表切换：确保先建立/启用 agent 关联
			// 后端 add 接口会自动禁用该 agent 其他数据源并启用当前数据源
			await agentDatasourceService.addDatasourceToAgent(
				String(agentId),
				nextDatasourceId,
			);
			allDatasources.value = allDatasources.value.map((item) => ({
				...item,
				isActive: item.id === nextDatasourceId,
			}));
			activeDatasource.value = { ...ds, isActive: true };
		} catch (e) {
			console.error('切换数据源失败', e);
		}
	}

	async function switchModel(modelId: number) {
		try {
			await modelConfigService.activate(modelId);
			const models = await modelConfigService.list();
			chatModels.value = models.filter((m) => m.modelType === 'CHAT');
			const active = chatModels.value.find((m) => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
			}
		} catch (e) {
			console.error('切换模型失败', e);
		}
	}

	async function createNewSession(agentId: number) {
		const newSession = await chatService.createSession(agentId, '新会话');
		sessions.value.unshift(newSession);
		await selectSession(newSession);
		return newSession;
	}

	async function selectSession(session: ChatSession) {
		// Save current session state
		if (currentSession.value) {
			saveViewToState(currentSession.value.id, { isStreaming, toolActivities });
		}
		currentSession.value = session;
		syncStateToView(session.id, { isStreaming, toolActivities });
		currentMessages.value = await chatService.getSessionMessages(session.id);
	}

	async function renameSession(session: ExtendedChatSession, newTitle: string) {
		await chatService.renameSession(session.id, newTitle);
		session.title = newTitle;
		session.editing = false;
		if (currentSession.value?.id === session.id)
			currentSession.value.title = newTitle;
	}

	async function pinSession(session: ChatSession) {
		await chatService.pinSession(session.id, !session.isPinned);
		session.isPinned = !session.isPinned;
	}

	async function removeSession(session: ChatSession) {
		await chatService.deleteSession(session.id);
		deleteSessionState(session.id);
		sessions.value = sessions.value.filter((s) => s.id !== session.id);
		if (currentSession.value?.id === session.id) {
			currentSession.value = null;
			currentMessages.value = [];
			isStreaming.value = false;
			toolActivities.value = [];
		}
	}

	async function clearSessions(agentId: number) {
		await chatService.clearAgentSessions(agentId);
		sessions.value.forEach((s) => deleteSessionState(s.id));
		sessions.value = [];
		currentSession.value = null;
		currentMessages.value = [];
		isStreaming.value = false;
		toolActivities.value = [];
	}

	// ── Message send & stream ───────────────────────────────────────────────────
	async function sendMessage(query: string) {
		if (!currentSession.value) return;

		const needsTitle =
			!currentSession.value.title || currentSession.value.title === '新会话';
		const userMessage: ChatMessage = {
			sessionId: currentSession.value.id,
			role: 'user',
			content: query,
			messageType: 'text',
			titleNeeded: needsTitle,
		};

		const saved = await chatService.saveMessage(
			currentSession.value.id,
			userMessage,
		);
		currentMessages.value.push(saved);

		const request: AgentStreamRequest = {
			agentId: String(currentAgentId.value || ''),
			conversationId: currentSession.value.id,
			query,
			hitl: requestOptions.value.humanFeedback,
			nl2sqlOnly: requestOptions.value.nl2sqlOnly,
			runId: undefined,
			confirmation: undefined,
		};

		await _sendGraphRequest(request);
	}

	async function _sendGraphRequest(request: AgentStreamRequest) {
		const session = currentSession.value;
		if (!session) return;

		const sessionId = session.id;
		const sessionTitle = session.title;
		const sessionState = getSessionState(sessionId);

		lastRequest.value = request;
		isStreaming.value = true;
		toolActivities.value = [];
		pendingConfirmations.value = [];

		sessionState.isStreaming = true;
		sessionState.toolActivities = [];
		sessionState.lastRequest = request;
		sessionState.markdownReportContent = '';
		streamingReportContent.value = '';
		isReportStreaming.value = false;

		let finalReply: string | null = null;
		let awaitingHumanFeedback = false;

		let viewSyncRafId: number | null = null;
		function scheduleViewSync() {
			if (viewSyncRafId) return;
			viewSyncRafId = requestAnimationFrame(() => {
				viewSyncRafId = null;
				if (currentSession.value?.id === sessionId) {
					toolActivities.value = [...sessionState.toolActivities];
				}
			});
		}

		// Throttle report content pushes: batch SSE chunks and push at most
		// once every ~80ms. This prevents excessive re-renders while keeping
		// the typewriter animation looking smooth on the frontend.
		let reportSyncTimer: ReturnType<typeof setTimeout> | null = null;
		const REPORT_SYNC_INTERVAL = 80; // ms
		function scheduleReportSync() {
			if (reportSyncTimer) return;
			reportSyncTimer = setTimeout(() => {
				reportSyncTimer = null;
				if (currentSession.value?.id === sessionId) {
					isReportStreaming.value = true;
					streamingReportContent.value = sessionState.markdownReportContent;
				}
			}, REPORT_SYNC_INTERVAL);
		}

		function flushPendingSync() {
			if (viewSyncRafId) {
				cancelAnimationFrame(viewSyncRafId);
				viewSyncRafId = null;
			}
			if (reportSyncTimer) {
				clearTimeout(reportSyncTimer);
				reportSyncTimer = null;
			}
			if (currentSession.value?.id === sessionId) {
				toolActivities.value = [...sessionState.toolActivities];
				if (sessionState.markdownReportContent) {
					isReportStreaming.value = true;
					streamingReportContent.value = sessionState.markdownReportContent;
				}
			}
		}

		const closeStream = await graphService.streamSearch(
			request,
			async (event: AgentStreamEvent) => {
				if (event.type === 'CUSTOM' && event.name === 'run_started') {
					const runId = String(event.value?.runId || '');
					if (runId && sessionState.lastRequest) {
						sessionState.lastRequest.runId = runId;
						lastRequest.value = sessionState.lastRequest;
					}
					return;
				}
				if (event.type === 'TEXT_BLOCK_DELTA' && event.delta) {
					sessionState.markdownReportContent += event.delta;
					scheduleReportSync();
					return;
				}
				if (
					event.type === 'TOOL_CALL_START' &&
					event.toolCallId &&
					event.toolCallName
				) {
					const label = visibleToolLabel(event.toolCallName);
					if (label) {
						sessionState.toolActivities.push({
							id: event.toolCallId,
							name: event.toolCallName,
							label,
							status: 'running',
						});
						scheduleViewSync();
					}
					return;
				}
				if (event.type === 'TOOL_RESULT_END' && event.toolCallId) {
					const activity = sessionState.toolActivities.find(
						(item) => item.id === event.toolCallId,
					);
					if (activity) {
						activity.status =
							event.state === 'success' ? 'completed' : 'failed';
						scheduleViewSync();
					}
					return;
				}
				if (event.type === 'REQUIRE_USER_CONFIRM') {
					awaitingHumanFeedback = true;
					pendingConfirmations.value = (event.toolCalls || []).map((tool) => ({
						...tool,
						input: { ...(tool.input || {}) },
					}));
					return;
				}
				if (event.type === 'AGENT_RESULT') {
					finalReply = agentResultText(event).trim() || null;
					if (finalReply) {
						sessionState.markdownReportContent = finalReply;
						scheduleReportSync();
					}
				}
			},
			async (error: Error) => {
				console.error('Stream error:', error);
				flushPendingSync();

				// Save error message
				const errorMsg: ChatMessage = {
					sessionId,
					role: 'assistant',
					content: error.message || '请求失败，请检查网络连接并重试。',
					messageType: 'error',
				};
				await chatService
					.saveMessage(sessionId, errorMsg)
					.catch((e) => console.error(e));

				sessionState.isStreaming = false;
				sessionState.closeStream = null;
				if (currentSession.value?.id === sessionId) {
					isStreaming.value = false;
					isReportStreaming.value = false;
					streamingReportContent.value = '';
					currentMessages.value =
						await chatService.getSessionMessages(sessionId);
				}
			},
			async () => {
				flushPendingSync();
				if (finalReply) {
					const replyMessage: ChatMessage = {
						sessionId,
						role: 'assistant',
						content: finalReply,
						messageType: 'text',
					};
					await chatService
						.saveMessage(sessionId, replyMessage)
						.catch((e) => console.error(e));
				}

				if (awaitingHumanFeedback) {
					showHumanFeedback.value = true;
				} else {
					sessionState.isStreaming = false;
					if (currentSession.value?.id === sessionId) isStreaming.value = false;
				}

				if (currentSession.value?.id === sessionId) {
					isReportStreaming.value = false;
					streamingReportContent.value = '';
				}

				await closeStream();
				sessionState.closeStream = null;
				if (currentSession.value?.id === sessionId) {
					currentMessages.value =
						await chatService.getSessionMessages(sessionId);
					toolActivities.value = [];
				}
				console.log(`会话[${sessionTitle}]处理完成`);
			},
		);
		sessionState.closeStream = closeStream;
	}

	async function stopStreaming() {
		if (!currentSession.value) return;
		const sessionId = currentSession.value.id;
		const sessionState = getSessionState(sessionId);
		if (!sessionState.closeStream) return;

		try {
			await sessionState.closeStream(true);
		} catch (error) {
			console.error('停止后端图任务失败:', error);
		}
		sessionState.closeStream = null;
		sessionState.isStreaming = false;
		sessionState.toolActivities = [];

		// Save user-terminated warning message
		const warningMsg: ChatMessage = {
			sessionId,
			role: 'assistant',
			content: '用户已终止本次对话。',
			messageType: 'warning',
		};
		await chatService
			.saveMessage(sessionId, warningMsg)
			.catch((e) => console.error(e));

		if (currentSession.value?.id === sessionId) {
			isStreaming.value = false;
			toolActivities.value = [];
			isReportStreaming.value = false;
			streamingReportContent.value = '';
			currentMessages.value = await chatService.getSessionMessages(sessionId);
		}
	}

	type HitlDecision = 'once' | 'tool-session' | 'all-session' | 'reject';

	async function submitFeedback(decision: HitlDecision) {
		if (!lastRequest.value) return;
		showHumanFeedback.value = false;
		pendingConfirmations.value = [];
		const confirmations = {
			once: ConfirmationDecision.APPROVE_ONCE,
			'tool-session': ConfirmationDecision.APPROVE_TOOL_FOR_SESSION,
			'all-session': ConfirmationDecision.APPROVE_ALL_FOR_SESSION,
			reject: ConfirmationDecision.REJECT,
		} as const;
		const newRequest: AgentStreamRequest = {
			...lastRequest.value,
			confirmation: confirmations[decision],
		};
		await _sendGraphRequest(newRequest);
	}

	// ── Report utils ────────────────────────────────────────────────────────────
	function openReportFullscreen(content: string) {
		fullscreenReportContent.value = content;
		showReportFullscreen.value = true;
	}

	async function downloadHtmlReport(content: string) {
		if (!currentSession.value) return;
		await chatService.downloadHtmlReport(currentSession.value.id, content);
	}

	return {
		// state
		sessions,
		currentSession,
		currentMessages,
		isStreaming,
		toolActivities,
		showHumanFeedback,
		lastRequest,
		pendingConfirmations,
		requestOptions,
		reportFormat,
		showReportFullscreen,
		fullscreenReportContent,
		streamingReportContent,
		isReportStreaming,
		currentAgentId,
		chatSidebarCollapsed,
		activeChatModel,
		currentAgentName,
		currentAgentAvatar,
		currentAgentDescription,
		allDatasources,
		activeDatasource,
		chatModels,
		activeModelConfig,
		// actions
		connectSessionStream,
		disconnectSessionStream,
		loadSessions,
		createNewSession,
		selectSession,
		renameSession,
		pinSession,
		removeSession,
		clearSessions,
		sendMessage,
		stopStreaming,
		submitFeedback,
		openReportFullscreen,
		downloadHtmlReport,
		switchDatasource,
		switchModel,
	};
});
