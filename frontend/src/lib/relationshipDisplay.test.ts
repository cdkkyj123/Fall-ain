import { describe, it, expect } from "vitest";
import {
  stageLabel,
  errorSignalMessage,
  turnsLeftLabel,
} from "./relationshipDisplay";

// Contract under test: frontend/src/lib/relationshipDisplay.ts (not yet implemented — RED)
//
// stageLabel(stage: string): string
//   Maps the server-provided relationship stage (RelationshipStatusResponse.stage)
//   to a Korean UI label. Product tone: "경청이 사랑" — warm, no game-y "level up"
//   language, no collection/dex framing.
//
// errorSignalMessage(code: string): string
//   Maps an ErrorCode / ErrorSignal.code to a user-facing Korean message.
//   Tone: gentle, in-world, never exposes raw backend wording.
//
// turnsLeftLabel(turnsLeftToday: number, turnBudget: number): string
//   Renders the daily turn budget as "N/M 남음".

describe("stageLabel", () => {
  it("maps STRANGER to a 'still unfamiliar' label", () => {
    expect(stageLabel("STRANGER")).toBe("아직 낯선 사이");
  });

  it("maps ACQUAINTED to a 'getting to know each other' label", () => {
    expect(stageLabel("ACQUAINTED")).toBe("조금씩 알아가는 사이");
  });

  it("maps CLOSE to a 'close' label", () => {
    expect(stageLabel("CLOSE")).toBe("가까워진 사이");
  });

  it("maps TRUSTED to a 'trusted' label", () => {
    expect(stageLabel("TRUSTED")).toBe("믿음이 쌓인 사이");
  });

  it("maps LOVED to a 'in love' label", () => {
    expect(stageLabel("LOVED")).toBe("사랑에 빠진 사이");
  });

  it("falls back to the raw stage for an unknown value", () => {
    expect(stageLabel("UNKNOWN_STAGE")).toBe("UNKNOWN_STAGE");
  });
});

describe("errorSignalMessage", () => {
  it("maps TURN_BUDGET_EXCEEDED to a budget-exhausted message", () => {
    expect(errorSignalMessage("TURN_BUDGET_EXCEEDED")).toBe(
      "오늘은 이만큼만 이야기할 수 있어요. 내일 다시 찾아와 주세요.",
    );
  });

  it("maps LLM_UNAVAILABLE to a connection-instability message", () => {
    expect(errorSignalMessage("LLM_UNAVAILABLE")).toBe(
      "잠시 연결이 불안정해요. 다시 시도해주세요.",
    );
  });

  it("maps TURN_IN_PROGRESS to a still-replying message", () => {
    expect(errorSignalMessage("TURN_IN_PROGRESS")).toBe(
      "아직 이전 이야기에 답하는 중이에요. 잠시만 기다려주세요.",
    );
  });

  it("maps DAY_CLOSED to a day-ended message", () => {
    expect(errorSignalMessage("DAY_CLOSED")).toBe(
      "오늘의 대화는 이미 마무리됐어요.",
    );
  });

  it("maps ARC_ENDED to a story-ended message", () => {
    expect(errorSignalMessage("ARC_ENDED")).toBe(
      "이 이야기는 이미 끝을 맺었어요.",
    );
  });

  it("maps RELATIONSHIP_NOT_FOUND to a not-found message", () => {
    expect(errorSignalMessage("RELATIONSHIP_NOT_FOUND")).toBe(
      "이 관계를 찾을 수 없어요.",
    );
  });

  it("maps AUTH_MISSING_PLAYER_ID to a re-login-ish message", () => {
    expect(errorSignalMessage("AUTH_MISSING_PLAYER_ID")).toBe(
      "잠시 접속이 끊어졌어요. 페이지를 새로고침해주세요.",
    );
  });

  it("maps CONCURRENT_MODIFICATION to a retry message", () => {
    expect(errorSignalMessage("CONCURRENT_MODIFICATION")).toBe(
      "잠깐 엇갈림이 있었어요. 다시 시도해주세요.",
    );
  });

  it("falls back to a generic message for an unknown code", () => {
    expect(errorSignalMessage("SOME_UNMAPPED_CODE")).toBe(
      "알 수 없는 문제가 발생했어요. 다시 시도해주세요.",
    );
  });
});

describe("turnsLeftLabel", () => {
  it("renders remaining turns out of the full budget", () => {
    expect(turnsLeftLabel(3, 8)).toBe("3/8 남음");
  });

  it("renders a full budget when no turns have been used", () => {
    expect(turnsLeftLabel(8, 8)).toBe("8/8 남음");
  });

  it("renders zero remaining turns", () => {
    expect(turnsLeftLabel(0, 8)).toBe("0/8 남음");
  });

  it("clamps negative remaining turns to zero", () => {
    expect(turnsLeftLabel(-1, 8)).toBe("0/8 남음");
  });
});
