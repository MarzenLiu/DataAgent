<!--
 Copyright 2026 the original author or authors.
 Licensed under the Apache License, Version 2.0 (the "License");
-->

<template>
	<v-container fluid class="pa-8 memory-page">
		<header
			class="d-flex flex-wrap align-center justify-space-between ga-4 mb-8"
		>
			<div>
				<h1 class="text-h4 font-weight-bold mb-1 text-slate-900">记忆管理</h1>
				<p class="text-body-2 text-medium-emphasis mb-0">
					管理当前智能体为全局用户 1 保存的长期记忆与每日记忆。
				</p>
			</div>
			<div class="d-flex ga-3">
				<v-btn
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadMemories"
				>
					刷新
				</v-btn>
				<v-btn
					color="error"
					variant="tonal"
					prepend-icon="mdi-delete-sweep-outline"
					:disabled="
						!agentId || (!overview?.consolidated && !overview?.daily.length)
					"
					@click="confirmClearAll"
				>
					清空全部
				</v-btn>
			</div>
		</header>

		<v-alert
			v-if="!agentId"
			type="info"
			variant="tonal"
			class="mb-6"
			title="请先选择智能体"
			text="从左侧智能体选择器选择一个智能体后，即可查看它的用户记忆。"
		/>

		<template v-else>
			<v-card rounded="xl" variant="outlined" class="mb-6">
				<v-card-title class="d-flex align-center px-6 pt-6">
					<v-avatar
						color="primary"
						variant="tonal"
						rounded="lg"
						size="40"
						class="mr-3"
					>
						<v-icon icon="mdi-head-heart-outline" />
					</v-avatar>
					<div>
						<div class="text-subtitle-1 font-weight-bold">长期记忆</div>
						<div class="text-caption text-medium-emphasis">
							MEMORY.md · 每轮推理自动注入
						</div>
					</div>
					<v-spacer />
					<v-chip v-if="overview?.consolidated" size="small" variant="tonal">
						版本 {{ overview.consolidated.version }}
					</v-chip>
				</v-card-title>
				<v-card-text class="px-6 pb-6 pt-4">
					<v-textarea
						v-model="consolidatedContent"
						variant="outlined"
						rows="14"
						auto-grow
						placeholder="尚未形成长期记忆。你可以手动录入，或在对话后等待 AgentScope 自动沉淀。"
						:disabled="loading"
					/>
					<div class="d-flex justify-end ga-3 mt-3">
						<v-btn
							v-if="overview?.consolidated"
							color="error"
							variant="text"
							@click="confirmDeleteConsolidated"
						>
							删除长期记忆
						</v-btn>
						<v-btn
							color="primary"
							variant="flat"
							prepend-icon="mdi-content-save-outline"
							:loading="saving"
							@click="saveConsolidated"
						>
							保存
						</v-btn>
					</div>
				</v-card-text>
			</v-card>

			<v-card rounded="xl" variant="outlined">
				<v-card-title class="d-flex align-center px-6 pt-6 pb-4">
					<v-avatar
						color="blue-grey"
						variant="tonal"
						rounded="lg"
						size="40"
						class="mr-3"
					>
						<v-icon icon="mdi-calendar-text-outline" />
					</v-avatar>
					<div>
						<div class="text-subtitle-1 font-weight-bold">每日记忆</div>
						<div class="text-caption text-medium-emphasis">
							自动抽取的增量事实，后台会定期合并进长期记忆
						</div>
					</div>
					<v-spacer />
					<v-chip size="small" variant="tonal"
						>{{ overview?.daily.length || 0 }} 条</v-chip
					>
				</v-card-title>

				<v-card-text class="px-6 pb-6">
					<v-skeleton-loader v-if="loading" type="list-item-three-line@3" />
					<v-alert
						v-else-if="!overview?.daily.length"
						variant="tonal"
						color="blue-grey"
						icon="mdi-information-outline"
						text="还没有每日记忆。完成包含稳定偏好或事实的对话后，系统会异步生成。"
					/>
					<v-expansion-panels v-else variant="accordion">
						<v-expansion-panel
							v-for="memory in overview.daily"
							:key="memory.path"
						>
							<v-expansion-panel-title>
								<div class="d-flex align-center w-100 pr-4">
									<v-icon
										icon="mdi-file-document-outline"
										size="20"
										class="mr-3"
									/>
									<span class="font-weight-medium">{{ memory.path }}</span>
									<v-spacer />
									<span class="text-caption text-medium-emphasis mr-4">
										{{ formatDate(memory.modifiedAt) }}
									</span>
									<v-btn
										icon="mdi-delete-outline"
										size="x-small"
										variant="text"
										color="error"
										@click.stop="confirmDeleteDaily(memory)"
									/>
								</div>
							</v-expansion-panel-title>
							<v-expansion-panel-text>
								<pre class="memory-content">{{ memory.content }}</pre>
							</v-expansion-panel-text>
						</v-expansion-panel>
					</v-expansion-panels>
				</v-card-text>
			</v-card>
		</template>

		<v-snackbar
			v-model="snackbar.visible"
			:color="snackbar.color"
			timeout="3000"
		>
			{{ snackbar.message }}
		</v-snackbar>
	</v-container>
