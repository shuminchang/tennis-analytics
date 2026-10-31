import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useMe } from "./useMe";
import { get } from "./api";
import PlayerPicker from "../players/PlayerPicker";
import SortableTable from "./SortableTable";

type Metadata = { tour:string;sourceLastDate:string|null;suggestedDate:string|null;maxDate:string|null;embargoDays:number;snapshot:string;version:string };
type Rating = { value:number;matches:number;date:string };
type Inputs = { tour:string;playerId:number;name:string;overall:Rating|null;surface:Rating|null;age:number|null;height:number|null;ageDate:string|null;heightDate:string|null };
type Score = { n:number;brier:number|null;logLoss:number|null };
type Estimate = { method:string;firstProbability:number;secondProbability:number;validation:Score|null };
export type Prediction = { available:boolean;reason:string|null;tour:string;asOf:string;historyBefore:string;sourceLastDate:string;surface:string;bestOf:number;snapshot:string;first:Inputs;second:Inputs;prediction:Estimate|null;comparisons:Estimate[];model:{year:number;trainThrough:string;validationThrough:string;selected:string;validation:{selected:Score}}|null;contributions:{feature:string;value:number;coefficient:number;logOdds:number}[];warnings:string[] };
function probability(p:number|null|undefined) { return p==null?"—":p<.001?"<0.1%":p>.999?">99.9%":`${(p*100).toFixed(1)}%`; }
function method(key:string,en:boolean) {return ({STANDARD_ELO:en?"Standard Elo baseline":"標準 Elo 基準",CALIBRATED_ELO:en?"Calibrated Elo & surface":"校準 Elo 與場地",ELO_AGE_HEIGHT:en?"Elo, surface, age & height":"Elo、場地、年齡與身高"})[key]??key;}
export default function MatchupPrediction() {
  const {i18n}=useTranslation();const en=i18n.language==="en";const me=useMe();const [params,setParams]=useSearchParams();const tour=params.get("tour")??"ATP";
  const allowed=me.data?.roles?.some(r=>r==="ADVANCED" || r==="ADMIN");
  const invalid=!["ATP","WTA"].includes(tour)||[...params].some(([k])=>!["tour","first","second","asOf","surface","bestOf"].includes(k)||params.getAll(k).length!==1);
  return <section className="feature-page prediction-page"><p className="eyebrow">MATCHUP LAB / EXPERIMENTAL</p><h1>{en?"Matchup win probability":"兩位球員相遇勝率預估"}</h1><p>{en?"Compare a rating-only baseline with a model learned from earlier matches. This is an experimental, snapshot-based estimate, separate from historical head-to-head win rates.":"比較 Elo 基準與使用先前比賽訓練的模型。這是依來源快照估計的實驗功能，與歷史交手勝率分開呈現。"}</p><Link to="/h2h">{en?"Historical head-to-head ↗":"歷史交手紀錄 ↗"}</Link>
    {!allowed ? <p><Link to="/account">{en?"Advanced or administrator access required":"需進階或管理員權限"}</Link></p> : invalid ? <div role="alert"><p>{en?"Invalid matchup filters":"對戰條件無效"}</p><button onClick={()=>setParams({tour:"ATP"})}>{en?"Reset":"重設"}</button></div> : <Study key={params.toString()} params={params} tour={tour} en={en} onApply={setParams}/>}
  </section>;
}
function Study({params,tour,en,onApply}:{params:URLSearchParams;tour:string;en:boolean;onApply:(p:URLSearchParams)=>void}) {
  const metadata=useQuery({queryKey:["matchup-info",tour],queryFn:({signal})=>get<Metadata>(`/v1/matchup-prediction/metadata?tour=${tour}`,signal),retry:false,staleTime:30000});
  const requested=params.has("first")&&params.has("second")&&params.has("asOf");
  const query=useQuery({queryKey:["matchup",params.toString()],queryFn:({signal})=>get<Prediction>(`/v1/matchup-prediction?${params}`,signal),enabled:requested&&!!metadata.data,retry:false,staleTime:30000});
  return <>
    {metadata.isPending ? <p role="status">{en?"Reading model source…":"讀取模型資料…"}</p> : metadata.isError || query.isError ? <div role="alert"><p>{en?"Model unavailable or its source version has changed. Please retry after the model is prepared.":"模型尚未就緒或來源版本已變更，請於模型準備完成後重試。"}</p><button onClick={()=>{void metadata.refetch();if(requested)void query.refetch();}}>{en?"Retry":"重試"}</button></div> : <>
      <Filters params={params} metadata={metadata.data} en={en} onApply={onApply}/>
      {!metadata.data.sourceLastDate && <p>{en?"No eligible model matches for this tour.":"此巡迴尚無可用的模型比賽資料。"}</p>}
      {requested && query.isFetching ? <p role="status">{en?"Calculating probabilities…":"計算相遇勝率…"}</p> : query.data ? <Result data={query.data} en={en}/> : <p>{en?"Search and select two players, then choose a date, surface and format.":"請以姓名選取兩位球員，再選擇日期、場地與賽制。"}</p>}
    </>}
  </>;
}
function Filters({params,metadata,en,onApply}:{params:URLSearchParams;metadata:Metadata;en:boolean;onApply:(p:URLSearchParams)=>void}) {
  const [error,setError]=useState("");
  return <><form className="match-filter prediction-filters" onSubmit={e=>{
    e.preventDefault();const f=new FormData(e.currentTarget),first=String(f.get("first")??""),second=String(f.get("second")??"");
    if(!first || !second || first===second){setError(en?"Select two different players from the results.":"請從搜尋結果選取兩位不同球員。");return;}
    setError("");onApply(new URLSearchParams({tour:metadata.tour,first,second,asOf:String(f.get("asOf")),surface:String(f.get("surface")),bestOf:String(f.get("bestOf"))}));
  }}><label>{en?"Tour":"巡迴"}<select value={metadata.tour} onChange={e=>onApply(new URLSearchParams({tour:e.target.value}))}><option>ATP</option><option>WTA</option></select></label>
    {["first","second"].map((name,i)=><PlayerPicker key={`${metadata.tour}-${name}`} tour={metadata.tour} name={name} label={en?`Player ${i+1} name`:`球員 ${i+1} 姓名`} initialId={params.get(name)} required en={en}/>)}
    <label>{en?"Hypothetical match date":"假設相遇日期"}<input type="date" name="asOf" min="1900-01-01" max={metadata.maxDate??undefined} required defaultValue={params.get("asOf")??metadata.suggestedDate??""}/></label>
    <label>{en?"Surface":"場地"}<select name="surface" defaultValue={params.get("surface")??"Hard"}>{["Hard","Clay","Grass","Carpet"].map(s=><option key={s}>{s}</option>)}</select></label>
    <label>{en?"Best of":"賽制"}<select name="bestOf" defaultValue={params.get("bestOf")??"3"}><option value="3">{en?"Best of 3":"三盤兩勝"}</option>{metadata.tour==="ATP"&&<option value="5">{en?"Best of 5":"五盤三勝"}</option>}</select></label>
    <button className="primary" disabled={!metadata.sourceLastDate}>{en?"Estimate win probability":"計算相遇勝率"}</button>
  </form>{error&&<p role="alert">{error}</p>}<p className="ranking-note">{en?"Source tournament dates through":"來源賽事日期截至"} {metadata.sourceLastDate??"—"} · {en?"A conservative 21-day lag is used because actual match timestamps are unavailable. The suggested date includes this lag.":"來源沒有完整開賽時間，採保守的 21 日隔離；預設日期已包含這段隔離期。"}</p></>;
}
const warningText=(key:string,en:boolean)=>({
  EXPERIMENTAL_NOT_OFFICIAL:en?"Experimental model, not an official probability or guaranteed outcome.":"實驗模型，不是官方機率或勝負保證。",
  TOURNAMENT_DATE_APPROXIMATION_EMBARGO21:en?"Only tournament dates strictly before the match date minus 21 days are used; this is an approximation, not exact historical information replay.":"只用相遇日前 21 日以前的賽事日期；這是保守近似，並非精確歷史賽前資訊重播。",
  NO_INJURY_FITNESS_OR_LIVE_DATA:en?"Injuries, fitness, weather and live results are not included.":"未納入傷勢、體能、天氣及即時賽果。",
  OBSERVED_AGE_PROJECTED_NOT_DOB:en?"Age is projected from a prior match observation; height is a prior observation, not a new measurement.":"年齡由先前賽事觀測推算，身高也是先前觀測，並非當日重新量測。",
  SNAPSHOT_RETROSPECTIVE_QUALITY:en?"Retrospective analysis uses the current source snapshot and quality decisions.":"回溯分析使用目前來源快照與品質判定。",
  AGE_OR_HEIGHT_MISSING_ELO_FALLBACK:en?"Age or height is unavailable; this matchup falls back to Elo.":"年齡或身高不足，本場退回 Elo，未自行補值。",
  UNCALIBRATED_SMALL_SAMPLE:en?"There is insufficient validation data for this model year; the baseline is not calibrated here.":"此年度的校準資料不足；本次基準機率尚未經校準。",
  STANDARD_ELO_NOT_FORMAT_CALIBRATED:en?"Standard Elo is a mixed-format baseline; it has no learned format adjustment.":"標準 Elo 是混合賽制基準，未套用學得的賽制修正。",
  SOURCE_NOT_LIVE:en?"The selected date is beyond the observed snapshot; this is a hypothetical estimate.":"選定日期超出來源觀測範圍，屬假設估計。"
})[key]??(en?"Model data limitation":"模型資料限制");
function Result({data,en}:{data:Prediction;en:boolean}) {
  const p=data.prediction;return <div className="prediction-result"><h2>{data.first.name} <span aria-hidden="true">／</span> {data.second.name}</h2><p>{data.tour} · {data.asOf} · {data.surface} · {en?`Best of ${data.bestOf}`:data.bestOf===5?"五盤三勝":"三盤兩勝"}</p>
    {!data.available || !p ? <p role="status">{en?"Insufficient recent history: each player needs at least 10 prior eligible matches and a last source match within 365 days. No probability is produced.":"近期資料不足：兩人都需有至少 10 場截止時可用的歷史比賽，且最近一場距相遇日不超過 365 天；本次未產生機率。"}</p> : <><p>{en?"Applied model":"實際使用模型"}：{method(p.method,en)}</p><div className="prediction-scores">{[[data.first.name,p.firstProbability],[data.second.name,p.secondProbability]].map(([name,value])=><div key={String(name)}><span>{name}</span><strong>{probability(Number(value))}</strong><meter min={0} max={1} value={Number(value)} aria-label={`${name} ${en?"estimated win probability":"預估勝率"}`}/></div>)}</div></>}
    <p className="ranking-note">{en?"History strictly before":"歷史資料嚴格早於"} {data.historyBefore} · {en?"Source last tournament date":"來源最後賽事日期"} {data.sourceLastDate}</p>
    <ul className="prediction-warnings">{data.warnings.map(key=><li key={key}>{warningText(key,en)}</li>)}</ul>
    <div className="table-scroll"><SortableTable><caption>{en?"Inputs from earlier eligible matches":"先前合格比賽的特徵觀測"}</caption><thead><tr>{[en?"Player":"球員",en?"Overall Elo":"綜合 Elo",en?"Surface Elo":"場地 Elo",en?"Prior matches":"先前場數",en?"Projected age (years)":"推算年齡（歲）",en?"Prior height (cm)":"先前身高（公分）",en?"Last match source date":"最近賽事來源日期"].map(t=><th key={t}>{t}</th>)}</tr></thead><tbody>{[data.first,data.second].map(row=><tr key={row.playerId}><td><Link to={`/players/${row.tour}/${row.playerId}`}>{row.name}</Link></td><td data-sort-value={row.overall?.value??null}>{row.overall?.value.toFixed(0)??"—"}</td><td data-sort-value={row.surface?.value??null}>{row.surface?.value.toFixed(0)??"—"} {row.surface?`(n=${row.surface.matches})`:""}</td><td>{row.overall?.matches??0}</td><td data-sort-value={row.age}>{row.age?.toFixed(1)??"—"}<small className="match-meta">{row.ageDate??""}</small></td><td data-sort-value={row.height}>{row.height?.toFixed(0)??"—"}<small className="match-meta">{row.heightDate??""}</small></td><td>{row.overall?.date??"—"}</td></tr>)}</tbody></SortableTable></div>
    {!!data.comparisons.length && <><h3>{en?"Model comparison":"模型對照"}</h3><p className="ranking-note">{en?"Validation N can differ: the augmented candidate uses complete age/height pairs. Values below are from earlier validation seasons; they are not the chance that this particular forecast is correct. Lower Brier/log loss is better.":"驗證 N 可能不同：綜合候選模型只評估年齡與身高齊全的配對。下列是較早年度的驗證結果，不是本場預測正確的機率；Brier／log loss 越低越好。"}</p><div className="table-scroll"><SortableTable><thead><tr>{[en?"Method":"方法",data.first.name,data.second.name,"N","Brier","Log loss"].map((s,i)=><th key={i}>{s}</th>)}</tr></thead><tbody>{data.comparisons.map(c=><tr key={c.method}><td>{method(c.method,en)}</td><td data-sort-value={c.firstProbability}>{probability(c.firstProbability)}</td><td data-sort-value={c.secondProbability}>{probability(c.secondProbability)}</td><td>{c.validation?.n??0}</td><td>{c.validation?.brier?.toFixed(4)??"—"}</td><td>{c.validation?.logLoss?.toFixed(4)??"—"}</td></tr>)}</tbody></SortableTable></div></>}
    <details className="explorer-source"><summary>{en?"Model methods and references":"模型方法與參考資料"}</summary><p>{en?"Year-specific, no-intercept logistic models are selected using earlier validation years. Swapping players negates every feature and complements the probability. Missing demographics use the Elo fallback; coefficients are learned, not manually chosen percentage bonuses.":"依年度使用較早資料訓練、驗證並選擇不含截距的邏輯斯模型。交換球員會反轉全部特徵，機率互補；缺少生理觀測時退回 Elo，係數由資料估計，不是人工指定加幾個百分點。"}</p><p>{data.model?`${en?"Training through":"訓練截至"} ${data.model.trainThrough} · ${en?"Validation through":"驗證截至"} ${data.model.validationThrough}`:en?"No learned model for this date; standard Elo only.":"此日期尚無學得的模型，僅使用標準 Elo。"}</p><p>matchup-v1 · prior-date-embargo21-v1<br/><code>{data.snapshot}</code><br/>Jeff Sackmann / Tennis Abstract · CC BY-NC-SA 4.0</p><ul><li><a href="https://www.tennisabstract.com/blog/2019/12/03/an-introduction-to-tennis-elo/" target="_blank" rel="noreferrer">Tennis Abstract — Tennis Elo</a></li><li><a href="https://vuir.vu.edu.au/34652/" target="_blank" rel="noreferrer">Kovalchik (2016) — Tennis win prediction</a></li><li><a href="https://arxiv.org/abs/2502.01613" target="_blank" rel="noreferrer">Buhamra & Groll (2025) — Enhanced features</a></li></ul></details>
  </div>;
}
