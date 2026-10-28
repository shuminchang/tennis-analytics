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
