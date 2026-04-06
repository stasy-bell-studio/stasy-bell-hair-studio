import AnimatedSection from "./AnimatedSection";

const VK_LINK = "https://vk.com/im?sel=829464173&msg=" + encodeURIComponent("Привет! Хочу записаться на процедуру 💜");

const CTABanner = () => (
  <section className="section-padding">
    <div className="max-w-4xl mx-auto rounded-3xl p-12 md:p-16 text-center animate-gradient-shift" style={{ background: "linear-gradient(135deg, hsl(var(--primary)), hsl(var(--primary-deep)), hsl(var(--primary)))", backgroundSize: "200% 200%" }}>
      <AnimatedSection>
        <h3 className="text-2xl md:text-4xl font-bold text-primary-foreground mb-4 leading-tight">
          Не знаете, что выбрать?
        </h3>
        <p className="text-primary-foreground/80 text-lg mb-10 max-w-xl mx-auto">
          Напишите — за 2 минуты подберу процедуру под ваш тип волос. Бесплатно.
        </p>
        <div className="flex flex-col sm:flex-row gap-4 justify-center">
          <a
            href="https://dikidi.net/2037517"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-block px-8 py-3.5 rounded-full bg-primary-foreground text-foreground font-semibold text-sm hover:opacity-90 transition-opacity"
          >
            📅 Записаться онлайн
          </a>
          <a
            href={VK_LINK}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-block px-8 py-3.5 rounded-full border-2 border-primary-foreground/30 text-primary-foreground font-semibold text-sm hover:bg-primary-foreground/10 transition-colors"
          >
            Написать ВКонтакте
          </a>
        </div>
        <p className="mt-6 text-sm text-primary-foreground/60">
          Также:{" "}
          <a href="https://wa.me/79145711243" target="_blank" rel="noopener noreferrer" className="underline hover:text-primary-foreground/80 transition-colors">WhatsApp</a>
          {" | "}
          <a href="https://t.me/Stasy10" target="_blank" rel="noopener noreferrer" className="underline hover:text-primary-foreground/80 transition-colors">Telegram</a>
        </p>
      </AnimatedSection>
    </div>
  </section>
);

export default CTABanner;
