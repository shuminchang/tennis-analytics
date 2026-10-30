import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import ResearchProfiles from "./ResearchProfiles";
import { researchFilters, studyApiParams, type Profiles } from "./researchModel";
vi.mock("./ServeCharts",()=>({default:()=> <div>Serve charts ready</div>}));
it("keeps low-sample raw ratios denominators and omitted groups explicit",async()=>{
 const m={value:5/60,observations:3,numerator:5,denominator:60,qualified:false};
 const data={context:{tour:"ATP",fromYear:2024,toYear:2024,surface:"ALL",source:{playersAndRankings:{snapshotSha:"a".repeat(40)},matchRunId:3,readModel:"v_stat_matches-live"}},x:"height_cm",y:"ace_rate",sample:{population:5,inspected:1,valid:0,excluded:1,omitted:4,reasons:[{code:"ace_rate:INSUFFICIENT_SAMPLE",count:1}]},items:[{tour:"ATP",playerId:1,displayName:"Fixture A",season:2024,surface:"Hard",hand:"R",matches:3,metrics:{height_cm:{...m,value:180,qualified:true},ace_rate:m}}],points:[],surfaces:[],hands:[]} as unknown as Profiles;
 render(<MemoryRouter><ResearchProfiles data={data} en={false}/></MemoryRouter>);
 expect(await screen.findByText("Serve charts ready")).toBeVisible();expect(screen.getByText(/上限省略 4/)).toBeVisible();
 fireEvent.click(screen.getByText("賽季場地資料與分母"));expect(screen.getByText(/8.3%/)).toBeVisible();expect(screen.getByText("n=3 · 5/60")).toBeVisible();expect(screen.getByRole("link",{name:"Fixture A"})).toHaveAttribute("href","/players/ATP/1");
});
it("serializes profile API fields without leaking inactive age controls",()=>{
 const r=researchFilters(new URLSearchParams("view=profiles&tour=ATP&fromYear=2020&toYear=2024&binWidth=5&x=height_cm&y=ace_rate&limit=50"));
 const params=studyApiParams(r);expect(params.get("limit")).toBe("50");expect(params.has("binWidth")).toBe(false);expect(params.has("view")).toBe(false);expect(()=>researchFilters(new URLSearchParams("view=profiles&x=ace_rate&y=ace_rate"))).toThrow();
});
