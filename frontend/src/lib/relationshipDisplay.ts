// 관계 상태를 유저에게 보여줄 한국어 문구로 변환하는 순수 함수 모음.
// "경청이 사랑" 톤 원칙: 게임적/레벨업 표현 금지, 따뜻하고 담백한 문구만 사용한다.

const STAGE_LABELS: Record<string, string> = {
  STRANGER: "아직 낯선 사이",
  ACQUAINTED: "조금씩 알아가는 사이",
  CLOSE: "가까워진 사이",
  TRUSTED: "믿음이 쌓인 사이",
  LOVED: "사랑에 빠진 사이",
};

/**
 * 서버가 내려주는 관계 단계(RelationshipStatusResponse.stage)를
 * 한국어 UI 라벨로 변환한다. 매핑되지 않는 값은 원본 문자열을 그대로 반환한다.
 */
export function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage;
}

const ERROR_SIGNAL_MESSAGES: Record<string, string> = {
  TURN_BUDGET_EXCEEDED: "오늘은 이만큼만 이야기할 수 있어요. 내일 다시 찾아와 주세요.",
  LLM_UNAVAILABLE: "잠시 연결이 불안정해요. 다시 시도해주세요.",
  TURN_IN_PROGRESS: "아직 이전 이야기에 답하는 중이에요. 잠시만 기다려주세요.",
  DAY_CLOSED: "오늘의 대화는 이미 마무리됐어요.",
  ARC_ENDED: "이 이야기는 이미 끝을 맺었어요.",
  RELATIONSHIP_NOT_FOUND: "이 관계를 찾을 수 없어요.",
  AUTH_MISSING_PLAYER_ID: "잠시 접속이 끊어졌어요. 페이지를 새로고침해주세요.",
  CONCURRENT_MODIFICATION: "잠깐 엇갈림이 있었어요. 다시 시도해주세요.",
};

const DEFAULT_ERROR_SIGNAL_MESSAGE = "알 수 없는 문제가 발생했어요. 다시 시도해주세요.";

/**
 * ErrorCode / ErrorSignal.code를 유저에게 보여줄 한국어 메시지로 변환한다.
 * 백엔드 원문 메시지를 그대로 노출하지 않고, 매핑되지 않은 코드는 일반 메시지로 대체한다.
 */
export function errorSignalMessage(code: string): string {
  return ERROR_SIGNAL_MESSAGES[code] ?? DEFAULT_ERROR_SIGNAL_MESSAGE;
}

const DAY_CLOSED_LABEL = "오늘은 마무리했어요";

/**
 * 오늘 남은 대화 턴을 "N/M 남음" 형태로 렌더링한다.
 * turnsLeftToday가 음수로 내려오는 경우(레이스 컨디션 등)에도 0으로 clamp한다.
 *
 * dayState가 "CLOSED"이면 턴이 남아있어도(예: 조기종료 직후 turnsLeftToday가 아직
 * 갱신되지 않은 상태) "8/8 남음" 같은 모순된 문구가 보이지 않도록, 잔여 턴 수 대신
 * 하루가 마무리됐다는 문구로 대체한다.
 */
export function turnsLeftLabel(turnsLeftToday: number, turnBudget: number, dayState?: string): string {
  if (dayState === "CLOSED") {
    return DAY_CLOSED_LABEL;
  }
  const clamped = Math.max(0, turnsLeftToday);
  return `${clamped}/${turnBudget} 남음`;
}
