import { useQueries } from "@tanstack/react-query";
import { fetchPlayer } from "../players/api";
import { useState } from "react";
import PlayerPicker from "../players/PlayerPicker";
import SortableTable from "./SortableTable";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useMe } from "./useMe";
import Resource from "./Resource";
interface Group { bucket: string; matches: number; firstWins: number; secondWins: number }
interface Result { matches: number; firstWins: number; secondWins: number; firstWinRate: number | null; confidence95: { lower: number | null; upper: number | null }; surfaces: Group[]; years: Group[] }
export default function H2H() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const [params, setParams] = useSearchParams();const allowed = me.data?.roles?.some(r => ["ADVANCED", "ADMIN"].includes(r));
  const pct = (x: number | null) => x === null ? "—" : `${(x * 100).toFixed(1)}%`;
  return <section className="feature-page"><h1>H2H</h1><p>{en ? "Stats-v1: played retirements included; walkovers/defaults excluded. Dates refer to tournaments. 95% Wilson intervals describe observed proportions, not predictions." : "stats-v1：納入已出賽退賽，排除不戰與判負。篩選日期為賽事日期；95% Wilson 區間描述觀測比例，不是預測。"}</p>{!allowed ? <Link to="/account">{en ? "Advanced access required" : "需進階或管理員權限"}</Link> : <><H2HFilters key={params.toString()} params={params} onApply={setParams} en={en}/>{params.has("first") && <><AppliedPair params={params} en={en}/><Resource<Result> path={`/v1/h2h?${params}`}>{r => <><h2>{r.firstWins} — {r.secondWins} ({r.matches})</h2>{r.matches < 5 && <p role="status">{en ? "Small sample: fewer than five matches." : "樣本過少：未達 5 場。"}</p>}<p>{en ? "Player 1 win rate" : "球員 1 勝率"}: {pct(r.firstWinRate)} · 95% Wilson: {pct(r.confidence95.lower)}–{pct(r.confidence95.upper)}</p>{[r.surfaces, r.years].map((rows,n) => <div className="table-scroll" key={n}><SortableTable><caption>{n === 0 ? (en ? "Surfaces" : "分場地") : (en ? "Years" : "分年份")}</caption><thead><tr><th>{en ? "Group" : "分組"}</th><th>{en ? "Samples" : "樣本"}</th><th>{en ? "Player 1 wins" : "球員 1 勝"}</th><th>{en ? "Player 2 wins" : "球員 2 勝"}</th></tr></thead><tbody>{rows.map(g => <tr key={g.bucket}><td>{g.bucket}</td><td>{g.matches}</td><td>{g.firstWins}</td><td>{g.secondWins}</td></tr>)}</tbody></SortableTable></div>)}</>}</Resource></>}</>}</section>;
}

function H2HFilters({params,onApply,en}:{params:URLSearchParams;onApply:(p:URLSearchParams)=>void;en:boolean}) {
  const [tour,setTour]=useState(params.get("tour")??"ATP"),[error,setError]=useState("");
  return <><form className="match-filter" onSubmit={e=>{e.preventDefault();const form=new FormData(e.currentTarget);const first=String(form.get("first")??""),second=String(form.get("second")??"");
    if(!first || !second || first===second) {setError(en?"Select two different players from the search results.":"請從搜尋結果選取兩位不同球員。");return;}
    setError("");onApply(new URLSearchParams({tour,first,second,from:String(form.get("from")??""),to:String(form.get("to")??""),surface:String(form.get("surface")??"")}));
  }}><label>{en?"Tour":"巡迴"}<select value={tour} onChange={e=>setTour(e.target.value)}><option>ATP</option><option>WTA</option></select></label>
  {["first","second"].map((name,i)=><PlayerPicker key={`${tour}-${name}`} name={name} tour={tour} label={en?`Player ${i+1} name`:`球員 ${i+1} 姓名`} initialId={tour===(params.get("tour")??"ATP")?params.get(name):null} required en={en}/>)}
  <label>{en?"From":"起日"}<input name="from" type="date" defaultValue={params.get("from")??""}/></label><label>{en?"To":"迄日"}<input name="to" type="date" defaultValue={params.get("to")??""}/></label><label>{en?"Surface":"場地"}<select name="surface" defaultValue={params.get("surface")??""}><option value="">{en?"All":"全部"}</option>{["Hard","Clay","Grass","Carpet"].map(s=><option key={s}>{s}</option>)}</select></label><button>{en?"Compare":"查詢"}</button></form>{error && <p role="alert">{error}</p>}</>;
}

function AppliedPair({params,en}:{params:URLSearchParams;en:boolean}) {
  const tour=params.get("tour")??"ATP";const ids=[params.get("first"),params.get("second")];
  const players=useQueries({queries:ids.map(id=>({queryKey:["picker-player",tour,id],queryFn:({signal}:{signal:AbortSignal})=>fetchPlayer(tour,Number(id),signal),enabled:["ATP","WTA"].includes(tour)&&!!id&&/^[1-9]\d*$/.test(id)&&Number(id)<=2147483647,retry:false,staleTime:30000}))});
  return <p className="ranking-note">{en?"Applied query":"已套用查詢"}：{tour} · {players.map(p=>p.data?[p.data.nameFirst,p.data.nameLast].filter(Boolean).join(" "):p.isPending?"…":en?"Name unavailable":"姓名暫不可用").join(" / ")}<br/>{en?"Changed filters apply after Compare.":"修改條件後，請按「查詢」才會更新下方結果。"}</p>;
}
