import { MapPin, Phone, Clock, MessageCircle } from "lucide-react";
import AnimatedSection from "./AnimatedSection";

const messengers = [
  {
    name: "WhatsApp",
    href: "https://wa.me/79145711243?text=Привет! Хочу записаться на процедуру 💜",
    color: "bg-[#25D366] hover:bg-[#20b557]",
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
        <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
      </svg>
    ),
  },
  {
    name: "Telegram",
    href: "https://t.me/Stasy10",
    color: "bg-[#2AABEE] hover:bg-[#1a9ada]",
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
        <path d="M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z"/>
      </svg>
    ),
  },
  {
    name: "ВКонтакте",
    href: "http://vk.com/stacybelll",
    color: "bg-[#0077FF] hover:bg-[#0062cc]",
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
        <path d="M15.684 0H8.316C1.592 0 0 1.592 0 8.316v7.368C0 22.408 1.592 24 8.316 24h7.368C22.408 24 24 22.408 24 15.684V8.316C24 1.592 22.391 0 15.684 0zm3.692 17.123h-1.744c-.66 0-.862-.525-2.049-1.714-1.033-1.01-1.49-.897-1.49.087v1.627c0 .375-.119.598-1.102.598-1.627 0-3.44-.99-4.716-2.84-1.913-2.7-2.44-4.716-2.44-5.128 0-.251.1-.487.487-.487H7.9c.363 0 .5.17.638.575.7 2.037 1.875 3.826 2.362 3.826.18 0 .263-.085.263-.55V9.756c-.058-.988-.58-1.072-.58-1.424 0-.175.143-.35.375-.35h2.75c.306 0 .413.162.413.525v2.824c0 .306.136.413.219.413.18 0 .331-.107.663-.44 1.026-1.148 1.76-2.913 1.76-2.913.097-.2.263-.387.625-.387h1.744c.525 0 .638.27.525.637-.219.981-2.35 4.028-2.35 4.028-.175.287-.237.413 0 .738.169.243.728.746 1.1 1.199.682.799 1.207 1.47 1.348 1.932.12.456-.12.687-.512.687z"/>
      </svg>
    ),
  },
];

const ContactsSection = () => (
  <section id="contacts" className="section-padding bg-white">
    <div className="max-w-6xl mx-auto">
      <AnimatedSection>
        <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
          Контакты
        </h2>
        <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-16">
          Как найти
        </h3>
      </AnimatedSection>

      <div className="grid lg:grid-cols-2 gap-8 items-start">
        {/* Left column — info */}
        <AnimatedSection delay={0.1}>
          <div className="flex flex-col gap-4">

            {/* Address */}
            <div className="flex gap-4 p-5 rounded-2xl bg-secondary/60 border border-secondary">
              <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <MapPin className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-1">Адрес</p>
                <p className="text-foreground font-medium leading-snug">г. Аксай, ул. Садовая, д. 31</p>
                <p className="text-sm text-muted-foreground">подъезд 7, офис 3</p>
                <a
                  href="https://yandex.ru/maps/?text=Аксай+ул+Садовая+31"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-primary mt-2 hover:underline"
                >
                  Открыть в Яндекс Картах →
                </a>
              </div>
            </div>

            {/* Phone */}
            <div className="flex gap-4 p-5 rounded-2xl bg-secondary/60 border border-secondary">
              <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <Phone className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-1">Телефон</p>
                <a
                  href="tel:+79145711243"
                  className="text-foreground font-medium hover:text-primary transition-colors"
                >
                  +7 (914) 571-12-43
                </a>
              </div>
            </div>

            {/* Hours */}
            <div className="flex gap-4 p-5 rounded-2xl bg-secondary/60 border border-secondary">
              <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <Clock className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-1">Режим работы</p>
                <p className="text-foreground font-medium">По записи</p>
                <p className="text-sm text-muted-foreground">Пн – Вс, обсуждается индивидуально</p>
              </div>
            </div>

            {/* Messengers */}
            <div className="p-5 rounded-2xl bg-secondary/60 border border-secondary">
              <div className="flex gap-3 items-center mb-4">
                <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
                  <MessageCircle className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Написать</p>
                  <p className="text-sm text-foreground font-medium">Выберите удобный мессенджер</p>
                </div>
              </div>
              <div className="flex flex-col sm:flex-row gap-2">
                {messengers.map((m) => (
                  <a
                    key={m.name}
                    href={m.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={`flex items-center justify-center gap-2 ${m.color} text-white rounded-xl px-4 py-2.5 text-sm font-medium transition-colors flex-1`}
                  >
                    {m.icon}
                    {m.name}
                  </a>
                ))}
              </div>
            </div>

          </div>
        </AnimatedSection>

        {/* Right column — map */}
        <AnimatedSection delay={0.2}>
          <div className="rounded-2xl overflow-hidden shadow-sm border border-secondary h-[420px] lg:h-full min-h-[380px]">
            <iframe
              src="https://yandex.ru/map-widget/v1/?text=%D0%90%D0%BA%D1%81%D0%B0%D0%B9%2C+%D1%83%D0%BB.+%D0%A1%D0%B0%D0%B4%D0%BE%D0%B2%D0%B0%D1%8F%2C+%D0%B4.+31&z=16&l=map"
              width="100%"
              height="100%"
              style={{ border: 0, display: "block" }}
              allowFullScreen
              loading="lazy"
              title="Карта — Стэйси Белл"
            />
          </div>
        </AnimatedSection>
      </div>
    </div>
  </section>
);

export default ContactsSection;
