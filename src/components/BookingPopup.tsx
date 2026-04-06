import { useState, useRef, useEffect, ReactNode } from "react";
import { createPortal } from "react-dom";
import { motion, AnimatePresence } from "framer-motion";
import { CalendarDays } from "lucide-react";
import { useIsMobile } from "@/hooks/use-mobile";

const WA_LINK = "https://wa.me/79145711243";
const TG_LINK = "https://t.me/Stasy10";
const DIKIDI_LINK = "https://dikidi.net/2037517";
const VK_BASE = "https://vk.com/im?sel=829464173&msg=";
const DEFAULT_VK_MSG = "Привет! Хочу записаться на процедуру 💜";

const WhatsAppIcon = () => (
  <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor">
    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347z" />
    <path d="M12 0C5.373 0 0 5.373 0 12c0 2.127.555 4.126 1.527 5.861L.06 23.487a.5.5 0 0 0 .612.612l5.626-1.467A11.938 11.938 0 0 0 12 24c6.627 0 12-5.373 12-12S18.627 0 12 0zm0 22a9.94 9.94 0 0 1-5.332-1.544l-.382-.227-3.332.869.87-3.332-.228-.382A9.94 9.94 0 0 1 2 12C2 6.477 6.477 2 12 2s10 4.477 10 10-4.477 10-10 10z" />
  </svg>
);

const TelegramIcon = () => (
  <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor">
    <path d="M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0h-.056zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.479.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z" />
  </svg>
);

const VKIcon = () => (
  <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor">
    <path d="M15.684 0H8.316C1.592 0 0 1.592 0 8.316v7.368C0 22.408 1.592 24 8.316 24h7.368C22.408 24 24 22.408 24 15.684V8.316C24 1.592 22.391 0 15.684 0zm3.692 17.123h-1.744c-.66 0-.864-.525-2.05-1.727-1.033-1-1.49-1.135-1.744-1.135-.356 0-.458.102-.458.593v1.575c0 .424-.135.678-1.253.678-1.846 0-3.896-1.118-5.335-3.202C4.624 10.857 4.03 8.57 4.03 8.096c0-.254.102-.491.593-.491h1.744c.44 0 .61.203.78.678.847 2.49 2.27 4.675 2.848 4.675.22 0 .322-.102.322-.66V9.721c-.068-1.186-.695-1.287-.695-1.71 0-.204.17-.407.44-.407h2.744c.373 0 .508.203.508.643v3.473c0 .372.17.508.271.508.22 0 .407-.136.813-.542 1.254-1.406 2.151-3.574 2.151-3.574.119-.254.322-.491.763-.491h1.744c.525 0 .643.27.525.643-.22 1.017-2.354 4.031-2.354 4.031-.186.305-.254.44 0 .78.186.254.796.779 1.203 1.253.745.847 1.32 1.558 1.473 2.05.17.49-.085.744-.576.744z" />
  </svg>
);

function buildOptions(vkMessage?: string) {
  const vkMsg = vkMessage || DEFAULT_VK_MSG;
  return [
    { label: "Онлайн-запись", href: DIKIDI_LINK, icon: <CalendarDays className="w-5 h-5" />, emoji: "📅" },
    { label: "WhatsApp", href: WA_LINK, icon: <WhatsAppIcon /> },
    { label: "Telegram", href: TG_LINK, icon: <TelegramIcon /> },
    { label: "ВКонтакте", href: `${VK_BASE}${encodeURIComponent(vkMsg)}`, icon: <VKIcon /> },
  ];
}

interface BookingPopupProps {
  children: (props: { onClick: () => void }) => ReactNode;
  title?: string;
  vkMessage?: string;
}

const BookingPopup = ({ children, title, vkMessage }: BookingPopupProps) => {
  const [open, setOpen] = useState(false);
  const isMobile = useIsMobile();
  const popoverRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number; openUp: boolean }>({ top: 0, left: 0, openUp: false });

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (
        popoverRef.current && !popoverRef.current.contains(e.target as Node) &&
        triggerRef.current && !triggerRef.current.contains(e.target as Node)
      ) setOpen(false);
    };
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", handleClick);
    document.addEventListener("keydown", handleKey);
    return () => { document.removeEventListener("mousedown", handleClick); document.removeEventListener("keydown", handleKey); };
  }, [open]);

  const handleOpen = () => {
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      const openUp = spaceBelow < 260;
      setPopoverPos({
        top: openUp ? rect.top + window.scrollY : rect.bottom + window.scrollY,
        left: rect.left + rect.width / 2,
        openUp,
      });
    }
    setOpen(!open);
  };

  const options = buildOptions(vkMessage);

  const menu = (
    <div className="flex flex-col gap-1 p-2">
      {options.map((opt) => (
        <a
          key={opt.label}
          href={opt.href}
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => setOpen(false)}
          className="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium text-foreground hover:bg-accent transition-colors"
        >
          <span className="text-primary">{opt.icon}</span>
          {opt.label}
        </a>
      ))}
    </div>
  );

  return (
    <>
      <div ref={triggerRef} className="inline-block">
        {children({ onClick: handleOpen })}
      </div>

      {/* Desktop popover via portal */}
      {createPortal(
        <AnimatePresence>
          {open && !isMobile && (
            <motion.div
              ref={popoverRef}
              initial={{ opacity: 0, scale: 0.95, y: popoverPos.openUp ? 4 : -4 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: popoverPos.openUp ? 4 : -4 }}
              transition={{ duration: 0.2 }}
              style={{
                position: "absolute",
                top: popoverPos.openUp ? "auto" : popoverPos.top + 8,
                bottom: popoverPos.openUp ? `${document.documentElement.scrollHeight - popoverPos.top + 8}px` : "auto",
                left: popoverPos.left,
                transform: "translateX(-50%)",
              }}
              className="z-[9999] w-72 bg-white/80 backdrop-blur-2xl border border-border rounded-xl shadow-lg"
            >
              {title && (
                <p className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground truncate">{title}</p>
              )}
              {menu}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )}

      {/* Mobile bottom sheet via portal */}
      {createPortal(
        <AnimatePresence>
          {open && isMobile && (
            <>
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="fixed inset-0 bg-black/30 z-[9998]"
                onClick={() => setOpen(false)}
              />
              <motion.div
                ref={popoverRef}
                initial={{ y: "100%" }}
                animate={{ y: 0 }}
                exit={{ y: "100%" }}
                transition={{ type: "spring", damping: 25, stiffness: 300 }}
                className="fixed bottom-0 left-0 right-0 z-[9999] bg-background rounded-t-2xl shadow-2xl pb-safe"
              >
                <div className="w-10 h-1 bg-muted-foreground/20 rounded-full mx-auto mt-3 mb-1" />
                <p className="text-center text-sm font-semibold text-foreground px-4 pt-2 pb-1">
                  {title || "Выберите способ записи"}
                </p>
                {menu}
                <div className="px-4 pb-6 pt-1">
                  <button
                    onClick={() => setOpen(false)}
                    className="w-full py-3 rounded-xl text-sm font-medium text-muted-foreground bg-secondary"
                  >
                    Отмена
                  </button>
                </div>
              </motion.div>
            </>
          )}
        </AnimatePresence>,
        document.body
      )}
    </>
  );
};

export default BookingPopup;
