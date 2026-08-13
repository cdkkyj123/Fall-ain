import { turnsLeftLabel } from "../lib/relationshipDisplay";

interface TurnBudgetBarProps {
  turnsLeftToday: number;
  turnBudget: number;
  dayState?: string;
}

export function TurnBudgetBar({ turnsLeftToday, turnBudget, dayState }: TurnBudgetBarProps) {
  const isDayClosed = dayState === "CLOSED";
  const usedCount = isDayClosed
    ? turnBudget
    : Math.max(0, turnBudget - Math.max(0, turnsLeftToday));
  const label = turnsLeftLabel(turnsLeftToday, turnBudget, dayState);

  return (
    <div className="flex items-center gap-2">
      <div className="flex gap-1" aria-hidden>
        {Array.from({ length: turnBudget }).map((_, index) => (
          <span
            key={index}
            className={`h-1.5 w-3 rounded-full transition-colors ${
              index < usedCount ? "bg-blush-200" : "bg-blush-400"
            }`}
          />
        ))}
      </div>
      <span className="whitespace-nowrap text-xs text-stone-500">
        {isDayClosed ? label : `오늘, ${label}`}
      </span>
    </div>
  );
}
