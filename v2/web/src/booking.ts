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
  channel_link?: string;
  channel_instruction?: string;
}

const raw = (import.meta.env.VITE_API_URL as string | undefined)?.trim() ?? "";
export const API_URL = /^https:\/\//i.test(raw) ? raw.replace(/\/$/, "") : "";

export async function requestApi<T>(path: string, init?: RequestInit): Promise<T> {
  if (!API_URL) throw new Error("Система онлайн-записи ещё не подключена.");
  const response = await fetch(API_URL + path, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    if (response.status === 503) throw new Error("Онлайн-запись пока недоступна.");
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
