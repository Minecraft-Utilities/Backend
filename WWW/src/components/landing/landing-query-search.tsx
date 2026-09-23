"use client";

import Card, { CardContent, CardFooter } from "@/components/ui/card";
import { ErrorResponse } from "mcutils-js-api/dist/types/response/error-response";
import { useState } from "react";
import QuerySearch from "../lookup/query-search";

export default function LandingQuerySearch() {
  const [queryError, setQueryError] = useState<ErrorResponse | undefined>(undefined);

  return (
    <div className="flex w-full max-w-xl flex-col">
      <Card className="w-full">
        <CardContent>
          <QuerySearch landingPage setQueryError={setQueryError} />
        </CardContent>
        <CardFooter className="text-xs md:text-sm">
          {queryError ? (
            <p className="text-destructive">{queryError.message}</p>
          ) : (
            <p className="text-muted-foreground">Enter a Username / UUID / Domain to get started.</p>
          )}
        </CardFooter>
      </Card>
    </div>
  );
}
