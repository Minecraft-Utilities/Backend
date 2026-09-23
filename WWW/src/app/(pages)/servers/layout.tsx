import Background from "@/components/background";

export default function ServersLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Background url="/media/backgrounds/servers.webp" />
      {children}
    </>
  );
}
