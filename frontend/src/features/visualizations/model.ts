export type Metric = "height_cm" | "rank" | "ranking_points";
export type Tour = "ATP" | "WTA";
export type Filters = { tour: Tour; rankingDate: string; topN: number; x: Metric; y: Metric; color: "hand" | "none"; bins: number };
export const defaults: Filters = { tour: "ATP", rankingDate: "", topN: 100, x: "height_cm", y: "rank", color: "hand", bins: 20 };
const metrics = ["height_cm", "rank", "ranking_points"];
export function parseFilters(params: URLSearchParams): Filters {
  const allowed = Object.keys(defaults);
  for (const key of params.keys()) if (!allowed.includes(key) || params.getAll(key).length !== 1 || !params.get(key)) throw Error("Invalid filters");
  const result = { ...defaults, ...Object.fromEntries(params) } as Filters;
  if (!["ATP", "WTA"].includes(result.tour) || !metrics.includes(result.x) || !metrics.includes(result.y) || !["hand", "none"].includes(result.color)) throw Error("Invalid filters");
  if ((result.x === "height_cm") === (result.y === "height_cm")) throw Error("Invalid axis pair");
  for (const [key, max] of [["topN", 1000], ["bins", 50]] as const) {
    const raw = String(result[key]); if (!/^\d{1,4}$/.test(raw) || +raw < 1 || +raw > max) throw Error("Invalid limit");
    result[key] = +raw;
  }
  if (result.rankingDate && (!/^\d{4}-\d{2}-\d{2}$/.test(result.rankingDate) || result.rankingDate.startsWith("0000") || Number.isNaN(Date.parse(result.rankingDate)) || new Date(result.rankingDate).toISOString().slice(0, 10) !== result.rankingDate)) throw Error("Invalid date");
  return result;
}
export function filterParams(filters: Filters): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value !== "") params.set(key, String(value));
  return params;
}
export function apiParams(filters: Filters, kind: "scatter" | "histogram") {
  const params = filterParams(filters);
  for (const key of kind === "scatter" ? ["bins"] : ["x", "y", "color"]) params.delete(key);
  if (kind === "histogram") params.set("metric", "height_cm");
  return params.toString();
}
export type Source = { repositoryUrl: string; snapshotSha: string; playerRunId: number; rankingRunId: number; qualityRevision: string; playerRules: { transformSha: string; qualitySha: string; scopeSha: string }; rankingRules: { transformSha: string; qualitySha: string; scopeSha: string } };
export type Sample = { population: number; valid: number; excluded: number; primaryReasons: { metric: Metric; code: string; count: number }[] };
export type Context = { tour: Tour; rankingDate: string | null; source: Source; metricVersion: string; qualityPolicyVersion: string; generatedAt: string };
export type Point = { tour: Tour; playerId: number; displayName: string; x: number; y: number; group: string };
export type Scatter = { context: Context; sample: Sample; items: Point[]; axes: { x: { key: Metric }; y: { key: Metric } }; warnings: string[] };
export type Histogram = { context: Context; sample: Sample; domain: { min: number; max: number } | null; bins: { lower: number; upper: number; upperInclusive: boolean; count: number }[]; warnings: string[] };
export type Metadata = { metricVersion: string; tour: Tour; earliestRankingDate: string | null; latestRankingDate: string | null; source: Source; limits: { maxTopN: number; maxBins: number; maxPoints: number } };
export function sameSource(a: Source, b: Source) {
  const values = (s: Source) => [s.snapshotSha, s.repositoryUrl, s.playerRunId, s.rankingRunId, s.qualityRevision, s.playerRules.transformSha, s.playerRules.qualitySha, s.playerRules.scopeSha, s.rankingRules.transformSha, s.rankingRules.qualitySha, s.rankingRules.scopeSha];
  return JSON.stringify(values(a)) === JSON.stringify(values(b));
}
export function sourceLink(value: string): string | undefined {
  try { const url = new URL(value);return ["https:", "http:"].includes(url.protocol) ? url.href : undefined; } catch { return undefined; }
}
export function interval(bin: Histogram["bins"][number]) { const n = (v: number) => Number(v.toFixed(2));return `[${n(bin.lower)}, ${n(bin.upper)}${bin.upperInclusive ? "]" : ")"}`; }
export const labels = (en: boolean) => ({
  height_cm: en ? "Height (cm)" : "身高（cm）", rank: en ? "Rank" : "名次", ranking_points: en ? "Ranking points" : "排名積分",
});
export const groupLabel = (group: string, en: boolean) => ({ R: en ? "Right-handed" : "右手", L: en ? "Left-handed" : "左手", U: en ? "Unknown (U)" : "來源未知（U）", A: en ? "Ambidextrous" : "雙手（A）", UNKNOWN: en ? "Not provided" : "未提供", ALL: en ? "All players" : "所有球員" }[group] ?? group);
