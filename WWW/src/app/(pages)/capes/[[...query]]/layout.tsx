import Background from "@/components/background";

export default function CapesLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/capes.webp" />
      {children}
    </>
  );
}
