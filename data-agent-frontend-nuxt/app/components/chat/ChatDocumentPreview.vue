<template>
	<Transition name="document-preview">
		<aside v-if="preview.state.value.open" class="document-preview">
			<header class="document-preview__header">
				<div class="document-preview__title">
					<v-icon size="18" color="primary">mdi-file-pdf-box</v-icon>
					<div>
						<div class="document-preview__filename">{{ preview.state.value.filename }}</div>
						<div class="document-preview__page">
							第 {{ pdfPage }} 页<span v-if="highlightRectangle"> · 已定位原文</span>
						</div>
					</div>
				</div>
				<v-btn icon="mdi-close" size="small" variant="text" aria-label="关闭文档预览" @click="preview.close" />
			</header>

			<div v-if="pdfReady" class="document-preview__body">
				<div class="document-preview__toolbar">
					<v-btn icon="mdi-chevron-left" size="x-small" variant="text" :disabled="pdfPage <= 1" @click="changePdfPage(pdfPage - 1)" />
					<span>{{ pdfPage }} / {{ pdfPageCount }}</span>
					<v-btn icon="mdi-chevron-right" size="x-small" variant="text" :disabled="pdfPage >= pdfPageCount" @click="changePdfPage(pdfPage + 1)" />
					<v-spacer />
					<a v-if="sourceObjectUrl" :href="sourceObjectUrl" target="_blank" rel="noopener">打开原文件</a>
				</div>
				<div ref="canvasWrap" class="document-preview__canvas-wrap">
					<div ref="pageLayer" class="document-preview__page-layer">
						<canvas ref="pdfCanvas" class="document-preview__canvas" />
						<div
							v-if="highlightRectangle"
							class="document-preview__highlight"
							:style="highlightStyle"
							aria-label="检索命中的原文位置"
						/>
					</div>
				</div>
			</div>

			<div v-else class="document-preview__empty">
				<v-progress-circular v-if="sourceLoading" indeterminate color="primary" />
				<v-icon v-else icon="mdi-file-alert-outline" size="52" color="error" />
				<p>{{ sourceLoading ? '正在加载原始 PDF…' : sourceLoadError || '原始 PDF 加载失败' }}</p>
			</div>
		</aside>
	</Transition>
</template>

<script setup lang="ts">
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import agentKnowledgeService from '~/services/agentKnowledge';
import { useDocumentPreview } from '~/composables/useDocumentPreview';
import { calculatePdfHighlight, type PdfHighlightRectangle } from '~/utils/pdfHighlight';

const preview = useDocumentPreview();
const sourceObjectUrl = ref('');
const sourceLoading = ref(false);
const sourceLoadError = ref('');
const pdfCanvas = ref<HTMLCanvasElement>();
const canvasWrap = ref<HTMLElement>();
const pageLayer = ref<HTMLElement>();
const pdfReady = ref(false);
const pdfPage = ref(1);
const pdfPageCount = ref(0);
const highlightRectangle = ref<PdfHighlightRectangle>();
const loadedKnowledgeId = ref<number>();
let pdfDocument: PDFDocumentProxy | undefined;
let pdfRenderTask: RenderTask | undefined;
let loadSequence = 0;
let resizeTimer: ReturnType<typeof setTimeout> | undefined;

const highlightStyle = computed(() => {
	const rectangle = highlightRectangle.value;
	if (!rectangle) return {};
	return {
		left: `${rectangle.left}px`,
		top: `${rectangle.top}px`,
		width: `${rectangle.width}px`,
		height: `${rectangle.height}px`,
	};
});

async function loadSource(knowledgeId: number) {
	const sequence = ++loadSequence;
	sourceLoading.value = true;
	sourceLoadError.value = '';
	pdfReady.value = false;
	highlightRectangle.value = undefined;
	try {
		const source = await agentKnowledgeService.getParseSource(knowledgeId);
		if (sequence !== loadSequence) return;
		if (sourceObjectUrl.value) URL.revokeObjectURL(sourceObjectUrl.value);
		sourceObjectUrl.value = URL.createObjectURL(source);
		await loadPdf(source);
		loadedKnowledgeId.value = knowledgeId;
	} catch {
		if (sequence === loadSequence) sourceLoadError.value = '原始 PDF 读取失败，请检查文件是否仍然存在。';
	} finally {
		if (sequence === loadSequence) sourceLoading.value = false;
	}
}

async function loadPdf(source: Blob) {
	await destroyPdfDocument();
	const pdfjs = await import('pdfjs-dist');
	const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
	pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
	pdfDocument = await pdfjs.getDocument({ data: new Uint8Array(await source.arrayBuffer()) }).promise;
	pdfPageCount.value = pdfDocument.numPages;
	pdfPage.value = clampPage(preview.state.value.pageNumber);
	pdfReady.value = true;
	await nextTick();
	await renderPdfPage();
}

async function destroyPdfDocument() {
	const documentToDestroy = pdfDocument;
	pdfDocument = undefined;
	pdfRenderTask?.cancel();
	pdfRenderTask = undefined;
	if (documentToDestroy && typeof documentToDestroy.destroy === 'function') await documentToDestroy.destroy();
}

