import { describe, expect, it } from "vitest";
import { isPrivacyReady } from "./legal";

const sample = {
  operator: "Синтетический оператор",
  address: "Тестовый адрес",
  contact: "privacy@example.invalid",
  retention: "Срок хранения для синтетической проверки",
};
describe("privacy publication gate", () => {
  it("requires explicit human approval, not merely an API", () => {
    expect(isPrivacyReady(sample, false)).toBe(false);
  });
  it("requires a complete public policy profile", () => {
    expect(isPrivacyReady({ ...sample, address: "" }, true)).toBe(false);
    expect(isPrivacyReady({ ...sample, retention: " " }, true)).toBe(false);
    expect(isPrivacyReady({ ...sample, operator: "TODO" }, true)).toBe(false);
  });
  it("allows a complete explicitly approved test profile", () => {
    expect(isPrivacyReady(sample, true)).toBe(true);
  });
});
