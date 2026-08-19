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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.rag.EmbeddingClient;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceFactory;
import com.alibaba.cloud.ai.dataagent.splitter.FixedSizeTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.ParagraphTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.SemanticTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.SentenceSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.TextSplitter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Management-service infrastructure; all agent execution lives in AgentScope. */
@Configuration
@EnableAsync
@EnableConfigurationProperties({ DataAgentProperties.class, FileStorageProperties.class })
public class DataAgentConfiguration implements DisposableBean {

	private ExecutorService dbOperationExecutor;

	@Bean
	@ConditionalOnMissingBean(FileStorageService.class)
	public FileStorageService fileStorageService(FileStorageProperties properties,
			OssStorageProperties ossStorageProperties) {
		return new FileStorageServiceFactory(properties, ossStorageProperties).getObject();
	}

	@Bean(name = "dbOperationExecutor")
	public ExecutorService dbOperationExecutor() {
		AtomicInteger sequence = new AtomicInteger();
		dbOperationExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()),
				runnable -> {
					Thread thread = new Thread(runnable, "data-agent-db-" + sequence.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				});
		return dbOperationExecutor;
	}

	@Bean(name = "token")
	public TextSplitter tokenTextSplitter(DataAgentProperties properties) {
		return new FixedSizeTextSplitter(properties.getTextSplitter().getChunkSize(), 0);
	}

	@Bean(name = "recursive")
	public TextSplitter recursiveTextSplitter(DataAgentProperties properties) {
		return new FixedSizeTextSplitter(properties.getTextSplitter().getChunkSize(),
				properties.getTextSplitter().getRecursive().getChunkOverlap());
	}

	@Bean(name = "sentence")
	public TextSplitter sentenceTextSplitter(DataAgentProperties properties) {
		return SentenceSplitter.builder()
			.withChunkSize(properties.getTextSplitter().getChunkSize())
			.withSentenceOverlap(properties.getTextSplitter().getSentence().getSentenceOverlap())
			.build();
	}

	@Bean(name = "semantic")
	public TextSplitter semanticTextSplitter(DataAgentProperties properties, EmbeddingClient embeddingClient) {
		return SemanticTextSplitter.builder()
			.embeddingClient(embeddingClient)
			.minChunkSize(properties.getTextSplitter().getSemantic().getMinChunkSize())
			.maxChunkSize(properties.getTextSplitter().getSemantic().getMaxChunkSize())
			.similarityThreshold(properties.getTextSplitter().getSemantic().getSimilarityThreshold())
			.embeddingBatchSize(properties.getEmbeddingBatch().getMaxTextCount())
			.build();
	}

	@Bean(name = "paragraph")
	public TextSplitter paragraphTextSplitter(DataAgentProperties properties) {
		return ParagraphTextSplitter.builder()
			.chunkSize(properties.getTextSplitter().getChunkSize())
			.paragraphOverlapChars(properties.getTextSplitter().getParagraph().getParagraphOverlapChars())
			.build();
	}

	@Override
	public void destroy() {
		if (dbOperationExecutor != null) {
			dbOperationExecutor.shutdownNow();
		}
	}

}
