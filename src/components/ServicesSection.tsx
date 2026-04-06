import AnimatedSection from "./AnimatedSection";
import BookingPopup from "./BookingPopup";

interface PriceRow {
  name: string;
  price: string;
  note?: string;
  vkMessage: string;
}

interface ServiceCard {
  title: string;
  emoji: string;
  description: string;
  rows: PriceRow[];
  note?: string;
  badge?: string;
  accent: string;
}

const cards: ServiceCard[] = [
  {
    title: "Кератин / Ботокс",
    emoji: "✨",
    description: "Гладкость, блеск и послушность от 2 до 5 месяцев",
    accent: "from-violet-50 to-purple-50",
    rows: [
      { name: "По плечи", price: "5 100 ₽", vkMessage: "Привет! Хочу записаться на кератин или ботокс. Длина волос — по плечи 💜" },
      { name: "Ниже плеч", price: "5 400 ₽", vkMessage: "Привет! Хочу записаться на кератин или ботокс. Длина — ниже плеч 💜" },
      { name: "По лопатки", price: "5 700 ₽", vkMessage: "Привет! Хочу записаться на кератин или ботокс. Длина — по лопатки 💜" },
      { name: "По талию", price: "6 000 ₽", vkMessage: "Привет! Хочу записаться на кератин или ботокс. Длина — по талию 💜" },
      { name: "По поясницу", price: "6 300 ₽", vkMessage: "Привет! Хочу записаться на кератин или ботокс. Длина — по поясницу 💜" },
    ],
    note: "+10% за густые волосы · +15% за очень густые",
  },
  {
    title: "Комбо-процедуры",
    emoji: "🎁",
    description: "Комплексный уход — максимальный результат по выгодной цене",
    accent: "from-fuchsia-50 to-violet-50",
    badge: "Выгодно",
    rows: [
      { name: "Уходовая стрижка + пилинг + Здоровье", price: "6 500 ₽", vkMessage: "Привет! Интересует комбо: уходовая стрижка + пилинг + Здоровье для волос 💜" },
      { name: "Здоровье для волос + кератин/ботокс", price: "+2 000 ₽", vkMessage: "Привет! Интересует комбо: Здоровье для волос + кератин или ботокс. Подскажите итоговую стоимость? 💜" },
      { name: "Здоровье + пилинг + прикорневой объём", price: "7 000 ₽", vkMessage: "Привет! Интересует комбо: Здоровье для волос + пилинг + прикорневой объём 💜" },
    ],
  },
  {
    title: "Здоровье для волос",
    emoji: "💎",
    description: "Глубокое восстановление, питание и укрепление структуры волос",
    accent: "from-indigo-50 to-violet-50",
    rows: [
      { name: "По плечи", price: "3 000 ₽", vkMessage: "Привет! Интересует лечение волос (Здоровье для волос). Длина — по плечи 💜" },
      { name: "По лопатки", price: "3 200 ₽", vkMessage: "Привет! Интересует лечение волос (Здоровье для волос). Длина — по лопатки 💜" },
      { name: "По талию", price: "3 400 ₽", vkMessage: "Привет! Интересует лечение волос (Здоровье для волос). Длина — по талию 💜" },
    ],
  },
  {
    title: "Дополнительные услуги",
    emoji: "🌸",
    description: "Дополни процедуру для полного ухода",
    accent: "from-pink-50 to-fuchsia-50",
    rows: [
      { name: "Пилинг кожи головы", price: "500 ₽", vkMessage: "Привет! Хочу записаться на пилинг кожи головы 💜" },
      { name: "Прикорневой объём на кератине", price: "4 000 ₽", vkMessage: "Привет! Хочу записаться на прикорневой объём на кератине 💜" },
    ],
    note: "от 7 см длины · при жирности у корней · при отсутствии природного объёма",
  },
];

const ServicesSection = () => (
  <section id="services" className="section-padding bg-secondary/50">
    <div className="max-w-6xl mx-auto">
      <AnimatedSection>
        <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
          Услуги
        </h2>
        <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-4">
          Прайс-лист
        </h3>
        <p className="text-center text-muted-foreground mb-16 max-w-md mx-auto">
          Нажмите на любую позицию — откроется форма записи
        </p>
      </AnimatedSection>

      <div className="grid md:grid-cols-2 gap-6">
        {cards.map((card, i) => (
          <AnimatedSection key={card.title} delay={i * 0.1}>
            <div className="relative rounded-2xl overflow-hidden bg-white shadow-sm border border-white/80 hover:-translate-y-1 transition-transform duration-300 h-full flex flex-col">
              {/* Card header */}
              <div className={`bg-gradient-to-br ${card.accent} px-7 pt-7 pb-5`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <span className="text-3xl">{card.emoji}</span>
                    <div>
                      <h4 className="text-lg font-bold text-foreground leading-tight">{card.title}</h4>
                      <p className="text-xs text-muted-foreground mt-0.5 leading-snug">{card.description}</p>
                    </div>
                  </div>
                  {card.badge && (
                    <span className="shrink-0 bg-primary text-white text-xs px-3 py-1 rounded-full font-semibold shadow-sm">
                      {card.badge}
                    </span>
                  )}
                </div>
              </div>

              {/* Divider */}
              <div className="h-px bg-gradient-to-r from-transparent via-primary/20 to-transparent" />

              {/* Price rows */}
              <div className="px-7 py-5 flex flex-col gap-0.5 flex-1">
                {card.rows.map((row) => (
                  <BookingPopup
                    key={row.name}
                    title={`Записаться: ${row.name}`}
                    vkMessage={row.vkMessage}
                  >
                    {({ onClick }) => (
                      <div
                        onClick={onClick}
                        className="group cursor-pointer rounded-xl px-3 py-3 -mx-3 hover:bg-primary/5 hover:ring-2 hover:ring-primary/30 active:bg-primary/10 transition-all duration-150"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-sm text-foreground/80 group-hover:text-foreground transition-colors leading-snug">
                            {row.name}
                          </span>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className="text-sm font-bold text-primary">{row.price}</span>
                            <svg className="w-3.5 h-3.5 text-primary/30 group-hover:text-primary/60 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                            </svg>
                          </div>
                        </div>
                        {row.note && (
                          <p className="text-xs text-muted-foreground/60 mt-1 leading-snug">{row.note}</p>
                        )}
                      </div>
                    )}
                  </BookingPopup>
                ))}
              </div>

              {/* Footer note */}
              {card.note && (
                <div className="px-7 pb-5">
                  <div className="rounded-xl bg-primary/5 border border-primary/10 px-4 py-2.5">
                    <p className="text-xs text-muted-foreground leading-relaxed">💡 {card.note}</p>
                  </div>
                </div>
              )}
            </div>
          </AnimatedSection>
        ))}
      </div>

      <AnimatedSection delay={0.4}>
        <p className="text-center text-xs text-muted-foreground/50 mt-8">
          Не знаете что выбрать? Напишите — подберу процедуру под ваши волосы бесплатно
        </p>
      </AnimatedSection>
    </div>
  </section>
);

export default ServicesSection;
