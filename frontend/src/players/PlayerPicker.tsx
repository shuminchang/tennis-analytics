import { useEffect, useId, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchPlayer, fetchPlayers, type Player } from "./api";
const fullName = (p: Player) => [p.nameFirst, p.nameLast].filter(Boolean).join(" ");
export default function PlayerPicker({ name, label, tour, initialId, required = false, en }: { name: string; label: string; tour: string; initialId?: string | null; required?: boolean; en: boolean }) {
  const id = useId();const [typed, setTyped] = useState<string | null>(null), [selected, setSelected] = useState<Player | null>(null), [open, setOpen] = useState(false), [active, setActive] = useState(0), [debounced, setDebounced] = useState("");
  const validId = !!initialId && /^[1-9]\d*$/.test(initialId) && Number(initialId) <= 2147483647;
  const original = useQuery({ queryKey:["picker-player",tour,initialId], queryFn:({signal})=>fetchPlayer(tour,Number(initialId),signal), enabled:validId && typed===null, staleTime:30000, retry:false });
  const choice = typed === null ? original.data : selected;const text = typed ?? (choice ? fullName(choice) : "");const term = text.trim();
  useEffect(()=>{const timer=setTimeout(()=>setDebounced(term),300);return()=>clearTimeout(timer);},[term]);
  const matches = useQuery({ queryKey:["picker-search",tour,debounced], queryFn:({signal})=>fetchPlayers({q:debounced,tour,page:0,size:10,valid:true},signal), enabled:open && !!term && term===debounced, staleTime:30000, retry:false });
  const items = term===debounced ? matches.data?.items ?? [] : [];
  const loading = term!==debounced || matches.isPending || matches.isFetching;
  function choose(player: Player) { setSelected(player);setTyped(fullName(player));setOpen(false); }
  return <div className="player-picker"><label htmlFor={id}>{label}</label><input id={id} role="combobox" autoComplete="off" aria-autocomplete="list" aria-expanded={open && !!term} aria-controls={`${id}-options`} aria-activedescendant={open && !loading && !matches.isError && items[active] ? `${id}-${active}` : undefined} maxLength={100} required={required} value={text} placeholder={en?"Search by name, then select":"輸入姓名並選取球員"} onChange={e=>{setTyped(e.target.value);setSelected(null);setActive(0);setOpen(true);}} onFocus={()=>{if(!choice)setOpen(true);}} onBlur={()=>setOpen(false)} onKeyDown={e=>{
    if(e.key==="Escape")setOpen(false);
    if(e.key==="ArrowDown" || e.key==="ArrowUp") {e.preventDefault();setOpen(true);setActive(n=>Math.max(0,Math.min(items.length-1,n+(e.key==="ArrowDown"?1:-1))));}
    if(e.key==="Enter" && open && !loading && !matches.isError && items[active]) {e.preventDefault();choose(items[active]);}
  }}/><input type="hidden" name={name} value={choice?.playerId ?? ""}/>
  {typed===null && original.isError && <p role="alert">{en?"Unable to load selected player. Search again.":"無法載入已選球員，請重新搜尋。"}</p>}
  {open && term && <div className="player-picker-options" id={`${id}-options`} role="listbox" aria-label={en?"Matching players":"符合姓名的球員"}>
    {loading ? <p role="status">{en?"Searching…":"搜尋中…"}</p> : matches.isError ? <p role="alert">{en?"Search unavailable. Edit the name to retry.":"暫時無法搜尋，請修改姓名後重試。"}</p> : !items.length ? <p role="status">{en?"No matching players":"沒有符合姓名的球員"}</p> : items.map((p,i)=><button key={`${p.tour}-${p.playerId}`} type="button" role="option" tabIndex={-1} id={`${id}-${i}`} aria-selected={active===i} onMouseDown={e=>e.preventDefault()} onClick={()=>choose(p)}><strong>{fullName(p)||(en?"Name unavailable":"姓名未提供")}</strong><small>{p.tour} · {p.ioc||"—"} · {p.dob||"—"}</small></button>)}
    {!loading && (matches.data?.total ?? 0)>items.length && <p>{en?"Showing the first 10 matches; refine the name.":"僅列前 10 筆，請輸入更完整姓名縮小範圍。"}</p>}
  </div>}</div>;
}
