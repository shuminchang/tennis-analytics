import { expect, test } from "@playwright/test";
const path = "/tennis/visualizations?tour=ATP&rankingDate=2024-01-25&topN=100&x=height_cm&y=rank&color=hand&bins=10";
test.beforeEach(async () => { test.skip(process.env.E2E_MOCK_API === "1", "Requires real visualization API"); });
test("explorer renders real Canvas, tooltip, point navigation and shared filters", async ({ page }) => {
  const errors: string[] = [];page.on("pageerror", e => errors.push(e.message));
  await page.goto(path);
  const plot = page.getByRole("img", { name: /散布圖/ });await expect(plot).toBeVisible();
  await expect(page.locator("canvas")).toHaveCount(2);
  await page.evaluate(() => document.fonts.ready);await plot.scrollIntoViewIfNeeded();await expect(plot).toHaveAttribute("aria-busy", "false");
  // Locate the one synthetic green point in the plot area, without assuming the
  // chart library places a degenerate numeric domain at the visual midpoint.
  const point = await plot.evaluate(root => {
    const canvas = root.querySelector("canvas")!;const pixels = canvas.getContext("2d")!.getImageData(0, 0, canvas.width, canvas.height).data;
    const sx = canvas.width / root.clientWidth, sy = canvas.height / root.clientHeight;
    let xsum = 0, ysum = 0, count = 0;
    for (let y = Math.ceil(46 * sy); y < (root.clientHeight - 78) * sy; y++) for (let x = Math.ceil(54 * sx); x < (root.clientWidth - 18) * sx; x++) {
      const offset = (y * canvas.width + x) * 4;const [r, g, b, a] = pixels.slice(offset, offset + 4);
      if (g > r + 10 && g > b + 5 && r < 160 && a > 150) { xsum += x;ysum += y;count++; }
    }
    if (!count) throw Error("The synthetic scatter point was not rendered");
    return { x: xsum / count / sx, y: ysum / count / sy };
  });
  const box = (await plot.boundingBox())!;
  await page.mouse.move(box.x + point.x, box.y + point.y);
  await expect(page.locator(".explorer-hover").first()).toContainText("Roger Federer");
  await page.screenshot({ path: "test-results/explorer-desktop.png", fullPage: true, animations: "disabled" });
  await page.mouse.click(box.x + point.x, box.y + point.y);
  await expect(page).toHaveURL(/players\/ATP\/1/);
  await page.goBack();await expect(plot).toBeVisible();
  await page.getByRole("button", { name: "交換座標軸 ↔" }).click();
  await page.getByRole("button", { name: "套用條件 ↗" }).click();await expect(page).toHaveURL(/x=rank&y=height_cm/);
  await page.getByRole("button", { name: "複製此快照連結 ↗" }).click();const shared = await page.getByLabel("可分享的快照網址").inputValue();
  await page.goto(shared);await expect(page.getByLabel("X 軸")).toHaveValue("rank");await expect(plot).toBeVisible();
  await page.getByText(/檢視資料表/).click();const player = page.getByRole("link", { name: /Roger Federer.*#1/ });await player.focus();await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/players\/ATP\/1/);expect(errors).toEqual([]);
});
test("mobile filters, empty dates and bilingual table remain usable", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });await page.goto(path);
  await expect(page.getByRole("img", { name: /散布圖/ })).toBeVisible();
  await page.getByLabel("排名日期").fill("2024-02-01");await page.getByRole("button", { name: "套用條件 ↗" }).click();await expect(page.getByText("沒有有效觀測", { exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "巡迴", exact: true }).selectOption("WTA");await page.getByLabel("排名日期").fill("");await page.getByRole("button", { name: "套用條件 ↗" }).click();
  await expect(page.getByText("WTA / 2024-01-01", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "切換語言" }).click();await expect(page.getByRole("button", { name: "Explore data ↗" })).toBeVisible();
  await page.getByText(/Accessible data tables/).click();await expect(page.getByRole("link", { name: /Serena Williams/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/explorer-mobile.png", fullPage: true, animations: "disabled" });
});
test("API failure can be retried and mixed source responses are withheld", async ({ page }) => {
  await page.route("**/tennis/api/v1/visualizations/scatter?**", route => route.fulfill({ status: 503, contentType: "application/json", body: '{"code":"UNAVAILABLE"}' }));
  await page.goto(path);await expect(page.getByRole("alert")).toContainText("暫時無法取得探索資料");
  await page.unroute("**/tennis/api/v1/visualizations/scatter?**");await page.getByRole("button", { name: "重試" }).click();await expect(page.getByRole("img", { name: /散布圖/ })).toBeVisible();
  await page.route("**/tennis/api/v1/visualizations/histogram?**", async route => { const response = await route.fetch();const body = await response.json();body.context.source.qualityRevision = "different-snapshot";await route.fulfill({ response, json: body }); });
  await page.reload();await expect(page.getByRole("alert")).toContainText("來源已變更");await expect(page.locator("canvas")).toHaveCount(0);
  await page.unroute("**/tennis/api/v1/visualizations/histogram?**");await page.getByRole("button", { name: "重試" }).click();await expect(page.getByRole("img", { name: /散布圖/ })).toBeVisible();
});
