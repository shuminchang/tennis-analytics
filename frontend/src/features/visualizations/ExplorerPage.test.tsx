import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import i18n from "../../i18n";
import ExplorerPage from "./ExplorerPage";
import { apiParams, defaults, parseFilters, sameSource, sourceLink } from "./model";
vi.mock("./ExplorerCharts", () => ({ default: () => <div data-testid="charts">Chart ready</div> }));
const rules = { transformSha: "a", qualitySha: "b", scopeSha: "c" };
const source = { repositoryUrl: "https://example.com", snapshotSha: "a".repeat(40), playerRunId: 1, rankingRunId: 3, qualityRevision: "q1", playerRules: rules, rankingRules: rules };
const sample = { population: 2, valid: 1, excluded: 1, primaryReasons: [{ metric: "height_cm", code: "MISSING", count: 1 }] };
let unavailable = false;let different = false;let differentPolicy = false;
function fixture(path: string) {
  const url = new URL(path, "http://localhost");const tour = url.searchParams.get("tour") ?? "ATP";
  const current = different && path.includes("histogram") ? { ...source, qualityRevision: "q2" } : source;
  if (path.includes("metadata")) return { source, tour, metricVersion: "visualization-v1", latestRankingDate: "2024-01-01", earliestRankingDate: "2024-01-01", limits: {} };
  const empty = path.includes("2024-01-02");const context = { source: current, tour, rankingDate: url.searchParams.get("rankingDate") ?? "2024-01-01", metricVersion: "visualization-v1", qualityPolicyVersion: differentPolicy && path.includes("histogram") ? "visualization-v2" : "visualization-v1" };
  const shared = { context, sample: empty ? { population: 0, valid: 0, excluded: 0, primaryReasons: [] } : sample, warnings: [] };
  if (path.includes("histogram")) return { ...shared, domain: empty ? null : { min: 185, max: 185 }, bins: empty ? [] : [{ lower: 184.5, upper: 185.5, count: 1, upperInclusive: true }] };
  return { ...shared, axes: { x: { key: url.searchParams.get("x") ?? "height_cm" }, y: { key: url.searchParams.get("y") ?? "rank" } }, items: empty ? [] : [{ tour, playerId: 1, displayName: tour === "ATP" ? "Roger Federer" : "Serena Williams", x: 185, y: 1, group: "R" }] };
}
function Location() { const location = useLocation();const navigate = useNavigate();return <><output data-testid="url">{location.search}</output><button onClick={() => { void navigate(-1); }}>History back</button></>; }
function mount(search = "") {
  vi.stubGlobal("fetch", vi.fn(async (path: string) => ({ ok: !(unavailable && path.includes("scatter")), status: 503, json: async () => fixture(path) })));
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={[`/visualizations${search}`]}><Location/><ExplorerPage/></MemoryRouter></QueryClientProvider>);
}
beforeEach(async () => { unavailable = false;different = false;differentPolicy = false;await i18n.changeLanguage("zh-TW"); });
it("restores shared URL filters and offers keyboard player links and exclusion reasons", async () => {
  mount("?tour=WTA&rankingDate=2024-01-01&topN=50&x=rank&y=height_cm&color=none&bins=5");
  expect(await screen.findByTestId("charts")).toBeVisible();
  expect(screen.getByLabelText("X 軸")).toHaveValue("rank");expect(screen.getByLabelText("巡迴")).toHaveValue("WTA");
  fireEvent.click(screen.getByText(/檢視資料表/));expect(screen.getByRole("link", { name: /Serena Williams/ })).toHaveAttribute("href", "/players/WTA/1");
  expect(screen.getAllByText("身高（cm）：缺值 1")).toHaveLength(2);
  expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes("histogram") && String(url).includes("x="))).toBe(false);
});
it("changes axes and parameters on apply and restores them on browser history back", async () => {
  mount();await screen.findByTestId("charts");const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("X 軸"), "ranking_points");expect(screen.getByLabelText("Y 軸")).toHaveValue("height_cm");
  await user.click(screen.getByRole("button", { name: "套用條件 ↗" }));await screen.findByTestId("charts");
  expect(screen.getByTestId("url")).toHaveTextContent("x=ranking_points");
  await user.click(screen.getByRole("button", { name: "History back" }));await waitFor(() => expect(screen.getByLabelText("X 軸")).toHaveValue("height_cm"));
});
it("does not request data for invalid URL filters and can reset", async () => {
  mount("?tour=ATP&topN=1001");expect(screen.getByRole("alert")).toHaveTextContent("探索條件無效");expect(fetch).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "重設條件" }));expect(await screen.findByTestId("charts")).toBeVisible();
});
it("shows a real zero sample as empty instead of retaining the preceding plot", async () => {
  mount("?tour=ATP&rankingDate=2024-01-02");expect(await screen.findByText("沒有有效觀測")).toBeVisible();
  expect(screen.queryByTestId("charts")).not.toBeInTheDocument();expect(screen.getAllByText("母體 0 · 有效 0 · 排除 0")).toHaveLength(2);
});
it("retries API failures without showing stale data", async () => {
  unavailable = true;mount();expect(await screen.findByRole("alert")).toHaveTextContent("暫時無法取得探索資料");expect(screen.queryByTestId("charts")).not.toBeInTheDocument();
  unavailable = false;fireEvent.click(screen.getByRole("button", { name: "重試" }));expect(await screen.findByTestId("charts")).toBeVisible();
});
it("rejects a mixed source snapshot instead of combining chart samples", async () => {
  different = true;mount();expect(await screen.findByRole("alert")).toHaveTextContent("來源已變更");expect(screen.queryByTestId("charts")).not.toBeInTheDocument();
});
it("pins the resolved source date in a selectable share URL if clipboard is unavailable", async () => {
  mount();await screen.findByTestId("charts");fireEvent.click(screen.getByRole("button", { name: "複製此快照連結 ↗" }));
  expect((await screen.findByLabelText("可分享的快照網址") as HTMLInputElement).value).toContain("rankingDate=2024-01-01");
});
it("updates bilingual controls and source explanations", async () => {
  mount();await screen.findByTestId("charts");await act(async () => { await i18n.changeLanguage("en"); });
  expect(screen.getByLabelText("X axis")).toBeVisible();expect(screen.getByRole("button", { name: "Explore data ↗" })).toBeVisible();
  fireEvent.click(screen.getByText("Source, definitions & limits"));expect(screen.getByText(/not measurements at every ranking date/)).toBeVisible();
});
it("validates duplicated unknown date and axis parameters with bounded API serialization", () => {
  for (const query of ["tour=ATP&tour=WTA", "tour=ATP&extra=x", "rankingDate=2024-02-30", "rankingDate=0000-01-01", "x=rank&y=ranking_points", "bins=51"]) expect(() => parseFilters(new URLSearchParams(query))).toThrow();
  expect(apiParams(defaults, "scatter")).not.toContain("bins");expect(apiParams(defaults, "histogram")).not.toContain("color");
  expect(parseFilters(new URLSearchParams("topN=1&bins=50"))).toMatchObject({ topN: 1, bins: 50 });
});

