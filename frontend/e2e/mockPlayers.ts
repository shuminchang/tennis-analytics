import type { Page } from "@playwright/test";
// Browser-only checks while Docker is unavailable. Real acceptance runs WITHOUT E2E_MOCK_API.
export async function mockPlayers(page: Page) {
  const players = [
    {
      tour: "ATP",
      playerId: 1,
      nameFirst: "Roger",
      nameLast: "Federer",
      hand: "R",
      dob: "1981-08-08",
      ioc: "SUI",
    },
    ...Array.from({ length: 20 }, (_, i) => ({
      tour: "ATP",
      playerId: i + 10,
      nameFirst: "Alex",
      nameLast: `Player${i + 10}`,
      hand: "R",
      dob: "1990-01-01",
      ioc: "USA",
    })),
    {
      tour: "WTA",
      playerId: 1,
      nameFirst: "Serena",
      nameLast: "Williams",
      hand: "R",
      dob: "1981-09-26",
      ioc: "USA",
    },
  ];
  await page.route("**/tennis/api/v1/players?**", (route) => {
    const params = new URL(route.request().url()).searchParams;
    const q = (params.get("q") ?? "").toLowerCase(),
      tour = params.get("tour");
    const pageNumber = Number(params.get("page") ?? 0),
      size = Number(params.get("size") ?? 20);
    const filtered = players.filter(
      (p) =>
        (!tour || p.tour === tour) &&
        `${p.nameFirst} ${p.nameLast}`.toLowerCase().includes(q),
    );
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        items: filtered.slice(pageNumber * size, (pageNumber + 1) * size),
        total: filtered.length,
        page: pageNumber,
        size,
      }),
    });
  });
}
