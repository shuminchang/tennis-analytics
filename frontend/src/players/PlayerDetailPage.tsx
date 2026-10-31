import { useEffect, useRef } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlayer } from "./api";
import RankingHistory from "./RankingHistory";
import MatchHistory from "./MatchHistory";
import PlayerSummary from "../features/PlayerSummary";
import PlayerAnalysis from "../features/PlayerAnalysis";
import RankingChart from "../features/RankingChart";
import FavoriteButton from "../features/FavoriteButton";
import Elo from "../features/Elo";

export default function PlayerDetailPage() {
  const { tour = "", playerId = "" } = useParams();
  const { search } = useLocation();
  const returnParams = new URLSearchParams(search);
  returnParams.delete("rankingPage");
  returnParams.delete("matchPage");
  returnParams.delete("matchYear");
  const { t, i18n } = useTranslation();
  const heading = useRef<HTMLHeadingElement>(null);
  const selectedTour = tour.toUpperCase();
  const id = Number(playerId);
  const valid = ["ATP", "WTA"].includes(selectedTour) && /^\d+$/.test(playerId)
    && Number.isInteger(id) && id > 0 && id <= 2147483647;
  const query = useQuery({
    queryKey: ["player", selectedTour, id],
    queryFn: ({ signal }) => fetchPlayer(selectedTour, id, signal),
    enabled: valid,
    retry: false,
    staleTime: 30000,
  });
  const player = query.data;
  useEffect(() => {
    heading.current?.focus();
  }, [tour, playerId, query.status]);
  const name = player && ([player.nameFirst, player.nameLast].filter(Boolean).join(" ") || t("unnamed"));
  const hand = player?.hand
    ? t(({ R: "right", L: "left", A: "both", U: "unknown" } as Record<string, string>)[player.hand] ?? "unknown")
    : t("missing");
  return (
    <section className="player-detail" aria-labelledby="detail-title">
      <Link to={`/players${returnParams.size ? `?${returnParams}` : ""}`} className="text-link detail-back">← {t("detailBack")}</Link>
      {!valid ? (
        <div className="state" role="alert"><h1 id="detail-title" ref={heading} tabIndex={-1}>{t("detailInvalid")}</h1></div>
      ) : query.isPending ? (
        <div className="state loading" role="status"><span className="loading-ball" />{t("loading")}</div>
      ) : query.isError ? (
        <div className="state" role="alert">
          <h1 id="detail-title" ref={heading} tabIndex={-1}>{t("errorTitle")}</h1>
          <p>{t("error")}</p>
          <button className="secondary" onClick={() => void query.refetch()}>{t("retry")}</button>
        </div>
      ) : !player ? (
        <div className="state">
          <h1 id="detail-title" ref={heading} tabIndex={-1}>{t("detailMissing")}</h1>
          <p>{t("detailMissingNote")}</p>
        </div>
      ) : (
        <>
          <header className="detail-heading">
            <div>
              <p className="eyebrow">{t("detailEdition")}</p>
              <h1 id="detail-title" ref={heading} tabIndex={-1}>{name}</h1>
              <p className="detail-identity"><span className="tour-tag">{player.tour}</span></p>
            </div>
            <span className="detail-monogram" aria-hidden="true">{[player.nameFirst, player.nameLast].filter(Boolean).map((part) => Array.from(part!)[0]).join("") || "—"}</span>
          </header>
          <section className="detail-facts" aria-labelledby="facts-title">
            <h2 id="facts-title">{t("detailTitle")}</h2>
            <dl>
              <div><dt>{t("tour")}</dt><dd>{player.tour}</dd></div>
              <div><dt>{t("country")}</dt><dd>{player.ioc || t("missing")}</dd></div>
              <div><dt>{t("born")}</dt><dd>{player.dob ? new Intl.DateTimeFormat(i18n.language, { timeZone: "UTC" }).format(new Date(`${player.dob}T00:00:00Z`)) : t("missing")}</dd></div>
              <div><dt>{t("hand")}</dt><dd>{hand}</dd></div>
            </dl>
          </section>
          <FavoriteButton tour={player.tour} id={player.playerId} />
          <PlayerSummary tour={player.tour} id={player.playerId} />
          <PlayerAnalysis tour={player.tour} id={player.playerId} />
          <Elo tour={player.tour} id={player.playerId} />
          <RankingChart tour={player.tour} id={player.playerId} />
          <RankingHistory tour={player.tour} playerId={player.playerId} />
          <MatchHistory tour={player.tour} playerId={player.playerId} />
          <aside className="about">
            <h2>{t("scopeTitle")}</h2>
            <div><p>{t("detailNote")}</p><p className="source-note">{t("source")} <a href="https://github.com/Aneeshers/tennis-sackmann-archive" target="_blank" rel="noreferrer">GitHub ↗</a> · <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank" rel="noreferrer">{t("license")}</a></p></div>
          </aside>
        </>
      )}
    </section>
  );
}
