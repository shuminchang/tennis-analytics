import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { get } from "./api";
interface Summary { ruleVersion: string; displayed: number; played: number; wins: number; losses: number; winRate: number | null }
export default function PlayerSummary({ tour, id }: { tour: string; id: number }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";
  const query = useQuery({ queryKey: ["summary", tour, id], retry: false, queryFn: async ({ signal }) => {
    const data = await get<Summary>(`/v1/players/${tour}/${id}/summary`, signal);
    if (!Number.isSafeInteger(data.played)) throw new Error("Invalid summary");return data;
  } });
  return <section className="detail-facts"><h2>{en ? "Observed results" : "來源結果摘要"}</h2>
    <p className="ranking-note">{en ? "stats-v1: walkovers, defaults and uncertain scores excluded; played retirements included. Coverage is incomplete." : "stats-v1：排除不戰、判負與不明比分；已出賽的退賽納入。不是完整生涯統計。"}</p>
    {query.isPending ? <p>{en ? "Loading…" : "載入中…"}</p> : query.isError ? <button className="secondary" onClick={() => void query.refetch()}>{en ? "Retry summary" : "重試摘要"}</button> : <dl>
      <div><dt>{en ? "Eligible matches" : "有效樣本"}</dt><dd>{query.data.played.toLocaleString()}</dd></div>
      <div><dt>{en ? "Wins" : "勝"}</dt><dd>{query.data.wins.toLocaleString()}</dd></div>
      <div><dt>{en ? "Losses" : "負"}</dt><dd>{query.data.losses.toLocaleString()}</dd></div>
      <div><dt>{en ? "Win rate" : "勝率"}</dt><dd>{query.data.winRate === null ? "—" : new Intl.NumberFormat(i18n.language, { style: "percent", maximumFractionDigits: 1 }).format(query.data.winRate)}</dd></div>
    </dl>}
  </section>;
}
