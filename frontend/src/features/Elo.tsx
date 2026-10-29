import { useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useMe } from "./useMe";
import Resource from "./Resource";
import LineChart from "./LineChart";
interface History { available: boolean; model: { id: number; rules_sha256: string; input_sha256: string } | null; items: { date: string; rating: number; sequence: number }[]; current: { dimension: string; rating: number; matches: number }[]; total: number }
export default function Elo({ tour, id }: { tour: string; id: number }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const [dimension, setDimension] = useState("OVERALL");const allowed = me.data?.roles?.some(r => ["ADVANCED", "ADMIN"].includes(r));
  return <section className="ranking-history"><div className="results-heading"><h2>Elo</h2><select aria-label={en ? "Elo dimension" : "Elo 分類"} value={dimension} onChange={e => setDimension(e.target.value)}>{["OVERALL", "Hard", "Clay", "Grass", "Carpet"].map(s => <option key={s}>{s}</option>)}</select></div><p className="ranking-note">{en ? "elo-v1: starts at 1500, K=250/(n+5)^0.4; stats-v1 input. Tournament dates and round ordering are approximations. Not an official ranking or prediction. Surface models are independent." : "elo-v1：初始 1500，K=250/(n+5)^0.4，採 stats-v1 樣本。賽事日期與輪次排序是模型近似；不是官方排名或預測。分場地模型獨立計算。"}</p>
    {!allowed ? <p className="ranking-note"><Link to="/account">{en ? "Advanced access required" : "需進階或管理員權限"}</Link></p> : <Resource<History> path={`/v1/players/${tour}/${id}/elo?dimension=${dimension}`}>{r => !r.available ? <p className="state">{en ? "Model not built yet." : "尚未建立模型。"}</p> : <><p className="ranking-note">{en ? "Model" : "模型"} #{r.model?.id} · {r.model?.rules_sha256.slice(0, 12)}</p><ul>{r.current.map(c => <li key={c.dimension}>{c.dimension}: {c.rating.toFixed(1)} (n={c.matches})</li>)}</ul>{r.items.length ? <LineChart label={`Elo · ${dimension}`} data={r.items.map(p => ({ date: p.date, value: p.rating }))}/> : <p>{en ? "No eligible observations." : "沒有合格觀測。"}</p>}{r.total > r.items.length && <p>{en ? "First 5,000 observations shown." : "僅顯示前 5,000 筆觀測。"}</p>}</>}</Resource>}
  </section>;
}
