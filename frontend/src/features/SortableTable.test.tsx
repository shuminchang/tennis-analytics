import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import SortableTable from "./SortableTable";
import "../i18n";
it("sorts numerically, reverses, keeps missing last and exposes aria-sort", async () => {
  render(<SortableTable><thead><tr><th>Name</th><th>Rate</th></tr></thead><tbody><tr><td>A</td><td data-sort-value={.1}>10%</td></tr><tr><td>B</td><td data-sort-value={null}>—</td></tr><tr><td>C</td><td data-sort-value={0}>0%</td></tr><tr><td>D</td><td data-sort-value={.02}>2%</td></tr><tr><td>E</td><td>Name unavailable</td></tr></tbody></SortableTable>);
  const user=userEvent.setup();const button=screen.getByRole("button",{name:/Rate/});
  await user.click(button);
  const names=()=>screen.getAllByRole("row").slice(1).map(row=>within(row).getAllByRole("cell")[0].textContent);
  expect(names()).toEqual(["C","D","A","B","E"]);expect(button.closest("th")).toHaveAttribute("aria-sort","ascending");
  await user.click(button);expect(names()).toEqual(["A","D","C","B","E"]);expect(button.closest("th")).toHaveAttribute("aria-sort","descending");
});
it("sorts the full loaded cohort before client pagination and explains page-only scope", async () => {
  render(<SortableTable page={0} pageSize={1} scope="page"><thead><tr><th>Name</th></tr></thead><tbody><tr><td>Zebra</td></tr><tr><td>Alpha</td></tr></tbody></SortableTable>);
  expect(screen.queryByText("Alpha")).not.toBeInTheDocument();await userEvent.click(screen.getByRole("button",{name:/Name/}));
  expect(screen.getByRole("cell")).toHaveTextContent("Alpha");expect(screen.getByText(/其他頁仍維持來源順序/)).toBeInTheDocument();
});
