import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, ensurePlayerId, fetchCharacters, startRelationship } from "../api/client";
import { errorSignalMessage } from "../lib/relationshipDisplay";
import { ErrorBanner } from "../components/ErrorBanner";
import type { CharacterListItem } from "../api/types";

type LoadState = "loading" | "ready" | "error";

export function EntryPage() {
  const navigate = useNavigate();
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [characters, setCharacters] = useState<CharacterListItem[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [startingCharacterId, setStartingCharacterId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoadState("loading");
    setErrorMessage(null);
    try {
      await ensurePlayerId();
      const list = await fetchCharacters();
      setCharacters(list);
      setLoadState("ready");
    } catch (err) {
      const message = err instanceof ApiError ? errorSignalMessage(err.code) : errorSignalMessage("UNKNOWN");
      setErrorMessage(message);
      setLoadState("error");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSelect = async (characterId: number) => {
    setStartingCharacterId(characterId);
    setErrorMessage(null);
    try {
      const { ucId } = await startRelationship(characterId);
      navigate(`/chat/${ucId}`);
    } catch (err) {
      const message = err instanceof ApiError ? errorSignalMessage(err.code) : errorSignalMessage("UNKNOWN");
      setErrorMessage(message);
      setStartingCharacterId(null);
    }
  };

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-gradient-to-b from-cream-50 via-blush-50 to-lavender-50 px-6 py-16">
      <div className="w-full max-w-md text-center">
        <p className="text-xs font-medium tracking-[0.2em] text-lavender-500">LISTENING IS LOVE</p>
        <h1 className="mt-2 text-2xl font-semibold text-stone-700">경청이 사랑</h1>
        <p className="mt-3 text-sm leading-relaxed text-stone-500">
          누군가 흘리듯 건넨 이야기를, 오래 기억해 본 적 있나요.
          <br />
          하루에 나눌 수 있는 이야기는 많지 않아요.
          <br />
          그래서 더, 귀 기울이게 돼요.
        </p>
      </div>

      <div className="mt-10 w-full max-w-md">
        {loadState === "loading" && (
          <p className="text-center text-sm text-stone-400">준비하고 있어요...</p>
        )}

        {errorMessage && (
          <div className="mb-4">
            <ErrorBanner message={errorMessage} onRetry={() => void load()} />
          </div>
        )}

        {loadState === "ready" && (
          <ul className="flex flex-col gap-3">
            {characters.map((character) => (
              <li
                key={character.id}
                className="rounded-2xl bg-white/90 p-5 text-left shadow-soft ring-1 ring-lavender-100"
              >
                <h2 className="text-base font-medium text-stone-700">{character.name}</h2>
                <p className="mt-1.5 text-sm leading-relaxed text-stone-500">{character.introduction}</p>
                <button
                  type="button"
                  disabled={startingCharacterId === character.id}
                  onClick={() => void handleSelect(character.id)}
                  className="mt-4 w-full rounded-full bg-blush-400 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blush-500 disabled:opacity-60"
                >
                  {startingCharacterId === character.id ? "이어지는 중..." : "이야기 시작하기"}
                </button>
              </li>
            ))}
            {characters.length === 0 && loadState === "ready" && (
              <p className="text-center text-sm text-stone-400">아직 만날 수 있는 상대가 없어요.</p>
            )}
          </ul>
        )}
      </div>
    </div>
  );
}
