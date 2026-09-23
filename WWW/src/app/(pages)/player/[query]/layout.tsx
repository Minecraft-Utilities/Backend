import Background from "@/components/background";

export default function PlayerLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/player.webp" />
      {children}
    </>
  );
}
