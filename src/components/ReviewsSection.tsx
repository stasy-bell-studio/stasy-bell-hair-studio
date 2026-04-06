import { Star } from "lucide-react";
import AnimatedSection from "./AnimatedSection";
import Marquee from "./Marquee";

const reviews = [
  { text: "Делала кератин у Насти — волосы как в рекламе! Держится уже 4 месяца", author: "Мария К." },
  { text: "После ботокса волосы стали плотные и блестящие. Теперь хожу только к ней!", author: "Алина Д." },
  { text: "Настя очень внимательная, подобрала процедуру именно под мой тип волос", author: "Виктория С." },
  { text: "Наконец-то нашла своего мастера! Профессионально, уютно, результат огонь 🔥", author: "Екатерина М." },
];

const ReviewCard = ({ text, author }: { text: string; author: string }) => (
  <div className="glass-card p-6 md:p-8 w-[280px] md:w-[340px] shrink-0">
    <div className="flex gap-0.5 mb-4">
      {Array.from({ length: 5 }).map((_, j) => (
        <Star key={j} className="w-4 h-4 fill-amber-400 text-amber-400" />
      ))}
    </div>
    <p className="text-foreground mb-4 leading-relaxed text-sm md:text-base">"{text}"</p>
    <p className="text-sm font-semibold text-muted-foreground">— {author}</p>
  </div>
);

const ReviewsSection = () => (
  <section id="reviews" className="section-padding max-w-full mx-auto overflow-hidden">
    <AnimatedSection>
      <h2 className="text-sm font-semibold uppercase tracking-widest text-primary text-center mb-4">
        Отзывы
      </h2>
      <h3 className="text-3xl md:text-5xl font-bold text-foreground text-center mb-16">
        Что говорят клиенты
      </h3>
    </AnimatedSection>

    <div className="space-y-6 overflow-hidden">
      <Marquee>
        {reviews.slice(0, 2).map((r) => (
          <ReviewCard key={r.author} text={r.text} author={r.author} />
        ))}
      </Marquee>
      <Marquee reverse>
        {reviews.slice(2).map((r) => (
          <ReviewCard key={r.author} text={r.text} author={r.author} />
        ))}
      </Marquee>
    </div>
  </section>
);

export default ReviewsSection;
