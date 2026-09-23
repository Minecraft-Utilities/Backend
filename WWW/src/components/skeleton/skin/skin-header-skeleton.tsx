import Skeleton from "@/components/skeleton/ui/skeleton";

export default function SkinHeaderSkeleton() {
  return (
    <header className="w-full max-w-[980px] text-left">
      <Skeleton className="h-9 w-72 rounded-md sm:h-10 sm:w-96" />
      <Skeleton className="mt-2 h-5 w-64 rounded-md sm:w-72" />
    </header>
  );
}
