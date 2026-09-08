import type { DocumentCitationTarget } from './documentCitation';

export interface PdfViewportLike {
	width: number;
	height: number;
	rawDims?: { pageX: number; pageY: number };
	convertToViewportPoint(x: number, y: number): number[];
}

export interface PdfHighlightRectangle {
	left: number;
	top: number;
	width: number;
	height: number;
}

export function calculatePdfHighlight(
	location: DocumentCitationTarget,
	viewport: PdfViewportLike,
	pdfPageWidth: number,
	pdfPageHeight: number,
	pixelRatio: number,
): PdfHighlightRectangle | undefined {
	const box = location.boundingBox;
	if (!box || pixelRatio <= 0) return undefined;
	const sourceWidth = location.pageWidth || pdfPageWidth;
	const sourceHeight = location.pageHeight || pdfPageHeight;
	if (sourceWidth <= 0 || sourceHeight <= 0) return undefined;
	const scaleX = pdfPageWidth / sourceWidth;
	const scaleY = pdfPageHeight / sourceHeight;
	const pageX = viewport.rawDims?.pageX || 0;
	const pageY = viewport.rawDims?.pageY || 0;
	const left = pageX + box.left * scaleX;
	const right = pageX + box.right * scaleX;
	const topLeftOrigin = location.coordinateOrigin?.toUpperCase().includes('TOP');
	const top = pageY + (topLeftOrigin ? pdfPageHeight - box.top * scaleY : box.top * scaleY);
	const bottom = pageY + (topLeftOrigin ? pdfPageHeight - box.bottom * scaleY : box.bottom * scaleY);
	const converted = [
		...viewport.convertToViewportPoint(left, bottom),
		...viewport.convertToViewportPoint(right, top),
	];
	if (converted.length !== 4 || converted.some((coordinate) => !Number.isFinite(coordinate))) return undefined;
	const x1 = Math.min(converted[0]!, converted[2]!) / pixelRatio;
	const x2 = Math.max(converted[0]!, converted[2]!) / pixelRatio;
	const y1 = Math.min(converted[1]!, converted[3]!) / pixelRatio;
	const y2 = Math.max(converted[1]!, converted[3]!) / pixelRatio;
	const viewportWidth = viewport.width / pixelRatio;
	const viewportHeight = viewport.height / pixelRatio;
	const padding = 3;
	const clampedLeft = Math.max(0, x1 - padding);
	const clampedTop = Math.max(0, y1 - padding);
	const clampedRight = Math.min(viewportWidth, x2 + padding);
	const clampedBottom = Math.min(viewportHeight, y2 + padding);
	if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return undefined;
	return {
		left: clampedLeft,
		top: clampedTop,
		width: clampedRight - clampedLeft,
		height: clampedBottom - clampedTop,
	};
}
