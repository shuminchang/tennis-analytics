import { expect, it, vi } from "vitest";
import { mutate } from "./api";
it("gets a fresh CSRF token and sends a same-origin mutation", async () => {
  const fetch=vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({token:"fixture-token",headerName:"X-CSRF-TOKEN"}))).mockResolvedValueOnce(new Response(JSON.stringify({code:"OK"})));
  vi.stubGlobal("fetch",fetch);await mutate("/auth/login","POST",{username:"fixture",password:"fixture-test-password"},true);
  expect(fetch.mock.calls[0][0]).toBe("/tennis/api/auth/csrf");
  expect(fetch.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("fixture-token");expect(fetch.mock.calls[1][1].credentials).toBe("same-origin");
});
it("does not treat rejected writes as success", async () => {
  vi.stubGlobal("fetch",vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({token:"fixture-token",headerName:"X-CSRF-TOKEN"}))).mockResolvedValueOnce(new Response("",{status:403})));
  await expect(mutate("/auth/logout","POST")).rejects.toThrow("403");
});
