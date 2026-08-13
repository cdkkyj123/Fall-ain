// REST API 래퍼.
// - playerId를 localStorage에 보관하고, 모든 요청에 X-Player-Id 헤더를 자동으로 붙인다.
// - 에러 응답({code, message})을 파싱해 ApiError로 던진다.

import type {
  CharacterListItem,
  EndDayResponse,
  PlayerBootstrapResponse,
  RelationshipStartResponse,
  RelationshipStatusResponse,
  TurnMessageResponse,
} from "./types";

const PLAYER_ID_STORAGE_KEY = "fallain.playerId";
const PLAYER_ID_HEADER = "X-Player-Id";

// 기본값은 상대 경로("")다. 개발 서버(vite.config.ts)가 /api, /ws를 백엔드(:8080)로
// 프록시하므로 브라우저 입장에서는 항상 같은 오리진으로 보인다(CORS 회피).
// 프록시가 없는 환경(예: 백엔드와 다른 오리진에 배포)에서는 VITE_API_BASE_URL로
// 오버라이드한다.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

/** REST 에러 응답({code, message})을 감싸는 예외. UI는 code를 보고 문구/재시도 여부를 결정한다. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

export function getStoredPlayerId(): string | null {
  try {
    return window.localStorage.getItem(PLAYER_ID_STORAGE_KEY);
  } catch {
    return null;
  }
}

function storePlayerId(playerId: string): void {
  try {
    window.localStorage.setItem(PLAYER_ID_STORAGE_KEY, playerId);
  } catch {
    // localStorage를 쓸 수 없는 환경(프라이빗 모드 등)에서도 세션 내 진행은 가능하도록 무시한다.
  }
}

interface RequestOptions {
  method?: "GET" | "POST";
  body?: unknown;
  /** true면 저장된 playerId를 X-Player-Id 헤더로 첨부한다. 기본 true. */
  withPlayerId?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, withPlayerId = true } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (withPlayerId) {
    const playerId = getStoredPlayerId();
    if (playerId) {
      headers[PLAYER_ID_HEADER] = playerId;
    }
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // 네트워크 자체가 끊긴 경우(서버 다운, CORS 등) — LLM_UNAVAILABLE과 동일한 톤으로 안내한다.
    throw new ApiError("NETWORK_ERROR", "잠시 연결이 불안정해요. 다시 시도해주세요.", 0);
  }

  if (!response.ok) {
    let code = "UNKNOWN";
    let message = "알 수 없는 문제가 발생했어요. 다시 시도해주세요.";
    try {
      const data = (await response.json()) as { code?: string; message?: string };
      if (data.code) code = data.code;
      if (data.message) message = data.message;
    } catch {
      // 에러 본문이 JSON이 아닌 경우 기본 메시지를 사용한다.
    }
    throw new ApiError(code, message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/** 최초 진입 시 신규 playerId를 발급받는다. 백엔드는 호출마다 항상 새 UUID를 발급하므로,
 * 이미 localStorage에 값이 있다면 호출하지 말고 그 값을 재사용해야 한다 (ensurePlayerId 참고). */
export async function bootstrapPlayer(): Promise<string> {
  const data = await request<PlayerBootstrapResponse>("/api/players", {
    method: "POST",
    withPlayerId: false,
  });
  storePlayerId(data.playerId);
  return data.playerId;
}

/** 저장된 playerId가 있으면 그대로 반환하고, 없으면 새로 발급받아 저장한다. */
export async function ensurePlayerId(): Promise<string> {
  const existing = getStoredPlayerId();
  if (existing) {
    return existing;
  }
  return bootstrapPlayer();
}

export function fetchCharacters(): Promise<CharacterListItem[]> {
  return request<CharacterListItem[]>("/api/characters");
}

export function startRelationship(characterId: number): Promise<RelationshipStartResponse> {
  return request<RelationshipStartResponse>(`/api/characters/${characterId}/start`, {
    method: "POST",
  });
}

export function fetchRelationshipStatus(ucId: number | string): Promise<RelationshipStatusResponse> {
  return request<RelationshipStatusResponse>(`/api/relationships/${ucId}`);
}

export function sendMessageRest(ucId: number | string, content: string): Promise<TurnMessageResponse> {
  return request<TurnMessageResponse>(`/api/relationships/${ucId}/message`, {
    method: "POST",
    body: { content },
  });
}

export function endDay(ucId: number | string): Promise<EndDayResponse> {
  return request<EndDayResponse>(`/api/relationships/${ucId}/end-day`, {
    method: "POST",
  });
}

export { API_BASE_URL };
