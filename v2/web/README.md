# Stasy Bell web 2.0 — private build, NOT deployed

React + TypeScript site in `v2/web`, built independently via `vite.config.ts`. The production GitHub Pages root remains unchanged. Do not merge or deploy until business acceptance.

Prices are copied verbatim from the live `src/components/ServicesSection.tsx`; optional +2 400 ₽ is marked as an add-on, not a standalone procedure. Actual API service durations/prices are **never seeded or invented**. Verified address and manual contact links are taken from the current site's contact components.

The booking form calls the backend for actual configured services, prices, durations and slots, requests a 45-minute hold, then instructs Telegram/VK confirmation with a 15-minute code. It never claims a pending hold is a confirmed appointment. The site does not accept payments. When API is not connected, it shows direct human contact links instead of a fake booking.

The API environment setting is `VITE_API_URL` (absolute HTTPS only), otherwise public booking stays disabled. No API credentials are bundled. Build is `npm ci` at the repository root, then `npx vite build --config v2/web/vite.config.ts`; catalog and booking safety tests use root Vitest/TypeScript, while Playwright checks responsive widths and a synthetic messenger-confirmation flow. Playwright screenshots are CI artifacts only.

`noindex,nofollow` is deliberately present until acceptance. Production launch must have an approved personal-data policy, secure hosting in Russia, working webhook integrations and final SEO/analytics review. No deploy workflow added.
