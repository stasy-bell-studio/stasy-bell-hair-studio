import { useRef, useState, ReactNode } from "react";

interface Props {
  children: ReactNode;
  className?: string;
}

const SpotlightCard = ({ children, className = "" }: Props) => {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [hovering, setHovering] = useState(false);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    setPos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  return (
    <div
      ref={ref}
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      className={`relative overflow-hidden ${className}`}
    >
      <div
        className="pointer-events-none absolute inset-0 z-10 transition-opacity duration-300"
        style={{
          opacity: hovering ? 1 : 0,
          background: `radial-gradient(200px circle at ${pos.x}px ${pos.y}px, hsl(var(--primary) / 0.1), transparent 80%)`,
        }}
      />
      {children}
    </div>
  );
};

export default SpotlightCard;
