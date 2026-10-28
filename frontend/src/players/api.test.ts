import { describe, it, expect } from "vitest";
import { readSearch } from "./api";
describe("search URL contract", () => {
  it("defaults to a bounded first page", () => {
    expect(readSearch(new URLSearchParams())).toEqual({
      q: "",
      tour: "",
      page: 0,
      size: 20,
      valid: true,
    });
  });
  it("normalizes text and tour", () => {
    expect(readSearch(new URLSearchParams("q=+Roger+&tour=wta")).tour).toBe(
      "WTA",
    );
  });
  it.each([
    "page=-1",
    "size=0",
    "size=101",
    "page=oops",
    "tour=ITF",
    "page=10001",
  ])("rejects invalid parameters %s", (value) => {
    expect(readSearch(new URLSearchParams(value)).valid).toBe(false);
  });
});
