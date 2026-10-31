import {render,screen} from "@testing-library/react";
import {expect,it} from "vitest";
import LineChart from "./LineChart";
it("shows integer Elo ticks and keeps a separate axis gutter",()=>{
 render(<LineChart label="Elo" data={[{date:"2024-01-01",value:1500.1234567},{date:"2024-02-01",value:1510.9876543}]}/>);
 const chart=screen.getByRole("img",{name:"Elo"});
 expect(chart.querySelectorAll("text")[0]).toHaveTextContent("1511");expect(chart.querySelectorAll("text")[1]).toHaveTextContent("1500");
 expect(chart.innerHTML).not.toContain("1500.1234567");expect(chart.querySelector("path")).toHaveAttribute("d",expect.stringContaining("M60.00"));
});
