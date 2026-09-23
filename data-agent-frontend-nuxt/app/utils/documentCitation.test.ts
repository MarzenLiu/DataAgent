import { describe, expect, it } from 'vitest';
import { parseDocumentCitationUrl } from './documentCitation';

describe('parseDocumentCitationUrl', () => {
	it('extracts the knowledge id and PDF page from an internal source link', () => {
		expect(
			parseDocumentCitationUrl(
				'/data-agent-management/api/agent-knowledge/21/review/source#page=18',
				'http://localhost:3000',
			),
		).toEqual({ knowledgeId: 21, pageNumber: 18 });
	});

	it('rejects external and malformed links', () => {
		expect(
			parseDocumentCitationUrl(
				'https://example.com/data-agent-management/api/agent-knowledge/21/review/source#page=18',
				'http://localhost:3000',
			),
		).toBeUndefined();
		expect(
			parseDocumentCitationUrl(
				'/data-agent-management/api/agent-knowledge/21/review/source#page=0',
				'http://localhost:3000',
			),
		).toBeUndefined();
	});

	it('extracts optional PDF highlight coordinates', () => {
		expect(
			parseDocumentCitationUrl(
				'/data-agent-management/api/agent-knowledge/21/review/source#page=18&bbox=84.5%2C278%2C510%2C220&origin=BOTTOMLEFT&pageWidth=595&pageHeight=842',
				'http://localhost:3000',
			),
		).toEqual({
			knowledgeId: 21,
			pageNumber: 18,
			boundingBox: { left: 84.5, top: 278, right: 510, bottom: 220 },
			coordinateOrigin: 'BOTTOMLEFT',
			pageWidth: 595,
			pageHeight: 842,
		});
	});
});
