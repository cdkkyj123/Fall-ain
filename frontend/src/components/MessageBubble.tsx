export interface ChatMessage {
  id: string;
  role: "user" | "character";
  content: string;
  /** 이번 발화에서 마음에 남는 것이 되짚어진 경우, 은은한 표시를 위한 플래그 */
  touchedMemory?: boolean;
}

interface MessageBubbleProps {
  message: ChatMessage;
}

export function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[78%] ${isUser ? "items-end" : "items-start"} flex flex-col gap-1`}>
        <div
          className={
            isUser
              ? "rounded-2xl rounded-br-sm bg-blush-400 px-4 py-2.5 text-sm leading-relaxed text-white shadow-soft"
              : "rounded-2xl rounded-bl-sm bg-white px-4 py-2.5 text-sm leading-relaxed text-stone-700 shadow-soft ring-1 ring-lavender-100"
          }
        >
          {message.content}
        </div>
        {message.touchedMemory && !isUser && (
          <span className="px-1 text-[11px] italic text-lavender-500">
            마음이 조금 더 열린 순간이었어요
          </span>
        )}
      </div>
    </div>
  );
}
