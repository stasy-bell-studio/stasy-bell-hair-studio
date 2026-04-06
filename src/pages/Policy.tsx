import { Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";

const Policy = () => (
  <div className="min-h-screen bg-white">
    {/* Top nav */}
    <div className="sticky top-0 z-10 bg-white/80 backdrop-blur border-b border-border">
      <div className="max-w-3xl mx-auto px-6 py-4 flex items-center justify-between">
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          На главную
        </Link>
        <span className="text-sm font-semibold text-primary">Стэйси Белл</span>
      </div>
    </div>

    {/* Document */}
    <main className="max-w-3xl mx-auto px-6 py-12 md:py-16">
      {/* Title */}
      <div className="mb-10 pb-8 border-b border-border">
        <h1 className="text-2xl md:text-3xl font-bold text-foreground mb-2">
          Политика конфиденциальности
        </h1>
        <p className="text-muted-foreground">Лукина Анастасия Александровна (Стэйси Белл)</p>
      </div>

      {/* Sections */}
      <div className="space-y-10 text-sm md:text-base">

        {/* 1 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">1. Общие положения</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>
              Настоящая политика обработки персональных данных составлена в соответствии с требованиями Федерального закона от 27.07.2006 № 152-ФЗ «О персональных данных» и определяет порядок обработки персональных данных и меры по обеспечению их безопасности Лукиной Анастасией Александровной (далее — Оператор).
            </p>
            <p>1.1. Оператор ставит своей важнейшей целью соблюдение прав и свобод человека и гражданина при обработке его персональных данных, в том числе защиты прав на неприкосновенность частной жизни, личную и семейную тайну.</p>
            <p>1.2. Настоящая Политика применяется ко всей информации, которую Оператор может получить о посетителях веб-сайта https://stasy-bell-hair-studio.lovable.app/.</p>
          </div>
        </section>

        {/* 2 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">2. Основные понятия</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>2.1. <strong>Автоматизированная обработка персональных данных</strong> — обработка персональных данных с помощью средств вычислительной техники.</p>
            <p>2.2. <strong>Блокирование персональных данных</strong> — временное прекращение обработки персональных данных (за исключением случаев, если обработка необходима для их уточнения).</p>
            <p>2.3. <strong>Веб-сайт</strong> — совокупность графических и информационных материалов, а также программ для ЭВМ и баз данных, обеспечивающих их доступность в сети Интернет по адресу https://stasy-bell-hair-studio.lovable.app/.</p>
            <p>2.4. <strong>Обработка персональных данных</strong> — любое действие или совокупность действий с персональными данными, включая сбор, запись, систематизацию, накопление, хранение, уточнение, извлечение, использование, передачу, обезличивание, блокирование, удаление, уничтожение персональных данных.</p>
            <p>2.5. <strong>Оператор</strong> — физическое лицо, самостоятельно или совместно с другими лицами организующее и/или осуществляющее обработку персональных данных.</p>
            <p>2.6. <strong>Персональные данные</strong> — любая информация, относящаяся прямо или косвенно к определённому или определяемому Пользователю веб-сайта https://stasy-bell-hair-studio.lovable.app/.</p>
            <p>2.7. <strong>Пользователь</strong> — любой посетитель веб-сайта https://stasy-bell-hair-studio.lovable.app/.</p>
            <p>2.8. <strong>Уничтожение персональных данных</strong> — действия, в результате которых персональные данные уничтожаются безвозвратно.</p>
          </div>
        </section>

        {/* 3 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">3. Персональные данные, которые обрабатывает Оператор</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>3.1. Фамилия, имя, отчество.</p>
            <p>3.2. Номер телефона.</p>
            <p>3.3. Адрес электронной почты.</p>
            <p>3.4. На сайте происходит сбор и обработка обезличенных данных о посетителях (в том числе файлов «cookie») с помощью сервисов интернет-статистики.</p>
          </div>
        </section>

        {/* 4 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">4. Цели обработки персональных данных</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>4.1. Цель обработки персональных данных Пользователя — информирование Пользователя о записи на услуги, их стоимости и характеристиках, а также отправка уведомлений, связанных с исполнением Договора.</p>
            <p>4.2. Оператор вправе направлять Пользователю уведомления о новых услугах, специальных предложениях и акциях. Пользователь вправе отказаться от получения информационных сообщений, направив уведомление на электронную почту: anastasiyab762@gmail.com с пометкой «Отказ от уведомлений».</p>
            <p>4.3. Обезличенные данные Пользователей используются для улучшения качества сайта и его содержания.</p>
          </div>
        </section>

        {/* 5 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">5. Правовые основания обработки персональных данных</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>5.1. Оператор обрабатывает персональные данные Пользователя только в случае их заполнения и/или отправки Пользователем самостоятельно через формы, расположенные на сайте https://stasy-bell-hair-studio.lovable.app/. Заполняя соответствующие формы, Пользователь выражает своё согласие с данной Политикой.</p>
            <p>5.2. Оператор обрабатывает обезличенные данные о Пользователе в случае, если это разрешено в настройках браузера Пользователя (включено сохранение файлов «cookie»).</p>
          </div>
        </section>

        {/* 6 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">6. Порядок сбора, хранения, передачи и иных видов обработки персональных данных</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>6.1. Оператор обеспечивает сохранность персональных данных и принимает все возможные меры, исключающие доступ к ним неуполномоченных лиц.</p>
            <p>6.2. Персональные данные Пользователя не будут переданы третьим лицам, за исключением случаев, предусмотренных действующим законодательством РФ.</p>
            <p>6.3. В случае выявления неточностей в персональных данных Пользователь может их актуализировать, направив уведомление на электронную почту anastasiyab762@gmail.com с пометкой «Актуализация персональных данных».</p>
            <p>6.4. Пользователь может в любой момент отозвать согласие на обработку персональных данных, направив уведомление на электронную почту anastasiyab762@gmail.com с пометкой «Отзыв согласия на обработку персональных данных».</p>
          </div>
        </section>

        {/* 7 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">7. Трансграничная передача персональных данных</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>7.1. Оператор до начала осуществления трансграничной передачи персональных данных обязан убедиться в том, что иностранным государством, на территорию которого планируется передача, обеспечивается надлежащая защита прав субъектов персональных данных.</p>
            <p>7.2. Трансграничная передача персональных данных на территории иностранных государств, не отвечающих указанным требованиям, осуществляется только при наличии письменного согласия субъекта персональных данных.</p>
          </div>
        </section>

        {/* 8 */}
        <section>
          <h2 className="text-base md:text-lg font-bold text-foreground mb-4 uppercase tracking-wide">8. Заключительные положения</h2>
          <div className="space-y-3 text-foreground/80 leading-relaxed">
            <p>8.1. Пользователь вправе получить любые разъяснения по вопросам обработки персональных данных, обратившись к Оператору по электронной почте: anastasiyab762@gmail.com.</p>
            <p>8.2. Любые изменения политики обработки персональных данных будут отражены в настоящем документе. Политика действует бессрочно до замены её новой версией.</p>
            <p>8.3. Актуальная версия Политики расположена по адресу: https://stasy-bell-hair-studio.lovable.app/policy.</p>
          </div>
        </section>

        {/* Operator details */}
        <section>
          <div className="bg-secondary/50 rounded-xl p-5 text-sm md:text-base text-foreground/80 leading-relaxed space-y-1">
            <p><strong>Оператор:</strong> Лукина Анастасия Александровна (самозанятая, ИНН: 281304117217)</p>
            <p><strong>Контакт:</strong> anastasiyab762@gmail.com | +7 (914) 571-12-43</p>
          </div>
        </section>

      </div>

      {/* Footer */}
      <div className="mt-16 pt-8 border-t border-border text-center">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-primary hover:underline">
          <ArrowLeft className="w-4 h-4" />
          Вернуться на главную
        </Link>
      </div>
    </main>
  </div>
);

export default Policy;
