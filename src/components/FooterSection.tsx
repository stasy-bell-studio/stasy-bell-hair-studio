import { Link } from "react-router-dom";
import SocialLinks from "./SocialLinks";

const FooterSection = () => (
  <footer className="section-padding pb-12 pt-12 text-center">
    <SocialLinks />
    <p className="mt-8 text-sm text-muted-foreground">
      © Стэйси Белл · Анастасия · С заботой о ваших волосах 💜
      {" · "}
      <a
        href="https://dikidi.net/2037517"
        target="_blank"
        rel="noopener noreferrer"
        className="text-primary hover:text-primary-deep transition-colors"
      >
        Онлайн-запись
      </a>
    </p>
    <p className="mt-3 text-xs text-muted-foreground/50">
      <Link to="/offer" className="hover:text-muted-foreground transition-colors">
        Договор оферты
      </Link>
      {" · "}
      <Link to="/policy" className="hover:text-muted-foreground transition-colors">
        Политика конфиденциальности
      </Link>
    </p>
  </footer>
);

export default FooterSection;
