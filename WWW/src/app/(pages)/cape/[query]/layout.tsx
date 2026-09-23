import Background from "@/components/background";

export default function CapeLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/cape.jpg" />
      {children}
    </>
  );
}
