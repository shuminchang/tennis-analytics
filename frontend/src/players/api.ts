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
