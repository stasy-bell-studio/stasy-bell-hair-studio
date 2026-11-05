import { describe, expect, it } from "vitest";
import { API_URL, lastBookableDay, requestApi } from "./booking";

describe("public booking safety", () => {
  it("caps availability to the 30-day studio window, including month boundaries", () => {
    expect(lastBookableDay("2026-11-05")).toBe("2026-12-04");
    expect(lastBookableDay("2026-12-20")).toBe("2027-01-18");
  });

  it("keeps online booking disabled without a configured HTTPS API", () => {
    expect(API_URL).toBe("");
  });

  it("explains that booking is not connected without making a request", async () => {
    await expect(requestApi("/public/services"))
      .rejects.toThrow("Система онлайн-записи ещё не подключена.");
  });
});
