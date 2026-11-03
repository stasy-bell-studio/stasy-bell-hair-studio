// Copied verbatim from the active site's src/components/ServicesSection.tsx.
// Any future catalog changes must be compared to the live site, not invented.
export interface PriceItem {
  label: string;
  price: string;
  addOnOnly?: boolean;
}
export interface PriceGroup {
  title: string;
  description: string;
  note?: string;
  items: PriceItem[];
}

export const priceGroups: PriceGroup[] = [
  {
    title: "Кератин / Ботокс",
    description: "Для гладкости и блеска",
    note: "+10% за густые волосы · +15% за очень густые",
    items: [
      { label: "По плечи", price: "6 200 ₽" },
      { label: "Ниже плеч", price: "6 500 ₽" },
      { label: "По лопатки", price: "6 900 ₽" },
      { label: "По талию", price: "7 200 ₽" },
      { label: "По поясницу", price: "7 700 ₽" },
    ],
  },
  {
    title: "Здоровье для волос",
    description: "Восстановление и уход",
    items: [
      { label: "По плечи", price: "3 700 ₽" },
      { label: "По лопатки", price: "3 900 ₽" },
      { label: "По талию", price: "4 200 ₽" },
    ],
  },
  {
    title: "Комбо-процедуры",
    description: "Комплексный уход",
    items: [
      { label: "Уходовая стрижка + пилинг + Здоровье", price: "8 000 ₽" },
      { label: "Здоровье для волос + кератин/ботокс", price: "+2 400 ₽", addOnOnly: true },
      { label: "Здоровье + пилинг + прикорневой объём", price: "8 500 ₽" },
    ],
  },
  {
    title: "Дополнительные услуги",
    description: "Детали, которые меняют результат",
    note: "Прикорневой объём: от 7 см длины, при жирности у корней и отсутствии природного объёма",
    items: [
      { label: "Пилинг кожи головы", price: "750 ₽" },
      { label: "Прикорневой объём на кератине", price: "5 500 ₽" },
    ],
  },
];
