import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import PlayerPicker from "./PlayerPicker";
const player={tour:"ATP",playerId:103819,nameFirst:"Roger",nameLast:"Federer",ioc:"SUI",dob:"1981-08-08",hand:"R"};
function mount(initialId?:string) {return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><PlayerPicker name="first" label="球員姓名" tour="ATP" initialId={initialId} required en={false}/></QueryClientProvider>);}
beforeEach(()=>vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:true,json:async()=>({items:[player],total:1,page:0,size:10})})));
it("waits for a nonempty name, selects by keyboard, and invalidates the selection on editing",async()=>{
 const user=userEvent.setup();const {container}=mount();const input=screen.getByRole("combobox",{name:"球員姓名"});
 await user.click(input);expect(fetch).not.toHaveBeenCalled();await user.type(input,"Roger");
 await screen.findByRole("option",{name:/Roger Federer/});await user.keyboard("{Enter}");
 expect(input).toHaveValue("Roger Federer");expect(container.querySelector('input[type="hidden"]')).toHaveValue("103819");
 expect(screen.queryByRole("listbox")).not.toBeInTheDocument();expect(screen.queryByText(/103819/)).not.toBeInTheDocument();
 await user.type(input,"x");expect(container.querySelector('input[type="hidden"]')).toHaveValue("");
});
it("resolves bookmarked identity to a name without searching all players",async()=>{
 vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>player} as Response);const {container}=mount("103819");
 await waitFor(()=>expect(screen.getByRole("combobox")).toHaveValue("Roger Federer"));
 expect(String(vi.mocked(fetch).mock.calls[0][0])).toContain("/players/ATP/103819");
 expect(container.querySelector('input[type="hidden"]')).toHaveValue("103819");
});
