/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

import axios from 'axios';

export interface MemoryDocument {
	path: string;
	content: string;
	version: number;
	modifiedAt?: string;
}

export interface MemoryOverview {
	userId: string;
	agentId: number;
	consolidated?: MemoryDocument;
	daily: MemoryDocument[];
}

const API_BASE_URL = '/api/memories';

class MemoryService {
	async get(agentId: number): Promise<MemoryOverview> {
		const response = await axios.get<MemoryOverview>(API_BASE_URL, {
			params: { agentId },
		});
		return response.data;
	}

	async saveConsolidated(
		agentId: number,
		content: string,
	): Promise<MemoryDocument> {
		const response = await axios.put<MemoryDocument>(
			`${API_BASE_URL}/consolidated`,
			{
				agentId,
				content,
			},
		);
		return response.data;
	}

	async deleteConsolidated(agentId: number): Promise<void> {
		await axios.delete(`${API_BASE_URL}/consolidated`, { params: { agentId } });
	}

	async deleteDaily(agentId: number, fileName: string): Promise<void> {
		await axios.delete(
			`${API_BASE_URL}/daily/${encodeURIComponent(fileName)}`,
			{
				params: { agentId },
			},
		);
	}

	async clear(agentId: number): Promise<void> {
		await axios.delete(API_BASE_URL, { params: { agentId } });
	}
}

export default new MemoryService();
