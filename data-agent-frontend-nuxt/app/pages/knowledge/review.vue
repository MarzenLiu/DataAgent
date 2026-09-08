<!--
 Copyright 2026 the original author or authors.
 Licensed under the Apache License, Version 2.0 (the "License");
-->

<template>
	<section class="review-page">
		<header class="d-flex flex-wrap align-center justify-space-between ga-4 mb-5">
			<div class="d-flex align-center ga-3">
				<v-btn icon="mdi-arrow-left" variant="text" @click="goBack" />
				<div>
					<div class="d-flex align-center ga-2">
						<h1 class="text-h5 font-weight-bold">{{ preview?.title || '解析审核' }}</h1>
						<v-chip
							v-if="preview"
							size="small"
							variant="tonal"
							:color="statusColor(preview.status)"
						>
							{{ statusLabel(preview.status) }}
						</v-chip>
					</div>
					<p class="text-body-2 text-medium-emphasis mb-0">
						{{ preview?.sourceFilename || '正在读取文档信息…' }}
						<span v-if="preview"> · 修订 {{ preview.revisionNo }}</span>
					</p>
				</div>
			</div>
			<div class="d-flex ga-2">
				<v-btn prepend-icon="mdi-refresh" variant="outlined" :loading="loading" @click="loadPreview">
					刷新
				</v-btn>
				<v-btn
					color="error"
					variant="tonal"
					prepend-icon="mdi-close-circle-outline"
					:disabled="preview?.status !== 'PENDING_REVIEW'"
					@click="reviewDialog = 'reject'"
				>
					驳回
				</v-btn>
				<v-btn
					color="success"
					variant="flat"
					prepend-icon="mdi-check-circle-outline"
					:disabled="preview?.status !== 'PENDING_REVIEW'"
					@click="reviewDialog = 'approve'"
				>
					通过并发布
				</v-btn>
			</div>
		</header>

		<v-alert v-if="loadError" type="error" variant="tonal" class="mb-4">
			{{ loadError }}
		</v-alert>
		<v-alert v-if="sourceLoadError" type="error" variant="tonal" class="mb-4">
			{{ sourceLoadError }}
		</v-alert>
		<v-alert
			v-else-if="preview?.status === 'PARSING'"
			type="info"
			variant="tonal"
			class="mb-4"
		>
			文档仍在解析，页面会自动刷新。
		</v-alert>
		<v-alert
			v-else-if="preview && preview.parser !== 'docling'"
			type="warning"
			variant="tonal"
			class="mb-4"
		>
			{{ fallbackParserMessage(preview.parser) }}
		</v-alert>

		<div v-if="preview" class="review-grid">
			<v-card variant="outlined" class="pane chunk-pane">
				<v-card-title class="pane-title">
					<span>分块目录</span>
					<div class="d-flex align-center ga-2">
						<v-chip size="x-small" variant="tonal">{{ parserLabel(preview.parser) }}</v-chip>
						<v-chip size="x-small" variant="tonal">{{ splitterLabel(preview.splitterType) }}</v-chip>
						<v-chip size="x-small" variant="tonal">{{ preview.chunkCount }}</v-chip>
					</div>
				</v-card-title>
				<v-card-text class="pa-0 chunk-list">
					<button
						v-for="chunk in preview.chunks"
						:key="chunk.id"
						type="button"
						class="chunk-item"
						:class="{ active: selectedChunk?.id === chunk.id }"
						@click="selectedChunkId = chunk.id"
					>
						<div class="d-flex align-center justify-space-between ga-2">
							<span class="text-caption font-weight-bold">Chunk {{ chunk.chunkIndex + 1 }}</span>
							<v-chip
								size="x-small"
								variant="tonal"
								:color="chunk.qualityFlags.length ? 'warning' : 'success'"
							>
								{{ chunk.qualityScore }}
							</v-chip>
						</div>
						<div class="text-caption text-medium-emphasis text-truncate mt-1">
							{{ chunk.sectionPath || contentTypeLabel(chunk.contentType) }}
						</div>
						<div class="text-caption mt-1">第 {{ chunk.pageNumber || '?' }} 页</div>
					</button>
					<div v-if="!preview.chunks.length" class="pa-6 text-center text-medium-emphasis">
						等待解析结果…
					</div>
				</v-card-text>
			</v-card>

			<v-card variant="outlined" class="pane source-pane">
				<v-card-title class="pane-title">
					<span>原始文档</span>
					<v-chip v-if="selectedChunk?.pageNumber" size="x-small" variant="tonal">
						定位第 {{ selectedChunk.pageNumber }} 页
					</v-chip>
				</v-card-title>
				<div class="source-frame-wrap">
					<div v-if="isPdf && pdfReady" class="pdf-preview">
						<div class="pdf-toolbar">
							<v-btn
								icon="mdi-chevron-left"
								size="x-small"
								variant="text"
								:disabled="pdfPage <= 1"
								@click="changePdfPage(pdfPage - 1)"
							/>
							<span>{{ pdfPage }} / {{ pdfPageCount }}</span>
							<v-btn
								icon="mdi-chevron-right"
								size="x-small"
								variant="text"
								:disabled="pdfPage >= pdfPageCount"
								@click="changePdfPage(pdfPage + 1)"
							/>
							<v-spacer />
							<a :href="sourceObjectUrl" target="_blank" rel="noopener">打开原文件</a>
						</div>
						<div class="pdf-canvas-wrap">
							<canvas ref="pdfCanvas" class="pdf-canvas" />
						</div>
					</div>
					<div v-else-if="!isPdf" class="empty-source">
						<v-icon icon="mdi-file-document-outline" size="52" color="blue-grey-lighten-1" />
						<p class="mt-3 mb-1">当前格式暂不支持浏览器内原文预览</p>
						<a :href="sourceObjectUrl" target="_blank" rel="noopener">打开原始文件</a>
					</div>
					<div v-else class="empty-source">
						<v-progress-circular v-if="sourceLoading" indeterminate color="primary" />
						<v-icon v-else icon="mdi-file-alert-outline" size="52" color="error" />
						<p class="mt-3 mb-1">{{ sourceLoading ? '正在加载原始 PDF…' : '原始 PDF 加载失败' }}</p>
					</div>
				</div>
			</v-card>

			<v-card variant="outlined" class="pane result-pane">
				<v-card-title class="pane-title">
					<span>格式化结果</span>
					<v-chip v-if="selectedChunk" size="x-small" variant="tonal">
						{{ contentTypeLabel(selectedChunk.contentType) }}
					</v-chip>
				</v-card-title>
				<v-card-text v-if="selectedChunk" class="result-content">
					<div v-if="selectedChunk.qualityFlags.length" class="d-flex flex-wrap ga-2 mb-4">
						<v-chip
							v-for="flag in selectedChunk.qualityFlags"
							:key="flag"
							size="small"
							color="warning"
							variant="tonal"
						>
							{{ qualityLabel(flag) }}
						</v-chip>
					</div>
					<div class="metadata-row mb-4">
						<span>元素类型：{{ contentTypeLabel(selectedChunk.contentType) }}</span>
						<span>标题路径：{{ selectedChunk.sectionPath || '无' }}</span>
						<span>页码：{{ selectedChunk.pageNumber || '未知' }}</span>
						<span>坐标：{{ selectedChunk.boundingBox || '无' }}</span>
						<span>元素 ID：{{ selectedChunk.documentId }}</span>
					</div>
					<div class="markdown-body" v-html="renderedChunk" />
				</v-card-text>
				<div v-else class="empty-source">请选择一个分块查看解析结果</div>
			</v-card>
		</div>

		<v-dialog :model-value="Boolean(reviewDialog)" max-width="520" @update:model-value="closeDialog">
			<v-card rounded="lg">
				<v-card-title class="pa-6 pb-2">
					{{ reviewDialog === 'approve' ? '通过解析结果' : '驳回解析结果' }}
				</v-card-title>
				<v-card-text class="pa-6 pt-3">
					<v-textarea
						v-model="reviewComment"
						label="审核说明（可选）"
						variant="outlined"
						rows="4"
						maxlength="1000"
						counter
					/>
					<v-alert v-if="reviewDialog === 'approve'" type="info" variant="tonal" density="compact">
						确认后将使用当前修订生成向量并发布到知识库。
					</v-alert>
				</v-card-text>
				<v-card-actions class="pa-4 pt-0 justify-end">
					<v-btn variant="text" @click="closeDialog">取消</v-btn>
					<v-btn
						:color="reviewDialog === 'approve' ? 'success' : 'error'"
						variant="flat"
						:loading="reviewing"
						@click="submitReview"
					>
						确认
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import agentKnowledgeService, {
	type KnowledgeParsePreview,
} from '~/services/agentKnowledge/index';
import { renderMarkdownContent } from '~/utils/markdown';

