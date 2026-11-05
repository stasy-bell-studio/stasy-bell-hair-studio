export interface Service {
  id: string;
  name: string;
  variant: string | null;
  price_kopecks: number | null;
  duration_minutes: number;
  buffer_minutes: number;
}

export interface Hold {
  id: string;
  status: "pending_channel";
  channel: "telegram" | "vk";
  hold_until: string;
  confirmation_token_expires_at: string;
  channel_confirmation_code: string;
  hold_secret: string;
  starts_at?: string;
  service_name?: string;
  service_variant?: string | null;
  channel_link?: string;
  channel_instruction?: string;
}

const raw = (import.meta.env.VITE_API_URL as string | undefined)?.trim() ?? "";
export const API_URL = /^https:\/\//i.test(raw) ? raw.replace(/\/$/, "") : "";

export async function requestApi<T>(path: string, init?: RequestInit): Promise<T> {
  if (!API_URL) throw new Error("Система онлайн-записи ещё не подключена.");
  let response: Response;
  try {
    response = await fetch(API_URL + path, {
      ...init,
      cache: "no-store",
      headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    });
  } catch {
    throw new Error("Система записи не отвечает. Проверьте соединение и повторите попытку.");
  }
  if (!response.ok) {
    if (response.status === 503) throw new Error("Онлайн-запись пока недоступна.");
    if (response.status === 410) throw new Error("Время брони или код подтверждения истекли.");
    if (response.status === 404) throw new Error("Заявка не найдена. Выберите свободное время заново.");
    if (response.status === 422) throw new Error("Данные записи устарели. Проверьте дату и время.");
    if (response.status === 409) throw new Error("Это время уже занято. Выберите другое.");
    if (response.status === 429) throw new Error("Слишком много запросов. Попробуйте позже.");
    throw new Error("Не удалось связаться с системой записи.");
  }
  return response.json() as Promise<T>;
}

export function formatMoscowNow(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Europe/Moscow", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date());
  const read = (kind: string) => parts.find((x) => x.type === kind)?.value ?? "";
  return `${read("year")}-${read("month")}-${read("day")}`;
}

export function clockMoscow(iso: string): string {
  return new Intl.DateTimeFormat("ru-RU", {
    timeZone: "Europe/Moscow", day: "2-digit", month: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(iso));
}

// The API accepts today through today + 29 days in the studio time zone.
export function lastBookableDay(today: string = formatMoscowNow()): string {
  const [year, month, day] = today.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + 29)).toISOString().slice(0, 10);
}

const HOLD_SESSION_KEY = "stasy-bell-v2-pending-hold";

export function savePendingHold(hold: Hold): void {
  try {
    if (typeof window !== "undefined") {
      window.sessionStorage.setItem(HOLD_SESSION_KEY, JSON.stringify(hold));
    }
  } catch {
    // Private browsing may disable session storage; booking still functions.
  }
}

export function clearPendingHold(): void {
  try {
    if (typeof window !== "undefined") window.sessionStorage.removeItem(HOLD_SESSION_KEY);
  } catch {
    // Ignore restricted browser storage.
  }
}

export function readPendingHold(): Hold | null {
  try {
    if (typeof window === "undefined") return null;
    const raw = window.sessionStorage.getItem(HOLD_SESSION_KEY);
    if (!raw) return null;
    const value: Partial<Hold> = JSON.parse(raw);
    if (value.status !== "pending_channel" || typeof value.id !== "string" ||
        typeof value.hold_secret !== "string" || typeof value.hold_until !== "string" ||
        typeof value.channel_confirmation_code !== "string" ||
        typeof value.confirmation_token_expires_at !== "string" ||
        (value.channel !== "telegram" && value.channel !== "vk") ||
        !Number.isFinite(Date.parse(value.hold_until)) ||
        Date.parse(value.hold_until) <= Date.now()) {
      clearPendingHold();
      return null;
    }
    return value as Hold;
  } catch {
    clearPendingHold();
    return null;
  }
}

// Date-only arithmetic in UTC preserves the studio calendar across DST on client devices.
export function bookableDays(today: string = formatMoscowNow()): string[] {
  const [year, month, day] = today.split("-").map(Number);
  return Array.from({ length: 30 }, (_, offset) =>
    new Date(Date.UTC(year, month - 1, day + offset)).toISOString().slice(0, 10),
  );
}
