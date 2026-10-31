// Read-only browser acceptance against the owned budget Apache proxy.
import { chromium } from "../../frontend/node_modules/playwright/index.mjs";
import { expect } from "../../frontend/node_modules/@playwright/test/index.mjs";
import fs from "node:fs/promises";
const [base, output] = process.argv.slice(2);
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) throw Error("Local owned proxy required");
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
const errors = [];
page.on("pageerror", error => errors.push(error.message));
page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
const plots = [];
try {
  for (const view of ["age", "profiles"]) {
    await page.goto(`${base}/tennis/visualizations/research?tour=ATP&fromYear=2017&toYear=2026&surface=ALL&view=${view}&limit=1000`);
    await page.waitForSelector('.explorer-canvas[aria-busy="false"]', { timeout: 30000 });
    await page.evaluate(() => document.fonts.ready);
    const data = await page.locator("main").innerText();
    if (data.includes("研究來源暫時不可用")) throw Error("Research query failed");
    plots.push({ view, canvasCount: await page.locator("canvas").count(), sourceDisclosed: await page.getByText(/83733587353df8a41f2fd4f516147d5aa83f5a8d/).count() > 0 });
    await page.screenshot({ path: `${output}/${view}-proxy-desktop.png`, fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await page.screenshot({ path: `${output}/${view}-proxy-mobile.png`, fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
  }
  // New lazy matchup entry must also load under the unchanged Apache CSP.
  // Authenticated calculations are covered by the real-API E2E suite and budget benchmark.
  await page.goto(`${base}/tennis/matchup`);
  await expect(page.getByRole("heading",{name:"兩位球員相遇勝率預估"})).toBeVisible();
  await expect(page.getByRole("link",{name:"需進階或管理員權限"})).toBeVisible();
  await page.setViewportSize({width:390,height:844});
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
  await page.screenshot({path:`${output}/matchup-proxy-mobile.png`,fullPage:true});
  plots.push({view:"matchup",protected:true,source:"authenticated calculations separately verified"});
  if (errors.length) throw Error(JSON.stringify(errors));
  await fs.writeFile(`${output}/browser.json`, JSON.stringify({ status: "PASSED", errors, plots, security: "Apache CSP and self-hosted assets; public read-only research" }, null, 2));
} finally { await browser.close(); }
