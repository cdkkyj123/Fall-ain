import { stageLabel } from "../lib/relationshipDisplay";

interface IntimacyMeterProps {
  intimacy: number;
  stage: string;
}

// intimacy는 서버 점수표 기준 0~900+ 범위로 관측된다(LOVED가 900부터 시작).
// 시각적으로는 넉넉하게 1000을 만점으로 두고 채운다.
const VISUAL_MAX = 1000;

export function IntimacyMeter({ intimacy, stage }: IntimacyMeterProps) {
  const ratio = Math.max(0, Math.min(1, intimacy / VISUAL_MAX));

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between text-xs text-stone-500">
        <span className="font-medium text-blush-600">{stageLabel(stage)}</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-blush-100">
        <div
          className="h-full rounded-full bg-gradient-to-r from-blush-300 to-lavender-400 transition-all duration-500"
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
    </div>
  );
}
