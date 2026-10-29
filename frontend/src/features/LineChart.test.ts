import { expect, it } from "vitest";
import { chartPath } from "./chartMath";
it("breaks the line at missing values", () => {
  const d=chartPath([{date:"2024-01-01",value:1},{date:"2024-01-08",value:null},{date:"2024-01-15",value:2}]);
  expect(d.match(/M/g)).toHaveLength(2);expect(d).not.toContain("NaN");
});
it("handles empty and constant values", () => { expect(chartPath([])).toBe("");expect(chartPath([{date:"2024-01-01",value:0}])).not.toContain("NaN"); });
