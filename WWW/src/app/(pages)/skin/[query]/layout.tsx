import Background from "@/components/background";

export default function SkinLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/skin.webp" />
      {children}
    </>
  );
}