</template>

<script setup lang="ts">
import memoryService, {
	type MemoryDocument,
	type MemoryOverview,
} from '~/services/memory/index';

const route = useRoute();
const { showConfirm } = useConfirm();
const loading = ref(false);
const saving = ref(false);
const overview = ref<MemoryOverview>();
const consolidatedContent = ref('');
const snackbar = reactive({ visible: false, message: '', color: 'success' });

const agentId = computed(() => {
	const value = Number(route.query.agentId);
	return Number.isFinite(value) && value > 0 ? value : undefined;
});

watch(agentId, () => loadMemories(), { immediate: true });

async function loadMemories() {
	if (!agentId.value) {
		overview.value = undefined;
		consolidatedContent.value = '';
		return;
	}
	loading.value = true;
	try {
		overview.value = await memoryService.get(agentId.value);
		consolidatedContent.value = overview.value.consolidated?.content || '';
	} catch (error) {
		notify(errorMessage(error, '加载记忆失败'), 'error');
	} finally {
		loading.value = false;
	}
}

async function saveConsolidated() {
	if (!agentId.value) return;
	saving.value = true;
	try {
		const saved = await memoryService.saveConsolidated(
			agentId.value,
			consolidatedContent.value,
		);
		if (overview.value) overview.value.consolidated = saved;
		notify('长期记忆已保存');
	} catch (error) {
		notify(errorMessage(error, '保存记忆失败'), 'error');
	} finally {
		saving.value = false;
	}
}

function confirmDeleteConsolidated() {
	showConfirm({
		title: '删除长期记忆',
		message: '删除后，智能体将不再在每轮推理中读取这份长期记忆。确定继续吗？',
		icon: 'mdi-delete-alert-outline',
		confirmText: '删除',
		onConfirm: () => void deleteConsolidated(),
	});
}

async function deleteConsolidated() {
	if (!agentId.value) return;
	try {
		await memoryService.deleteConsolidated(agentId.value);
		if (overview.value) overview.value.consolidated = undefined;
		consolidatedContent.value = '';
		notify('长期记忆已删除');
	} catch (error) {
		notify(errorMessage(error, '删除记忆失败'), 'error');
	}
}

function confirmDeleteDaily(memory: MemoryDocument) {
	showConfirm({
		title: '删除每日记忆',
		message: `确定删除 ${memory.path} 吗？`,
		icon: 'mdi-delete-alert-outline',
		confirmText: '删除',
		onConfirm: () => void deleteDaily(memory),
	});
}

async function deleteDaily(memory: MemoryDocument) {
	if (!agentId.value) return;
	try {
		await memoryService.deleteDaily(agentId.value, memory.path);
		if (overview.value) {
			overview.value.daily = overview.value.daily.filter(
				(item) => item.path !== memory.path,
			);
		}
		notify('每日记忆已删除');
	} catch (error) {
		notify(errorMessage(error, '删除每日记忆失败'), 'error');
	}
}

function confirmClearAll() {
	showConfirm({
		title: '清空全部记忆',
		message: '这会删除当前智能体的长期记忆与全部每日记忆，且无法恢复。',
		icon: 'mdi-delete-sweep-outline',
		confirmText: '全部清空',
		onConfirm: () => void clearAll(),
	});
}

async function clearAll() {
	if (!agentId.value) return;
	try {
		await memoryService.clear(agentId.value);
		overview.value = { userId: '1', agentId: agentId.value, daily: [] };
		consolidatedContent.value = '';
		notify('全部记忆已清空');
	} catch (error) {
		notify(errorMessage(error, '清空记忆失败'), 'error');
	}
}

function formatDate(value?: string) {
	if (!value) return '';
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

function errorMessage(error: unknown, fallback: string) {
	if (typeof error === 'object' && error && 'message' in error)
		return String(error.message);
	return fallback;
}

function notify(message: string, color = 'success') {
	Object.assign(snackbar, { visible: true, message, color });
}
</script>

<style scoped>
.memory-page {
	max-width: 1400px;
}

.memory-content {
	margin: 0;
	padding: 16px;
	border-radius: 10px;
	background: #f8fafc;
	color: #334155;
	font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
	font-size: 13px;
	line-height: 1.65;
	white-space: pre-wrap;
	word-break: break-word;
}
</style>
