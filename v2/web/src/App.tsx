import { useEffect, useState } from "react";
import { API_URL, clockMoscow, formatMoscowNow, requestApi, type Hold, type Service } from "./booking";
import { priceGroups } from "./prices";
import masterPhoto from "../../../src/assets/master-photo.png";
import portfolioOne from "../../../src/assets/portfolio-1.png";
import portfolioTwo from "../../../src/assets/portfolio-2.png";

const contactTelegram = "https://t.me/Stasy10";
const contactVK = "https://vk.com/im?sel=829464173";
const studioAddress = "Санкт-Петербург, ул. Ольги Берггольц, 9, корп. 2";
const mapLink = "https://yandex.ru/maps/?text=" + encodeURIComponent(studioAddress);

function Booking() {
  const [services, setServices] = useState<Service[]>([]);
  const [serviceId, setServiceId] = useState("");
  const [day, setDay] = useState(formatMoscowNow());
  const [slots, setSlots] = useState<string[]>([]);
  const [slot, setSlot] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [channel, setChannel] = useState<"telegram" | "vk">("telegram");
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [hold, setHold] = useState<Hold | null>(null);
  const [holdStatus, setHoldStatus] = useState("pending_channel");
  const configured = Boolean(API_URL);

  useEffect(() => {
    if (!configured) return;
    let stale = false;
    requestApi<{ services: Service[] }>("/public/services")
      .then((data) => { if (!stale) setServices(data.services); })
      .catch((err: Error) => { if (!stale) setError(err.message); });
    return () => { stale = true; };
  }, [configured]);

  useEffect(() => {
    setSlots([]);
    setSlot("");
    if (!serviceId || !day || !configured) return;
    let stale = false;
    const params = new URLSearchParams({ day, service_id: serviceId });
    requestApi<{ starts_at: string[] }>("/public/availability?" + params)
      .then((data) => { if (!stale) { setSlots(data.starts_at); setError(""); } })
      .catch((err: Error) => { if (!stale) setError(err.message); });
    return () => { stale = true; };
  }, [serviceId, day, configured]);

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
        if (active) setHoldStatus(result.status);
      } catch {
        // Network errors never imply confirmation.
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
      setHold(response);
      setHoldStatus("pending_channel");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось создать заявку.");
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
      setHold(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось обновить код.");
    } finally {
      setBusy(false);
    }
  }

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
          <p className="fineprint">До подтверждения в мессенджере время только временно зарезервировано. Бронь не равна подтверждённой записи.</p>
        </div>
        <div className="booking-form">
          {!configured && (
            <div className="availability-note" role="status">
              <strong>Онлайн-запись готовится к запуску.</strong>
              <p>Пока можно написать мастеру напрямую. Мы не выдаём неподключённую форму за работающую.</p>
              <p><a href={contactTelegram} target="_blank" rel="noreferrer">Написать в Telegram</a> · <a href={contactVK} target="_blank" rel="noreferrer">Написать во VK</a></p>
            </div>
          )}
          {configured && !hold && (
            <>
              <h3>Выбрать время</h3>
              <label className="field-label" htmlFor="service">Процедура</label>
              <select id="service" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
                <option value="">Выберите услугу</option>
                {services.map((service) => (
                  <option value={service.id} key={service.id}>
                    {service.name}{service.variant ? " · " + service.variant : ""}
                    {service.price_kopecks != null ? " · " + (service.price_kopecks / 100).toLocaleString("ru-RU") + " ₽" : ""}
                  </option>
                ))}
              </select>
              <label className="field-label" htmlFor="booking-day">Дата</label>
              <input id="booking-day" type="date" min={formatMoscowNow()} value={day}
                onChange={(e) => setDay(e.target.value)} />
              <label className="field-label" htmlFor="booking-time">Свободное время</label>
              <select id="booking-time" value={slot} onChange={(e) => setSlot(e.target.value)}>
                <option value="">Выберите время</option>
                {slots.map((when) => <option key={when} value={when}>{clockMoscow(when)}</option>)}
              </select>
              <label className="field-label" htmlFor="client-name">Как к вам обращаться</label>
              <input id="client-name" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={200} />
              <label className="field-label" htmlFor="phone">Телефон (необязательно)</label>
              <input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" maxLength={40} inputMode="tel" />
              <fieldset className="channel-choice">
                <legend className="field-label">Подтверждение в мессенджере</legend>
                <label><input type="radio" name="channel" checked={channel === "telegram"} onChange={() => setChannel("telegram")}/> Telegram</label>
                <label><input type="radio" name="channel" checked={channel === "vk"} onChange={() => setChannel("vk")}/> VK</label>
              </fieldset>
              <label className="consent">
                <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                <span>Согласна(ен) на обработку указанных данных исключительно для записи и связи по услуге.</span>
              </label>
              <button className="button primary wide" type="button" disabled={!serviceId || !slot || !name.trim() || !consent || busy} onClick={reserve}>
                {busy ? "Создаём заявку…" : "Зарезервировать время"}
              </button>
              {services.length === 0 && <p className="fineprint">Услуги для онлайн-записи пока не настроены мастером.</p>}
            </>
          )}
          {configured && hold && (
            <div className="hold-detail" role="status">
              <span className="eyebrow">ВРЕМЕННАЯ БРОНЬ</span>
              <h3>{holdStatus === "confirmed" ? "Запись подтверждена!" :
                holdStatus === "pending_channel" ? "Осталось подтвердить запись" :
                "Бронь завершена"}</h3>
              {holdStatus === "confirmed" ? <p>Подтверждение получено. Ждём вас!</p> :
                holdStatus === "pending_channel" ?
                <p>Интервал удерживается до <strong>{clockMoscow(hold.hold_until)}</strong>.</p> :
                <p>Выберите другое свободное время.</p>}
              {holdStatus === "pending_channel" &&
                <p>Код действует до <strong>{clockMoscow(hold.confirmation_token_expires_at)}</strong>.</p>}
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
                  Обновить код (если истёк)
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
      <header className="site-header">
        <nav className="container navigation" aria-label="Основная навигация">
          <a href="#home" className="wordmark">STASY <em>BELL</em><small>HAIR STUDIO</small></a>
          <div className="nav-links"><a href="#about">О мастере</a><a href="#services">Услуги и цены</a><a href="#contacts">Контакты</a></div>
          <a href="#booking" className="button nav-book">Записаться</a>
        </nav>
      </header>
      <main id="home">
        <section className="hero">
          <div className="container hero-grid">
            <div className="hero-copy">
              <span className="eyebrow">HAIR CARE STUDIO · САНКТ-ПЕТЕРБУРГ</span>
              <h1>Волосы, в которых <em>ты — это ты.</em></h1>
              <p>Кератин, ботокс и забота о здоровье волос. Бережный подход и процедуры, которые хочется повторять.</p>
              <div className="hero-actions"><a className="button primary" href="#booking">Выбрать время</a><a className="text-link" href="#services">Смотреть прайс</a></div>
              <span className="hero-footnote">Санкт-Петербург · ул. Ольги Берггольц, 9, корп. 2</span>
            </div>
            <div className="hero-media">
              <img src={masterPhoto} alt="Мастер Stasy Bell" />
              <div className="floating-note">Красота начинается с заботы.</div>
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
                <article className="service-card" key={group.title}>
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
