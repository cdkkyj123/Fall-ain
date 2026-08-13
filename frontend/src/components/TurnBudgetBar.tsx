import { turnsLeftLabel } from "../lib/relationshipDisplay";

interface TurnBudgetBarProps {
  turnsLeftToday: number;
  turnBudget: number;
}

export function TurnBudgetBar({ turnsLeftToday, turnBudget }: TurnBudgetBarProps) {
  const usedCount = Math.max(0, turnBudget - Math.max(0, turnsLeftToday));

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
        오늘, {turnsLeftLabel(turnsLeftToday, turnBudget)}
      </span>
    </div>
  );
}
