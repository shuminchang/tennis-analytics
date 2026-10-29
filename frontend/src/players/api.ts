export interface Player {
  tour: "ATP" | "WTA";
  playerId: number;
  nameFirst: string | null;
  nameLast: string | null;
  hand: string | null;
  dob: string | null;
  ioc: string | null;
}
export interface PlayerPage {
  items: Player[];
  total: number;
  page: number;
  size: number;
}
export interface Search {
  q: string;
  tour: string;
  page: number;
  size: number;
  valid: boolean;
}
export function readSearch(params: URLSearchParams): Search {
  const q = (params.get("q") ?? "").trim();
  const tour = (params.get("tour") ?? "").toUpperCase();
  const pageText = params.get("page") ?? "0";
  const sizeText = params.get("size") ?? "20";
  const page = Number(pageText),
    size = Number(sizeText);
  const valid =
    q.length <= 100 &&
    ["", "ATP", "WTA"].includes(tour) &&
    /^\d+$/.test(pageText) &&
    page >= 0 &&
    page <= 10000 &&
    /^\d+$/.test(sizeText) &&
    size >= 1 &&
    size <= 100;
  return { q, tour, page, size, valid };
}
export async function fetchPlayers(
  search: Search,
  signal: AbortSignal,
): Promise<PlayerPage> {
  const params = new URLSearchParams({
    q: search.q,
    page: String(search.page),
    size: String(search.size),
  });
  if (search.tour) params.set("tour", search.tour);
  const response = await fetch(`/tennis/api/v1/players?${params}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("Player search failed");
  const body = (await response.json()) as PlayerPage;
  if (
    !Array.isArray(body.items) ||
    !Number.isSafeInteger(body.total) ||
    body.total < 0
  )
    throw new Error("Invalid response");
  return body;
}

export async function fetchPlayer(tour: string, playerId: number, signal: AbortSignal): Promise<Player | null> {
  const response = await fetch(`/tennis/api/v1/players/${encodeURIComponent(tour)}/${playerId}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Player details failed");
  const body = (await response.json()) as Player;
  if (body.tour !== tour || body.playerId !== playerId) throw new Error("Invalid player response");
  return body;
}

export interface RankingEntry { date: string; rank: number; points: number | null }
export interface RankingHistory { items: RankingEntry[]; total: number; page: number; size: number; available: boolean }
export async function fetchRankings(tour: string, playerId: number, page: number, signal: AbortSignal): Promise<RankingHistory> {
  const response = await fetch(`/tennis/api/v1/players/${encodeURIComponent(tour)}/${playerId}/rankings?page=${page}&size=20`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("Ranking history failed");
  const body = (await response.json()) as RankingHistory;
  if (!Array.isArray(body.items) || !Number.isSafeInteger(body.total) || body.total < 0 || typeof body.available !== "boolean")
    throw new Error("Invalid ranking response");
  return body;
}

export interface MatchEntry {
  id: number; tournamentId: string; tournamentName: string | null; tournamentDate: string;
  sourceLevel: string | null; surface: string | null; round: string; matchNumber: number;
  score: string | null; scoreNeedsReview: boolean; status: string; result: string;
  opponent: { playerId: number; nameFirst: string | null; nameLast: string | null; visible: boolean };
}
export interface MatchHistory { items: MatchEntry[]; total: number; page: number; size: number; available: boolean }
export async function fetchMatches(tour: string, playerId: number, year: string, page: number, signal: AbortSignal): Promise<MatchHistory> {
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (year) params.set("year", year);
  const response = await fetch(`/tennis/api/v1/players/${encodeURIComponent(tour)}/${playerId}/matches?${params}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]), headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("Match history failed");
  const body = (await response.json()) as MatchHistory;
  if (!Array.isArray(body.items) || !Number.isSafeInteger(body.total) || body.total < 0 || typeof body.available !== "boolean") throw new Error("Invalid match response");
  return body;
}