const route = useRoute();
const { $tip } = useNuxtApp();
const knowledgeId = computed(() => Number(route.query.id));
const preview = ref<KnowledgeParsePreview>();
const selectedChunkId = ref<number>();
const loading = ref(false);
const reviewing = ref(false);
const loadError = ref('');
const reviewDialog = ref<'approve' | 'reject'>();
const reviewComment = ref('');
const sourceObjectUrl = ref('');
const sourceLoading = ref(false);
const sourceLoadError = ref('');
const pdfCanvas = ref<HTMLCanvasElement>();
const pdfReady = ref(false);
const pdfPage = ref(1);
const pdfPageCount = ref(0);
let pdfDocument: PDFDocumentProxy | undefined;
let pdfRenderTask: RenderTask | undefined;
let pollTimer: ReturnType<typeof setTimeout> | undefined;

const selectedChunk = computed(() =>
	preview.value?.chunks.find((chunk) => chunk.id === selectedChunkId.value),
);
const isPdf = computed(
	() =>
		preview.value?.fileType?.includes('pdf') ||
		preview.value?.sourceFilename?.toLowerCase().endsWith('.pdf'),
);
const renderedChunk = computed(() =>
	DOMPurify.sanitize(renderMarkdownContent(selectedChunk.value?.content || '')),
);

