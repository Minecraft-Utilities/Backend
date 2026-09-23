import CapeCardSkeleton from "@/components/skeleton/capes/cape-card-skeleton";

export default function CapesLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Minecraft Capes</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          A list of all official Minecraft capes and which players own and have used them
        </p>
      </header>

      <div className="w-full max-w-[980px]">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
          {Array.from({ length: 25 }).map((_, i) => (
            <CapeCardSkeleton key={i} />
          ))}
        </div>
      </div>
    </div>
  );
}
