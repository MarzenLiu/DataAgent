import type { DocumentCitationTarget } from '~/utils/documentCitation';

export interface DocumentPreviewState {
	open: boolean;
	knowledgeId?: number;
	filename: string;
	pageNumber: number;
	location?: DocumentCitationTarget;
}

export function useDocumentPreview() {
	const state = useState<DocumentPreviewState>('chat-document-preview', () => ({
		open: false,
		filename: '',
		pageNumber: 1,
	}));

	function open(location: DocumentCitationTarget, filename: string) {
		state.value = {
			open: true,
			knowledgeId: location.knowledgeId,
			filename,
			pageNumber: location.pageNumber,
			location,
		};
	}

	function close() {
		state.value = {
			open: false,
			filename: '',
			pageNumber: 1,
		};
	}

	return { state, open, close };
}
