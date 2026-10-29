import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import i18n from "../i18n";
import MatchHistory from "./MatchHistory";

function mount(url = "/players/ATP/1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[url]}><MatchHistory tour="ATP" playerId={1} /></MemoryRouter></QueryClientProvider>);
}
const entry = { id: 1, tournamentId: "test", tournamentName: "Test Open", tournamentDate: "2024-01-01", sourceLevel: "A", surface: "Hard", round: "F", matchNumber: 1, score: "W/O", scoreNeedsReview: false, status: "WALKOVER", result: "ADVANCED", opponent: { playerId: 2, nameFirst: "Rafael", nameLast: "Nadal", visible: true } };
function response(available = true, items: object[] = [], total = items.length) {
  return { ok: true, json: async () => ({ available, items, total, page: 0, size: 20 }) };
}
beforeEach(async () => { await i18n.changeLanguage("zh-TW"); });
it("distinguishes not imported from no displayable matches", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(false)));mount();
  expect(await screen.findByText("比賽資料尚未匯入。")).toBeInTheDocument();
});
it("shows an empty filter result without fabricating a win rate", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response()));mount();
  expect(await screen.findByText("目前沒有符合條件的可顯示比賽紀錄。")).toBeInTheDocument();
});
it("labels walkovers, links opponents and paginates", async () => {
  const fetch = vi.fn().mockResolvedValue(response(true, [entry], 21));vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();mount("/players/ATP/1?q=Roger&matchYear=2024&rankingPage=2");
  const table = await screen.findByRole("table");
  expect(within(table).getByText("不戰晉級")).toBeInTheDocument();
  expect(within(table).getByRole("link", { name: "Rafael Nadal" })).toHaveAttribute("href", "/players/ATP/2?q=Roger&matchYear=2024");
  await user.click(screen.getByRole("button", { name: "下一頁" }));
  await waitFor(() => expect(fetch.mock.calls.at(-1)![0]).toContain("page=1&size=20&year=2024"));
});
it("applies a year and resets pagination", async () => {
  const fetch = vi.fn().mockResolvedValue(response(true, [entry], 21));vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();mount("/players/ATP/1?matchPage=1");await screen.findByRole("table");
  await user.type(screen.getByLabelText("賽事年份"), "2023");
  await user.click(screen.getByRole("button", { name: "篩選比賽" }));
  await waitFor(() => expect(fetch.mock.calls.at(-1)![0]).toContain("page=0&size=20&year=2023"));
});
it("rejects invalid URL conditions without fetching", () => {
  const fetch = vi.fn();vi.stubGlobal("fetch", fetch);mount("/players/ATP/1?matchYear=0");
  expect(screen.getByRole("alert")).toHaveTextContent("比賽篩選條件無效");expect(fetch).not.toHaveBeenCalled();
});
it("shows score review and missing data without an opponent link", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(true, [{ ...entry, score: null, scoreNeedsReview: true, opponent: { ...entry.opponent, visible: false } }])));
  mount();const table = await screen.findByRole("table");
  expect(within(table).getByText("比分待查")).toBeInTheDocument();expect(within(table).getByText("未提供")).toBeInTheDocument();
  expect(within(table).queryByRole("link")).not.toBeInTheDocument();
});
it("shows loading and retries after a failure", async () => {
  let resolve!: (value: unknown) => void;
  const fetch = vi.fn().mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockResolvedValue(response());
  vi.stubGlobal("fetch", fetch);const user = userEvent.setup();mount();
  expect(screen.getByRole("status")).toHaveTextContent("正在查詢比賽");resolve({ ok: false });
  expect(await screen.findByRole("alert")).toHaveTextContent("暫時無法取得比賽紀錄");
  await user.click(screen.getByRole("button", { name: "重新載入" }));
  expect(await screen.findByText("目前沒有符合條件的可顯示比賽紀錄。")).toBeInTheDocument();
});
