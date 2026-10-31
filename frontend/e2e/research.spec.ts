import { expect, test, type Page } from "@playwright/test";
import fs from "node:fs/promises";
const study = "/tennis/visualizations/research?tour=ATP&fromYear=2024&toYear=2024&surface=ALL";
async function login(page: Page, user: string) {
  await page.goto("/tennis/account");
  await page.getByLabel("帳號", { exact: true }).fill(user);
  await page.getByLabel("密碼", { exact: true }).fill("fixture-password-123");
  await page.getByRole("button", { name: "登入", exact: true }).click();
  await expect(page.getByText(`${user} · ${user === "fixturemember" ? "MEMBER" : "ADVANCED"}`, { exact: true })).toBeVisible();
}
test.beforeEach(() => test.skip(process.env.E2E_MOCK_API === "1", "Requires isolated real API"));
test("age denominators, filters, gaps and bilingual mobile table", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", error => errors.push(error.message));
  await page.goto(study);
  await expect(page.getByText("球員－比賽觀測 88 · 有效 88 · 排除 0", { exact: true })).toBeVisible();
  const chart = page.getByRole("img", { name: /年齡/ }); await expect(chart).toHaveAttribute("aria-busy", "false");
  await expect(page.getByRole("table")).toContainText("[37, 38)");
  await expect(page.getByRole("table")).toContainText("[42, 43)");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/research-age-mobile.png", fullPage: true });
  await page.getByRole("combobox", { name: "場地", exact: true }).selectOption("Grass");
  await page.getByRole("button", { name: "套用研究條件" }).click();
  await expect(page.getByText("此時間窗沒有有效觀測。", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "切換語言" }).click();
  await expect(page.getByText("No valid observations in this window.", { exact: true })).toBeVisible();
  await page.goto(study + "&fromYear=1900");
  await expect(page.getByRole("alert")).toContainText("Invalid research filters");
  expect(errors).toEqual([]);
});
test("serve profiles expose weighted values, full-window heatmap and CSV provenance", async ({ page }) => {
  const errors: string[] = [];page.on("pageerror", error => errors.push(error.message));page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await page.goto(study + "&view=profiles&x=match_win_rate&y=ace_rate&limit=1");
  await expect(page.getByText(/球員－賽季－場地觀測 4/)).toBeVisible();
  await expect(page.getByRole("img")).toHaveCount(2);
  for (const plot of await page.getByRole("img").all()) await expect(plot.locator("canvas").first()).toBeVisible();
  await expect(page.getByRole("img").first()).toHaveAttribute("aria-busy", "false");
  const plot = (await page.locator(".research-observation-plot .explorer-canvas").boundingBox())!;
  expect(plot.height).toBeGreaterThanOrEqual(420);expect(plot.width/plot.height).toBeLessThan(2);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: /CSV/ }).click();
  const file = await download; const csv = await fs.readFile((await file.path())!, "utf8");
  expect(csv).toContain("CC BY-NC-SA 4.0");expect(csv).toContain("research-v1");expect(csv).toContain("1440");
  await page.screenshot({ path: "test-results/research-profiles-desktop.png", fullPage: true });
  expect(errors).toEqual([]);
});
test("guest and member cannot load advanced workbench", async ({ page }) => {
  await page.goto(study + "&view=workbench");
  await expect(page.getByRole("link", { name: "需進階或管理員權限" })).toBeVisible();
  await expect(page.locator("canvas")).toHaveCount(0);
  await login(page, "fixturemember");await page.goto(study + "&view=workbench");
  await expect(page.getByRole("link", { name: "需進階或管理員權限" })).toBeVisible();
  const denied = await page.request.get("/tennis/api/v1/visualizations/correlations?tour=ATP&fromYear=2024&toYear=2024");
  expect(denied.status()).toBe(403);
});
test("advanced pairwise N, constant blanks, real hexagons and PNG exports", async ({ page }) => {
  const errors: string[] = [];page.on("pageerror", error => errors.push(error.message));page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await login(page, "fixtureadvanced");await page.goto(study + "&view=workbench&x=match_win_rate&y=ace_rate");
  await expect(page.getByRole("heading", { name: "探索性統計工作台" })).toBeVisible();
  await expect(page.getByRole("img")).toHaveCount(3);
  for (const plot of await page.getByRole("img").all()) await expect(plot.locator("canvas").first()).toBeVisible();
  for (const plot of await page.getByRole("img").all()) await expect(plot).toHaveAttribute("aria-busy", "false");
  const coloredCells = await page.getByRole("img", { name: /相關矩陣/ }).evaluate(root => {
    let count = 0;
    for (const canvas of root.querySelectorAll("canvas")) {
      const ctx = canvas.getContext("2d")!;const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
      const sx=canvas.width/root.clientWidth,sy=canvas.height/root.clientHeight;
      for(let y=Math.ceil(45*sy);y<(root.clientHeight-160)*sy;y++) for(let x=Math.ceil(55*sx);x<(root.clientWidth-20)*sx;x++) {
        const i=(y*canvas.width+x)*4;if(pixels[i+1]>pixels[i]+10&&pixels[i]<150&&pixels[i+3]>150) count++;
      }
    }
    return count;
  });
  expect(coloredCells).toBeGreaterThan(20);
  await page.getByText("配對統計資料表", { exact: true }).click();
  await expect(page.getByRole("table").first()).toContainText("4（低樣本）");
  await expect(page.getByRole("table").first()).toContainText("—");
  await page.getByRole("combobox", { name: "相關方法", exact: true }).selectOption("pearson");
  await page.getByRole("combobox", { name: "箱型圖指標", exact: true }).selectOption("ace_rate");
  await expect(page.getByRole("button", { name: "下載矩陣 PNG" })).toBeEnabled();
  const download = page.waitForEvent("download");await page.getByRole("button", { name: "下載矩陣 PNG" }).click();
  const image = await download;const png = await fs.readFile((await image.path())!);
  await image.saveAs("test-results/research-export.png");
  expect(png.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");expect(png.length).toBeGreaterThan(10000);
  const matrix=(await page.locator(".research-matrix-plot .explorer-canvas").boundingBox())!;expect(matrix.height).toBeGreaterThanOrEqual(480);
  const hex = (await page.locator(".research-hex .explorer-canvas").boundingBox())!;
  expect(hex.height).toBeGreaterThanOrEqual(480);expect(hex.width/hex.height).toBeLessThan(2);
  await page.screenshot({ path: "test-results/research-workbench-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: "test-results/research-workbench-mobile.png", fullPage: true });
  expect(errors).toEqual([]);
});
test("research source mismatch and server error are recoverable", async ({ page }) => {
  await page.route("**/age-performance?**", route => route.fulfill({ status: 503, contentType: "application/json", body: '{}' }));
  await page.goto(study);await expect(page.getByRole("alert")).toContainText("研究來源暫時不可用");
  await page.unroute("**/age-performance?**");await page.getByRole("button", { name: "重試", exact: true }).click();
  await expect(page.getByRole("img", { name: /年齡/ })).toBeVisible();
  await page.route("**/age-performance?**", async route => {const response=await route.fetch();const body=await response.json();body.context.source.qualityRevision="changed";await route.fulfill({response,json:body});});
  await page.reload();await expect(page.getByRole("alert")).toContainText("已變更");await expect(page.locator("canvas")).toHaveCount(0);
});