async function loadPreview() {
	if (!Number.isFinite(knowledgeId.value)) return;
	loading.value = true;
	loadError.value = '';
	try {
		preview.value = await agentKnowledgeService.getParsePreview(knowledgeId.value);
		if (!selectedChunkId.value && preview.value.chunks.length) {
			selectedChunkId.value = preview.value.chunks[0]?.id;
		}
		if (preview.value.status === 'PARSING') schedulePoll();
	} catch {
		loadError.value = '解析结果尚未生成或读取失败，请稍后刷新。';
		schedulePoll();
	} finally {
		loading.value = false;
	}
}

async function loadSource() {
	if (!Number.isFinite(knowledgeId.value)) return;
	sourceLoading.value = true;
	sourceLoadError.value = '';
	try {
		const source = await agentKnowledgeService.getParseSource(knowledgeId.value);
		if (sourceObjectUrl.value) URL.revokeObjectURL(sourceObjectUrl.value);
		sourceObjectUrl.value = URL.createObjectURL(source);
		if (source.type.includes('pdf') || preview.value?.sourceFilename?.toLowerCase().endsWith('.pdf')) {
			await loadPdf(source);
		}
	} catch {
		sourceLoadError.value = '原始文档读取失败，请检查文件是否仍然存在。';
	} finally {
		sourceLoading.value = false;
	}
}

async function loadPdf(source: Blob) {
	pdfReady.value = false;
	await destroyPdfDocument();
	const pdfjs = await import('pdfjs-dist');
	const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
	pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
	pdfDocument = await pdfjs.getDocument({ data: new Uint8Array(await source.arrayBuffer()) }).promise;
	pdfPageCount.value = pdfDocument.numPages;
	pdfPage.value = Math.min(Math.max(selectedChunk.value?.pageNumber || 1, 1), pdfDocument.numPages);
	pdfReady.value = true;
	await nextTick();
	await renderPdfPage();
}

