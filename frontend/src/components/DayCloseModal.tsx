interface DayCloseModalProps {
  closedDay: number;
  onConfirm: () => void;
}

export function DayCloseModal({ closedDay, onConfirm }: DayCloseModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-stone-900/30 px-6 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-3xl bg-cream-50 px-6 py-8 text-center shadow-soft">
        <p className="text-sm text-lavender-500">{closedDay}일째 밤</p>
        <h2 className="mt-2 text-lg font-medium text-stone-700">
          오늘의 대화는 여기까지예요
        </h2>
        <p className="mt-3 text-sm leading-relaxed text-stone-500">
          오늘 나눈 이야기는 조용히 마음에 남아있을 거예요.
          <br />
          내일, 다시 이야기해요.
        </p>
        <button
          type="button"
          onClick={onConfirm}
          className="mt-6 w-full rounded-full bg-blush-400 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blush-500"
        >
          알겠어요
        </button>
      </div>
    </div>
  );
}
