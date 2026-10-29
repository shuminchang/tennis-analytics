import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import Resource from "./Resource";
export default function Home() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const navigate = useNavigate();
  return <section className="feature-page"><p className="eyebrow">TENNIS LEDGER</p><h1>{en ? "Follow the records. Understand the game." : "沿著紀錄，理解每一場比賽。"}</h1><p>{en ? "Explore archived ATP and WTA observations with explicit coverage and quality rules." : "探索 ATP／WTA 封存觀測，以明確範圍與品質規則呈現資料。"}</p><form className="match-filter" onSubmit={e => { e.preventDefault();const f = new FormData(e.currentTarget);navigate(`/players?q=${encodeURIComponent(String(f.get("q")))}`); }}><label>{en ? "Player name" : "球員姓名"}<input name="q" maxLength={100}/></label><button>{en ? "Search" : "搜尋"}</button></form><p><Link to="/tournaments">{en ? "Explore events" : "瀏覽賽事"}</Link> · <Link to="/rankings">{en ? "Ranking observations" : "查看排名榜"}</Link></p><h2>{en ? "Successful local imports" : "已完成匯入"}</h2><Resource<{ data_kind: string; imported_at: string }[]> path="/v1/home">{rows => <ul>{rows.map(r => <li key={r.data_kind}>{r.data_kind} · {new Date(r.imported_at).toLocaleString(i18n.language)}</li>)}</ul>}</Resource><p>{en ? "Import timestamps are not the dates of the observations." : "匯入時間不等於資料觀測日期；來源缺漏與排除規則請見各頁說明。"}</p></section>;
}
