import Background from "@/components/background";

export default function NamesLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/names.webp" />
      {children}
    </>
  );
}
