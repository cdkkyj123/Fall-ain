export function TypingIndicator() {
  return (
    <div className="flex justify-start">
      <div className="flex items-center gap-1 rounded-2xl rounded-bl-sm bg-white px-4 py-3 shadow-soft ring-1 ring-lavender-100">
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-blush-300 [animation-delay:-0.3s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-blush-300 [animation-delay:-0.15s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-blush-300" />
      </div>
    </div>
  );
}
