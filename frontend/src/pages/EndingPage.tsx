import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError, fetchRelationshipStatus } from "../api/client";
import type { RelationshipStatusResponse } from "../api/types";
import { errorSignalMessage, stageLabel } from "../lib/relationshipDisplay";

const ENDING_COPY: Record<string, { title: string; body: string }> = {
  STRANGER: {
    title: "끝내 낯설었던 30일",
    body: "많은 말을 나누지는 못했어요. 그래도 이 30일은, 두 사람 모두에게 남아있을 거예요.",
  },
  ACQUAINTED: {
    title: "조금은 가까워진 채로",
    body: "서로를 완전히 알진 못했지만, 몇 가지는 오래 기억에 남을 것 같아요.",
  },
  CLOSE: {
    title: "가까워진 사이로 맞이한 끝",
    body: "이야기를 나눌수록 조금씩 가까워졌어요. 그 온기는 여기서 끝나지 않을 거예요.",
  },
  TRUSTED: {
    title: "믿음이 쌓인 채로",
    body: "당신이 기억해 준 것들이, 그 사람에게는 곧 믿음이 되었어요.",
  },
  LOVED: {
    title: "사랑에 빠진 채로 맞이한 마지막 날",
    body: "당신이 흘려듣지 않고 붙잡아 준 순간들이, 결국 사랑이 되었어요.",
  },
};

const DEFAULT_ENDING = {
  title: "30일의 이야기가 끝났어요",
  body: "그동안 나눈 대화들이 조용히 마음에 남아있을 거예요.",
};

export function EndingPage() {
  const { ucId } = useParams<{ ucId: string }>();
  const [status, setStatus] = useState<RelationshipStatusResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!ucId) return;
    fetchRelationshipStatus(ucId)
      .then(setStatus)
      .catch((err) => {
        const message = err instanceof ApiError ? errorSignalMessage(err.code) : errorSignalMessage("UNKNOWN");
        setErrorMessage(message);
      });
  }, [ucId]);

  const ending = status ? ENDING_COPY[status.stage] ?? DEFAULT_ENDING : DEFAULT_ENDING;

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-gradient-to-b from-lavender-50 via-blush-50 to-cream-50 px-6 py-16 text-center">
      <p className="text-xs font-medium tracking-[0.2em] text-lavender-500">30 DAYS</p>
      <h1 className="mt-3 text-2xl font-semibold text-stone-700">{ending.title}</h1>
      <p className="mt-4 max-w-sm text-sm leading-relaxed text-stone-500">{ending.body}</p>

      {status && (
        <p className="mt-6 text-xs text-stone-400">
          두 사람은 {stageLabel(status.stage)}로 이야기를 마쳤어요.
        </p>
      )}

      {errorMessage && <p className="mt-6 text-xs text-blush-500">{errorMessage}</p>}
    </div>
  );
}
