export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/tennis/api${path}`, { credentials: "same-origin", signal, headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(String(response.status));
  const body = await response.json();
  if (path.includes("/ranking-chart") && !Array.isArray(body?.items)) throw new Error("Invalid chart response");
  return body as T;
}
export async function mutate<T>(path: string, method: string, body?: unknown, form = false): Promise<T> {
  const csrf = await get<{ token: string; headerName: string }>("/auth/csrf");
  const response = await fetch(`/tennis/api${path}`, {
    method, credentials: "same-origin",
    headers: { [csrf.headerName]: csrf.token, "Content-Type": form ? "application/x-www-form-urlencoded" : "application/json" },
    body: body === undefined ? undefined : form ? new URLSearchParams(body as Record<string, string>).toString() : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(String(response.status));
  return response.status === 204 || response.headers.get("content-length") === "0" ? undefined as T : await response.text().then(text => text ? JSON.parse(text) as T : undefined as T);
}
