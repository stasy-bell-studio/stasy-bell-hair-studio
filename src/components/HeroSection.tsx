import { useState } from "react";
import TextReveal from "./TextReveal";
import AnimatedSection from "./AnimatedSection";
import BookingFormModal from "./BookingFormModal";

const HeroSection = () => {
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden">
      {/* Animated mesh gradient background */}
      <div className="absolute inset-0 animate-mesh-gradient" style={{
        background: "linear-gradient(-45deg, hsl(255 85% 74%), hsl(270 60% 85%), hsl(0 0% 100%), hsl(261 30% 54%), hsl(270 60% 85%))",
        backgroundSize: "400% 400%",
      }} />
      <div className="absolute inset-0 bg-background/30" />

      <div className="relative z-10 text-center px-6 max-w-3xl mx-auto">
        <TextReveal
          text="Стэйси Белл"
          className="text-5xl md:text-7xl lg:text-8xl font-bold tracking-tighter text-foreground mb-4 whitespace-nowrap"
        />
        <AnimatedSection delay={0.6}>
          <p className="text-3xl md:text-4xl font-semibold text-foreground leading-tight">
            Волосы, в которые влюбляешься заново
          </p>
          <p className="text-lg font-normal text-muted-foreground mt-3 mb-10">
            Кератин · Ботокс · Прикорневой объём · Лечение — премиальными составами в Санкт-Петербурге
          </p>
        </AnimatedSection>
        <AnimatedSection delay={0.9}>
          <button
            onClick={() => setModalOpen(true)}
            className="inline-block text-base font-semibold px-10 py-4 rounded-full bg-primary text-primary-foreground hover:shadow-lg hover:shadow-primary/25 transition-all duration-300 hover:-translate-y-0.5"
          >
            Записаться
          </button>
          <p className="text-sm text-muted-foreground/60 mt-4">
            120+ довольных клиентов · результат от 1 процедуры
          </p>
        </AnimatedSection>
      </div>

      <BookingFormModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </section>
  );
};

export default HeroSection;