async function destroyPdfDocument() {
	const documentToDestroy = pdfDocument;
	pdfDocument = undefined;
	pdfRenderTask?.cancel();
	pdfRenderTask = undefined;
	if (documentToDestroy && typeof documentToDestroy.destroy === 'function') {
		await documentToDestroy.destroy();
	}
}

async function renderPdfPage() {
	if (!pdfDocument || !pdfCanvas.value) return;
	pdfRenderTask?.cancel();
	const page = await pdfDocument.getPage(pdfPage.value);
	const unscaled = page.getViewport({ scale: 1 });
	const availableWidth = Math.max(280, (pdfCanvas.value.parentElement?.clientWidth || 600) - 24);
	const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
	const viewport = page.getViewport({ scale: (availableWidth / unscaled.width) * pixelRatio });
	const canvas = pdfCanvas.value;
	const context = canvas.getContext('2d');
	if (!context) return;
	canvas.width = Math.floor(viewport.width);
	canvas.height = Math.floor(viewport.height);
	canvas.style.width = `${Math.floor(viewport.width / pixelRatio)}px`;
	canvas.style.height = `${Math.floor(viewport.height / pixelRatio)}px`;
	pdfRenderTask = page.render({ canvas, canvasContext: context, viewport });
	try {
		await pdfRenderTask.promise;
	} catch (error) {
		if (error instanceof Error && error.name !== 'RenderingCancelledException') throw error;
	}
}

function changePdfPage(page: number) {
	if (!pdfDocument) return;
	pdfPage.value = Math.min(Math.max(page, 1), pdfDocument.numPages);
	void renderPdfPage();
}

function schedulePoll() {
	if (pollTimer) clearTimeout(pollTimer);
	pollTimer = setTimeout(() => void loadPreview(), 2500);
}

