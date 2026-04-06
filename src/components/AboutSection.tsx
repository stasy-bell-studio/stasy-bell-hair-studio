import AnimatedSection from "./AnimatedSection";
import StatsCounter from "./StatsCounter";
import masterPhoto from "@/assets/master-photo.png";

const AboutSection = () => (
  <section id="about" className="section-padding max-w-6xl mx-auto">
    <div className="grid md:grid-cols-2 gap-16 items-center">
      <AnimatedSection>
        <img
          src={masterPhoto}
          alt="Настя — мастер Стэйси Белл"
          className="aspect-[4/5] rounded-2xl object-cover object-top w-full"
        />
      </AnimatedSection>
      <div>
        <AnimatedSection delay={0.2}>
          <h2 className="text-sm font-semibold uppercase tracking-widest text-primary mb-4">
            О мастере
          </h2>
          <h3 className="text-3xl md:text-4xl font-bold text-foreground mb-6 leading-tight">
            Настя — ваш мастер по уходу за волосами
          </h3>
          <p className="text-lg leading-relaxed text-muted-foreground mb-4">
            Началось всё с собственных волос — устала бороться. Разобралась, нашла решение, захотелось делиться. 
            Использую только премиальные составы — не потому что «дорого&nbsp;= круто», а потому что дешёвые просто 
            не дают того результата, ради которого вы приходите.
          </p>
          <p className="text-lg leading-relaxed text-muted-foreground">
            Каждый клиент для меня — не просто процедура. Это возможность сделать так, чтобы человек ушёл счастливее, чем пришёл.
          </p>
        </AnimatedSection>
        <StatsCounter />
      </div>
    </div>
  </section>
);

export default AboutSection;
