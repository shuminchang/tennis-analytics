import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import Resource from "./Resource";
export default function Rankings() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const [params, setParams] = useSearchParams();
  return <section className="feature-page"><h1>{en ? "Ranking observations" : "指定日期排名榜"}</h1><p>{en ? "The latest available source date is used when no date is selected. This is not a live ranking." : "未選日期時使用來源最新可用日期；不是即時排名。指定日期沒有紀錄時不借用前一期。"}</p>
    <form className="match-filter" onSubmit={e => { e.preventDefault();const f = new FormData(e.currentTarget);setParams({ tour: String(f.get("tour")), date: String(f.get("date")) }); }}><label>{en ? "Tour" : "巡迴"}<select name="tour" defaultValue={params.get("tour") ?? "ATP"}><option>ATP</option><option>WTA</option></select></label><label>{en ? "Date" : "日期"}<input type="date" name="date" defaultValue={params.get("date") ?? ""}/></label><button>{en ? "View" : "查看"}</button></form>
    <Resource<{ date: string | null; items: { playerId: number; nameFirst: string; nameLast: string; rank: number; points: number | null }[] }> path={`/v1/rankings?${params}`}>{data => <><h2>{data.date ?? (en ? "No source date" : "尚無來源日期")}</h2><div className="table-scroll"><table><thead><tr><th>{en ? "Rank" : "名次"}</th><th>{en ? "Player" : "球員"}</th><th>{en ? "Points" : "積分"}</th></tr></thead><tbody>{data.items.map(r => <tr key={r.playerId}><td>{r.rank}</td><td><Link to={`/players/${params.get("tour") ?? "ATP"}/${r.playerId}`}>{r.nameFirst} {r.nameLast}</Link></td><td>{r.points ?? "—"}</td></tr>)}</tbody></table></div>{!data.items.length && <p>{en ? "No observations for this date." : "此日期沒有可顯示紀錄。"}</p>}</>}</Resource>
  </section>;
}
