import {render,screen,waitFor} from "@testing-library/react";
import {QueryClient,QueryClientProvider} from "@tanstack/react-query";
import {MemoryRouter} from "react-router-dom";
import {beforeEach,expect,it,vi} from "vitest";
import i18n from "../i18n";
import MatchupPrediction from "./MatchupPrediction";
vi.mock("../players/PlayerPicker",()=>({default:({label}:{label:string})=><label>{label}<input/></label>}));
const metadata={tour:"ATP",sourceLastDate:"2026-06-08",suggestedDate:"2026-06-30",maxDate:"2027-06-09",embargoDays:21,snapshot:"a".repeat(40),version:"matchup-v1"};
const rating={value:1900,matches:40,date:"2026-05-01"};
const result={available:true,tour:"ATP",asOf:"2026-06-01",historyBefore:"2026-05-11",sourceLastDate:"2026-06-08",surface:"Hard",bestOf:3,snapshot:"a".repeat(40),first:{tour:"ATP",playerId:123456,name:"Alpha Player",overall:rating,surface:rating,age:25,height:185,ageDate:"2026-05-01",heightDate:"2026-05-01"},second:{tour:"ATP",playerId:654321,name:"Beta Player",overall:rating,surface:rating,age:null,height:null},prediction:{method:"CALIBRATED_ELO",firstProbability:.6,secondProbability:.4,validation:{n:300,brier:.21,logLoss:.61}},comparisons:[],model:null,contributions:[],warnings:["AGE_OR_HEIGHT_MISSING_ELO_FALLBACK"]};
beforeEach(async()=>{await i18n.changeLanguage("zh-TW");});
function mount(role="ADVANCED",data:object=result,fail=false,query="?tour=ATP&first=123456&second=654321&asOf=2026-06-01&surface=Hard&bestOf=3") {
 vi.stubGlobal("fetch",vi.fn(async(path:string)=>({ok:!fail||path.includes("/auth/me"),status:503,json:async()=>path.includes("/auth/me")?{authenticated:true,roles:[role]}:path.includes("metadata")?metadata:data})));
 render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={[`/matchup${query}`]}><MatchupPrediction/></MemoryRouter></QueryClientProvider>);
}
it("protects model APIs for members",async()=>{
 mount("MEMBER");expect(await screen.findByText("需進階或管理員權限")).toBeVisible();await waitFor(()=>expect(fetch).toHaveBeenCalledTimes(1));
});
it("shows complementary probabilities and name links, without visible player identities",async()=>{
 mount();expect(await screen.findByText("60.0%")).toBeVisible();expect(screen.getByText("40.0%")).toBeVisible();expect(screen.getByText(/年齡或身高不足/)).toBeVisible();expect(screen.getByRole("link",{name:"Alpha Player"})).toHaveAttribute("href","/players/ATP/123456");expect(screen.getByRole("table")).not.toHaveTextContent("123456");expect(screen.getByText(/歷史資料嚴格早於/)).toHaveTextContent("2026-05-11");
});
it("does not invent a probability when history is insufficient",async()=>{
 mount("ADVANCED",{...result,available:false,prediction:null});expect(await screen.findByText(/近期資料不足：/)).toBeVisible();expect(screen.queryByRole("meter")).not.toBeInTheDocument();
});
it("reports unavailable source and offers retry",async()=>{
 mount("ADVANCED",result,true);expect(await screen.findByRole("alert")).toHaveTextContent("來源版本已變更");expect(screen.getByRole("button",{name:"重試"})).toBeVisible();
});
it("rejects duplicate filters before calling protected endpoints",async()=>{
 mount("ADVANCED",result,false,"?tour=ATP&tour=WTA");expect(await screen.findByRole("alert")).toHaveTextContent("對戰條件無效");expect(vi.mocked(fetch).mock.calls.some(([path])=>String(path).includes("matchup-prediction"))).toBe(false);
});
