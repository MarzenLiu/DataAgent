import { describe, expect, it } from 'vitest';
import { calculatePdfHighlight } from './pdfHighlight';

describe('calculatePdfHighlight', () => {
	it('maps a bottom-left Docling box into the rendered PDF viewport', () => {
		const result = calculatePdfHighlight(
			{
				knowledgeId: 21,
				pageNumber: 18,
				boundingBox: { left: 10, top: 30, right: 50, bottom: 10 },
				coordinateOrigin: 'BOTTOMLEFT',
				pageWidth: 100,
				pageHeight: 100,
			},
			{
				width: 200,
				height: 200,
				rawDims: { pageX: 400, pageY: 10 },
				convertToViewportPoint: (x, y) => [(x - 400) * 2, 200 - (y - 10) * 2],
			},
			100,
			100,
			2,
		);

		expect(result).toEqual({ left: 7, top: 67, width: 46, height: 26 });
	});

	it('returns no highlight when the citation has no bounding box', () => {
		expect(
			calculatePdfHighlight(
				{ knowledgeId: 21, pageNumber: 18 },
				{ width: 100, height: 100, convertToViewportPoint: (x, y) => [x, y] },
				100,
				100,
				1,
			),
		).toBeUndefined();
	});
});
