import type { FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { fetchMatches } from "./api";

export default function MatchHistory({ tour, playerId }: { tour: string; playerId: number }) {
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const year = params.get("matchYear") ?? "";
  const rawPage = params.get("matchPage") ?? "0";
  const page = Number(rawPage);
  const valid = (!year || /^\d+$/.test(year) && Number(year) >= 1 && Number(year) <= 9998)
    && /^\d+$/.test(rawPage) && page >= 0 && page <= 10000;
  const query = useQuery({
    queryKey: ["matches", tour, playerId, year, page],
    queryFn: ({ signal }) => fetchMatches(tour, playerId, year, page, signal),
    enabled: valid, retry: false, staleTime: 30000,
  });
  function navigate(nextPage: number) {
    const next = new URLSearchParams(params);
    if (nextPage === 0) next.delete("matchPage"); else next.set("matchPage", String(nextPage));
    setParams(next);
  }
  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const selected = String(new FormData(event.currentTarget).get("year") ?? "");
    const next = new URLSearchParams(params);next.delete("matchPage");
    if (selected) next.set("matchYear", selected); else next.delete("matchYear");
    setParams(next);
  }
  function opponentLink(id: number) {
    const next = new URLSearchParams(params);next.delete("matchPage");next.delete("rankingPage");
    return `/players/${tour}/${id}${next.size ? `?${next}` : ""}`;
  }
  const pages = Math.max(1, Math.ceil((query.data?.total ?? 0) / 20));
  return (
    <section className="match-history ranking-history" aria-labelledby="match-title">
      <div className="results-heading"><h2 id="match-title">{t("matchTitle")}</h2></div>
      <p className="ranking-note">{t("matchScope")}</p>
      <form className="match-filter" onSubmit={filter} key={year}>
        <label>{t("matchYear")}<input name="year" type="number" min="1" max="9998" step="1" defaultValue={year} placeholder={t("matchAllYears")} /></label>
        <button className="secondary" type="submit">{t("matchFilter")}</button>
      </form>
      {!valid ? <div className="state" role="alert"><p>{t("matchInvalid")}</p><button className="secondary" onClick={() => { const next = new URLSearchParams(params);next.delete("matchYear");next.delete("matchPage");setParams(next); }}>{t("clear")}</button></div>
      : query.isPending || query.isFetching ? <div className="state loading" role="status">{t("matchLoading")}</div>
      : query.isError ? <div className="state" role="alert"><p>{t("matchError")}</p><button className="secondary" onClick={() => void query.refetch()}>{t("retry")}</button></div>
      : !query.data?.available ? <div className="state"><p>{t("matchNotImported")}</p></div>
      : page >= pages ? <div className="state"><p>{t("outOfRange")}</p><button className="secondary" onClick={() => navigate(0)}>{t("firstPage")}</button></div>
      : !query.data.items.length ? <div className="state"><p>{t("matchEmpty")}</p></div>
      : <>
          <p className="match-scroll-note">{t("matchScroll")}</p>
          <div className="table-scroll" tabIndex={0} role="region" aria-label={t("matchTable")}>
            <table><caption className="sr-only">{t("matchTable")}</caption>
              <thead><tr>{["matchDate", "matchEvent", "matchRound", "matchOpponent", "matchResult", "matchScore"].map(key => <th scope="col" key={key}>{t(key)}</th>)}</tr></thead>
              <tbody>{query.data.items.map(row => <tr key={row.id}>
                <td>{new Intl.DateTimeFormat(i18n.language, { timeZone: "UTC" }).format(new Date(`${row.tournamentDate}T00:00:00Z`))}</td>
                <td><strong>{row.tournamentName || t("missing")}</strong><small className="match-meta">{row.sourceLevel || t("missing")} · {row.surface || t("missing")}</small></td>
                <td>{row.round}</td>
                <td>{row.opponent.visible ? <Link className="player-link" to={opponentLink(row.opponent.playerId)}>{[row.opponent.nameFirst, row.opponent.nameLast].filter(Boolean).join(" ") || t("unnamed")}</Link> : t("matchOpponentUnavailable")}</td>
                <td>{t(`matchResult_${row.result}`)}{row.status !== "RECORDED" && <small className="match-meta">{t(`matchStatus_${row.status}`)}</small>}</td>
                <td>{row.score || t("missing")}{row.scoreNeedsReview && <small className="match-meta">{t("matchScoreReview")}</small>}</td>
              </tr>)}</tbody>
            </table>
          </div>
          <nav className="pagination" aria-label={t("matchPagination")}>
            <button className="secondary" disabled={page === 0} onClick={() => navigate(page - 1)}>{t("previous")}</button>
            <span>{t("page", { current: page + 1, total: pages })}</span>
            <button className="secondary" disabled={page + 1 >= pages || page >= 10000} onClick={() => navigate(page + 1)}>{t("next")}</button>
          </nav>
        </>}
    </section>
  );
}
