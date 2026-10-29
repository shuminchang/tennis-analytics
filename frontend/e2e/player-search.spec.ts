import { test, expect } from "@playwright/test";
import { mockPlayers } from "./mockPlayers";
test.beforeEach(async ({ page }) => {
  if (process.env.E2E_MOCK_API === "1") await mockPlayers(page);
});
test("search, tour, pagination, language, refresh and mobile layout", async ({
  page,
}) => {
  await page.goto("/tennis/players");
  await expect(page.getByRole("table")).toBeVisible();
  await expect(page.getByRole("row")).toHaveCount(21);
  await page.getByRole("link", { name: "下一頁" }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.getByRole("table")).toBeVisible();
  await page.getByLabel("球員姓名").fill("Federer");
  await page.getByRole("button", { name: /搜尋球員/ }).click();
  await expect(page.getByText("Roger Federer", { exact: true })).toBeVisible();
  await expect(page.getByRole("row")).toHaveCount(2);
  await page.getByRole("combobox", { name: "巡迴", exact: true }).selectOption("WTA");
  await page.getByRole("button", { name: /搜尋球員/ }).click();
  await expect(page.getByText("沒有找到符合條件的球員")).toBeVisible();
  await page.getByLabel("球員姓名").fill("Serena");
  await page.getByRole("button", { name: /搜尋球員/ }).click();
  await expect(
    page.getByText("Serena Williams", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "切換語言" }).click();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: /Search results/ }),
  ).toBeVisible();
  await expect(
    page.getByText("Serena Williams", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/player-search-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: /Find players/ }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/player-search-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("API failure offers retry and a delayed response shows loading", async ({
  page,
}) => {
  await page.route("**/tennis/api/v1/players?**", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/json",
      body: '{"code":"SEARCH_UNAVAILABLE"}',
    }),
  );
  await page.goto("/tennis/players");
  await expect(page.getByRole("alert")).toBeVisible();
  await page.unroute("**/tennis/api/v1/players?**");
  if (process.env.E2E_MOCK_API === "1") await mockPlayers(page);
  await page.route("**/tennis/api/v1/players?**", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 700));
    await route.fallback();
  });
  await page.getByRole("button", { name: "重新載入" }).click();
  await expect(page.getByRole("status")).toBeVisible();
  await expect(page.getByRole("table")).toBeVisible();
});

test("player details support refresh, returning to filters and mobile layout", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Detail acceptance uses the real API");
  await page.goto("/tennis/players?q=Roger&tour=ATP&size=20");
  await page.getByRole("link", { name: "Roger Federer" }).click();
  await expect(page).toHaveURL(/\/players\/ATP\/1\?/);
  await expect(page.getByRole("heading", { name: "Roger Federer" })).toBeVisible();
  await page.reload();
  await expect(page.getByText("SUI", { exact: true })).toBeVisible();
  await page.screenshot({ path: "test-results/player-detail-desktop.png", fullPage: true, animations: "disabled" });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Roger Federer" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/player-detail-mobile.png", fullPage: true, animations: "disabled" });
  await page.getByRole("link", { name: /返回搜尋結果/ }).click();
  await expect(page.getByLabel("球員姓名")).toHaveValue("Roger");
  await expect(page.getByRole("combobox", { name: "巡迴", exact: true })).toHaveValue("ATP");
  await page.goto("/tennis/players/WTA/1");
  await expect(page.getByRole("heading", { name: "Serena Williams" })).toBeVisible();
  await page.goto("/tennis/players/ATP/2");
  await expect(page.getByRole("heading", { name: "找不到這位球員" })).toBeVisible();
});

test("ranking history uses real imported data and preserves paging on refresh", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Ranking acceptance uses the real API");
  await page.goto("/tennis/players/ATP/1?q=Roger&tour=ATP");
  const history = page.getByRole("region", { name: "排名歷史", exact: true }).filter({ has: page.getByRole("heading", { name: "排名歷史" }) });
  await expect(history.getByRole("table")).toBeVisible();
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(history.getByRole("cell", { name: "0", exact: true })).toBeVisible();
  await history.getByRole("button", { name: "下一頁" }).click();
  await expect(page).toHaveURL(/rankingPage=1/);
  await page.reload();
  await expect(history.getByRole("row")).toHaveCount(6);
  await page.getByRole("button", { name: "切換語言" }).click();
  await expect(page.getByRole("heading", { name: "Ranking history" })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/player-ranking-mobile.png", fullPage: true, animations: "disabled" });
  await page.getByRole("link", { name: /Back to search results/ }).click();
  await expect(page.getByLabel("Player name")).toHaveValue("Roger");
  await expect(page).not.toHaveURL(/rankingPage/);
});

