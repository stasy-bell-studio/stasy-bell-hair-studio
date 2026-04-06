import { useEffect, useRef, useState } from "react";
import { motion, useInView } from "framer-motion";

interface Stat {
  value: number;
  suffix: string;
  label: string;
  decimals?: number;
}

const stats: Stat[] = [
  { value: 120, suffix: "+", label: "Довольных клиентов" },
  { value: 4.9, suffix: "", label: "Средняя оценка", decimals: 1 },
  { value: 3, suffix: " года", label: "Опыта" },
];

const AnimatedNumber = ({ stat, inView }: { stat: Stat; inView: boolean }) => {
  const [display, setDisplay] = useState("0");

  useEffect(() => {
    if (!inView) return;
    const duration = 1500;
    const steps = 40;
    const stepTime = duration / steps;
    let step = 0;

    const interval = setInterval(() => {
      step++;
      const progress = step / steps;
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = eased * stat.value;
      setDisplay(stat.decimals ? current.toFixed(stat.decimals) : Math.round(current).toString());
      if (step >= steps) clearInterval(interval);
    }, stepTime);

    return () => clearInterval(interval);
  }, [inView, stat]);

  return (
    <span className="text-4xl md:text-5xl font-bold text-foreground">
      {display}{stat.suffix}
    </span>
  );
};

const StatsCounter = () => {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: "-80px" });

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 30 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.7, delay: 0.3 }}
      className="flex justify-around mt-12 pt-10 border-t border-border"
    >
      {stats.map((stat) => (
        <div key={stat.label} className="text-center">
          <AnimatedNumber stat={stat} inView={inView} />
          <p className="text-sm text-muted-foreground mt-1">{stat.label}</p>
        </div>
      ))}
    </motion.div>
  );
};

export default StatsCounter;
