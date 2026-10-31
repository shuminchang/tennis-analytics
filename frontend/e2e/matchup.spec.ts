import {expect,test} from "@playwright/test";
test("name-selected matchup uses source-compatible baseline, preserves dates and declines insufficient history",async({page})=>{
 test.skip(process.env.E2E_MOCK_API==="1","Requires isolated real model and database");
 await page.goto("/tennis/account");await page.getByLabel("帳號",{exact:true}).fill("fixtureadvanced");await page.getByLabel("密碼",{exact:true}).fill("fixture-password-123");await page.getByRole("button",{name:"登入",exact:true}).click();await expect(page.getByText("fixtureadvanced · ADVANCED",{exact:true})).toBeVisible();
 await page.goto("/tennis/matchup");
 for(const [label,query,name] of [["球員 1 姓名","Roger","Roger Federer"],["球員 2 姓名","Rafael","Rafael Nadal"]]) {
  await page.getByRole("combobox",{name:label,exact:true}).fill(query);await page.getByRole("option",{name:new RegExp(name)}).click();
 }
 await page.getByRole("button",{name:"計算相遇勝率",exact:true}).click();await expect(page.getByRole("meter")).toHaveCount(2);
 await expect(page.getByText(/本次基準機率尚未經校準/)).toBeVisible();await expect(page.getByRole("table").first()).toContainText("Roger Federer");
 const url=new URL(page.url());const response=await page.request.get(`/tennis/api/v1/matchup-prediction?${url.searchParams}`);expect(response.ok()).toBeTruthy();const data=await response.json();expect(data.prediction.firstProbability+data.prediction.secondProbability).toBeCloseTo(1,12);expect(data.first.overall.matches).toBe(25);
 const swapped=new URLSearchParams(url.searchParams);swapped.set("first","3");swapped.set("second","1");const reverse=await(await page.request.get(`/tennis/api/v1/matchup-prediction?${swapped}`)).json();expect(reverse.prediction.firstProbability).toBeCloseTo(data.prediction.secondProbability,12);
 await page.reload();await expect(page.getByRole("combobox",{name:"球員 1 姓名",exact:true})).toHaveValue("Roger Federer");await expect(page.getByRole("meter")).toHaveCount(2);
 await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await page.screenshot({path:"test-results/matchup-mobile.png",fullPage:true});
 await page.getByLabel("假設相遇日期",{exact:true}).fill("2024-01-22");await page.getByRole("button",{name:"計算相遇勝率",exact:true}).click();await expect(page.getByText(/近期資料不足：/)).toBeVisible();await expect(page.getByRole("meter")).toHaveCount(0);
});
