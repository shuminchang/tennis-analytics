import { useState } from "react";
import { Link } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useMe } from "./useMe";
import { mutate } from "./api";
export default function FavoriteButton({ tour, id }: { tour: string; id: number }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const client = useQueryClient();const [message, setMessage] = useState("");
  return me.data?.authenticated ? <div><button className="secondary" onClick={async () => { try { await mutate("/v1/favorites", "POST", { tour, playerId: id });await client.invalidateQueries({ queryKey: ["resource", "/v1/favorites"] });setMessage(en ? "Saved." : "已收藏。"); } catch { setMessage(en ? "Unable to save." : "收藏失敗。"); } }}>{en ? "Save player" : "收藏球員"}</button><span role="status">{message}</span></div> : <Link to="/account">{en ? "Sign in to save" : "登入收藏"}</Link>;
}
