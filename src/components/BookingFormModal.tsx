import { useState } from "react";
import { X, Loader2, CheckCircle2 } from "lucide-react";
import { submitBooking, buildVkMessage, openVkChat, BookingData } from "@/lib/bookingApi";

const SERVICES = [
  "Кератиновое выпрямление волос",
  "Ботокс волос",
  "Здоровье для волос (восстановление)",
  "Пилинг кожи головы",
  "Прикорневой объём на кератине",
  "Комбо-процедура",
];

interface Props {
  open: boolean;
  onClose: () => void;
  prefilledService?: string;
}

type Status = "idle" | "loading" | "success" | "error";

export default function BookingFormModal({ open, onClose, prefilledService }: Props) {
  const [form, setForm] = useState<BookingData>({
    name: "",
    phone: "",
    service: prefilledService || "",
    date: "",
    time: "",
    comment: "",
  });
  const [status, setStatus] = useState<Status>("idle");
  const [errorMsg, setErrorMsg] = useState("");

  if (!open) return null;

  const handleChange = (field: keyof BookingData) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus("loading");
    setErrorMsg("");
    try {
      await submitBooking(form);
      setStatus("success");
    } catch {
      setStatus("error");
      setErrorMsg("Не удалось отправить. Проверьте соединение и попробуйте ещё раз.");
    }
  };

  const handleVk = () => {
    openVkChat(buildVkMessage(form));
    onClose();
  };

  const inputCls =
    "w-full rounded-xl border border-border bg-white px-4 py-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition";

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      {/* backdrop */}
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />

      {/* card */}
      <div className="relative z-10 w-full max-w-md rounded-2xl bg-white shadow-2xl overflow-hidden">
        {/* header */}
        <div className="bg-gradient-to-r from-violet-500 to-purple-600 px-6 py-5">
          <div className="flex items-start justify-between">
            <div>
              <h2 className="text-lg font-bold text-white">Записаться</h2>
              <p className="text-sm text-white/70 mt-0.5">Заполни форму — мы всё согласуем в VK</p>
            </div>
            <button onClick={onClose} className="text-white/70 hover:text-white transition mt-0.5">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* body */}
        <div className="px-6 py-5">
          {status === "success" ? (
            <div className="text-center py-4 space-y-4">
              <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto" />
              <div>
                <p className="font-semibold text-foreground text-base">Заявка отправлена! 💜</p>
                <p className="text-sm text-muted-foreground mt-1">
                  Данные сохранены. Теперь отправь сообщение в VK — Настя подтвердит время.
                </p>
              </div>
              <button
                onClick={handleVk}
                className="w-full py-3 rounded-xl font-semibold text-white text-sm"
                style={{ background: "#0077FF" }}
              >
                Написать в VK
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-muted-foreground mb-1 block">Имя *</label>
                  <input
                    required
                    className={inputCls}
                    placeholder="Анастасия"
                    value={form.name}
                    onChange={handleChange("name")}
                  />
                </div>
                <div>
                  <label className="text-xs font-medium text-muted-foreground mb-1 block">Телефон *</label>
                  <input
                    required
                    type="tel"
                    className={inputCls}
                    placeholder="+7 900 000 00 00"
                    value={form.phone}
                    onChange={handleChange("phone")}
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-medium text-muted-foreground mb-1 block">Услуга *</label>
                <select
                  required
                  className={inputCls}
                  value={form.service}
                  onChange={handleChange("service")}
                >
                  <option value="">Выберите услугу</option>
                  {SERVICES.map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-muted-foreground mb-1 block">Дата *</label>
                  <input
                    required
                    type="date"
                    className={inputCls}
                    min={new Date().toISOString().split("T")[0]}
                    value={form.date}
                    onChange={handleChange("date")}
                  />
                </div>
                <div>
                  <label className="text-xs font-medium text-muted-foreground mb-1 block">Время *</label>
                  <select
                    required
                    className={inputCls}
                    value={form.time}
                    onChange={handleChange("time")}
                  >
                    <option value="">Выберите</option>
                    {["09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00","19:00"].map((t) => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="text-xs font-medium text-muted-foreground mb-1 block">Комментарий</label>
                <textarea
                  rows={2}
                  className={inputCls + " resize-none"}
                  placeholder="Длина волос, пожелания..."
                  value={form.comment}
                  onChange={handleChange("comment")}
                />
              </div>

              {status === "error" && (
                <p className="text-xs text-red-500">{errorMsg}</p>
              )}

              <button
                type="submit"
                disabled={status === "loading"}
                className="w-full py-3 rounded-xl font-semibold text-white text-sm bg-gradient-to-r from-violet-500 to-purple-600 hover:opacity-90 transition disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {status === "loading" ? (
                  <><Loader2 className="w-4 h-4 animate-spin" /> Отправляем...</>
                ) : (
                  "Отправить заявку →"
                )}
              </button>

              <p className="text-xs text-muted-foreground text-center">
                После отправки вы перейдёте в VK для подтверждения времени
              </p>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
