import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import i18n from "../../i18n";
import ResearchPage from "./ResearchPage";
import { researchFilters } from "./researchModel";
vi.mock("./ResearchCharts", () => ({ default: () => <div>Age chart ready</div> }));
const source={playersAndRankings:{snapshotSha:"a".repeat(40)},matchRunId:3,readModel:"v_stat_matches-live"};
beforeEach(async()=>{ await i18n.changeLanguage("zh-TW"); });
function mount(query="") {
 vi.stubGlobal("fetch",vi.fn(async(path:string)=>({ok:true,json:async()=>path.includes("/auth/me")?{authenticated:true,roles:["MEMBER"]}:path.includes("metadata")?{source,latestMatchYear:2024}:{context:{source,tour:"ATP",fromYear:2024,toYear:2024,surface:"ALL"},sample:{population:6,valid:5,excluded:1,reasons:[{code:"AGE_QUALITY_BLOCKED",count:1}]},items:[{lowerAge:20,upperAge:21,surface:"ALL",matches:2,wins:1,winRate:.5,lower95:.094531,upper95:.905469,lowSample:true}],warnings:[]}})));
 render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={[`/visualizations/research${query}`]}><ResearchPage/></MemoryRouter></QueryClientProvider>);
}
it("shows role counts uncertainty and the age qualification caveat",async()=>{
 mount();expect(await screen.findByText("Age chart ready")).toBeVisible();expect(screen.getByText("球員－比賽觀測 6 · 有效 5 · 排除 1")).toBeVisible();expect(screen.getByText("9.5%–90.5%")).toBeVisible();expect(screen.getByText("低樣本")).toBeVisible();
 fireEvent.click(screen.getByText("研究來源與方法"));expect(screen.getByText(/CC BY-NC-SA/)).toBeVisible();
});
it("rejects oversized windows and preserves explicit five-year bins",()=>{
 expect(()=>researchFilters(new URLSearchParams("fromYear=2000&toYear=2024"))).toThrow();expect(()=>researchFilters(new URLSearchParams("fromYear=2024"))).toThrow();expect(researchFilters(new URLSearchParams("fromYear=2020&toYear=2024&binWidth=5"))).toMatchObject({fromYear:2020,toYear:2024,binWidth:5});
 mount("?fromYear=2000&toYear=2024");expect(screen.getByRole("alert")).toHaveTextContent("最多十年");expect(fetch).not.toHaveBeenCalled();
});

it("does not call the protected matrix API for a member",async()=>{
 mount("?view=workbench&fromYear=2024&toYear=2024");expect(await screen.findByRole("link",{name:"需進階或管理員權限"})).toBeVisible();expect(vi.mocked(fetch).mock.calls.some(([path])=>String(path).includes("/correlations"))).toBe(false);
});
