import { useState } from "react";
import { useTranslation } from "react-i18next";
import Resource from "./Resource";
import LineChart from "./LineChart";
export default function RankingChart({ tour, id }: { tour: string; id: number }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";const [metric, setMetric] = useState("rank");
  return <section className="ranking-history"><div className="results-heading"><h2>{en ? "Ranking timeline" : "排名／積分走勢"}</h2><select aria-label={en ? "Chart metric" : "圖表指標"} value={metric} onChange={e => setMetric(e.target.value)}><option value="rank">{en ? "Rank" : "名次"}</option><option value="points">{en ? "Points" : "積分"}</option></select></div><p className="ranking-note">{en ? "Source observations only; missing values break the line. Rank 1 is at the top. Missing publication dates are not synthesized." : "僅呈現來源觀測，缺值斷線；名次 1 在上方，不補造缺少的排名日期。"}</p>
    <Resource<{ items: { date: string; rank: number; points: number | null }[]; total: number }> path={`/v1/players/${tour}/${id}/ranking-chart`}>{data => data.items.length ? <><LineChart data={data.items.map(p => ({ date: p.date, value: metric === "rank" ? p.rank : p.points }))} inverted={metric === "rank"} label={metric === "rank" ? (en ? "Rank" : "名次") : (en ? "Points" : "積分")}/>{data.total > data.items.length && <p>{en ? "First 5,000 observations shown." : "僅顯示前 5,000 筆觀測。"}</p>}</> : <p className="state">{en ? "No observations." : "尚無觀測資料。"}</p>}</Resource>
  </section>;
}
