export interface DocumentCitationTarget {
	knowledgeId: number;
	pageNumber: number;
	boundingBox?: DocumentBoundingBox;
	coordinateOrigin?: string;
	pageWidth?: number;
	pageHeight?: number;
}

export interface DocumentBoundingBox {
	left: number;
	top: number;
	right: number;
	bottom: number;
}

const SOURCE_PATH = /^\/api\/agent-knowledge\/(\d+)\/review\/source$/;

export function parseDocumentCitationUrl(
	href: string,
	origin: string,
): DocumentCitationTarget | undefined {
	try {
		const url = new URL(href, origin);
		if (url.origin !== origin) return undefined;
		const match = SOURCE_PATH.exec(url.pathname);
		const params = new URLSearchParams(url.hash.slice(1));
		const pageNumber = Number(params.get('page'));
		const knowledgeId = Number(match?.[1]);
		if (!Number.isInteger(knowledgeId) || knowledgeId < 1) return undefined;
		if (!Number.isInteger(pageNumber) || pageNumber < 1) return undefined;
		const boundingBox = parseBoundingBox(params.get('bbox'));
		const pageWidth = positiveNumber(params.get('pageWidth'));
		const pageHeight = positiveNumber(params.get('pageHeight'));
		return {
			knowledgeId,
			pageNumber,
			...(boundingBox ? { boundingBox } : {}),
			...(params.get('origin') ? { coordinateOrigin: params.get('origin') || undefined } : {}),
			...(pageWidth ? { pageWidth } : {}),
			...(pageHeight ? { pageHeight } : {}),
		};
	} catch {
		return undefined;
	}
}

function parseBoundingBox(value: string | null): DocumentBoundingBox | undefined {
	if (!value) return undefined;
	const coordinates = value.split(',').map(Number);
	if (coordinates.length !== 4 || coordinates.some((coordinate) => !Number.isFinite(coordinate))) {
		return undefined;
	}
	const [left, top, right, bottom] = coordinates;
	if (left === undefined || top === undefined || right === undefined || bottom === undefined) return undefined;
	return { left, top, right, bottom };
}

function positiveNumber(value: string | null): number | undefined {
	if (!value) return undefined;
	const parsed = Number(value);
	return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}
