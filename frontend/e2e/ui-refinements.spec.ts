import { expect, test } from "@playwright/test";
test("empty directory waits for a name, and tournament results use names with sortable columns", async ({ page }) => {
  test.skip(process.env.E2E_MOCK_API === "1", "Requires isolated real API");
  const searches: string[] = [];
  page.on("request", request => { if (request.url().includes("/tennis/api/v1/players?")) searches.push(request.url()); });
  await page.goto("/tennis/players");
  await expect(page.getByText("請輸入球員姓名後搜尋。")).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);expect(searches).toHaveLength(0);
  await page.goto("/tennis/tournaments/ATP/fixture-open");
  await expect(page.getByRole("heading", { name: "Fixture Open", exact: true })).toBeVisible();
  const table=page.getByRole("table");
  await expect(table).toContainText("Roger Federer");await expect(table).toContainText("Rafael Nadal");
  await expect(table).not.toContainText("#");
  const sort=page.getByRole("button", { name: /^來源勝方/ });await sort.click();
  await expect(sort.locator("..")).toHaveAttribute("aria-sort", "ascending");
  await expect(table.getByRole("link", { name: "Roger Federer" }).first()).toHaveAttribute("href", "/tennis/players/ATP/1");
  await page.screenshot({path:"test-results/tournament-names-sort.png",fullPage:true});
});

async function signIn(page: import("@playwright/test").Page) {
  await page.goto("/tennis/account");await page.getByLabel("帳號",{exact:true}).fill("fixtureadvanced");await page.getByLabel("密碼",{exact:true}).fill("fixture-password-123");await page.getByRole("button",{name:"登入",exact:true}).click();await expect(page.getByText("fixtureadvanced · ADVANCED",{exact:true})).toBeVisible();
}
async function selectPlayer(page: import("@playwright/test").Page,label:string,query:string,name:string) {
  await page.getByRole("combobox",{name:label,exact:true}).fill(query);
  await page.getByRole("option",{name:new RegExp(name)}).click();
  await expect(page.getByRole("combobox",{name:label,exact:true})).toHaveValue(name);
}
test("H2H selects names, preserves bookmarks, and clears selections on tour changes",async({page})=>{
  test.skip(process.env.E2E_MOCK_API === "1","Requires real session");await signIn(page);await page.goto("/tennis/h2h");
  await selectPlayer(page,"球員 1 姓名","Roger","Roger Federer");await selectPlayer(page,"球員 2 姓名","Rafael","Rafael Nadal");
  await page.getByRole("button",{name:"查詢",exact:true}).click();await expect(page).toHaveURL(/first=1&second=3/);await expect(page.getByText(/95% Wilson:/)).toBeVisible();
  await page.reload();await expect(page.getByRole("combobox",{name:"球員 1 姓名",exact:true})).toHaveValue("Roger Federer");
  await page.getByRole("combobox",{name:"巡迴",exact:true}).selectOption("WTA");await expect(page.getByRole("combobox",{name:"球員 1 姓名",exact:true})).toHaveValue("");
  await selectPlayer(page,"球員 1 姓名","Serena","Serena Williams");
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
  await page.screenshot({path:"test-results/h2h-name-picker-mobile.png",fullPage:true});
});
test("comparison accepts selected names and rejects repeated players",async({page})=>{
  test.skip(process.env.E2E_MOCK_API === "1","Requires real session");await signIn(page);await page.goto("/tennis/compare");
  await selectPlayer(page,"球員 1 姓名","Roger","Roger Federer");await selectPlayer(page,"球員 2 姓名","Roger","Roger Federer");
  await page.getByRole("button",{name:"比較",exact:true}).click();await expect(page.getByRole("alert")).toContainText("不同球員");
  await selectPlayer(page,"球員 2 姓名","Rafael","Rafael Nadal");await page.getByRole("button",{name:"比較",exact:true}).click();
  await expect(page).toHaveURL(/ids=1%2C3/);await expect(page.getByRole("table")).toContainText("Roger");await expect(page.getByRole("table")).toContainText("Rafael");
  await page.screenshot({path:"test-results/comparison-name-picker-desktop.png",fullPage:true});
});
