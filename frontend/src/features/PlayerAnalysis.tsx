import SortableTable from "./SortableTable";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import Resource from "./Resource";
interface Row { bucket: string; matches: number; wins: number; serveSamples: number; serveRate: number | null; aceSamples: number; aceRate: number | null; dfSamples: number; dfRate: number | null }
export default function PlayerAnalysis({ tour, id }: { tour: string; id: number }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";const [by, setBy] = useState("surface");
  const pct = (v: number | null) => v === null ? "—" : new Intl.NumberFormat(i18n.language, { style: "percent", maximumFractionDigits: 1 }).format(v);
  return <section className="ranking-history"><div className="results-heading"><h2>{en ? "Performance samples" : "表現與有效樣本"}</h2><label>{en ? "Group" : "分組"}<select value={by} onChange={e => setBy(e.target.value)}><option value="surface">{en ? "Surface" : "場地"}</option><option value="year">{en ? "Year" : "年份"}</option></select></label></div><p className="ranking-note">{en ? "Stats-v1. Serve metrics use eligible observations separately; rates are weighted by service points, not averaged match percentages. n is the number of valid matches for that metric." : "stats-v1；各發球指標分別檢查必要欄位，以發球分數加權，不平均各場百分比。n 為該指標有效場數。"}</p>
    <Resource<Row[]> path={`/v1/players/${tour}/${id}/analysis?by=${by}`}>{rows => rows.length ? <div className="table-scroll"><SortableTable><thead><tr>{[en ? "Group" : "分組", en ? "Matches / wins" : "樣本／勝", en ? "Serve points won" : "發球分得分率", "ACE", en ? "Double faults" : "雙誤率"].map(x => <th key={x}>{x}</th>)}</tr></thead><tbody>{rows.map(r => <tr key={r.bucket}><td>{r.bucket}</td><td>{r.matches} / {r.wins}</td><td data-sort-value={r.serveRate}>{pct(r.serveRate)} (n={r.serveSamples})</td><td data-sort-value={r.aceRate}>{pct(r.aceRate)} (n={r.aceSamples})</td><td data-sort-value={r.dfRate}>{pct(r.dfRate)} (n={r.dfSamples})</td></tr>)}</tbody></SortableTable></div> : <p className="state">{en ? "No eligible samples." : "沒有有效樣本。"}</p>}</Resource>
  </section>;
}
