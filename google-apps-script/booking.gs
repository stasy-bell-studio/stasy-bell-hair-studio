// ============================================================
// Google Apps Script для обработки записей с сайта Стэйси Белл
// ============================================================
// Инструкция по установке:
// 1. Открой таблицу: https://docs.google.com/spreadsheets/d/1AO8GzNd0HUDrkaxvmBupviofh_QAgjnp4MeOJ9OugkU
// 2. Меню → Расширения → Apps Script
// 3. Вставь весь этот код, нажми Сохранить (Ctrl+S)
// 4. Нажми "Развернуть" → "Новое развёртывание"
//    - Тип: Веб-приложение
//    - Выполнять как: Я (Анастасия Биличенко)
//    - Доступ: Все
// 5. Скопируй URL развёртывания → вставь в src/lib/bookingApi.ts
// ============================================================

const SPREADSHEET_ID = '1AO8GzNd0HUDrkaxvmBupviofh_QAgjnp4MeOJ9OugkU';
const SHEET_NAME = 'new_entry';
const CALENDAR_ID = 'anastasiyab762@gmail.com';

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);

    // Запись в таблицу
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const sheet = ss.getSheetByName(SHEET_NAME);
    const timestamp = Utilities.formatDate(new Date(), 'Europe/Moscow', 'yyyy-MM-dd HH:mm:ss');

    sheet.appendRow([
      timestamp,
      data.name || '',
      data.phone || '',
      data.service || '',
      data.date || '',
      data.time || '',
      data.comment || ''
    ]);

    // Создание события в Календаре
    const calendar = CalendarApp.getCalendarById(CALENDAR_ID);
    const dateTimeStr = data.date + 'T' + (data.time || '10:00') + ':00+03:00';
    const startDate = new Date(dateTimeStr);
    const endDate = new Date(startDate.getTime() + 2 * 60 * 60 * 1000); // +2 часа

    const eventTitle = data.service + ' — ' + data.name;
    const eventDesc = [
      'Телефон: ' + data.phone,
      'Услуга: ' + data.service,
      'Дата: ' + data.date + ' ' + (data.time || ''),
      data.comment ? 'Комментарий: ' + data.comment : '',
      '',
      '📌 Запись через сайт stasy-bell'
    ].filter(Boolean).join('\n');

    calendar.createEvent(eventTitle, startDate, endDate, {
      description: eventDesc
    });

    return ContentService
      .createTextOutput(JSON.stringify({ success: true }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService
      .createTextOutput(JSON.stringify({ success: false, error: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// Для проверки что скрипт работает (открой URL в браузере)
function doGet(e) {
  return ContentService
    .createTextOutput(JSON.stringify({ status: 'ok', message: 'Stacy Bell booking API is running' }))
    .setMimeType(ContentService.MimeType.JSON);
}
