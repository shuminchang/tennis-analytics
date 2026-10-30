import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import ResearchWorkbench from "./ResearchWorkbench";
import { csvCell, csvContent } from "./export";
import type { Workbench } from "./researchModel";
vi.mock("./WorkbenchCharts",()=>({default:()=> <div>Workbench charts ready</div>}));
it("keeps undefined correlations distinct from zero and exposes pairwise N",async()=>{
 const data={context:{tour:"ATP",fromYear:2024,toYear:2024,surface:"ALL",source:{playersAndRankings:{snapshotSha:"a".repeat(40)},matchRunId:3}},population:2,inspected:2,omitted:0,x:"height_cm",y:"ace_rate",metrics:["height_cm","ace_rate"],correlations:[{x:"height_cm",y:"ace_rate",n:2,pearson:null,spearman:null,lowSample:true}],boxes:[],hexbin:{observations:0,cells:[]}} as unknown as Workbench;
 render(<MemoryRouter><ResearchWorkbench data={data} en={false}/></MemoryRouter>);expect(await screen.findByText("Workbench charts ready")).toBeVisible();fireEvent.click(screen.getByText("配對統計資料表"));expect(screen.getByText("2（低樣本）")).toBeVisible();expect(screen.getAllByText("—")).toHaveLength(2);
});
it("CSV protects formula cells while retaining numeric zero negatives quotes and provenance rows",()=>{
 expect(csvCell("=SUM(A1)")).toBe('"\'=SUM(A1)"');expect(csvCell(" @cmd")).toBe('"\' @cmd"');expect(csvCell("\tplain")).toBe('"\'\tplain"');expect(csvCell(-.5)).toBe('"-0.5"');expect(csvCell(0)).toBe('"0"');expect(csvCell(null)).toBe('""');expect(csvCell('a"b')).toBe('"a""b"');
 expect(csvContent([["snapshot","metric_version"],["abc","research-v1"]])).toBe('\uFEFF"snapshot","metric_version"\r\n"abc","research-v1"');
});
