// STOMP(WS) 대화 채널 클라이언트 훅.
// docs/api/API.md "WebSocket (STOMP)" 기준: 핸드셰이크 /ws(SockJS), SEND /app/chat/{ucId},
// SUBSCRIBE /topic/chat/{ucId}. 수신 프레임은 TurnMessageResponse 또는 ErrorSignal({code,message})
// 둘 중 하나이며 code 필드 유무로 구분한다.
//
// 연결이 끊겼다 재연결되면, WS 프레임 자체는 상태의 단일 진실원천이 아니므로
// (docs/api/API.md "재동기화" 절) 호출자는 onResync 콜백에서 GET /relationships/{ucId}를
// 다시 호출해 상태를 맞춰야 한다.

import { useCallback, useEffect, useRef, useState } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { API_BASE_URL, getStoredPlayerId } from "./client";
import type { ErrorSignalPayload, TurnMessageResponse } from "./types";

export type ChatSocketStatus = "connecting" | "connected" | "disconnected";

interface UseChatSocketOptions {
  ucId: number | string | undefined;
  onMessage: (response: TurnMessageResponse) => void;
  onError: (error: ErrorSignalPayload) => void;
  onResync: () => void;
}

export interface ChatSocket {
  status: ChatSocketStatus;
  /** 소켓이 연결돼 있으면 SEND하고 true, 아니면 아무 것도 하지 않고 false를 반환한다
   * (호출자는 false일 때 REST 폴백을 사용하면 된다). */
  sendMessage: (content: string) => boolean;
}

function isErrorSignal(payload: unknown): payload is ErrorSignalPayload {
  return (
    typeof payload === "object" &&
    payload !== null &&
    typeof (payload as { code?: unknown }).code === "string"
  );
}

export function useChatSocket({ ucId, onMessage, onError, onResync }: UseChatSocketOptions): ChatSocket {
  const [status, setStatus] = useState<ChatSocketStatus>("connecting");
  const clientRef = useRef<Client | null>(null);
  const hasConnectedBeforeRef = useRef(false);

  // 콜백이 매 렌더마다 바뀌어도 소켓을 재생성하지 않도록 ref로 최신 값을 유지한다.
  const onMessageRef = useRef(onMessage);
  const onErrorRef = useRef(onError);
  const onResyncRef = useRef(onResync);
  useEffect(() => {
    onMessageRef.current = onMessage;
    onErrorRef.current = onError;
    onResyncRef.current = onResync;
  });

  useEffect(() => {
    if (ucId === undefined || ucId === null) {
      return;
    }

    hasConnectedBeforeRef.current = false;
    setStatus("connecting");

    const playerId = getStoredPlayerId();
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`) as unknown as WebSocket,
      reconnectDelay: 3000,
      connectHeaders: playerId ? { "X-Player-Id": playerId } : {},
      onConnect: () => {
        setStatus("connected");

        client.subscribe(`/topic/chat/${ucId}`, (message: IMessage) => {
          let payload: unknown;
          try {
            payload = JSON.parse(message.body);
          } catch {
            return;
          }
          if (isErrorSignal(payload)) {
            onErrorRef.current(payload);
          } else {
            onMessageRef.current(payload as TurnMessageResponse);
          }
        });

        if (hasConnectedBeforeRef.current) {
          // 재연결된 경우에만 재동기화한다. 최초 연결 시에는 페이지 진입 시점에 이미
          // 상태를 조회했으므로 다시 부를 필요가 없다.
          onResyncRef.current();
        }
        hasConnectedBeforeRef.current = true;
      },
      onWebSocketClose: () => {
        setStatus("disconnected");
      },
      onStompError: () => {
        setStatus("disconnected");
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [ucId]);

  const sendMessage = useCallback((content: string): boolean => {
    const client = clientRef.current;
    if (!client || !client.connected || ucId === undefined || ucId === null) {
      return false;
    }
    client.publish({
      destination: `/app/chat/${ucId}`,
      body: JSON.stringify({ content }),
    });
    return true;
  }, [ucId]);

  return { status, sendMessage };
}
