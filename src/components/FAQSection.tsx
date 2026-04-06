import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import AnimatedSection from "./AnimatedSection";

const faqs = [
  { q: "Можно ли краситься после кератина?", a: "Да. Либо до процедуры, либо через 1-2 недели после." },
  { q: "Что выбрать: ботокс или кератин?", a: "Кератин — для выпрямления и гладкости. Ботокс — для восстановления и плотности без выпрямления." },
  { q: "Сколько держится эффект?", a: "От 2 до 5 месяцев — зависит от ухода и типа волос." },
  { q: "Нужен ли специальный шампунь?", a: "Подберу шампунь под ваш тип кожи головы — дам рекомендации после процедуры." },
  { q: "Можно беременным/кормящим?", a: "Кератин и ботокс при беременности и кормлении делать нельзя. Но можно: восстановление волос, пилинг кожи головы, полировка волос без потери длины." },
  { q: "Можно ли детям и подросткам?", a: "Маленьким детям подойдут уходовые процедуры для дисциплины волос. С 12 лет можно делать кератин и ботокс." },
  { q: "Кому подходит прикорневой объём?", a: "Процедура идеальна, если: длина волос от 7 см, волосы быстро жирнятся, распадаются на затылке или от природы нет объёма." },
  { q: "Как подготовиться к процедуре?", a: "Приходите с чистыми волосами без укладочных средств." },
];

const FAQSection = () => (
  <section id="faq" className="section-padding bg-secondary/50">
    <div className="max-w-3xl mx-auto">
      <AnimatedSection>
        <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
          FAQ
        </h2>
        <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-16">
          Частые вопросы
        </h3>
      </AnimatedSection>

      <AnimatedSection delay={0.15}>
        <Accordion type="single" collapsible className="space-y-3">
          {faqs.map((faq, i) => (
            <AccordionItem key={i} value={`faq-${i}`} className="glass-card px-6 border-none">
              <AccordionTrigger className="text-left font-semibold text-foreground hover:no-underline py-5 min-h-[48px]">
                {faq.q}
              </AccordionTrigger>
              <AccordionContent className="text-muted-foreground pb-5 text-sm md:text-base">
                {faq.a}
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </AnimatedSection>
    </div>
  </section>
);

export default FAQSection;
