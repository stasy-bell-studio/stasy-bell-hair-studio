import AnimatedSection from "./AnimatedSection";

const steps = [
  {
    number: "01",
    emoji: "💬",
    title: "Пишете мне",
    desc: "WhatsApp, Telegram или VK — как удобнее",
    color: "from-violet-500 to-purple-600",
    bg: "bg-violet-50",
    border: "border-violet-100",
  },
  {
    number: "02",
    emoji: "🔍",
    title: "Консультация",
    desc: "Разбираемся с вашими волосами и подбираем процедуру",
    color: "from-purple-500 to-fuchsia-600",
    bg: "bg-purple-50",
    border: "border-purple-100",
  },
  {
    number: "03",
    emoji: "📅",
    title: "Выбираете время",
    desc: "Договариваемся на удобный день и час",
    color: "from-fuchsia-500 to-pink-500",
    bg: "bg-fuchsia-50",
    border: "border-fuchsia-100",
  },
  {
    number: "04",
    emoji: "✨",
    title: "Результат",
    desc: "Уходите с волосами, в которые влюбляетесь заново",
    color: "from-pink-500 to-rose-500",
    bg: "bg-pink-50",
    border: "border-pink-100",
  },
];

const HowItWorksSection = () => (
  <section id="how" className="section-padding bg-white overflow-hidden">
    <div className="max-w-6xl mx-auto">
      <AnimatedSection>
        <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
          Как это работает
        </h2>
        <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-4">
          Как проходит запись
        </h3>
        <p className="text-center text-muted-foreground mb-12 max-w-sm mx-auto">
          От первого сообщения до красивых волос — 4 простых шага
        </p>
      </AnimatedSection>

      {/* Grid: 2 cols on mobile, 4 on desktop */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
        {steps.map((step, i) => (
          <AnimatedSection key={step.number} delay={i * 0.12}>
            <div className={`rounded-2xl border ${step.border} ${step.bg} p-5 flex flex-col items-center text-center gap-3 h-full hover:-translate-y-1 transition-transform duration-300`}>
              {/* Number badge + icon */}
              <div className="relative mt-1">
                <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${step.color} flex items-center justify-center shadow-md`}>
                  <span className="text-2xl">{step.emoji}</span>
                </div>
                <span className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-white border border-border text-[10px] font-bold text-foreground/50 flex items-center justify-center shadow-sm">
                  {i + 1}
                </span>
              </div>

              <div>
                <h4 className="text-sm md:text-base font-bold text-foreground mb-1">{step.title}</h4>
                <p className="text-xs md:text-sm text-muted-foreground leading-relaxed">{step.desc}</p>
              </div>
            </div>
          </AnimatedSection>
        ))}
      </div>

      {/* Connector line — desktop only */}
      <div className="hidden lg:flex justify-center mt-8 gap-1 items-center">
        {steps.map((step, i) => (
          <div key={step.number} className="flex items-center gap-1">
            <div className={`w-2 h-2 rounded-full bg-gradient-to-br ${step.color} opacity-60`} />
            {i < steps.length - 1 && (
              <div className="w-16 h-px border-t-2 border-dashed border-primary/20" />
            )}
          </div>
        ))}
      </div>

    </div>
  </section>
);

export default HowItWorksSection;
