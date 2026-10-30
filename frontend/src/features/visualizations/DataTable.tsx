import { useState } from "react";
import { Link } from "react-router-dom";
import { groupLabel, interval, labels, type Scatter, type Histogram } from "./model";
export default function DataTable({ scatter, histogram, en }: { scatter: Scatter; histogram: Histogram; en: boolean }) {
  const [page, setPage] = useState(0);const names = labels(en);const pages = Math.max(1, Math.ceil(scatter.items.length / 50));const current = Math.min(page, pages - 1);
  return <details className="explorer-data"><summary>{en ? "Accessible data tables" : "檢視資料表（鍵盤操作）"} · {scatter.items.length}</summary>
    <p className="explorer-table-hint">{en ? "Tables scroll sideways on small screens. Player links work with the keyboard." : "小螢幕表格可左右捲動；球員連結可使用鍵盤操作。"}</p>
    <div className="table-scroll" tabIndex={0} role="region" aria-label={en ? "Scatter table scroll region" : "散布圖資料表捲動區"}><table><caption>{en ? "Valid scatter observations; 50 per page" : "散布圖有效觀測；每頁 50 筆"}</caption><thead><tr><th scope="col">{en ? "Player" : "球員"}</th><th scope="col">{names[scatter.axes.x.key]}</th><th scope="col">{names[scatter.axes.y.key]}</th><th scope="col">{en ? "Group" : "分組"}</th></tr></thead><tbody>{scatter.items.slice(current * 50, (current + 1) * 50).map(p => <tr key={`${p.tour}-${p.playerId}`}><td><Link to={`/players/${p.tour}/${p.playerId}`}>{p.displayName || `#${p.playerId}`} <small>#{p.playerId}</small></Link></td><td>{p.x}</td><td>{p.y}</td><td>{groupLabel(p.group, en)}</td></tr>)}</tbody></table></div>
    <div className="explorer-pagination"><button disabled={current === 0} onClick={() => setPage(current - 1)}>{en ? "Previous" : "上一頁"}</button><span>{current + 1} / {pages}</span><button disabled={current + 1 >= pages} onClick={() => setPage(current + 1)}>{en ? "Next" : "下一頁"}</button></div>
    <div className="table-scroll" tabIndex={0} role="region" aria-label={en ? "Histogram table scroll region" : "直方圖資料表捲動區"}><table><caption>{en ? "Height histogram bins (cm)" : "身高直方圖分箱（cm）"}</caption><thead><tr><th scope="col">{en ? "Interval" : "區間"}</th><th scope="col">{en ? "Players" : "球員數"}</th></tr></thead><tbody>{histogram.bins.map((bin, i) => <tr key={i}><td>{interval(bin)}</td><td>{bin.count}</td></tr>)}</tbody></table></div>
  </details>;
}
