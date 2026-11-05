import { privacy, privacyReady } from "./legal";

export default function PrivacyPage() {
  return (
    <>
      <a href="#privacy-content" className="skip-link">Перейти к политике</a>
      <header className="privacy-header">
        <div className="container privacy-header-inner">
          <a href="#home" className="privacy-wordmark">Stasy Bell <small>HAIR STUDIO</small></a>
          <a href="#home" className="button secondary">На главную</a>
        </div>
      </header>
      <main id="privacy-content" className="privacy-page section">
        <div className="container privacy-layout">
          <span className="eyebrow">КОНФИДЕНЦИАЛЬНОСТЬ</span>
          <h1>Обработка персональных данных</h1>
          {!privacyReady && <div className="privacy-draft" role="status">
            <strong>Документ готовится к утверждению.</strong>
            <p>Онлайн-запись отключена до проверки реквизитов оператора,
            сроков хранения и утверждения политики. Ниже — описание запланированной
            обработки, а не окончательная правовая редакция.</p>
          </div>}
          <div className="privacy-article">
            <section aria-labelledby="privacy-operator"><h2 id="privacy-operator">1. Оператор данных</h2>
              {privacyReady
                ? <p><strong>{privacy.operator}</strong>. Адрес: {privacy.address}.
                    Контакт по персональным данным: {privacy.contact}.</p>
                : <p>Сведения об операторе, его адресе и способах обращения будут
                    опубликованы после проверки. Адрес студии:
                    Санкт-Петербург, ул. Ольги Берггольц, 9, корп. 2.</p>}
            </section>
            <section aria-labelledby="privacy-purpose"><h2 id="privacy-purpose">2. Цели и состав данных</h2>
              <p>Оформление записи на процедуру, бронирование окна, подтверждение через
                выбранный клиентом мессенджер, связь по визиту и календарь мастера.</p>
              <p>Имя, необязательный телефон, услуга, дата и время, статус записи,
                время согласия и идентификатор мессенджера после подтверждения.
                Банковские карты и сведения об оплате сайт не запрашивает.
                Согласие на рекламу не собирается.</p>
            </section>
            <section aria-labelledby="privacy-operations"><h2 id="privacy-operations">3. Обработка и мессенджеры</h2>
              <p>Сбор, запись, хранение, уточнение, использование для ведения записи,
                предоставление доступа мастеру и удаление при наступлении предусмотренных
                оснований. Клиент самостоятельно переходит в Telegram или VK для
                подтверждения; у этих сервисов действуют собственные условия обработки.</p>
            </section>
            <section aria-labelledby="privacy-retention"><h2 id="privacy-retention">4. Срок хранения и удаление</h2>
              <p>{privacyReady ? privacy.retention :
                "Конкретные сроки хранения, удаления и исключения для обязательного хранения подлежат утверждению оператором до запуска записи."}</p>
            </section>
            <section aria-labelledby="privacy-rights"><h2 id="privacy-rights">5. Права и обращения</h2>
              <p>Можно запросить сведения о данных, их уточнение, блокирование или
                удаление, а также отозвать согласие. Ограничения, предусмотренные
                законом, проверяются оператором в каждом случае.</p>
              <p>{privacyReady ? <>Для обращения: <strong>{privacy.contact}</strong>.</> :
                "До публикации контакта оператора форма записи отключена."}</p>
            </section>
          </div>
          <a className="privacy-return" href="#home">Вернуться на сайт</a>
        </div>
      </main>
      <footer className="footer"><div className="container footer-inner">
        <span>STASY BELL · HAIR STUDIO</span><span>Санкт-Петербург · 2026</span>
      </div></footer>
    </>
  );
}
