import {render,screen} from "@testing-library/react";
import {MemoryRouter,Routes,Route} from "react-router-dom";
import {QueryClient,QueryClientProvider} from "@tanstack/react-query";
import {beforeEach,expect,it,vi} from "vitest";
import i18n from "../i18n";
import {TournamentDetail} from "./Tournaments";
function mount(item:object) {
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:true,json:async()=>({items:[item],total:1})}));
 render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={["/tournaments/ATP/event"]}><Routes><Route path="/tournaments/:tour/:id" element={<TournamentDetail/>}/></Routes></MemoryRouter></QueryClientProvider>);
}
beforeEach(async()=>{await i18n.changeLanguage("zh-TW");});
it("shows names from the joined result, retaining identity only in links",async()=>{
 mount({id:1,winner_id:1,loser_id:2,winner_name:"Roger Federer",loser_name:"Rafael Nadal",tourney_name:"Fixture Open",round:"F",score:"6-4 6-4"});
 expect(await screen.findByRole("link",{name:"Roger Federer"})).toHaveAttribute("href","/players/ATP/1");expect(screen.getByRole("link",{name:"Rafael Nadal"})).toBeInTheDocument();expect(screen.getByRole("table")).not.toHaveTextContent("#1");
});
it("reports old backend fields instead of claiming player names are absent",async()=>{
 mount({id:1,winner_id:1,loser_id:2,round:"F",score:"6-4 6-4"});
 expect(await screen.findByRole("alert")).toHaveTextContent("請重新啟動後端服務");expect(screen.queryByText("姓名未提供")).not.toBeInTheDocument();
});
