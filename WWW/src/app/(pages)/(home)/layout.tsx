import Background from "@/components/background";

export default function HomeLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/landing.webp" />
      {children}
    </>
  );
}