async function submitReview() {
	if (!reviewDialog.value) return;
	reviewing.value = true;
	try {
		if (reviewDialog.value === 'approve') {
			await agentKnowledgeService.approveParse(knowledgeId.value, reviewComment.value);
			$tip('审核已通过，正在发布向量');
		} else {
			await agentKnowledgeService.rejectParse(knowledgeId.value, reviewComment.value);
			$tip('解析结果已驳回');
		}
		closeDialog();
		await loadPreview();
	} catch {
		$tip('审核操作失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		reviewing.value = false;
	}
}

function closeDialog() {
	reviewDialog.value = undefined;
	reviewComment.value = '';
}

function goBack() {
	void navigateTo({ path: '/knowledge/agents', query: { agentId: route.query.agentId } });
}

function statusLabel(status: string) {
	return {
		PARSING: '解析中',
		PENDING_REVIEW: '待审核',
		APPROVED: '发布中',
		PUBLISHED: '已发布',
		REJECTED: '已驳回',
		FAILED: '解析失败',
	}[status] || status;
}

function statusColor(status: string) {
	return {
		PARSING: 'blue',
		PENDING_REVIEW: 'warning',
		APPROVED: 'blue',
		PUBLISHED: 'success',
		REJECTED: 'grey',
		FAILED: 'error',
	}[status] || 'grey';
}

function qualityLabel(flag: string) {
	return {
		EMPTY_CONTENT: '内容为空',
		TOO_LONG: '分块过长',
		TOO_SHORT: '分块过短',
		INVALID_CHARACTER: '疑似乱码',
		DUPLICATE_CONTENT: '重复内容',
	}[flag] || flag;
}

function parserLabel(parser?: string) {
	if (parser === 'docling') return `Docling ${preview.value?.parserVersion || ''}`.trim();
	if (parser === 'tika-poi-hwpf') return 'Tika/POI（DOC）';
	return 'Tika 降级解析';
}

function fallbackParserMessage(parser?: string) {
	if (parser === 'tika-poi-hwpf') {
		return '当前文件是旧版 Word DOC，已使用 Tika/POI 解析。正文可用于检索，但标题层级、表格和图片结构可能不完整；如需 Docling 结构化解析，请另存为 DOCX 后重新上传。';
	}
	return '当前修订使用了 Tika 降级解析器，页码、标题层级、表格和图片结构可能不可用。请确认 Docling 服务配置后重新上传文档。';
}

function splitterLabel(splitter?: string) {
	return {
		'docling-hybrid': '混合分块',
		token: '固定长度',
		recursive: '固定长度（重叠）',
		sentence: '句子分块',
		paragraph: '段落分块',
		semantic: '语义分块',
	}[splitter || ''] || splitter || '未知策略';
}

function contentTypeLabel(type?: string) {
	return {
		text: '正文',
		table: '表格',
		picture: '图片',
	}[type || ''] || type || '未知';
}

onMounted(() => {
	void loadPreview();
	void loadSource();
});
watch(
	() => selectedChunk.value?.pageNumber,
	(page) => {
		if (page && pdfDocument && page !== pdfPage.value) changePdfPage(page);
	},
);
onBeforeUnmount(() => {
	if (pollTimer) clearTimeout(pollTimer);
	if (sourceObjectUrl.value) URL.revokeObjectURL(sourceObjectUrl.value);
	void destroyPdfDocument().catch(() => undefined);
});
</script>

<style scoped>
.review-page {
	padding: 28px;
	height: calc(100vh - 72px);
	min-height: 720px;
	background: #f7f9fc;
}

.review-grid {
	display: grid;
	grid-template-columns: minmax(220px, 0.72fr) minmax(360px, 1.35fr) minmax(360px, 1.15fr);
	gap: 14px;
	height: calc(100% - 82px);
}

.pane {
	min-width: 0;
	height: 100%;
	overflow: hidden;
	background: white;
}

.pane-title {
	height: 54px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	font-size: 14px;
	font-weight: 700;
	border-bottom: 1px solid #e8edf3;
}

.chunk-list,
.result-content {
	height: calc(100% - 54px);
	overflow: auto;
}

.chunk-item {
	width: 100%;
	padding: 13px 14px;
	text-align: left;
	border: 0;
	border-bottom: 1px solid #eef1f5;
	background: white;
	cursor: pointer;
}

.chunk-item:hover,
.chunk-item.active {
	background: #eaf6f5;
}

.chunk-item.active {
	box-shadow: inset 3px 0 #00897b;
}

.source-frame-wrap {
	height: calc(100% - 54px);
	background: #eef1f5;
}

.pdf-preview {
	height: 100%;
	display: flex;
	flex-direction: column;
}

.pdf-toolbar {
	height: 42px;
	display: flex;
	align-items: center;
	gap: 6px;
	padding: 4px 10px;
	background: #263238;
	color: white;
	font-size: 12px;
}

.pdf-toolbar a {
	color: white;
}

.pdf-canvas-wrap {
	flex: 1;
	overflow: auto;
	padding: 12px;
	text-align: center;
}

.pdf-canvas {
	display: inline-block;
	background: white;
	box-shadow: 0 2px 10px rgb(15 23 42 / 18%);
}

.empty-source {
	height: 100%;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	color: #607d8b;
}

.result-content {
	padding: 20px;
}

.metadata-row {
	display: flex;
	flex-wrap: wrap;
	gap: 8px 20px;
	padding: 10px 12px;
	border-radius: 8px;
	background: #f4f7fa;
	font-size: 12px;
	color: #546e7a;
}

.markdown-body {
	font-size: 14px;
	line-height: 1.75;
	color: #263238;
}

.markdown-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
	padding: 8px;
	border: 1px solid #d9e0e7;
}

@media (max-width: 1200px) {
	.review-page {
		height: auto;
	}

	.review-grid {
		grid-template-columns: 240px 1fr;
		height: auto;
	}

	.pane {
		height: 680px;
	}

	.result-pane {
		grid-column: 1 / -1;
		height: 520px;
	}
}
</style>
