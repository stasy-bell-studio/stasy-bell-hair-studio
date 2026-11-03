import { describe, expect, it } from "vitest";
import { priceGroups } from "./prices";

describe("Live site price catalog preservation", () => {
  it("retains exact prices for keratin variants", () => {
    expect(priceGroups[0].items.map((x) => x.price)).toEqual([
      "6 200 ₽", "6 500 ₽", "6 900 ₽", "7 200 ₽", "7 700 ₽",
    ]);
  });

  it("does not turn a plus-price add-on into a standalone service", () => {
    const addOn = priceGroups.flatMap((g) => g.items).find((x) => x.price === "+2 400 ₽");
    expect(addOn?.addOnOnly).toBe(true);
  });

  it("keeps additional treatment prices as on the live site", () => {
    expect(priceGroups[3].items.map((x) => x.price)).toEqual(["750 ₽", "5 500 ₽"]);
  });
});
