<!--
 Copyright 2026 the original author or authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<template>
	<div class="code-block-wrapper">
		<div class="code-block-header">
			<span class="code-language">{{ language.toUpperCase() }}</span>
			<button type="button" class="code-copy-button" @click="copyCode">
				{{ copied ? '已复制' : '复制' }}
			</button>
		</div>
		<pre
			class="hljs"
		><code :class="`language-${language}`" v-html="highlightedCode" /></pre>
	</div>
</template>

<script setup lang="ts">
import { hljs } from '~/utils/markdown/markdown-plugin-highlight';

const props = withDefaults(
	defineProps<{
		code: string;
		language?: 'sql' | 'json' | 'text';
	}>(),
	{ language: 'text' },
);

const copied = ref(false);
const highlightedCode = computed(() => {
	if (hljs.getLanguage(props.language)) {
		return hljs.highlight(props.code, { language: props.language }).value;
	}
	return hljs.highlightAuto(props.code).value;
});

async function copyCode() {
	try {
		await navigator.clipboard.writeText(props.code);
		copied.value = true;
		setTimeout(() => {
			copied.value = false;
		}, 1500);
	} catch {
		window.__tipShow?.('复制失败', {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	}
}
</script>

<style scoped>
.code-block-wrapper {
	background: #f6f8fa;
}
.code-block-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6px 10px;
	border-bottom: 1px solid #e1e4e8;
	font-size: 11px;
}
.code-language {
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 10px;
	font-weight: 600;
	color: #6a737d;
}
.code-copy-button {
	padding: 3px 10px;
	border: 1px solid #d1d5da;
	border-radius: 4px;
	background: transparent;
	font-size: 10px;
	color: #24292e;
	cursor: pointer;
}
.code-copy-button:hover {
	background: #eef1f4;
}
pre.hljs {
	margin: 0;
	padding: 10px;
	overflow-x: auto;
	background: #f6f8fa;
	font-size: 12px;
	line-height: 1.5;
	white-space: pre;
}
pre.hljs code {
	display: block;
	min-width: max-content;
	font-family: 'Monaco', 'Menlo', monospace;
}
</style>
