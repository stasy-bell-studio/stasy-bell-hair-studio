import { ReactNode } from "react";

interface Props {
  children: ReactNode;
  reverse?: boolean;
  className?: string;
}

const Marquee = ({ children, reverse = false, className = "" }: Props) => (
  <div className={`group flex overflow-hidden ${className}`}>
    <div
      className={`flex shrink-0 gap-6 py-2 ${
        reverse ? "animate-marquee-reverse" : "animate-marquee"
      } group-hover:[animation-play-state:paused]`}
    >
      {children}
      {children}
    </div>
  </div>
);

export default Marquee;
