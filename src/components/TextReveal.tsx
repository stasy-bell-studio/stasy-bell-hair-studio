import { motion } from "framer-motion";

interface Props {
  text: string;
  className?: string;
}

const TextReveal = ({ text, className = "" }: Props) => {
  const chars = text.split("");

  return (
    <h1 className={className}>
      {chars.map((char, i) => (
        <motion.span
          key={i}
          initial={{ opacity: 0, filter: "blur(8px)" }}
          animate={{ opacity: 1, filter: "blur(0px)" }}
          transition={{ duration: 0.4, delay: i * 0.05, ease: "easeOut" }}
          className="inline-block"
          style={{ whiteSpace: char === " " ? "pre" : undefined }}
        >
          {char}
        </motion.span>
      ))}
    </h1>
  );
};

export default TextReveal;
