"use client";

import { ErrorResponse } from "mcutils-js-api/dist/types/response/error-response";
import { useState } from "react";
import QuerySearch from "../lookup/query-search";
import Card from "../ui/card";

export default function QuerySearchNavbar() {
  const [queryError, setQueryError] = useState<ErrorResponse | undefined>(undefined);

  return (
    <div className="relative flex min-w-0 flex-col items-end">
      <QuerySearch setQueryError={setQueryError} className="w-40 min-w-0 md:w-56" />
      {queryError && (
        <Card className="bg-card absolute top-full right-0 z-50 mt-1 w-full max-w-[min(100vw-2rem,24rem)] min-w-48 px-3 py-2 shadow-md">
          <p className="text-destructive text-xs leading-snug" role="alert">
            {queryError.message}
          </p>
        </Card>
      )}
    </div>
  );
}
