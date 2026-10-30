import { Component, lazy, Suspense, useState, type ReactNode } from "react";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { get } from "../api";
import FilterPanel from "./FilterPanel";
import SampleFootnote from "./SampleFootnote";
import DataTable from "./DataTable";
import { apiParams, defaults, filterParams, parseFilters, sameSource, sourceLink, type Filters, type Metadata, type Scatter, type Histogram } from "./model";
import "./explorer.css";
const Charts = lazy(() => import("./ExplorerCharts"));
class ChartBoundary extends Component<{ children: ReactNode; en: boolean }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <p role="alert">{this.props.en ? "Charts could not load. The data tables below remain available." : "圖表無法載入，仍可使用下方資料表。"}<button onClick={() => window.location.reload()}>{this.props.en ? "Reload charts" : "重新載入圖表"}</button></p> : this.props.children; }
}
export default function ExplorerPage() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const [params, setParams] = useSearchParams();
  let filters = defaults;let invalid = false;try { filters = parseFilters(params); } catch { invalid = true; }
  return <section className="explorer-page"><header className="explorer-heading"><div><p className="explorer-kicker">TENNIS DATA EXPLORER / 01</p><h1>{en ? "Put a question" : "把問題，"}<br/><em>{en ? "on the chart." : "放進座標。"}</em></h1><p>{en ? "Explore one ranking snapshot. Compare source observations, understand the missing pieces." : "從同一天的排名出發，比較來源觀測，也看見缺失的部分。"}</p></div><div className="explorer-stamp"><span>{en ? "OBSERVE / COMPARE / QUESTION" : "觀察 ／ 比較 ／ 提問"}</span><strong>ATP<br/>WTA</strong><span>{en ? "One player. One point." : "同一母體，一人一點。"}</span></div></header>
    {invalid ? <div className="explorer-state" role="alert"><h2>{en ? "Invalid explorer filters" : "探索條件無效"}</h2><p>{en ? "Check the date, axis pair and query limits." : "請檢查日期、座標配對與查詢上限。"}</p><button onClick={() => setParams(filterParams(defaults))}>{en ? "Reset filters" : "重設條件"}</button></div> : <Explorer key={params.toString()} filters={filters} searchKey={params.toString()} onApply={setParams} en={en}/>}
  </section>;
}
function Explorer({ filters, searchKey, onApply, en }: { filters: Filters; searchKey: string; onApply: (p: URLSearchParams) => void; en: boolean }) {
  const metadata = useQuery({ queryKey: ["visualizations", "metadata", filters.tour], queryFn: ({ signal }) => get<Metadata>(`/v1/visualizations/metadata?tour=${filters.tour}`, signal), staleTime: 30_000, retry: false });
  const version = metadata.data?.source;const baseKey = ["visualizations", version, filters];
  const scatter = useQuery({ queryKey: [...baseKey, "scatter"], enabled: !!version, queryFn: ({ signal }) => get<Scatter>(`/v1/visualizations/scatter?${apiParams(filters, "scatter")}`, signal), staleTime: 30_000, retry: false });
  const histogram = useQuery({ queryKey: [...baseKey, "histogram"], enabled: !!version, queryFn: ({ signal }) => get<Histogram>(`/v1/visualizations/histogram?${apiParams(filters, "histogram")}`, signal), staleTime: 30_000, retry: false });
  const [share, setShare] = useState("");
  const mismatch = scatter.data && histogram.data && version && (!sameSource(scatter.data.context.source, histogram.data.context.source) || !sameSource(scatter.data.context.source, version) || scatter.data.context.rankingDate !== histogram.data.context.rankingDate || scatter.data.context.metricVersion !== histogram.data.context.metricVersion || scatter.data.context.metricVersion !== metadata.data?.metricVersion || scatter.data.context.qualityPolicyVersion !== histogram.data.context.qualityPolicyVersion);
  const error = metadata.isError || scatter.isError || histogram.isError || mismatch;
  const pending = metadata.isPending || scatter.isPending || histogram.isPending || metadata.isFetching || scatter.isFetching || histogram.isFetching;
  async function shareLink() {
    const query = filterParams({ ...filters, rankingDate: scatter.data?.context.rankingDate ?? "" });const link = `${window.location.origin}${window.location.pathname}?${query}`;
    setShare(link);try { await navigator.clipboard.writeText(link); } catch { /* The selectable link remains available without clipboard access. */ }
  }
  return <><FilterPanel key={searchKey} filters={filters} en={en} latest={metadata.data?.latestRankingDate ?? null} onApply={onApply}/>
    {error ? <div className="explorer-state" role="alert"><h2>{en ? "Data is temporarily unavailable" : "暫時無法取得探索資料"}</h2><p>{mismatch ? (en ? "The source changed during loading. Refresh to use one consistent snapshot." : "載入期間來源已變更，請重新整理以使用一致快照。") : (en ? "Check your connection or try a smaller cohort." : "請確認連線，或縮小查詢母體後再試。")}</p><button onClick={() => { void metadata.refetch();void scatter.refetch();void histogram.refetch(); }}>{en ? "Retry" : "重試"}</button></div>
      : pending ? <div className="explorer-state" role="status">{en ? "Reading the source snapshot…" : "正在讀取來源快照…"}</div>
      : scatter.data && histogram.data && metadata.data ? <>
        <div className="explorer-results-heading"><div><p className="explorer-kicker">{filters.tour} / {scatter.data.context.rankingDate ?? (en ? "NO SOURCE DATE" : "尚無來源日期")}</p><h2>{en ? "Same cohort. Two views." : "同一母體，兩種觀察。"}</h2><p>{en ? `Rank ≤ ${filters.topN}; ties included. Missing values are never replaced with zero.` : `名次 ≤ ${filters.topN}，同名次全納入；缺值不補成零。`}</p></div><button className="explorer-quiet" onClick={() => { void shareLink(); }}>{en ? "Copy snapshot link ↗" : "複製此快照連結 ↗"}</button></div>
        {share && <label className="explorer-share">{en ? "Shareable snapshot URL" : "可分享的快照網址"}<input readOnly value={share} onFocus={e => e.currentTarget.select()}/></label>}
        <div className="explorer-chart-headings"><div><span className="explorer-kicker">A / {en ? "RELATIONSHIP" : "變數關聯"}</span><h3>{en ? "Player observations" : "球員觀測"}</h3><SampleFootnote sample={scatter.data.sample} en={en}/></div><div><span className="explorer-kicker">B / {en ? "DISTRIBUTION" : "分布形狀"}</span><h3>{en ? "Height distribution" : "身高分布"}</h3><SampleFootnote sample={histogram.data.sample} en={en}/></div></div>
        {scatter.data.sample.valid > 0 || histogram.data.sample.valid > 0 ? <ChartBoundary en={en}><Suspense fallback={<p role="status">{en ? "Loading charts…" : "正在載入圖表…"}</p>}><Charts key={searchKey} scatter={scatter.data} histogram={histogram.data} en={en}/></Suspense></ChartBoundary> : <div className="explorer-state"><h3>{en ? "No valid observations" : "沒有有效觀測"}</h3><p>{en ? "Try another source date or cohort. Excluded observations remain counted above." : "可嘗試其他日期或母體；被排除的觀測仍計入上方說明。"}</p></div>}
        <DataTable key={searchKey} scatter={scatter.data} histogram={histogram.data} en={en}/>
        <details className="explorer-source"><summary>{en ? "Source, definitions & limits" : "來源、定義與限制"}</summary><p>{en ? "Heights are source attributes, not measurements at every ranking date. Only values within 100–250 cm without blocking quality flags are used. Tour is not a verified gender field. These observations do not establish causation or complete career coverage." : "身高是來源屬性，不是每個排名日的即時量測。僅採 100～250 cm 且無相關品質標記者；巡迴不等於已驗證性別。這些觀測不代表因果或完整生涯涵蓋。"}</p><p>{en ? "Source: Jeff Sackmann tennis datasets and the archived version. Data license: CC BY-NC-SA 4.0." : "資料來源：Jeff Sackmann 網球資料集及其封存版本。資料授權：CC BY-NC-SA 4.0。"}</p><p>{en ? "Snapshot" : "來源快照"}：<code>{scatter.data.context.source.snapshotSha}</code><br/>{en ? "Metric rules" : "指標規則"}：{scatter.data.context.metricVersion}<br/>{en ? "Player / ranking runs" : "球員／排名批次"}：{version?.playerRunId} / {version?.rankingRunId}</p>{sourceLink(metadata.data.source.repositoryUrl) && <a href={sourceLink(metadata.data.source.repositoryUrl)} target="_blank" rel="noreferrer">{en ? "Source repository ↗" : "來源資料庫 ↗"}</a>}</details>
      </> : null}
  </>;
}
