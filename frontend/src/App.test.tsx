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
function mount(url = "/players") {
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
  mount("/players?page=2");
  expect(await screen.findByText("Roger Federer")).toBeInTheDocument();
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
  mount("/players?page=999");
  expect(await screen.findByText("頁碼超出結果範圍。")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "回到第一頁" })).toHaveAttribute("href", "/players?page=0");
});
