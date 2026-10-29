import { useEffect } from "react";
import { Routes, Route, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import PlayerSearchPage from "./players/PlayerSearchPage";
import PlayerDetailPage from "./players/PlayerDetailPage";
import Tournaments, { TournamentDetail } from "./features/Tournaments";
import Rankings from "./features/Rankings";
import Home from "./features/Home";
import "./App.css";

export default function App() {
  const { t, i18n } = useTranslation();
  useEffect(() => {
    document.documentElement.lang = i18n.language;
    document.title = `${t("nav")} · ${t("brand")}`;
  }, [i18n.language, t]);
  function changeLanguage() {
    const next = i18n.language === "en" ? "zh-TW" : "en";
    void i18n.changeLanguage(next);
    try {
      localStorage.setItem("tennis-language", next);
    } catch {
      /* Optional preference storage. */
    }
  }
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main">
        {t("skip")}
      </a>
      <header className="site-header">
        <Link to="/players" className="brand">
          <span className="brand-mark" aria-hidden="true">
            ↗
          </span>
          {t("brand")}
        </Link>
        <nav aria-label={t("nav")}>
          <Link to="/players" className="nav-active">
            {t("nav")}
          </Link>
          <Link to="/">{i18n.language === "en" ? "Home" : "首頁"}</Link>
          <Link to="/rankings">{i18n.language === "en" ? "Rankings" : "排名"}</Link>
          <Link to="/tournaments">{i18n.language === "en" ? "Events" : "賽事"}</Link>
          <button
            className="language"
            aria-label={t("language")}
            onClick={changeLanguage}
          >
            {i18n.language === "en" ? "繁體中文" : "English"}{" "}
            <span aria-hidden="true">↔</span>
          </button>
        </nav>
      </header>
      <main id="main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/rankings" element={<Rankings />} />
          <Route path="/tournaments" element={<Tournaments />} />
          <Route path="/tournaments/:tour/:id" element={<TournamentDetail />} />
          <Route path="/players" element={<PlayerSearchPage />} />
          <Route path="/players/:tour/:playerId" element={<PlayerDetailPage />} />
          <Route
            path="*"
            element={
              <section className="state">
                <h1>{t("notFound")}</h1>
                <Link to="/players">{t("back")}</Link>
              </section>
            }
          />
        </Routes>
      </main>
      <footer>
        <span>{t("footer")}</span>
        <a href="https://deerflow.tech" target="_blank" rel="noreferrer">
          Created By Deerflow
        </a>
      </footer>
    </div>
  );
}
