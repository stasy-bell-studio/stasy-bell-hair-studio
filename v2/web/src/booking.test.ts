import { describe, expect, it } from "vitest";
import { API_URL, requestApi } from "./booking";

describe("public booking safety", () => {
  it("keeps online booking disabled without a configured HTTPS API", () => {
    expect(API_URL).toBe("");
  });

  it("explains that booking is not connected without making a request", async () => {
    await expect(requestApi("/public/services"))
      .rejects.toThrow("Система онлайн-записи ещё не подключена.");
  });
});
