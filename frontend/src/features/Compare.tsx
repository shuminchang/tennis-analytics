import { useState } from "react";
import PlayerPicker from "../players/PlayerPicker";
import SortableTable from "./SortableTable";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useMe } from "./useMe";
import Resource from "./Resource";
interface Comparison { player: { tour: string; playerId: number; nameFirst: string; nameLast: string }; summary: { played: number; wins: number; losses: number; winRate: number | null } }
export default function Compare() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const [params, setParams] = useSearchParams();
  return <section className="feature-page"><h1>{en ? "Compare players" : "球員並列比較"}</h1><p>{en ? "Compare 2–4 distinct players in the same tour with identical filters and stats-v1 rules." : "同一巡迴 2～4 位球員，使用相同年份、場地與 stats-v1 資格；缺值不補零。"}</p>{!me.data?.authenticated ? <Link to="/account">{en ? "Sign in to compare" : "登入使用比較"}</Link> : <><ComparisonFilters key={params.toString()} params={params} onApply={setParams} en={en}/>{params.has("ids") && <Resource<Comparison[]> path={`/v1/compare?${params}`}>{rows => <div className="table-scroll"><SortableTable><thead><tr>{[en ? "Player" : "球員", en ? "Samples" : "樣本", en ? "Wins" : "勝", en ? "Losses" : "負", en ? "Win rate" : "勝率"].map(x => <th key={x}>{x}</th>)}</tr></thead><tbody>{rows.map(r => <tr key={r.player.playerId}><td><Link to={`/players/${r.player.tour}/${r.player.playerId}`}>{r.player.nameFirst} {r.player.nameLast}</Link></td><td>{r.summary.played}</td><td>{r.summary.wins}</td><td>{r.summary.losses}</td><td>{r.summary.winRate === null ? "—" : `${(r.summary.winRate * 100).toFixed(1)}%`}</td></tr>)}</tbody></SortableTable></div>}</Resource>}</>}</section>;
}

function ComparisonFilters({params,onApply,en}:{params:URLSearchParams;onApply:(p:URLSearchParams)=>void;en:boolean}) {
  const [tour,setTour]=useState(params.get("tour")??"ATP"),[error,setError]=useState("");const original=(params.get("ids")??"").split(",");
  return <><form className="match-filter" onSubmit={e=>{e.preventDefault();const form=new FormData(e.currentTarget);const ids=[0,1,2,3].map(i=>String(form.get(`player${i}`)??"")).filter(Boolean);
    const invalid=[...e.currentTarget.querySelectorAll<HTMLInputElement>('input[role="combobox"]')].some((input,i)=>input.value.trim() && !form.get(`player${i}`));
    if(invalid || ids.length<2 || new Set(ids).size!==ids.length) {setError(en?"Select 2–4 different players from the search results.":"請從搜尋結果選取 2～4 位不同球員。");return;}
    setError("");onApply(new URLSearchParams({tour,ids:ids.join(","),year:String(form.get("year")??""),surface:String(form.get("surface")??"")}));
  }}><label>{en?"Tour":"巡迴"}<select value={tour} onChange={e=>setTour(e.target.value)}><option>ATP</option><option>WTA</option></select></label>
  {[0,1,2,3].map(i=><PlayerPicker key={`${tour}-${i}`} name={`player${i}`} tour={tour} label={en?`Player ${i+1} name${i>1?" (optional)":""}`:`球員 ${i+1} 姓名${i>1?"（選填）":""}`} initialId={tour===(params.get("tour")??"ATP")?original[i]:null} required={i<2} en={en}/>)}
  <label>{en?"Year":"年份"}<input name="year" type="number" min={1} max={9998} defaultValue={params.get("year")??""}/></label><label>{en?"Surface":"場地"}<select name="surface" defaultValue={params.get("surface")??""}><option value="">{en?"All":"全部"}</option>{["Hard","Clay","Grass","Carpet"].map(s=><option key={s}>{s}</option>)}</select></label><button>{en?"Compare":"比較"}</button></form>{error && <p role="alert">{error}</p>}</>;
}
