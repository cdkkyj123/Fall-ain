interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
  onDismiss?: () => void;
}

export function ErrorBanner({ message, onRetry, onDismiss }: ErrorBannerProps) {
  return (
    <div className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3 rounded-xl bg-blush-100 px-4 py-2.5 text-sm text-blush-700">
      <span>{message}</span>
      <div className="flex shrink-0 items-center gap-2">
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="rounded-full bg-blush-400 px-3 py-1 text-xs font-medium text-white transition-colors hover:bg-blush-500"
          >
            다시 시도
          </button>
        )}
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            className="text-xs text-blush-500 hover:text-blush-700"
            aria-label="닫기"
          >
            닫기
          </button>
        )}
      </div>
    </div>
  );
}