test("match history filters years, refreshes pages and opens an opponent", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Match acceptance uses the real API");
  await page.goto("/tennis/players/ATP/1?q=Roger&tour=ATP");
  const history = page.getByRole("region", { name: "比賽紀錄", exact: true });
  await expect(history.getByRole("table")).toBeVisible();
  await history.getByRole("spinbutton", { name: "賽事年份" }).fill("2024");
  await history.getByRole("button", { name: "篩選比賽" }).click();
  await expect(history.getByRole("row")).toHaveCount(21);
  await expect(history.getByRole("cell", { name: /不戰晉級/ })).toBeVisible();
  await page.screenshot({ path: "test-results/player-matches-desktop.png", fullPage: true, animations: "disabled" });
  await history.getByRole("button", { name: "下一頁" }).click();
  await page.reload();
  await expect(history.getByRole("row")).toHaveCount(6);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/player-matches-mobile.png", fullPage: true, animations: "disabled" });
  await history.getByRole("link", { name: "Rafael Nadal" }).first().click();
  await expect(page.getByRole("heading", { name: "Rafael Nadal" })).toBeVisible();
  await page.getByRole("link", { name: /返回搜尋結果/ }).click();
  await expect(page.getByLabel("球員姓名")).toHaveValue("Roger");
  await expect(page).not.toHaveURL(/matchPage|matchYear/);
});

test("public analytics browse tournaments, rankings and player summaries", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Requires real feature API");
  await page.goto("/tennis/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("沿著紀錄");
  await page.goto("/tennis/tournaments?tour=ATP&year=2023");
  await page.getByRole("link", { name: "Fixture 2023", exact: true }).click();
  await expect(page.getByRole("table")).toBeVisible();
  await page.goto("/tennis/rankings?tour=ATP&date=2024-01-25");
  await expect(page.getByRole("link", { name: "Roger Federer" })).toBeVisible();
  await page.getByRole("link", { name: "Roger Federer" }).click();
  await expect(page.getByRole("heading", { name: "來源結果摘要" })).toBeVisible();
  await expect(page.getByRole("img", { name: "名次" })).toBeVisible();
});

test("member login, CSRF-backed favorites and comparison work across reloads", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Requires session authentication");
  await page.goto("/tennis/account");
  await page.getByLabel("帳號", { exact: true }).fill("fixturemember");
  await page.getByLabel("密碼", { exact: true }).fill("fixture-password-123");
  await page.getByRole("button", { name: "登入", exact: true }).click();
  await expect(page.getByText("fixturemember · MEMBER", { exact: true })).toBeVisible();
  await page.goto("/tennis/players/ATP/1");
  await page.getByRole("button", { name: "收藏球員" }).click();
  await expect(page.getByText("已收藏。", { exact: true })).toBeVisible();
  await page.goto("/tennis/favorites");
  await expect(page.getByRole("link", { name: /Roger Federer/ })).toBeVisible();
  await page.goto("/tennis/compare?tour=ATP&ids=1,3");
  await expect(page.getByRole("table")).toBeVisible();
  await page.goto("/tennis/h2h?tour=ATP&first=1&second=3");
  await expect(page.getByRole("link", { name: "需進階或管理員權限" })).toBeVisible();
  await page.goto("/tennis/account");
  await page.getByRole("button", { name: "登出", exact: true }).click();
  await expect(page.getByRole("button", { name: "登入", exact: true })).toBeVisible();
});

test("advanced users can inspect H2H and precomputed Elo", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Requires session authentication");
  await page.goto("/tennis/account");
  await page.getByLabel("帳號", { exact: true }).fill("fixtureadvanced");
  await page.getByLabel("密碼", { exact: true }).fill("fixture-password-123");
  await page.getByRole("button", { name: "登入", exact: true }).click();
  await expect(page.getByText("fixtureadvanced · ADVANCED", { exact: true })).toBeVisible();
  await page.goto("/tennis/h2h?tour=ATP&first=1&second=3");
  await expect(page.getByText(/95% Wilson:/)).toBeVisible();
  await page.goto("/tennis/players/ATP/1");
  await expect(page.getByRole("img", { name: "Elo · OVERALL" })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/pre-release-mobile.png", fullPage: true, animations: "disabled" });
});

test("administrator can provision accounts and operate bounded analytics jobs", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Requires session authentication");
  await page.goto("/tennis/account");
  await page.getByLabel("帳號", { exact: true }).fill("fixtureadmin");
  await page.getByLabel("密碼", { exact: true }).fill("fixture-password-123");
  await page.getByRole("button", { name: "登入", exact: true }).click();
  await expect(page.getByRole("heading", { name: "帳號管理" })).toBeVisible();
  await page.getByLabel("新帳號", { exact: true }).fill("createdmember");
  await page.getByLabel("新密碼（至少 12 字元）", { exact: true }).fill("fixture-created-password-123");
  await page.getByRole("button", { name: "建立", exact: true }).click();
  await expect(page.getByText("已建立。", { exact: true })).toBeVisible();
  const account = page.getByRole("listitem").filter({ hasText: "createdmember" });
  await expect(account).toContainText("MEMBER");
  await account.getByRole("button", { name: "切換啟用" }).click();
  await expect(account).toContainText("停用");
  for (const name of ["清除快取", "刷新分析", "重建 Elo"]) {
    await page.getByRole("button", { name, exact: true }).click();
    await expect(page.getByText("已完成。", { exact: true })).toBeVisible();
  }
});
