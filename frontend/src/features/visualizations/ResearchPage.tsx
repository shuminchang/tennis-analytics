import { lazy, Suspense, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { get } from "../api";
import { percent, researchFilters, researchParams, studyApiParams, researchMetrics, metricLabel, type ResearchFilters, type ResearchInfo, type Ages, type Profiles } from "./researchModel";
import "./explorer.css";
const ProfileResults = lazy(() => import("./ResearchProfiles"));
const Charts = lazy(() => import("./ResearchCharts"));
export default function ResearchPage() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const [params, setParams] = useSearchParams();let filters: ResearchFilters | null = null;
  try { filters = researchFilters(params); } catch { /* Render a reset without requesting invalid data. */ }
  return <section className="explorer-page"><header className="explorer-heading"><div><p className="explorer-kicker">TENNIS DATA EXPLORER / 02</p><h1>{en ? "Age, with" : "沿著年齡，"}<br/><em>{en ? "context." : "看見勝率。"}</em></h1><p>{en ? "Descriptive match perspectives, with denominators and uncertainty." : "從比賽的雙方視角，連同分母與不確定性一起觀察。"}</p><Link to="/visualizations">{en ? "Ranking snapshot explorer ↗" : "同日排名探索 ↗"}</Link></div></header>
  {!filters ? <div role="alert" className="explorer-state">{en ? "Invalid research filters (maximum ten years)." : "研究條件無效（最多十年）。"}<button onClick={() => setParams({ tour: "ATP" })}>{en ? "Reset" : "重設"}</button></div> : <Study key={params.toString()} filters={filters} onApply={setParams} en={en}/>}</section>;
}
function Study({ filters, onApply, en }: { filters: ResearchFilters; onApply: (p: URLSearchParams) => void; en: boolean }) {
  const info = useQuery({ queryKey: ["research-info", filters.tour], queryFn: ({ signal }) => get<ResearchInfo>(`/v1/visualizations/research-metadata?tour=${filters.tour}`, signal), retry: false, staleTime: 30_000 });
  const resolved = { ...filters, fromYear: filters.fromYear || info.data?.latestMatchYear || 2024, toYear: filters.toYear || info.data?.latestMatchYear || 2024 };
  const result = useQuery({ queryKey: ["research-data", resolved, info.data?.source], queryFn: ({ signal }) => get<Ages | Profiles>(`/v1/visualizations/${filters.view === "age" ? "age-performance" : "profile-performance"}?${studyApiParams(resolved)}`, signal), enabled: !!info.data, retry: false, staleTime: 30_000 });
  const mismatch = result.data && info.data && JSON.stringify(result.data.context.source) !== JSON.stringify(info.data.source);
  return <><div className="research-tabs">{(["age", "profiles"] as const).map(view => <button key={view} className={filters.view === view ? "explorer-apply" : "explorer-quiet"} onClick={() => onApply(researchParams({ ...resolved, view }))}>{view === "age" ? (en ? "Age & wins" : "年齡與勝率") : (en ? "Serve & surfaces" : "發球與場地")}</button>)}</div><StudyFilters key={`${resolved.fromYear}-${resolved.toYear}`} filters={resolved} en={en} onApply={onApply}/>
    {info.isError || result.isError || mismatch ? <div className="explorer-state" role="alert">{en ? "Research source unavailable or changed. Please retry." : "研究來源暫時不可用或已變更，請重試。"}<button onClick={() => { void info.refetch();void result.refetch(); }}>{en ? "Retry" : "重試"}</button></div> : result.isPending || result.isFetching ? <p role="status">{en ? "Reading match observations…" : "正在讀取比賽觀測…"}</p> : filters.view === "age" ? <AgeResults data={result.data as Ages} en={en}/> : <Suspense fallback={<p role="status">Loading…</p>}><ProfileResults data={result.data as Profiles} en={en}/></Suspense>}</>;
}
function StudyFilters({ filters, onApply, en }: { filters: ResearchFilters; onApply: (p: URLSearchParams) => void; en: boolean }) {
  const [draft, setDraft] = useState(filters);
  return <form className="research-filters" onSubmit={e => { e.preventDefault();onApply(researchParams(draft)); }}>
    <label>{en ? "Tour" : "巡迴"}<select value={draft.tour} onChange={e => setDraft({ ...draft, tour: e.target.value as ResearchFilters["tour"] })}><option>ATP</option><option>WTA</option></select></label>
    <label>{en ? "From year" : "起始年"}<input type="number" min={1800} max={2100} required value={draft.fromYear} onChange={e => setDraft({ ...draft, fromYear: +e.target.value })}/></label>
    <label>{en ? "To year" : "結束年"}<input type="number" min={1800} max={2100} required value={draft.toYear} onChange={e => setDraft({ ...draft, toYear: +e.target.value })}/></label>
    <label>{en ? "Surface" : "場地"}<select value={draft.surface} onChange={e => setDraft({ ...draft, surface: e.target.value })}>{["ALL", "Hard", "Clay", "Grass", "Carpet", "Unknown"].map(s => <option key={s}>{s}</option>)}</select></label>
    {draft.view === "age" && <><label>{en ? "Age bin width" : "年齡箱寬"}<select value={draft.binWidth} onChange={e => setDraft({ ...draft, binWidth: +e.target.value })}><option value="1">1</option><option value="5">5</option></select></label>
    <label>{en ? "Group" : "分組"}<select value={draft.group} onChange={e => setDraft({ ...draft, group: e.target.value })}><option value="overall">{en ? "Overall" : "整體"}</option><option value="surface">{en ? "By surface" : "分場地"}</option></select></label></>}
    {draft.view !== "age" && <>{(["x", "y"] as const).map(key => <label key={key}>{key.toUpperCase()} {en ? "metric" : "指標"}<select value={draft[key]} onChange={e => setDraft({ ...draft, [key]: e.target.value })}>{researchMetrics.map(m => <option key={m} value={m}>{metricLabel(m, en)}</option>)}</select></label>)}<label>{en ? "Maximum profiles" : "最多賽季場地觀測"}<input type="number" min={1} max={1000} required value={draft.limit} onChange={e => setDraft({ ...draft, limit: +e.target.value })}/></label></>}
    <button className="explorer-apply">{en ? "Apply research filters" : "套用研究條件"}</button>
  </form>;
}
function AgeResults({ data, en }: { data: Ages; en: boolean }) {
  return <><h2>{en ? "Age and observed win rate" : "年齡與觀測勝率"}</h2><p>{data.context.tour} · {data.context.fromYear}–{data.context.toYear} · {data.context.surface}</p><p>{en ? `Player-match observations ${data.sample.population} · Valid ${data.sample.valid} · Excluded ${data.sample.excluded}` : `球員－比賽觀測 ${data.sample.population} · 有效 ${data.sample.valid} · 排除 ${data.sample.excluded}`}</p>
    <ul>{data.sample.reasons.map(r => <li key={r.code}>{({ AGE_QUALITY_BLOCKED: en ? "Age quality flag" : "年齡品質標記", AGE_MISSING: en ? "Age missing" : "年齡缺值", AGE_NONPOSITIVE: en ? "Nonpositive age" : "非正數年齡" }[r.code] ?? r.code)}：{r.count}</li>)}</ul>
    <p className="explorer-warning">{en ? "Source ages are not independently verified. Tournament dates are approximate. Wilson intervals are descriptive; repeated players are not independent. Groups with fewer than 20 observations remain visible and flagged." : "來源年齡未獨立查證；日期為賽事近似值。Wilson 區間僅供描述，重複出賽球員並非獨立樣本。未滿 20 筆的群組仍顯示並標記。"}</p>
    {data.items.length ? <Suspense fallback={<p role="status">{en ? "Loading chart…" : "載入圖表…"}</p>}><Charts data={data} en={en}/></Suspense> : <p>{en ? "No valid observations in this window." : "此時間窗沒有有效觀測。"}</p>}
    <div className="table-scroll" tabIndex={0}><table><caption>{en ? "Age observations and Wilson 95% intervals" : "年齡觀測與 Wilson 95% 區間"}</caption><thead><tr>{[en ? "Age interval" : "年齡區間", en ? "Surface" : "場地", "n", en ? "Wins" : "勝場", en ? "Win rate" : "勝率", "95% CI", en ? "Sample" : "樣本"].map(v => <th scope="col" key={v}>{v}</th>)}</tr></thead><tbody>{data.items.map(r => <tr key={`${r.surface}-${r.lowerAge}`}><td>[{r.lowerAge}, {r.upperAge})</td><td>{r.surface}</td><td>{r.matches}</td><td>{r.wins}</td><td>{percent(r.winRate)}</td><td>{percent(r.lower95)}–{percent(r.upper95)}</td><td>{r.lowSample ? en ? "Low n" : "低樣本" : "—"}</td></tr>)}</tbody></table></div>
    <ResearchNote source={data.context.source} en={en}/>
  </>;
}
export function ResearchNote({ source, en }: { source: ResearchInfo["source"]; en: boolean }) {
  return <details className="explorer-source"><summary>{en ? "Research source and methods" : "研究來源與方法"}</summary><p>{en ? "Jeff Sackmann tennis data · CC BY-NC-SA 4.0. Reviewed source coverage only; not causal inference or complete career statistics." : "Jeff Sackmann 網球資料 · CC BY-NC-SA 4.0。僅限已納入來源範圍，不代表因果或完整生涯。"}</p><p><code>{source.playersAndRankings.snapshotSha}</code><br/>stats-v1 · research-v1 · {en ? "Match run" : "比賽批次"} {source.matchRunId}</p></details>;
}
