"use client";

import { timeAgo } from "@/common/time-utils";
import { useEffect, useState } from "react";

interface TimeAgoProps {
  date: Date;
}

export default function TimeAgo({ date }: TimeAgoProps) {
  const [text, setText] = useState<string>("");

  useEffect(() => {
    setText(timeAgo(date));
    const id = setInterval(() => setText(timeAgo(date)), 1000);
    return () => clearInterval(id);
  }, [date]);

  return <>{text}</>;
}
