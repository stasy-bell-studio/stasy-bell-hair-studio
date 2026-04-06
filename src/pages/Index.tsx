import Navbar from "@/components/Navbar";
import HeroSection from "@/components/HeroSection";
import AboutSection from "@/components/AboutSection";
import ServicesSection from "@/components/ServicesSection";
import HowItWorksSection from "@/components/HowItWorksSection";
import PortfolioSection from "@/components/PortfolioSection";
import ReviewsSection from "@/components/ReviewsSection";
import FAQSection from "@/components/FAQSection";
import CTABanner from "@/components/CTABanner";
import ContactsSection from "@/components/ContactsSection";
import FooterSection from "@/components/FooterSection";
import SectionDivider from "@/components/SectionDivider";

const Index = () => (
  <>
    <Navbar />
    <main>
      <HeroSection />
      <SectionDivider />
      <AboutSection />
      <SectionDivider />
      <ServicesSection />
      <SectionDivider />
      <HowItWorksSection />
      <SectionDivider />
      <PortfolioSection />
      <SectionDivider />
      <ReviewsSection />
      <SectionDivider />
      <FAQSection />
      <SectionDivider />
      <CTABanner />
      <SectionDivider />
      <ContactsSection />
    </main>
    <FooterSection />
  </>
);

export default Index;
