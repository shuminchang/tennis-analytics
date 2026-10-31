import SortableTable from "../features/SortableTable";
import { useMemo } from "react";
import type { FormEvent } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import type { ColumnDef } from "@tanstack/react-table";
import { useTranslation } from "react-i18next";
import { fetchPlayers, readSearch } from "./api";
import type { Player } from "./api";

const emptyPlayers: Player[] = [];

export default function PlayerSearchPage() {
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const search = readSearch(params);
  const query = useQuery({
    queryKey: ["players", search.q, search.tour, search.page, search.size],
    queryFn: ({ signal }) => fetchPlayers(search, signal),
    enabled: search.valid && !!search.q,
    retry: false,
    staleTime: 30000,
  });
  const columns = useMemo<ColumnDef<Player>[]>(
    () => [
      {
        id: "name",
        header: t("player"),
        cell: ({ row }) => (
          <div className="player-name">
            <Link to={`/players/${row.original.tour}/${row.original.playerId}?${params}`} className="player-link">
              {[row.original.nameFirst, row.original.nameLast]
                .filter(Boolean)
                .join(" ") || t("unnamed")}
            </Link>
          </div>
        ),
      },
      {
        accessorKey: "tour",
        header: t("tour"),
        cell: ({ getValue }) => (
          <span className="tour-tag">{getValue<string>()}</span>
        ),
      },
      {
        accessorKey: "ioc",
        header: t("country"),
        cell: ({ getValue }) => getValue<string>() || t("missing"),
      },
      {
        accessorKey: "dob",
        header: t("born"),
        cell: ({ getValue }) => {
          const date = getValue<string>();
          return date
            ? new Intl.DateTimeFormat(i18n.language, {
                timeZone: "UTC",
              }).format(new Date(`${date}T00:00:00Z`))
            : t("missing");
        },
      },
      {
        accessorKey: "hand",
        header: t("hand"),
        cell: ({ getValue }) => {
          const hand = getValue<string>();
          return hand
            ? t(
                (
                  { R: "right", L: "left", A: "both", U: "unknown" } as Record<
                    string,
                    string
                  >
                )[hand] ?? "unknown",
              )
            : t("missing");
        },
      },
    ],
    [t, i18n.language, params],
  );
  // TanStack Table supplies an imperative table instance; this component is not React-Compiler memoized.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: query.data?.items ?? emptyPlayers,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => `${row.tour}-${row.playerId}`,
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams({
      q: String(form.get("q") ?? "").trim(),
      size: String(form.get("size") ?? "20"),
    });
    const tour = String(form.get("tour") ?? "");
    if (tour) next.set("tour", tour);
    setParams(next);
  }
  function pageLink(page: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(page));
    return `?${next}`;
  }
  const totalPages = query.data
    ? Math.max(1, Math.ceil(query.data.total / search.size))
    : 1;
  return (
    <>
      <section className="hero" aria-labelledby="page-title">
        <div className="hero-copy">
          <p className="eyebrow">{t("edition")}</p>
          <h1 id="page-title">
            {t("heading")}
            <br />
            <em>{t("headingAccent")}</em>
          </h1>
          <p className="intro">{t("intro")}</p>
        </div>
        <div className="court-art" aria-hidden="true">
          <div className="court-lines">
            <span />
            <span />
          </div>
          <div className="tennis-ball" />
          <span className="court-label">LOVE THE GAME.</span>
        </div>
      </section>
      <section className="directory" aria-labelledby="results-title">
        <form className="search-form" key={params.toString()} onSubmit={submit}>
          <label className="name-field">
            {t("searchLabel")}
            <input
              name="q"
              type="search"
              maxLength={100}
              defaultValue={search.q}
              placeholder={t("placeholder")}
            />
          </label>
          <label>
            {t("tourLabel")}
            <select name="tour" defaultValue={search.tour}>
              <option value="">{t("allTours")}</option>
              <option value="ATP">ATP</option>
              <option value="WTA">WTA</option>
            </select>
          </label>
          <label>
            {t("sizeLabel")}
            <select
              name="size"
              defaultValue={search.valid ? search.size : 20}
            >
              {search.valid && ![20, 50, 100].includes(search.size) && <option value={search.size}>{search.size}</option>}
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
          </label>
          <button className="primary" type="submit">
            {t("search")} <span aria-hidden="true">↗</span>
          </button>
        </form>
        <div className="results-heading" aria-live="polite">
          <h2 id="results-title">
            {t("results")}{" "}
            {search.q && query.data && !query.isFetching && (
              <span className="result-count">
                {t("count", { count: query.data.total })}
              </span>
            )}
          </h2>
          <Link to="/players" className="text-link">
            {t("clear")}
          </Link>
        </div>
        {!search.valid ? (
          <div className="state" role="alert">
            {t("invalid")}
          </div>
        ) : !search.q ? (
          <div className="state"><p>{i18n.language === "en" ? "Enter a player name to search." : "請輸入球員姓名後搜尋。"}</p></div>
        ) : query.isPending || query.isFetching ? (
          <div className="state loading" role="status">
            <span className="loading-ball" />
            {t("loading")}
          </div>
        ) : query.isError ? (
          <div className="state" role="alert">
            <h3>{t("errorTitle")}</h3>
            <p>{t("error")}</p>
            <button className="secondary" onClick={() => void query.refetch()}>
              {t("retry")}
            </button>
          </div>
        ) : !query.data?.items.length ? (
          <div className="state">
            <span className="empty-mark" aria-hidden="true">
              ∅
            </span>
            <h3>{t("emptyTitle")}</h3>
            <p>{t("empty")}</p>
          </div>
        ) : (
          <div
            className="table-scroll"
            tabIndex={0}
            role="region"
            aria-label={t("tableCaption")}
          >
            <SortableTable scope="page">
              <caption className="sr-only">{t("tableCaption")}</caption>
              <thead>
                {table.getHeaderGroups().map((group) => (
                  <tr key={group.id}>
                    {group.headers.map((header) => (
                      <th key={header.id} scope="col">
                        {flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                      </th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody>
                {table.getRowModel().rows.map((row) => (
                  <tr key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} data-sort-value={cell.column.id === "name" ? [cell.row.original.nameFirst, cell.row.original.nameLast].filter(Boolean).join(" ") : cell.row.original[cell.column.id as keyof Player]}>
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </SortableTable>
          </div>
        )}
        {search.valid && !!search.q &&
          query.data &&
          !query.isFetching &&
          !query.isError &&
          search.page >= totalPages && (
            <div className="pagination">
              <span>{t("outOfRange")}</span>
              <Link className="secondary" to={pageLink(0)}>
                {t("firstPage")}
              </Link>
            </div>
          )}
        {search.valid && !!search.q &&
          query.data &&
          !query.isFetching &&
          !query.isError &&
          search.page < totalPages && (
            <nav className="pagination" aria-label={t("pagination")}>
              {search.page > 0 ? (
                <Link className="secondary" to={pageLink(search.page - 1)}>
                  {t("previous")}
                </Link>
              ) : (
                <button className="secondary" disabled>
                  {t("previous")}
                </button>
              )}
              <span>
                {t("page", { current: search.page + 1, total: totalPages })}
              </span>
              {search.page + 1 < totalPages ? (
                <Link className="secondary" to={pageLink(search.page + 1)}>
                  {t("next")}
                </Link>
              ) : (
                <button className="secondary" disabled>
                  {t("next")}
                </button>
              )}
            </nav>
          )}
      </section>
      <aside id="about" className="about">
        <h2>{t("scopeTitle")}</h2>
        <div>
          <p>{t("scope")}</p>
          <p className="source-note">
            {t("source")}{" "}
            <a
              href="https://github.com/Aneeshers/tennis-sackmann-archive"
              target="_blank"
              rel="noreferrer"
            >
              GitHub ↗
            </a>{" "}
            ·{" "}
            <a
              href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
              target="_blank"
              rel="noreferrer"
            >
              {t("license")}
            </a>
          </p>
        </div>
      </aside>
    </>
  );
}
