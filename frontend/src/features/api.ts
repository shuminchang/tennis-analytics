export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/tennis/api${path}`, { credentials: "same-origin", signal, headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(String(response.status));
  const body = await response.json();
  if (path.includes("/ranking-chart") && !Array.isArray(body?.items)) throw new Error("Invalid chart response");
  return body as T;
}
