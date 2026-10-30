import type { Source } from "./model";
export type ResearchSource = { playersAndRankings: Source; matchRunId: number; matchRules: { transformSha: string; qualitySha: string; scopeSha: string }; qualityRevision: string; readModel: string };
export type ResearchFilters = { tour: "ATP" | "WTA"; fromYear: number; toYear: number; surface: string; binWidth: number; group: string };
export type ResearchContext = { grain: string; tour: string; fromYear: number; toYear: number; surface: string; metricVersion: string; source: ResearchSource };
export type ResearchInfo = { source: ResearchSource; earliestMatchYear: number | null; latestMatchYear: number | null; maxYears: number; maxProfiles: number };
export type AgeBin = { lowerAge: number; upperAge: number; surface: string; matches: number; wins: number; winRate: number; lower95: number; upper95: number; lowSample: boolean };
export type Ages = { context: ResearchContext; binWidth: number; sample: { population: number; valid: number; excluded: number; reasons: { code: string; count: number }[] }; items: AgeBin[]; warnings: string[] };
export function researchFilters(p: URLSearchParams): ResearchFilters {
  const allowed = ["tour", "fromYear", "toYear", "surface", "binWidth", "group"];
  for (const key of p.keys()) if (!allowed.includes(key) || p.getAll(key).length !== 1 || !p.get(key)) throw Error("Invalid filters");
  const tour = p.get("tour") ?? "ATP", surface = p.get("surface") ?? "ALL", group = p.get("group") ?? "overall";
  if (!["ATP", "WTA"].includes(tour) || !["ALL", "Hard", "Clay", "Grass", "Carpet", "Unknown"].includes(surface) || !["overall", "surface"].includes(group)) throw Error("Invalid filters");
  const from = p.get("fromYear"), to = p.get("toYear");if (!!from !== !!to) throw Error("Both years required");
  const year = (s: string | null) => { if (s === null) return 0;if (!/^\d{4}$/.test(s) || +s < 1800 || +s > 2100) throw Error("Invalid year");return +s; };
  const fromYear = year(from), toYear = year(to);if (fromYear > toYear || toYear - fromYear > 9) throw Error("Ten years maximum");
  const binWidth = +(p.get("binWidth") ?? "1");if (![1, 5].includes(binWidth)) throw Error("Invalid bins");
  return { tour: tour as ResearchFilters["tour"], fromYear, toYear, surface, group, binWidth };
}
export function researchParams(f: ResearchFilters) { return new URLSearchParams(Object.entries(f).map(([k, v]) => [k, String(v)])); }
export const percent = (n: number | null | undefined) => n == null ? "—" : `${(n * 100).toFixed(1)}%`;
