/** Public legal configuration. VITE_ values intentionally appear in the public site build. */
export interface PrivacyDetails {
  operator: string;
  address: string;
  contact: string;
  retention: string;
}
export function isPrivacyReady(details: PrivacyDetails, approved: boolean): boolean {
  return approved && Object.values(details).every(v => v.trim().length > 0) &&
    !Object.values(details).some(v => /^(todo|tbd|placeholder|указать|заполнить)$/i.test(v.trim()));
}
export const privacy: PrivacyDetails = {
  operator: (import.meta.env.VITE_PRIVACY_OPERATOR as string | undefined)?.trim() ?? "",
  address: (import.meta.env.VITE_PRIVACY_OPERATOR_ADDRESS as string | undefined)?.trim() ?? "",
  contact: (import.meta.env.VITE_PRIVACY_CONTACT as string | undefined)?.trim() ?? "",
  retention: (import.meta.env.VITE_PRIVACY_RETENTION as string | undefined)?.trim() ?? "",
};
export const privacyReady = isPrivacyReady(privacy, import.meta.env.VITE_PRIVACY_APPROVED === "1");
