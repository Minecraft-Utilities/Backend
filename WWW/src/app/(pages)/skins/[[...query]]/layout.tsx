import Background from "@/components/background";

export default function SkinsLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/skins.webp" />
      {children}
    </>
  );
}
