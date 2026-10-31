import SortableTable from "../features/SortableTable";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { fetchRankings } from "./api";

export default function RankingHistory({ tour, playerId }: { tour: string; playerId: number }) {
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const rawPage = params.get("rankingPage") ?? "0";
  const page = Number(rawPage);
  const valid = /^\d+$/.test(rawPage) && Number.isInteger(page) && page >= 0 && page <= 10000;
  const query = useQuery({
    queryKey: ["rankings", tour, playerId, page],
    queryFn: ({ signal }) => fetchRankings(tour, playerId, page, signal),
    enabled: valid,
    retry: false,
    staleTime: 30000,
  });
  function navigate(nextPage: number) {
    const next = new URLSearchParams(params);
    if (nextPage === 0) next.delete("rankingPage");
    else next.set("rankingPage", String(nextPage));
    setParams(next);
  }
  const pages = Math.max(1, Math.ceil((query.data?.total ?? 0) / 20));
  return (
    <section className="ranking-history" aria-labelledby="ranking-title">
      <div className="results-heading"><h2 id="ranking-title">{t("rankingTitle")}</h2><span className="eyebrow">ATP / WTA</span></div>
      <p className="ranking-note">{t("rankingScope")}</p>
      {!valid ? <div className="state" role="alert"><p>{t("rankingInvalid")}</p><button className="secondary" onClick={() => navigate(0)}>{t("firstPage")}</button></div>
      : query.isPending || query.isFetching ? <div className="state loading" role="status">{t("rankingLoading")}</div>
      : query.isError ? <div className="state" role="alert"><p>{t("rankingError")}</p><button className="secondary" onClick={() => void query.refetch()}>{t("retry")}</button></div>
      : !query.data?.available ? <div className="state"><p>{t("rankingNotImported")}</p></div>
      : page >= pages ? <div className="state"><p>{t("outOfRange")}</p><button className="secondary" onClick={() => navigate(0)}>{t("firstPage")}</button></div>
      : !query.data.items.length ? <div className="state"><p>{t("rankingEmpty")}</p></div>
      : <>
          <div className="table-scroll" tabIndex={0} role="region" aria-label={t("rankingTitle")}>
            <SortableTable scope="page"><caption className="sr-only">{t("rankingTitle")}</caption>
              <thead><tr><th scope="col">{t("rankingDate")}</th><th scope="col">{t("rankingRank")}</th><th scope="col">{t("rankingPoints")}</th></tr></thead>
              <tbody>{query.data.items.map((row) => <tr key={row.date}>
                <td data-sort-value={row.date}>{new Intl.DateTimeFormat(i18n.language, { timeZone: "UTC" }).format(new Date(`${row.date}T00:00:00Z`))}</td>
                <td data-sort-value={row.rank}>{row.rank.toLocaleString(i18n.language)}</td>
                <td data-sort-value={row.points}>{row.points === null ? t("missing") : row.points.toLocaleString(i18n.language)}</td>
              </tr>)}</tbody>
            </SortableTable>
          </div>
          <nav className="pagination" aria-label={t("rankingPagination")}>
            <button className="secondary" disabled={page === 0} onClick={() => navigate(page - 1)}>{t("previous")}</button>
            <span>{t("page", { current: page + 1, total: pages })}</span>
            <button className="secondary" disabled={page + 1 >= pages || page >= 10000} onClick={() => navigate(page + 1)}>{t("next")}</button>
          </nav>
        </>}
    </section>
  );
}
