import { useState } from "react";
import { defaults, filterParams, labels, type Filters, type Metric } from "./model";
export default function FilterPanel({ filters, en, latest, onApply }: { filters: Filters; en: boolean; latest: string | null; onApply: (params: URLSearchParams) => void }) {
  const [draft, setDraft] = useState(filters);const names = labels(en);
  function axis(key: "x" | "y", value: Metric) {
    const next = { ...draft, [key]: value };const other = key === "x" ? "y" : "x";
    if (value !== "height_cm") next[other] = "height_cm";
    else if (next[other] === "height_cm") next[other] = "rank";
    setDraft(next);
  }
  return <form className="explorer-filters" aria-label={en ? "Explorer filters" : "探索篩選"} onSubmit={e => { e.preventDefault();onApply(filterParams(draft)); }}>
    <div className="filter-section"><span className="explorer-kicker">01 / {en ? "COHORT" : "選擇母體"}</span>
      <label>{en ? "Tour" : "巡迴"}<select value={draft.tour} onChange={e => setDraft({ ...draft, tour: e.target.value as Filters["tour"] })}><option>ATP</option><option>WTA</option></select></label>
      <label>{en ? "Ranking date" : "排名日期"}<input type="date" value={draft.rankingDate} onChange={e => setDraft({ ...draft, rankingDate: e.target.value })} /><small>{en ? "Blank = latest source date" : "留空使用最新來源日期"}{latest ? ` · ${latest}` : ""}</small></label>
      <label>{en ? "Top N (rank cutoff)" : "Top N（名次門檻）"}<input type="number" min={1} max={1000} step={1} required value={draft.topN} onChange={e => setDraft({ ...draft, topN: +e.target.value })}/></label>
    </div>
    <div className="filter-section"><span className="explorer-kicker">02 / {en ? "COORDINATES" : "設定座標"}</span>
      {(["x", "y"] as const).map(key => <label key={key}>{key.toUpperCase()} {en ? "axis" : "軸"}<select value={draft[key]} onChange={e => axis(key, e.target.value as Metric)}>{Object.entries(names).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></label>)}
      <button className="explorer-quiet" type="button" onClick={() => setDraft({ ...draft, x: draft.y, y: draft.x })}>{en ? "Swap axes ↔" : "交換座標軸 ↔"}</button>
    </div>
    <div className="filter-section"><span className="explorer-kicker">03 / {en ? "DISPLAY" : "呈現方式"}</span>
      <label>{en ? "Point color" : "散點顏色"}<select value={draft.color} onChange={e => setDraft({ ...draft, color: e.target.value as Filters["color"] })}><option value="hand">{en ? "Playing hand" : "慣用手"}</option><option value="none">{en ? "Single color" : "單一顏色"}</option></select></label>
      <label>{en ? "Histogram bins" : "直方圖箱數"}<input type="number" min={1} max={50} step={1} required value={draft.bins} onChange={e => setDraft({ ...draft, bins: +e.target.value })}/></label>
      <div className="explorer-actions"><button type="submit" className="explorer-apply">{en ? "Explore data ↗" : "套用條件 ↗"}</button><button type="button" className="explorer-quiet" onClick={() => onApply(filterParams(defaults))}>{en ? "Reset" : "重設"}</button></div>
    </div>
  </form>;
}
