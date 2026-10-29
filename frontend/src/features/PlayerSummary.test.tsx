import { expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "../i18n";
import PlayerSummary from "./PlayerSummary";
it("shows missing rate for zero eligible observations", async () => {
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:true,json:async()=>({ruleVersion:"stats-v1",displayed:4,played:0,wins:0,losses:0,winRate:null})}));
  render(<QueryClientProvider client={new QueryClient()}><PlayerSummary tour="ATP" id={1}/></QueryClientProvider>);
  expect(await screen.findByText("—")).toBeInTheDocument();
});
