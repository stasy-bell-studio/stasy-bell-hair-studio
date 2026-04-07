// URL из Google Apps Script → Развернуть → Веб-приложение
// После деплоя скрипта замени эту строку на реальный URL
const APPS_SCRIPT_URL = 'ВСТАВЬ_URL_APPS_SCRIPT_СЮДА';

export interface BookingData {
  name: string;
  phone: string;
  service: string;
  date: string;
  time: string;
  comment?: string;
}

export async function submitBooking(data: BookingData): Promise<void> {
  const response = await fetch(APPS_SCRIPT_URL, {
    method: 'POST',
    mode: 'no-cors', // Apps Script требует no-cors
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  // no-cors не даёт читать ответ, но запрос уходит
  // Ошибка бросается только если сеть недоступна
}

export function buildVkMessage(data: BookingData): string {
  const lines = [
    'Привет! Я записалась через сайт 💜',
    `Имя: ${data.name}`,
    `Телефон: ${data.phone}`,
    `Услуга: ${data.service}`,
    `Дата: ${data.date}`,
    `Время: ${data.time}`,
  ];
  if (data.comment) lines.push(`Комментарий: ${data.comment}`);
  return lines.join('\n');
}

export function openVkChat(message: string): void {
  const url = `https://vk.com/im?sel=stacybelll&msg=${encodeURIComponent(message)}`;
  window.open(url, '_blank');
}
