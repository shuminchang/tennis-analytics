import type { Source } from "./model";
export type ResearchSource = { playersAndRankings: Source; matchRunId: number; matchRules: { transformSha: string; qualitySha: string; scopeSha: string }; qualityRevision: string; readModel: string };
export type ResearchFilters = { tour: "ATP" | "WTA"; fromYear: number; toYear: number; surface: string; binWidth: number; group: string; view: "age" | "profiles"; x: string; y: string; limit: number };
export type ResearchContext = { grain: string; tour: string; fromYear: number; toYear: number; surface: string; metricVersion: string; source: ResearchSource };
export type ResearchInfo = { source: ResearchSource; earliestMatchYear: number | null; latestMatchYear: number | null; maxYears: number; maxProfiles: number };
export type AgeBin = { lowerAge: number; upperAge: number; surface: string; matches: number; wins: number; winRate: number; lower95: number; upper95: number; lowSample: boolean };
export type Ages = { context: ResearchContext; binWidth: number; sample: { population: number; valid: number; excluded: number; reasons: { code: string; count: number }[] }; items: AgeBin[]; warnings: string[] };
export function researchFilters(p: URLSearchParams): ResearchFilters {
  const allowed = ["tour", "fromYear", "toYear", "surface", "binWidth", "group", "view", "x", "y", "limit"];
  for (const key of p.keys()) if (!allowed.includes(key) || p.getAll(key).length !== 1 || !p.get(key)) throw Error("Invalid filters");
  const tour = p.get("tour") ?? "ATP", surface = p.get("surface") ?? "ALL", group = p.get("group") ?? "overall";
  if (!["ATP", "WTA"].includes(tour) || !["ALL", "Hard", "Clay", "Grass", "Carpet", "Unknown"].includes(surface) || !["overall", "surface"].includes(group)) throw Error("Invalid filters");
  const from = p.get("fromYear"), to = p.get("toYear");if (!!from !== !!to) throw Error("Both years required");
  const year = (s: string | null) => { if (s === null) return 0;if (!/^\d{4}$/.test(s) || +s < 1800 || +s > 2100) throw Error("Invalid year");return +s; };
  const fromYear = year(from), toYear = year(to);if (fromYear > toYear || toYear - fromYear > 9) throw Error("Ten years maximum");
  const binWidth = +(p.get("binWidth") ?? "1");if (![1, 5].includes(binWidth)) throw Error("Invalid bins");
  const view = p.get("view") ?? "age", x = p.get("x") ?? "height_cm", y = p.get("y") ?? "ace_rate", limit = p.get("limit") ?? "500";
  if (!["age", "profiles"].includes(view) || !researchMetrics.includes(x) || !researchMetrics.includes(y) || x === y || !/^\d{1,4}$/.test(limit) || +limit < 1 || +limit > 1000) throw Error("Invalid metrics");
  return { tour: tour as ResearchFilters["tour"], fromYear, toYear, surface, group, binWidth, view: view as ResearchFilters["view"], x, y, limit: +limit };
}
export function researchParams(f: ResearchFilters) { return new URLSearchParams(Object.entries(f).map(([k, v]) => [k, String(v)])); }
export const percent = (n: number | null | undefined) => n == null ? "—" : `${(n * 100).toFixed(1)}%`;
export type Measure = { value: number | null; observations: number; numerator: number; denominator: number; qualified: boolean };
export type Profile = { tour: string; playerId: number; displayName: string; season: number; surface: string; hand: string; matches: number; metrics: Record<string, Measure> };
export type Slice = { season: string; surface: string; hand: string; opponentHand: string; matches: number; metrics: Record<string, Measure> };
export type ProfilePoint = { tour: string; playerId: number; displayName: string; season: number; surface: string; hand: string; x: number; y: number; matches: number };
export type Profiles = { context: ResearchContext; x: string; y: string; sample: { population: number; inspected: number; valid: number; excluded: number; omitted: number; reasons: { code: string; count: number }[] }; items: Profile[]; points: ProfilePoint[]; surfaces: Slice[]; hands: Slice[] };
export const researchMetrics = ["height_cm", "match_win_rate", "ace_rate", "double_fault_rate", "first_serve_points_won", "serve_points_won"];
export const metricLabel = (key: string, en: boolean) => (en ? { height_cm: "Height (cm)", match_win_rate: "Match win rate", ace_rate: "ACE / serve points", double_fault_rate: "Double faults / serve points", first_serve_points_won: "First serve points won", serve_points_won: "Serve points won" } : { height_cm: "身高（cm）", match_win_rate: "比賽勝率", ace_rate: "ACE／發球分", double_fault_rate: "雙誤／發球分", first_serve_points_won: "一發得分率", serve_points_won: "發球分得分率" })[key] ?? key;
export const metricValue = (key: string, value: number | null | undefined) => value == null ? "—" : key === "height_cm" ? String(value) : percent(value);
export function studyApiParams(f: ResearchFilters) {
  const p = researchParams(f);for (const key of f.view === "age" ? ["view", "x", "y", "limit"] : ["view", "binWidth", "group"]) p.delete(key);return p;
}
