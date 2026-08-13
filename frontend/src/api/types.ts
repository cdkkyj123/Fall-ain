// 백엔드 DTO와 1:1로 대응하는 타입들.
// docs/api/API.md 및 각 컨트롤러/DTO(src/main/java/com/sok/fallain/api/**)를 근거로 작성.

export type RelationshipStage =
  | "STRANGER"
  | "ACQUAINTED"
  | "CLOSE"
  | "TRUSTED"
  | "LOVED"
  | string;

export type DayState = "IN_PROGRESS" | "CLOSED" | string;

export type Freshness = "ACTIVE" | "FADING" | "FADED" | string;

/** 에러 응답 공통 포맷 ({code, message}). WS의 ErrorSignal과 동일 형태. */
export interface ErrorSignalPayload {
  code: string;
  message: string;
}

export interface PlayerBootstrapResponse {
  playerId: string;
}

export interface CharacterListItem {
  id: number;
  name: string;
  introduction: string;
}

export interface RelationshipStartResponse {
  ucId: number;
}

export interface MemoryCandidate {
  factKey: string;
  freshness: Freshness;
}

export interface RelationshipStatusResponse {
  ucId: number;
  day: number;
  intimacy: number;
  stage: RelationshipStage;
  turnsUsedToday: number;
  turnBudget: number;
  turnsLeftToday: number;
  dayState: DayState;
  memoryCandidates: MemoryCandidate[];
}

export interface TurnMessageResponse {
  ucId: number;
  replyText: string;
  droppedFactKeys: string[];
  intimacy: number;
  intimacyDelta: number;
  turnsUsedToday: number;
  turnsLeftToday: number;
  dayState: DayState;
  dayJustClosed: boolean;
}

export interface EndDayResponse {
  ucId: number;
  closedDay: number;
  closedReason: string;
  dayState: DayState;
}
