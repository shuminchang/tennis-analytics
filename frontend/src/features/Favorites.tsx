import { useState } from "react";
import { Link } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useMe } from "./useMe";
import { mutate } from "./api";
import Resource from "./Resource";
export default function Favorites() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const client = useQueryClient();const [error, setError] = useState(false);
  return <section className="feature-page"><h1>{en ? "My favorites" : "我的收藏"}</h1>{!me.data?.authenticated ? <Link to="/account">{en ? "Sign in" : "請先登入"}</Link> : <Resource<{ tour: string; player_id: number; name_first: string; name_last: string }[]> path="/v1/favorites">{rows => rows.length ? <ul>{rows.map(r => <li key={`${r.tour}-${r.player_id}`}><Link to={`/players/${r.tour}/${r.player_id}`}>{r.tour} · {r.name_first} {r.name_last}</Link> <button onClick={async () => { try { await mutate(`/v1/favorites/${r.tour}/${r.player_id}`, "DELETE");await client.invalidateQueries({ queryKey: ["resource", "/v1/favorites"] }); } catch { setError(true); } }}>{en ? "Remove" : "移除"}</button></li>)}</ul> : <p>{en ? "No favorites yet." : "尚無收藏。"}</p>}</Resource>}{error && <p role="alert">{en ? "Update failed." : "更新失敗。"}</p>}</section>;
}
