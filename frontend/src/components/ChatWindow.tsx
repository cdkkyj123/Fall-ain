import { useEffect, useRef } from "react";
import { MessageBubble, type ChatMessage } from "./MessageBubble";
import { TypingIndicator } from "./TypingIndicator";

interface ChatWindowProps {
  messages: ChatMessage[];
  isWaitingForReply: boolean;
}

export function ChatWindow({ messages, isWaitingForReply }: ChatWindowProps) {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length, isWaitingForReply]);

  return (
    <div className="scrollbar-thin flex-1 overflow-y-auto px-4 py-6">
      <div className="mx-auto flex max-w-2xl flex-col gap-3">
        {messages.length === 0 && !isWaitingForReply && (
          <p className="mx-auto mt-10 max-w-xs text-center text-sm text-stone-400">
            먼저 인사를 건네보세요. 오늘 나눈 이야기가 내일의 대화를 만들어요.
          </p>
        )}
        {messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}
        {isWaitingForReply && <TypingIndicator />}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
