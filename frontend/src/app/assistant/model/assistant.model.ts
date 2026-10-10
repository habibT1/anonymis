export interface DocumentSummary {
  id: string;
  filename: string;
  pageCount: number;
  needsOcr: boolean;
  characters: number;
  uploadedAt: string;
}

export type MaskLevel = 'standard' | 'strict';

export interface DetectionOptions {
  level: MaskLevel;
  watchlist: { term: string; category: string }[];
  allowlist: string[];
}

export interface AskRequest {
  conversationId: string | null;
  question: string;
  documentIds: string[];
  options: DetectionOptions;
  dryRun: boolean;
}

export interface AskResponse {
  conversationId: string;
  answer: string | null;
  aiView: { sent: string; received: string | null };
  maskedByType: Record<string, number>;
  maskedTotal: number;
  unknownTokens: string[];
  model: string | null;
}

export interface AssistantInfo {
  model: string;
  local: boolean;
}

/** Un échange affiché dans la conversation. */
export interface Exchange {
  question: string;
  status: 'pending' | 'done' | 'preview' | 'blocked' | 'error';
  response?: AskResponse;
  error?: string;
  blockedTypes?: string[];
}
