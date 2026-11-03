import { expect, test, type Page } from "@playwright/test";

const apiOrigin = "https://api.stasybell.test";

async function mockPublicApi(page: Page) {
  let bookingBody: Record<string, unknown> | undefined;

  await page.route(`${apiOrigin}/**`, async (route) => {
    const url = new URL(route.request().url());

    if (url.pathname === "/public/services") {
      await route.fulfill({
        json: {
          services: [{
            id: "synthetic-service",
            name: "Тестовая услуга",
            variant: "до плеч",
            price_kopecks: 123456,
            duration_minutes: 60,
            buffer_minutes: 15,
          }],
        },
      });
      return;
    }

    if (url.pathname === "/public/channels") {
      await route.fulfill({ json: { telegram: true, vk: false } });
      return;
    }

    if (url.pathname === "/public/availability") {
      const day = url.searchParams.get("day") ?? "2026-11-10";
      await route.fulfill({ json: { starts_at: [`${day}T07:00:00Z`] } });
      return;
    }

    if (url.pathname === "/public/holds" && route.request().method() === "POST") {
      bookingBody = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        json: {
          id: "synthetic-hold",
          status: "pending_channel",
          channel: "telegram",
          hold_until: "2026-11-10T07:45:00Z",
          confirmation_token_expires_at: "2026-11-10T07:15:00Z",
          channel_confirmation_code: "123456",
          hold_secret: "synthetic-test-secret",
          channel_link: "https://t.me/example",
          channel_instruction: "Синтетический тест подтверждения записи.",
        },
      });
      return;
    }

    if (url.pathname === "/public/holds/synthetic-hold/status") {
      await new Promise((resolve) => setTimeout(resolve, 1200));
      await route.fulfill({ json: { status: "confirmed" } });
      return;
    }

    await route.fulfill({ status: 404, json: { detail: "Unknown synthetic route" } });
  });

  return { getBookingBody: () => bookingBody };
}

test("Sage landing page stays within the approved responsive widths", async ({ page }, testInfo) => {
  await mockPublicApi(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Волосы, в которых");
  await expect(page.getByRole("heading", { name: "Меню красоты" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "До встречи в студии." })).toBeVisible();
  await expect(page.getByText("Оплата на сайте не принимается.")).toBeVisible();

  for (const width of [320, 375, 425, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.evaluate(() => document.fonts.ready);
    const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(documentWidth, `horizontal overflow at ${width}px`).toBeLessThanOrEqual(width);
    await page.screenshot({ path: testInfo.outputPath(`site-${width}.png`), fullPage: true });
  }
});

test("offline booking offers direct contacts without presenting a live form", async ({ page }) => {
  test.skip(Boolean(process.env.VITE_API_URL), "requires the default build without a booking API");
  await page.goto("/#booking");

  await expect(page.getByText("Онлайн-запись готовится к запуску.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Написать в Telegram", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Написать во VK", exact: true })).toBeVisible();
  await expect(page.getByLabel("Процедура")).toHaveCount(0);
  await expect(page.getByText("Оплата на сайте не принимается.")).toBeVisible();
});

test("booking uses a synthetic hold and waits for messenger confirmation without payment", async ({ page }) => {
  test.skip(!process.env.VITE_API_URL, "requires a configured synthetic API host");
  const api = await mockPublicApi(page);
  await page.goto("/#booking");

  await page.getByLabel("Процедура").selectOption("synthetic-service");
  const dateField = page.getByLabel("Дата");
  const minimumDate = await dateField.getAttribute("min");
  if (!minimumDate) throw new Error("Minimum booking date is missing");
  const [year, month, day] = minimumDate.split("-").map(Number);
  const bookingDay = new Date(Date.UTC(year, month - 1, day + 7)).toISOString().slice(0, 10);
  await dateField.fill(bookingDay);
  const timeField = page.getByLabel("Свободное время");
  await expect(timeField.getByRole("option", { name: /10:00/ })).toBeAttached();
  await timeField.selectOption(`${bookingDay}T07:00:00Z`);
  await page.getByLabel("Как к вам обращаться").fill("Тестовый гость");
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Зарезервировать время" }).click();

  await expect(page.getByRole("heading", { name: "Осталось подтвердить запись" })).toBeVisible();
  await expect(page.getByText("Синтетический тест подтверждения записи.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Запись подтверждена!" })).toBeVisible({ timeout: 5_000 });
  expect(api.getBookingBody()).toMatchObject({
    name: "Тестовый гость",
    service_id: "synthetic-service",
    channel: "telegram",
    personal_data_consent: true,
  });
  expect(api.getBookingBody()).not.toHaveProperty("payment");
  await expect(page.getByRole("button", { name: /оплатить|внести оплату/i })).toHaveCount(0);
});
