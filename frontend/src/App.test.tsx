import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import i18n from "./i18n";
import App from "./App";
import type { Player } from "./players/api";

const roger: Player = {
  tour: "ATP",
  playerId: 1,
  nameFirst: "Roger",
  nameLast: "Federer",
  hand: "R",
  dob: "1981-08-08",
  ioc: "SUI",
};
const serena: Player = {
  tour: "WTA",
  playerId: 1,
  nameFirst: "Serena",
  nameLast: "Williams",
  hand: "R",
  dob: null,
  ioc: "USA",
};
function mount(url = "/players?q=Roger") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
function response(items: Player[] = [roger], total = items.length) {
  return {
    ok: true,
    json: async () => ({ items, total, page: 0, size: 20 }),
  } as Response;
}
beforeEach(async () => {
  localStorage.clear();
  await i18n.changeLanguage("zh-TW");
});
it("submits filters, resets pagination, and renders API data", async () => {
  const fetch = vi.fn().mockResolvedValue(response());
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount("/players?q=Roger&page=2");
  expect(await screen.findByText("Roger Federer")).toBeInTheDocument();
  await user.clear(screen.getByLabelText("球員姓名"));
  await user.type(screen.getByLabelText("球員姓名"), "Serena");
  await user.selectOptions(screen.getByLabelText("巡迴"), "WTA");
  fetch.mockResolvedValue(response([serena]));
  await user.click(screen.getByRole("button", { name: /搜尋球員/ }));
  expect(await screen.findByText("Serena Williams")).toBeInTheDocument();
  const url = new URL(fetch.mock.calls.at(-1)![0], "http://localhost");
  expect(url.searchParams.get("q")).toBe("Serena");
  expect(url.searchParams.get("tour")).toBe("WTA");
  expect(url.searchParams.get("page")).toBe("0");
});
it("supports next page and language preference without changing data identity", async () => {
  const fetch = vi.fn().mockResolvedValue(response([roger], 30));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount();
  await screen.findByText("Roger Federer");
  await user.click(screen.getByRole("link", { name: "下一頁" }));
  await waitFor(() => expect(fetch.mock.calls.at(-1)![0]).toContain("page=1"));
  await user.click(screen.getByRole("button", { name: "切換語言" }));
  expect(
    await screen.findByRole("heading", { name: /Search results/ }),
  ).toBeInTheDocument();
  expect(localStorage.getItem("tennis-language")).toBe("en");
});
it("shows loading, then empty results", async () => {
  let resolve!: (value: Response) => void;
  vi.stubGlobal(
    "fetch",
    vi.fn(
      () =>
        new Promise<Response>((r) => {
          resolve = r;
        }),
    ),
  );
  mount();
  expect(screen.getByRole("status")).toHaveTextContent("正在查詢");
  resolve(response([]));
  expect(await screen.findByText("沒有找到符合條件的球員")).toBeInTheDocument();
});
it("shows error and permits retry", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue(response()),
  );
  const user = userEvent.setup();
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "暫時無法取得資料",
  );
  await user.click(screen.getByRole("button", { name: "重新載入" }));
  expect(await screen.findByText("Roger Federer")).toBeInTheDocument();
});
it("does not request invalid URL filters", () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  mount("/players?size=999");
  expect(screen.getByRole("alert")).toHaveTextContent("搜尋條件無效");
  expect(fetch).not.toHaveBeenCalled();
});

it("offers recovery for an out-of-range page", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response([], 1)));
  mount("/players?q=Roger&page=999");
  expect(await screen.findByText("頁碼超出結果範圍。")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "回到第一頁" })).toHaveAttribute("href", "/players?q=Roger&page=0");
});

it("opens details and returns to the original search filters", async () => {
  const fetch = vi.fn().mockImplementation((url: string) => Promise.resolve(
    url.includes("/ATP/1") ? { ok: true, json: async () => roger } : response(),
  ));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount("/players?q=Roger&tour=ATP&size=20");
  await user.click(await screen.findByRole("link", { name: "Roger Federer" }));
  expect(await screen.findByRole("heading", { name: "Roger Federer" })).toBeInTheDocument();
  expect(screen.getByText("SUI")).toBeInTheDocument();
  const back = screen.getByRole("link", { name: /返回搜尋結果/ });
  expect(back).toHaveAttribute("href", "/players?q=Roger&tour=ATP&size=20");
  await user.click(back);
  expect(await screen.findByLabelText("球員姓名")).toHaveValue("Roger");
});
it("loads a direct WTA detail URL and preserves missing fields", async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...serena, hand: "U", ioc: null }) });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount("/players/WTA/1");
  expect(await screen.findByRole("heading", { name: "Serena Williams" })).toBeInTheDocument();
  expect(fetch.mock.calls[0][0]).toBe("/tennis/api/v1/players/WTA/1");
  expect(screen.getAllByText("未提供")).toHaveLength(2);
  expect(screen.getByText("未知")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "切換語言" }));
  expect(await screen.findByText("At a glance")).toBeInTheDocument();
});
it("shows a distinct detail not-found state", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404 }));
  mount("/players/ATP/999");
  expect(await screen.findByRole("heading", { name: "找不到這位球員" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "重新載入" })).not.toBeInTheDocument();
});
it.each(["ITF/1", "ATP/0", "ATP/-1", "ATP/no", "ATP/2147483648"])("rejects invalid detail identity %s without fetching", (identity) => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  mount(`/players/${identity}`);
  expect(screen.getByRole("alert")).toHaveTextContent("球員網址無效");
  expect(fetch).not.toHaveBeenCalled();
});
it("shows detail loading and permits retry after failure", async () => {
  let resolve!: (value: unknown) => void;
  const fetch = vi.fn().mockImplementationOnce(() => new Promise((r) => { resolve = r; }))
    .mockResolvedValue({ ok: true, json: async () => roger });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount("/players/ATP/1");
  expect(screen.getByRole("status")).toHaveTextContent("正在查詢");
  resolve({ ok: false, status: 503 });
  expect(await screen.findByRole("alert")).toHaveTextContent("暫時無法取得資料");
  await user.click(screen.getByRole("button", { name: "重新載入" }));
  expect(await screen.findByRole("heading", { name: "Roger Federer" })).toBeInTheDocument();
});

it("does not search or display unnamed players before a name is entered", () => {
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);mount("/players");
  expect(screen.getByText("請輸入球員姓名後搜尋。")).toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
