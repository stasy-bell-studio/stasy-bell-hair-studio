import { useEffect, useState } from "react";
import { API_URL, bookableDays, clearPendingHold, clockMoscow, formatMoscowNow, lastBookableDay, readPendingHold, requestApi, savePendingHold, type Hold, type Service } from "./booking";
import { priceGroups } from "./prices";
import masterPhoto from "../../../src/assets/master-photo.png";
import portfolioOne from "../../../src/assets/portfolio-1.png";
import portfolioTwo from "../../../src/assets/portfolio-2.png";
import portfolioThree from "../../../src/assets/portfolio-3.png";
import { ArrowRight, Leaf, Sparkles, Heart, Scissors } from "lucide-react";

const contactTelegram = "https://t.me/Stasy10";
const contactVK = "https://vk.com/im?sel=829464173";
const studioAddress = "Санкт-Петербург, ул. Ольги Берггольц, 9, корп. 2";
const mapLink = "https://yandex.ru/maps/?text=" + encodeURIComponent(studioAddress);

function Booking() {
  const configured = Boolean(API_URL);
  const [services, setServices] = useState<Service[]>([]);
  const [channels, setChannels] = useState({ telegram: false, vk: false });
  const [catalogLoading, setCatalogLoading] = useState(configured);
  const [catalogFailed, setCatalogFailed] = useState(false);
  const [catalogRetry, setCatalogRetry] = useState(0);
  const [availabilityRevision, setAvailabilityRevision] = useState(0);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [serviceId, setServiceId] = useState("");
  const [day, setDay] = useState(formatMoscowNow());
  const [slots, setSlots] = useState<string[]>([]);
  const [openDays, setOpenDays] = useState<{ day: string; slot_count: number }[]>([]);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarError, setCalendarError] = useState(false);
  const [slot, setSlot] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [channel, setChannel] = useState<"telegram" | "vk">("telegram");
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [hold, setHold] = useState<Hold | null>(() => readPendingHold());
  const [holdStatus, setHoldStatus] = useState("pending_channel");
  const [statusCheckError, setStatusCheckError] = useState(false);

  useEffect(() => {
    if (!configured) return;
    let stale = false;
    setCatalogLoading(true);
    setCatalogFailed(false);
    setError("");
    Promise.all([
      requestApi<{ services: Service[] }>("/public/services"),
      requestApi<{ telegram: boolean; vk: boolean }>("/public/channels"),
    ]).then(([catalog, configuredChannels]) => {
      if (!stale) {
        setServices(catalog.services);
        setChannels(configuredChannels);
        setChannel(configuredChannels.telegram ? "telegram" : "vk");
      }
    }).catch((err: Error) => {
      if (!stale) { setCatalogFailed(true); setError(err.message); }
    }).finally(() => { if (!stale) setCatalogLoading(false); });
    return () => { stale = true; };
  }, [configured, catalogRetry]);

  useEffect(() => {
    setOpenDays([]);
    setCalendarError(false);
    if (!serviceId || !configured) {
      setCalendarLoading(false);
      return;
    }
    let stale = false;
    setCalendarLoading(true);
    const params = new URLSearchParams({ service_id: serviceId });
    requestApi<{ days: { day: string; slot_count: number }[] }>("/public/available-days?" + params)
      .then((response) => {
        if (!stale) {
          setOpenDays(response.days);
          setDay((current) => response.days.some((item) => item.day === current)
            ? current : response.days[0]?.day ?? formatMoscowNow());
        }
      })
      .catch(() => { if (!stale) setCalendarError(true); })
      .finally(() => { if (!stale) setCalendarLoading(false); });
    return () => { stale = true; };
  }, [serviceId, configured, availabilityRevision]);

  useEffect(() => {
    setSlots([]);
    setSlot("");
    if (!serviceId || !day || !configured) {
      setAvailabilityLoading(false);
      return;
    }
    let stale = false;
    setAvailabilityLoading(true);
    const params = new URLSearchParams({ day, service_id: serviceId });
    requestApi<{ starts_at: string[] }>("/public/availability?" + params)
      .then((data) => { if (!stale) { setSlots(data.starts_at); setError(""); } })
      .catch((err: Error) => { if (!stale) setError(err.message); })
      .finally(() => { if (!stale) setAvailabilityLoading(false); });
    return () => { stale = true; };
  }, [serviceId, day, configured, availabilityRevision]);

  useEffect(() => {
    if (!hold || !configured || holdStatus !== "pending_channel") return;
    let active = true;
    const poll = async () => {
      try {
        const result = await requestApi<{ status: string }>(
          `/public/holds/${hold.id}/status`, {
            method: "POST", body: JSON.stringify({ hold_secret: hold.hold_secret }),
          },
        );
        if (active) {
          setStatusCheckError(false);
          setHoldStatus(result.status);
          if (result.status !== "pending_channel") clearPendingHold();
        }
      } catch (err) {
        if (!active) return;
        if (err instanceof Error && (err.message.includes("Заявка не найдена") ||
            err.message.includes("истекли"))) {
          clearPendingHold();
          setHoldStatus("expired");
          setStatusCheckError(false);
        } else {
          // Failed verification never implies a confirmed booking.
          setStatusCheckError(true);
        }
      }
    };
    const timer = window.setInterval(poll, 15000);
    void poll();
    return () => { active = false; window.clearInterval(timer); };
  }, [hold, configured, holdStatus]);

  async function reserve() {
    if (!serviceId || !slot || !name.trim() || !consent) return;
    setBusy(true);
    setError("");
    try {
      const response = await requestApi<Hold>("/public/holds", {
        method: "POST",
        body: JSON.stringify({
          name: name.trim(), phone: phone.trim() || null,
          service_id: serviceId, starts_at: slot, channel,
          personal_data_consent: true,
        }),
      });
      savePendingHold(response);
      setHold(response);
      setStatusCheckError(false);
      setHoldStatus("pending_channel");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Не удалось создать заявку.";
      setError(message);
      if (message.includes("Это время уже занято")) {
        setSlot("");
        setAvailabilityRevision((current) => current + 1);
      }
    } finally {
      setBusy(false);
    }
  }

  async function reissue() {
    if (!hold) return;
    setBusy(true);
    setError("");
    try {
      const response = await requestApi<Hold>(`/public/holds/${hold.id}/reissue`, {
        method: "POST",
        body: JSON.stringify({ hold_secret: hold.hold_secret }),
      });
      savePendingHold(response);
      setHold(response);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Не удалось обновить код.";
      setError(message);
      if (message.includes("истекли")) {
        clearPendingHold();
        setHoldStatus("expired");
      }
    } finally {
      setBusy(false);
    }
  }

  function restartBooking() {
    clearPendingHold();
    setHold(null);
    setStatusCheckError(false);
    setHoldStatus("pending_channel");
    setSlot("");
    setError("");
    setAvailabilityRevision((current) => current + 1);
  }

  const selectedService = services.find((item) => item.id === serviceId);
  const dates = bookableDays();
  const calendarOffset = (new Date(dates[0] + "T12:00:00Z").getUTCDay() + 6) % 7;
  const availableByDay = new Map(openDays.map((item) => [item.day, item.slot_count]));
  const readableDay = (date: string) => new Intl.DateTimeFormat("ru-RU", {
    timeZone: "UTC", day: "numeric", month: "long", weekday: "long",
  }).format(new Date(date + "T12:00:00Z"));

  return (
    <section className="booking-section section" id="booking">
      <div className="container booking-grid">
        <div className="booking-intro">
          <span className="eyebrow">ВАШЕ ВРЕМЯ ДЛЯ СЕБЯ</span>
          <h2>Запись без лишних звонков.</h2>
          <p>Выберите процедуру и свободное время. Подтвердите бронь в Telegram или VK — и встреча появится в календаре.</p>
          <div className="booking-facts">
            <div><strong>45 минут</strong><span>время на подтверждение заявки</span></div>
            <div><strong>15 минут</strong><span>действует один код подтверждения</span></div>
          </div>
          <p className="payment-note">Оплата на сайте не принимается. Стоимость процедуры согласуется с мастером отдельно.</p>
          <p className="fineprint">До подтверждения в мессенджере время только временно зарезервировано. Бронь не равна подтверждённой записи.</p>
        </div>
        <div className="booking-form">
          {(!configured || catalogFailed) && (
            <div className="availability-note" role="status">
              <strong>{!configured ? "Онлайн-запись готовится к запуску." : "Система записи временно недоступна."}</strong>
              <p>{!configured ? "Пока можно написать мастеру напрямую. Мы не выдаём неподключённую форму за работающую." : "Не удалось получить услуги и доступные каналы. Можно повторить загрузку или написать мастеру."}</p>
              <p><a href={contactTelegram} target="_blank" rel="noreferrer">Написать в Telegram</a> · <a href={contactVK} target="_blank" rel="noreferrer">Написать во VK</a></p>
              {catalogFailed && <button className="button secondary wide retry-button" type="button" onClick={() => setCatalogRetry((current) => current + 1)} disabled={catalogLoading}>{catalogLoading ? "Загружаем…" : "Повторить загрузку"}</button>}
            </div>
          )}
          {configured && !hold && !catalogFailed && (
            <>
              <h3>Выбрать время</h3>
              <label className="field-label" htmlFor="service">Процедура</label>
              <select id="service" value={serviceId} onChange={(e) => setServiceId(e.target.value)} required>
                <option value="">Выберите услугу</option>
                {services.map((service) => (
                  <option value={service.id} key={service.id}>
                    {service.name}{service.variant ? " · " + service.variant : ""}
                    {service.price_kopecks != null ? " · " + (service.price_kopecks / 100).toLocaleString("ru-RU") + " ₽" : ""}
                    {" · " + service.duration_minutes + " мин"}
                  </option>
                ))}
              </select>
              {selectedService && <p className="booking-service-summary" role="status">
                Длительность: {selectedService.duration_minutes} мин
                {selectedService.price_kopecks != null && <> · Цена по записи: {(selectedService.price_kopecks / 100).toLocaleString("ru-RU")} ₽</>}
              </p>}
              <span className="field-label">Свободные даты</span>
              <div className="booking-calendar" role="group" aria-label="Календарь свободных дат">
                <div className="booking-weekdays" aria-hidden="true">
                  {["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"].map((label) => <span key={label}>{label}</span>)}
                </div>
                <div className="booking-day-grid">
                  {Array.from({ length: calendarOffset }, (_, index) => <span key={`empty-${index}`} aria-hidden="true" />)}
                  {dates.map((date) => {
                    const count = availableByDay.get(date) ?? 0;
                    const selected = day === date;
                    return (
                      <button type="button" key={date}
                        className={`booking-day ${selected ? "is-selected" : ""}`}
                        aria-label={`${readableDay(date)}. ${count ? `Свободных окон: ${count}` : "Свободных окон нет"}`}
                        aria-pressed={selected} disabled={!count || calendarLoading}
                        onClick={() => { setDay(date); setError(""); }}>
                        <strong>{Number(date.slice(-2))}</strong>
                        <small>{count || "—"}</small>
                      </button>
                    );
                  })}
                </div>
                <p className="fineprint booking-calendar-caption">
                  {calendarLoading ? "Загружаем свободные даты…" :
                    calendarError ? "Не удалось обновить календарь. Можно выбрать дату вручную." :
                    openDays.length ? "Число под датой — количество свободных вариантов времени." :
                    "На ближайшие 30 дней открытых окон пока нет."}
                </p>
              </div>
              <label className="field-label" htmlFor="booking-day">Дата</label>
              <input id="booking-day" type="date" min={formatMoscowNow()} max={lastBookableDay()} value={day} required
                onChange={(e) => { setDay(e.target.value); setError(""); }} />
              <p className="fineprint booking-date-hint">Доступны даты на ближайшие 30 дней по времени Санкт-Петербурга.</p>
              <label className="field-label" htmlFor="booking-time">Свободное время</label>
              <select id="booking-time" value={slot} disabled={!serviceId || availabilityLoading || slots.length === 0}
                onChange={(e) => setSlot(e.target.value)} required>
                <option value="">{availabilityLoading ? "Ищем свободное время…" : "Выберите время"}</option>
                {slots.map((when) => <option key={when} value={when}>{clockMoscow(when)}</option>)}
              </select>
              <label className="field-label" htmlFor="client-name">Как к вам обращаться</label>
              <input id="client-name" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={200} required />
              <label className="field-label" htmlFor="phone">Телефон (необязательно)</label>
              <input id="phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" maxLength={40} inputMode="tel" />
              {!channels.telegram && !channels.vk && <p className="fineprint">Подтверждение через мессенджеры ещё не подключено. Используйте прямую связь с мастером.</p>}
              <fieldset className="channel-choice">
                <legend className="field-label">Подтверждение в мессенджере</legend>
                <label><input type="radio" name="channel" checked={channel === "telegram"} disabled={!channels.telegram} onChange={() => setChannel("telegram")}/> Telegram</label>
                <label><input type="radio" name="channel" checked={channel === "vk"} disabled={!channels.vk} onChange={() => setChannel("vk")}/> VK</label>
              </fieldset>
              <label className="consent">
                <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                <span>Согласна(ен) на обработку указанных данных исключительно для записи и связи по услуге.</span>
              </label>
              <button className="button primary wide" type="button" disabled={!serviceId || !slot || !name.trim() || !consent || busy || !channels[channel]} onClick={reserve}>
                {busy ? "Создаём заявку…" : "Зарезервировать время"}
              </button>
              {catalogLoading && <p className="loading-note" role="status">Загружаем доступные услуги…</p>}
              {!catalogLoading && services.length === 0 && !error &&
                <p className="fineprint">Услуги для онлайн-записи пока не настроены мастером.</p>}
              {serviceId && availabilityLoading &&
                <p className="loading-note" role="status">Проверяем свободные окна…</p>}
              {serviceId && !availabilityLoading && slots.length === 0 && !error &&
                <p className="fineprint" role="status">На выбранную дату свободных окон нет. Выберите другую дату или напишите мастеру.</p>}
            </>
          )}
          {configured && hold && (
            <div className="hold-detail" role="status">
              <span className="eyebrow">{holdStatus === "confirmed" ? "ПОДТВЕРЖДЁННАЯ ЗАПИСЬ" :
                holdStatus === "pending_channel" ? "ВРЕМЕННАЯ БРОНЬ" : "БРОНЬ ЗАВЕРШЕНА"}</span>
              <h3>{holdStatus === "confirmed" ? "Запись подтверждена!" :
                holdStatus === "pending_channel" ? "Осталось подтвердить запись" :
                holdStatus === "expired" ? "Время брони истекло" : "Бронь завершена"}</h3>
              {(hold.starts_at || hold.service_name) && <p className="booking-visit-summary">
                {hold.service_name && <strong>{hold.service_name}{hold.service_variant ? " · " + hold.service_variant : ""}</strong>}
                {hold.starts_at && <span>{clockMoscow(hold.starts_at)} · Санкт-Петербург</span>}
              </p>}
              {holdStatus === "confirmed" ? <p>Подтверждение получено. Ждём вас по адресу: {studioAddress}.</p> :
                holdStatus === "pending_channel" ?
                <p>Интервал удерживается до <strong>{clockMoscow(hold.hold_until)}</strong>.</p> :
                <p>Выберите другое свободное время.</p>}
              {holdStatus === "pending_channel" &&
                <p>Код действует до <strong>{clockMoscow(hold.confirmation_token_expires_at)}</strong>.</p>}
              {holdStatus === "pending_channel" && statusCheckError &&
                <p className="form-error" role="alert">Не удалось проверить текущий статус. Бронь пока не считается подтверждённой. Проверка продолжится автоматически.</p>}
              {holdStatus === "pending_channel" && hold.channel_link && (
                <a className="button primary wide" href={hold.channel_link} target="_blank" rel="noreferrer">
                  Открыть {hold.channel === "telegram" ? "Telegram-бота" : "сообщество VK"}
                </a>
              )}
              {holdStatus === "pending_channel" && hold.channel_instruction && <p className="code-instruction">{hold.channel_instruction}</p>}
              {holdStatus === "pending_channel" && !hold.channel_link && <p>Подключение канала ещё не завершено. Эта заявка пока не подтверждена.</p>}
              {holdStatus === "pending_channel" &&
                <p className="fineprint">Код: <code>{hold.channel_confirmation_code}</code>. Не передавайте его посторонним.</p>}
              {holdStatus === "pending_channel" &&
                <button className="button secondary wide" type="button" onClick={reissue} disabled={busy}>
                  Получить новый код
                </button>}
              {holdStatus !== "pending_channel" &&
                <button className="button secondary wide" type="button" onClick={restartBooking}>
                  Выбрать другое время
                </button>}
            </div>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
        </div>
      </div>
    </section>
  );
}

function App() {
  return (
    <>
      <a href="#home" className="skip-link">Перейти к содержимому</a>
      <header className="site-header">
        <nav className="container navigation" aria-label="Основная навигация">
          <a href="#home" className="wordmark"><Leaf aria-hidden="true" strokeWidth={1.2} /><span>Stasy <em>Bell</em><small>HAIR STUDIO</small></span></a>
          <div className="nav-links"><a href="#about">О мастере</a><a href="#services">Услуги и цены</a><a href="#contacts">Контакты</a></div>
          <a href="#booking" className="button primary nav-book">Записаться</a>
        </nav>
      </header>
      <main id="home">
        <section className="hero">
          <div className="container hero-grid">
            <div className="hero-copy">
              <span className="eyebrow">СТУДИЯ ЗДОРОВЬЯ И КРАСОТЫ ВОЛОС</span>
              <h1>Stasy Bell</h1>
              <p className="hero-tagline">Волосы, в которых <em>ты — это ты.</em></p>
              <p>Кератин, ботокс и забота о здоровье волос. Бережный подход и процедуры, которые хочется повторять.</p>
              <div className="hero-actions"><a className="button primary" href="#booking">Выбрать время <ArrowRight size={16} aria-hidden="true" /></a><a className="button hero-secondary" href="#services">Услуги и цены</a></div>
              <span className="hero-footnote">Санкт-Петербург · ул. Ольги Берггольц, 9, корп. 2</span>
            </div>
            <div className="hero-media">
              <img src={masterPhoto} alt="Мастер Stasy Bell" />
              <div className="floating-note"><span>ВАШ МАСТЕР</span>Красота начинается с заботы.</div>
            </div>
          </div>
          <div className="container studio-principles">
            <span><Sparkles aria-hidden="true" />Гладкость и блеск</span>
            <span><Leaf aria-hidden="true" />Бережный уход</span>
            <span><Heart aria-hidden="true" />Индивидуальный подход</span>
            <span><Scissors aria-hidden="true" />Здоровье волос</span>
          </div>
        </section>
        <section className="service-preview section">
          <div className="container">
            <div className="preview-heading"><h2>Наши услуги</h2><a className="text-link" href="#services">Все услуги <ArrowRight size={16} aria-hidden="true" /></a></div>
            <div className="preview-grid">
              {priceGroups.slice(0, 3).map((group, index) => (
                <a className="preview-card" href={`#price-group-${index}`} key={group.title}>
                  <img src={[portfolioTwo, portfolioOne, portfolioThree][index]} alt="Пример ухода из портфолио Stasy Bell" />
                  <div><h3>{group.title}</h3><p>{group.description}</p>
                    <span className="preview-price">от {group.items.find((item) => !item.addOnOnly)?.price}<ArrowRight size={20} aria-hidden="true" /></span>
                  </div>
                </a>
              ))}
            </div>
          </div>
        </section>
        <section className="section intro-section" id="about">
          <div className="container about-grid">
            <div><span className="eyebrow">МОЯ ФИЛОСОФИЯ</span><h2>У каждой истории волос — свой подход.</h2></div>
            <div><p>Уход — это не конвейер. Перед процедурой важно понять состояние и особенности волос, чтобы подобрать решение под вас.</p><p>Сосредоточимся на том, что действительно нужно вашим волосам.</p></div>
          </div>
        </section>
        <section className="section services-section" id="services">
          <div className="container">
            <div className="section-heading"><span className="eyebrow">ПРОЦЕДУРЫ</span><h2>Меню красоты</h2><p>Актуальные цены с действующего сайта. Точная стоимость зависит от варианта услуги и особенностей волос.</p></div>
            <div className="services-grid">
              {priceGroups.map((group, index) => (
                <article className="service-card" id={`price-group-${index}`} key={group.title}>
                  <span className="card-index">0{index + 1}</span>
                  <h3>{group.title}</h3>
                  <p className="service-subtitle">{group.description}</p>
                  <div className="price-rows">
                    {group.items.map((item) => (
                      <div className="price-row" key={item.label}>
                        <span>{item.label}{item.addOnOnly && <small> · дополнение к процедуре</small>}</span>
                        <strong>{item.price}</strong>
                      </div>
                    ))}
                  </div>
                  {group.note && <p className="service-note">{group.note}</p>}
                </article>
              ))}
            </div>
            <p className="price-disclaimer">Стоимость и длительность окончательно согласуются при выборе процедуры.</p>
          </div>
        </section>
        <section className="gallery-section section">
          <div className="container">
            <div className="section-heading"><span className="eyebrow">ДО И ПОСЛЕ</span><h2>Результат в деталях</h2></div>
            <div className="gallery-grid"><img src={portfolioOne} alt="Пример результата ухода за волосами" loading="lazy"/><img src={portfolioTwo} alt="Результат процедуры Stasy Bell" loading="lazy"/></div>
          </div>
        </section>
        <Booking />
        <section className="contacts-section section" id="contacts">
          <div className="container contacts-grid">
            <div><span className="eyebrow">БУДУ РАДА ВСТРЕЧЕ</span><h2>До встречи в студии.</h2><p>{studioAddress}</p><a className="text-link" href={mapLink} target="_blank" rel="noreferrer">Показать на Яндекс Картах</a></div>
            <div className="contact-list"><a href={contactTelegram} target="_blank" rel="noreferrer">Написать в Telegram <span>↗</span></a><a href={contactVK} target="_blank" rel="noreferrer">Написать во VK <span>↗</span></a><a href="#booking">Перейти к записи <span>↗</span></a></div>
          </div>
        </section>
      </main>
      <footer className="footer"><div className="container footer-inner"><span>STASY BELL · HAIR STUDIO</span><span>Санкт-Петербург · 2026</span></div></footer>
    </>
  );
}

export default App;
