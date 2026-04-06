import AnimatedSection from "./AnimatedSection";
import TiltCard from "./TiltCard";
import portfolio1 from "@/assets/portfolio-1.png";
import portfolio2 from "@/assets/portfolio-2.png";
import portfolio3 from "@/assets/portfolio-3.png";
import portfolio4 from "@/assets/portfolio-4.png";
import portfolio5 from "@/assets/portfolio-5.png";
import portfolio6 from "@/assets/portfolio-6.png";

const images = [portfolio1, portfolio2, portfolio3, portfolio4, portfolio5, portfolio6];

const PortfolioSection = () => (
  <section id="portfolio" className="section-padding bg-secondary/50">
    <div className="max-w-6xl mx-auto">
      <AnimatedSection>
        <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
          Примеры работ
        </h2>
        <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-16">
          Портфолио
        </h3>
      </AnimatedSection>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-3 md:gap-4">
        {images.map((src, i) => (
          <AnimatedSection key={i} delay={i * 0.08}>
            <TiltCard className="aspect-square rounded-2xl overflow-hidden group cursor-pointer tilt-card">
              <img
                src={src}
                alt={`Результат работы ${i + 1}`}
                className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
              />
            </TiltCard>
          </AnimatedSection>
        ))}
      </div>

      <AnimatedSection delay={0.3}>
        <div className="text-center mt-12">
          <a
            href="https://www.instagram.com/_stacybel_"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm font-medium text-primary hover:text-primary-deep transition-colors"
          >
            Больше работ в Instagram →
          </a>
        </div>
      </AnimatedSection>
    </div>
  </section>
);

export default PortfolioSection;
