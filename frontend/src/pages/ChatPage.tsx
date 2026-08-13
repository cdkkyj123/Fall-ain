import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, endDay, fetchRelationshipStatus, sendMessageRest } from "../api/client";
import { useChatSocket } from "../api/ws";
import type { EndDayResponse, ErrorSignalPayload, RelationshipStatusResponse, TurnMessageResponse } from "../api/types";
import { errorSignalMessage } from "../lib/relationshipDisplay";
import { ChatWindow } from "../components/ChatWindow";
import type { ChatMessage } from "../components/MessageBubble";
import { IntimacyMeter } from "../components/IntimacyMeter";
import { TurnBudgetBar } from "../components/TurnBudgetBar";
import { LingeringHints } from "../components/LingeringHints";
import { DayCloseModal } from "../components/DayCloseModal";
import { ErrorBanner } from "../components/ErrorBanner";

const PENDING_REPLY_TIMEOUT_MS = 25000;

function createMessageId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function ChatPage() {
  const { ucId } = useParams<{ ucId: string }>();
  const navigate = useNavigate();

  const [status, setStatus] = useState<RelationshipStatusResponse | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isWaitingForReply, setIsWaitingForReply] = useState(false);
  const [pendingContent, setPendingContent] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState("");
  const [turnBanner, setTurnBanner] = useState<{ message: string; retryable: boolean } | null>(null);
  const [dayCloseInfo, setDayCloseInfo] = useState<{ closedDay: number } | null>(null);

  const pendingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearPendingTimeout = () => {
    if (pendingTimeoutRef.current) {
      clearTimeout(pendingTimeoutRef.current);
      pendingTimeoutRef.current = null;
    }
  };

  const loadStatus = useCallback(async () => {
    if (!ucId) return;
    try {
      const data = await fetchRelationshipStatus(ucId);
      setStatus(data);
      setStatusError(null);
    } catch (err) {
      const message = err instanceof ApiError ? errorSignalMessage(err.code) : errorSignalMessage("UNKNOWN");
      setStatusError(message);
    }
  }, [ucId]);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  const applyTurnResult = useCallback((response: TurnMessageResponse) => {
    setStatus((prev) =>
      prev
        ? {
            ...prev,
            intimacy: response.intimacy,
            turnsUsedToday: response.turnsUsedToday,
            turnsLeftToday: response.turnsLeftToday,
            dayState: response.dayState,
          }
        : prev,
    );
  }, []);

  const handleTurnSuccess = useCallback(
    (response: TurnMessageResponse) => {
      clearPendingTimeout();
      setIsWaitingForReply(false);
      setPendingContent(null);
      setTurnBanner(null);
      setMessages((prev) => [
        ...prev,
        {
          id: createMessageId(),
          role: "character",
          content: response.replyText,
          touchedMemory: response.droppedFactKeys.length > 0,
        },
      ]);
      applyTurnResult(response);
      if (response.dayJustClosed) {
        setDayCloseInfo({ closedDay: status?.day ?? response.turnsUsedToday });
      }
    },
    [applyTurnResult, status?.day],
  );

  const handleTurnError = useCallback(
    (error: { code: string; message?: string }) => {
      clearPendingTimeout();
      setIsWaitingForReply(false);
      const message = errorSignalMessage(error.code);

      if (error.code === "ARC_ENDED") {
        navigate(`/ending/${ucId}`);
        return;
      }

      if (error.code === "DAY_CLOSED") {
        setStatus((prev) => (prev ? { ...prev, dayState: "CLOSED" } : prev));
      }

      setTurnBanner({ message, retryable: error.code !== "DAY_CLOSED" });
    },
    [navigate, ucId],
  );

  const handleResync = useCallback(() => {
    void loadStatus();
  }, [loadStatus]);

  const handleWsMessage = useCallback(
    (response: TurnMessageResponse) => {
      handleTurnSuccess(response);
    },
    [handleTurnSuccess],
  );

  const handleWsError = useCallback(
    (signal: ErrorSignalPayload) => {
      handleTurnError(signal);
    },
    [handleTurnError],
  );

  const socket = useChatSocket({
    ucId,
    onMessage: handleWsMessage,
    onError: handleWsError,
    onResync: handleResync,
  });

  const submitContent = useCallback(
    async (content: string) => {
      if (!ucId) return;

      setPendingContent(content);
      setIsWaitingForReply(true);
      setTurnBanner(null);

      const usedWs = socket.status === "connected" && socket.sendMessage(content);
      if (usedWs) {
        pendingTimeoutRef.current = setTimeout(() => {
          setIsWaitingForReply(false);
          setTurnBanner({ message: errorSignalMessage("LLM_UNAVAILABLE"), retryable: true });
        }, PENDING_REPLY_TIMEOUT_MS);
        return;
      }

      try {
        const response = await sendMessageRest(ucId, content);
        handleTurnSuccess(response);
      } catch (err) {
        const code = err instanceof ApiError ? err.code : "UNKNOWN";
        handleTurnError({ code });
      }
    },
    [handleTurnError, handleTurnSuccess, socket, ucId],
  );

  const handleSend = () => {
    const content = inputValue.trim();
    if (!content || !status) return;
    if (status.dayState === "CLOSED") {
      setTurnBanner({ message: errorSignalMessage("DAY_CLOSED"), retryable: false });
      return;
    }
    if (status.turnsLeftToday <= 0) {
      setTurnBanner({ message: errorSignalMessage("TURN_BUDGET_EXCEEDED"), retryable: false });
      return;
    }

    setMessages((prev) => [...prev, { id: createMessageId(), role: "user", content }]);
    setInputValue("");
    void submitContent(content);
  };

  const handleRetry = () => {
    if (!pendingContent) return;
    void submitContent(pendingContent);
  };

  const handleEndDay = async () => {
    if (!ucId) return;
    try {
      const response: EndDayResponse = await endDay(ucId);
      setStatus((prev) => (prev ? { ...prev, dayState: response.dayState, turnsLeftToday: 0 } : prev));
      setDayCloseInfo({ closedDay: response.closedDay });
    } catch (err) {
      const code = err instanceof ApiError ? err.code : "UNKNOWN";
      setTurnBanner({ message: errorSignalMessage(code), retryable: false });
    }
  };

  const canSend = Boolean(
    status && status.dayState !== "CLOSED" && status.turnsLeftToday > 0 && !isWaitingForReply,
  );

  if (statusError && !status) {
    return (
      <div className="flex min-h-full items-center justify-center px-6">
        <div className="w-full max-w-sm text-center">
          <ErrorBanner message={statusError} onRetry={() => void loadStatus()} />
        </div>
      </div>
    );
  }

  if (!status) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <p className="text-sm text-stone-400">불러오는 중이에요...</p>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col bg-gradient-to-b from-cream-50 via-blush-50/60 to-lavender-50">
      <header className="border-b border-blush-100/70 bg-white/70 px-4 py-3 backdrop-blur">
        <div className="mx-auto flex max-w-2xl flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-stone-400">{status.day}일째</p>
              <IntimacyMeter intimacy={status.intimacy} stage={status.stage} />
            </div>
            <TurnBudgetBar turnsLeftToday={status.turnsLeftToday} turnBudget={status.turnBudget} dayState={status.dayState} />
          </div>
          <LingeringHints memoryCandidates={status.memoryCandidates} />
        </div>
      </header>

      {turnBanner && (
        <div className="px-4 pt-3">
          <ErrorBanner
            message={turnBanner.message}
            onRetry={turnBanner.retryable ? handleRetry : undefined}
            onDismiss={() => setTurnBanner(null)}
          />
        </div>
      )}

      <ChatWindow messages={messages} isWaitingForReply={isWaitingForReply} />

      <footer className="border-t border-blush-100/70 bg-white/70 px-4 py-3 backdrop-blur">
        <div className="mx-auto flex max-w-2xl items-end gap-2">
          <textarea
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                handleSend();
              }
            }}
            placeholder={canSend ? "오늘의 이야기를 건네보세요" : "오늘은 여기까지 이야기했어요"}
            disabled={!canSend}
            rows={1}
            className="max-h-28 flex-1 resize-none rounded-2xl border border-blush-100 bg-white px-4 py-2.5 text-sm text-stone-700 outline-none placeholder:text-stone-300 focus:border-blush-300 disabled:bg-stone-50 disabled:text-stone-300"
          />
          <button
            type="button"
            onClick={handleSend}
            disabled={!canSend || !inputValue.trim()}
            className="shrink-0 rounded-full bg-blush-400 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blush-500 disabled:opacity-40"
          >
            보내기
          </button>
        </div>
        <div className="mx-auto mt-2 flex max-w-2xl justify-end">
          <button
            type="button"
            onClick={() => void handleEndDay()}
            disabled={status.dayState === "CLOSED"}
            className="text-xs text-stone-400 underline-offset-2 hover:text-stone-500 hover:underline disabled:opacity-40"
          >
            오늘은 여기까지 할게요
          </button>
        </div>
      </footer>

      {dayCloseInfo && (
        <DayCloseModal
          closedDay={dayCloseInfo.closedDay}
          onConfirm={() => setDayCloseInfo(null)}
        />
      )}
    </div>
  );
}