async function renderPdfPage() {
	if (!pdfDocument || !pdfCanvas.value || !pageLayer.value) return;
	pdfRenderTask?.cancel();
	highlightRectangle.value = undefined;
	const page = await pdfDocument.getPage(pdfPage.value);
	const unscaled = page.getViewport({ scale: 1 });
	const availableWidth = Math.max(280, (canvasWrap.value?.clientWidth || 600) - 24);
	const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
	const viewport = page.getViewport({ scale: (availableWidth / unscaled.width) * pixelRatio });
	const canvas = pdfCanvas.value;
	const context = canvas.getContext('2d');
	if (!context) return;
	const cssWidth = viewport.width / pixelRatio;
	const cssHeight = viewport.height / pixelRatio;
	canvas.width = Math.floor(viewport.width);
	canvas.height = Math.floor(viewport.height);
	canvas.style.width = `${cssWidth}px`;
	canvas.style.height = `${cssHeight}px`;
	pageLayer.value.style.width = `${cssWidth}px`;
	pageLayer.value.style.height = `${cssHeight}px`;
	pdfRenderTask = page.render({ canvas, canvasContext: context, viewport });
	try {
		await pdfRenderTask.promise;
		const location = preview.state.value.location;
		if (location?.pageNumber === pdfPage.value) {
			highlightRectangle.value = calculatePdfHighlight(location, viewport, unscaled.width, unscaled.height, pixelRatio);
			if (highlightRectangle.value) {
				await nextTick();
				canvasWrap.value?.scrollTo({
					top: Math.max(0, highlightRectangle.value.top - 56),
					behavior: 'smooth',
				});
			}
		}
	} catch (error) {
		if (error instanceof Error && error.name !== 'RenderingCancelledException') throw error;
	}
}

function clampPage(page: number) {
	return Math.min(Math.max(page || 1, 1), Math.max(pdfPageCount.value, 1));
}

function changePdfPage(page: number) {
	if (!pdfDocument) return;
	pdfPage.value = clampPage(page);
	void renderPdfPage();
}

async function closeDocument() {
	++loadSequence;
	loadedKnowledgeId.value = undefined;
	pdfReady.value = false;
	highlightRectangle.value = undefined;
	await destroyPdfDocument();
	if (sourceObjectUrl.value) URL.revokeObjectURL(sourceObjectUrl.value);
	sourceObjectUrl.value = '';
}

watch(
	() => [preview.state.value.open, preview.state.value.knowledgeId] as const,
	([open, knowledgeId]) => {
		if (!open || !knowledgeId) {
			void closeDocument();
			return;
		}
		if (loadedKnowledgeId.value !== knowledgeId) void loadSource(knowledgeId);
	},
	{ immediate: true },
);

watch(
	() => preview.state.value.location,
	(location) => {
		if (!location || !pdfDocument || !pdfReady.value) return;
		pdfPage.value = clampPage(location.pageNumber);
		void nextTick(renderPdfPage);
	},
);

function handleResize() {
	if (resizeTimer) clearTimeout(resizeTimer);
	resizeTimer = setTimeout(() => {
		if (pdfReady.value) void renderPdfPage();
	}, 120);
}

onMounted(() => window.addEventListener('resize', handleResize));
onBeforeUnmount(() => {
	window.removeEventListener('resize', handleResize);
	if (resizeTimer) clearTimeout(resizeTimer);
	void closeDocument().catch(() => undefined);
});
</script>

<style scoped>
.document-preview {
	width: clamp(420px, 42vw, 720px);
	height: 100%;
	display: flex;
	flex-direction: column;
	background: #fff;
	border-left: 1px solid #dce3ec;
	box-shadow: -8px 0 24px rgb(15 23 42 / 10%);
	z-index: 5;
}

.document-preview__header {
	height: 64px;
	padding: 8px 12px 8px 16px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	border-bottom: 1px solid #e8edf2;
}

.document-preview__title {
	min-width: 0;
	display: flex;
	align-items: center;
	gap: 10px;
}

.document-preview__filename {
	max-width: 520px;
	overflow: hidden;
	font-size: 13px;
	font-weight: 600;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.document-preview__page {
	margin-top: 2px;
	font-size: 12px;
	color: #64748b;
}

.document-preview__body {
	min-height: 0;
	flex: 1;
	display: flex;
	flex-direction: column;
	background: #eef2f7;
}

.document-preview__toolbar {
	height: 42px;
	display: flex;
	align-items: center;
	gap: 6px;
	padding: 4px 10px;
	background: #263238;
	color: white;
	font-size: 12px;
}

.document-preview__toolbar a { color: white; }

.document-preview__canvas-wrap {
	min-height: 0;
	flex: 1;
	overflow: auto;
	padding: 12px;
	text-align: center;
}

.document-preview__page-layer {
	position: relative;
	display: inline-block;
	background: white;
	box-shadow: 0 2px 10px rgb(15 23 42 / 18%);
}

.document-preview__canvas { display: block; }

.document-preview__highlight {
	position: absolute;
	border: 2px solid rgb(245 158 11 / 92%);
	border-radius: 3px;
	background: rgb(250 204 21 / 32%);
	box-shadow: 0 0 0 1px rgb(255 255 255 / 72%);
	pointer-events: none;
	animation: highlight-pulse 900ms ease-out;
}

.document-preview__empty {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 12px;
	padding: 24px;
	color: #607d8b;
	text-align: center;
}

@keyframes highlight-pulse {
	0% { opacity: 0; transform: scale(0.98); }
	100% { opacity: 1; transform: scale(1); }
}

.document-preview-enter-active,
.document-preview-leave-active {
	transition: width 180ms ease, opacity 180ms ease;
}

.document-preview-enter-from,
.document-preview-leave-to {
	width: 0;
	opacity: 0;
}

@media (max-width: 960px) {
	.document-preview {
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		width: min(92vw, 680px);
	}
}
</style>
