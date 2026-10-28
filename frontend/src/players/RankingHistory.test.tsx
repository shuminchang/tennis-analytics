import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import i18n from "../i18n";
import RankingHistory from "./RankingHistory";

function mount(url = "/players/ATP/1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[url]}><RankingHistory tour="ATP" playerId={1} /></MemoryRouter></QueryClientProvider>);
}
function response(available = true, items: object[] = [], total = items.length) {
  return { ok: true, json: async () => ({ available, items, total, page: 0, size: 20 }) };
}
beforeEach(async () => { await i18n.changeLanguage("zh-TW"); });
it("distinguishes unimported data from no observations", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(false)));
  mount();
  expect(await screen.findByText("排名資料尚未匯入。")).toBeInTheDocument();
});
it("shows no observations without inventing a ranking", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response()));
  mount();
  expect(await screen.findByText("目前沒有這位球員可顯示的排名紀錄。")).toBeInTheDocument();
});
it("preserves zero and missing points, and requests the next page", async () => {
  const fetch = vi.fn().mockResolvedValue(response(true, [{ date: "2024-01-15", rank: 1, points: 0 }, { date: "2024-01-08", rank: 2, points: null }], 21));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();mount();
  const table = await screen.findByRole("table");
  expect(within(table).getByText("0")).toBeInTheDocument();
  expect(within(table).getByText("未提供")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "下一頁" }));
  await waitFor(() => expect(fetch.mock.calls.at(-1)![0]).toContain("page=1&size=20"));
});
it("recovers from an invalid page without initially querying", async () => {
  const fetch = vi.fn().mockResolvedValue(response());vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();mount("/players/ATP/1?rankingPage=bad");
  expect(screen.getByRole("alert")).toHaveTextContent("排名頁碼無效");expect(fetch).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "回到第一頁" }));
  await waitFor(() => expect(fetch).toHaveBeenCalled());
});
it("offers recovery for out-of-range pages", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(true, [], 1)));mount("/players/ATP/1?rankingPage=5");
  expect(await screen.findByText("頁碼超出結果範圍。")).toBeInTheDocument();
});
it("shows loading and permits retry after failure", async () => {
  let resolve!: (value: unknown) => void;
  const fetch = vi.fn().mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockResolvedValue(response());
  vi.stubGlobal("fetch", fetch);const user = userEvent.setup();mount();
  expect(screen.getByRole("status")).toHaveTextContent("正在查詢排名");
  resolve({ ok: false });expect(await screen.findByRole("alert")).toHaveTextContent("暫時無法取得排名歷史");
  await user.click(screen.getByRole("button", { name: "重新載入" }));
  expect(await screen.findByText("目前沒有這位球員可顯示的排名紀錄。")).toBeInTheDocument();
});
