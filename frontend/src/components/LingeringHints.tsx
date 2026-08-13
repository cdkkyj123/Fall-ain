import type { MemoryCandidate } from "../api/types";

interface LingeringHintsProps {
  memoryCandidates: MemoryCandidate[];
}

// factKey는 서버 내부 식별자다. 여기서는 절대 원문 그대로 노출하지 않고,
// "무엇이었는지"를 흐릿하게만 암시하는 짧은 문구로 바꾼다. 매핑에 없는 키는
// 범용 문구로 대체한다. "도감"·"수집" 같은 표현은 쓰지 않는다.
const HINT_PHRASES: Record<string, string> = {
  favorite_food: "무심코 스쳐간, 좋아한다고 했던 것",
  childhood_wound: "마음 한 켠에 오래 남아있던 이야기",
  daily_habit: "매일 반복한다고 했던 작은 습관",
  core_value: "소중히 여긴다고 했던 것",
  deepest_secret: "아직 다 하지 못한 이야기",
};

const FALLBACK_HINT = "문득 스쳐 지나간 이야기 하나";

function hintFor(factKey: string): string {
  return HINT_PHRASES[factKey] ?? FALLBACK_HINT;
}

export function LingeringHints({ memoryCandidates }: LingeringHintsProps) {
  const fading = memoryCandidates
    .filter((candidate) => candidate.freshness === "FADING")
    .slice(0, 2);

  if (fading.length === 0) {
    return null;
  }

  return (
    <div className="rounded-xl bg-lavender-50/70 px-4 py-3">
      <p className="mb-1.5 text-[11px] font-medium tracking-wide text-lavender-500">
        요즘 마음에 남는 것
      </p>
      <ul className="flex flex-col gap-1">
        {fading.map((candidate) => (
          <li key={candidate.factKey} className="text-xs italic text-stone-400">
            {hintFor(candidate.factKey)}
          </li>
        ))}
      </ul>
    </div>
  );
}