it("cancels a superseded tour request and never displays its late data", async () => {
  mount();await screen.findByTestId("charts");let aborted = false;
  vi.mocked(fetch).mockImplementation(async (path, options) => {
    if (String(path).includes("scatter") && String(path).includes("tour=WTA")) return new Promise<Response>((_resolve, reject) => {
      options?.signal?.addEventListener("abort", () => { aborted = true;reject(new DOMException("Aborted", "AbortError")); });
    });
    return { ok: true, json: async () => fixture(String(path)) } as Response;
  });
  const user = userEvent.setup();await user.selectOptions(screen.getByLabelText("巡迴"), "WTA");await user.click(screen.getByRole("button", { name: "套用條件 ↗" }));
  await waitFor(() => expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes("scatter") && String(url).includes("tour=WTA"))).toBe(true));
  await user.selectOptions(screen.getByLabelText("巡迴"), "ATP");await user.click(screen.getByRole("button", { name: "套用條件 ↗" }));
  await screen.findByTestId("charts");await waitFor(() => expect(aborted).toBe(true));
  fireEvent.click(screen.getByText(/檢視資料表/));expect(screen.getByRole("link", { name: /Roger Federer/ })).toBeVisible();expect(screen.queryByText("Serena Williams")).not.toBeInTheDocument();
});
it("compares semantic source versions and rejects unsafe repository links", () => {
  expect(sameSource(source, { ...source, playerRules: { scopeSha: "c", qualitySha: "b", transformSha: "a" } })).toBe(true);
  expect(sourceLink("javascript:alert(1)")).toBeUndefined();expect(sourceLink("https://example.com/data")).toBe("https://example.com/data");
});

it("does not combine different metric quality policies from the same source", async () => {
  differentPolicy = true;mount();expect(await screen.findByRole("alert")).toHaveTextContent("來源已變更");expect(screen.queryByTestId("charts")).not.toBeInTheDocument();
});
