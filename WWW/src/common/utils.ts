import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import { env } from "./env";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Checks if the environment is production.
 *
 * @returns true if the environment is production
 */
export function isProduction(): boolean {
  return env.NEXT_PUBLIC_ENVIRONMENT === "production";
}

/**
 * Checks if the given query is an IP or a Domain (optionally with :port).
 *
 * @param query the query to check
 * @returns true if ip or domain
 */
export function isIpOrDomain(query: string): boolean {
  if (!query || typeof query !== "string") {
    return false;
  }

  let trimmed = query.trim();

  // Strip optional :port so "play.atmworld.nl:25570" validates the host part
  const withPort = trimmed.match(/^(.+):(\d{1,5})$/);
  if (withPort && !withPort[1].endsWith(":") && parseInt(withPort[2], 10) <= 65535) {
    trimmed = withPort[1];
  }

  // Check for IPv4 (e.g., 192.168.1.1)
  const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
  if (ipv4Regex.test(trimmed)) {
    // Validate that each octet is 0-255
    const octets = trimmed.split(".");
    return octets.every(octet => {
      const num = parseInt(octet, 10);
      return num >= 0 && num <= 255;
    });
  }

  // Check for IPv6 (e.g., 2001:0db8:85a3::8a2e:0370:7334 or ::1)
  const ipv6Regex =
    /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$/;
  if (ipv6Regex.test(trimmed)) {
    return true;
  }

  // Check for domain name
  // Valid domains: letters, numbers, hyphens, dots
  // Must have at least one dot and valid TLD
  const domainRegex = /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$/;
  if (domainRegex.test(trimmed)) {
    return true;
  }

  return false;
}

/**
 * Formats a number with commas.
 *
 * @param number the number to format
 * @returns the formatted number
 */
export function formatNumberWithCommas(number: number | string): string {
  if (typeof number === "number") {
    return number.toLocaleString();
  }
  return Number(number).toLocaleString();
}

/**
 * Capitalizes the first letter of the given string.
 *
 * @param string the string to capitalize
 * @returns the capitalized string
 */
export function capitalize(string: string): string {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

/**
 * Returns a random subset of the given array (Fisher–Yates shuffle, then slice).
 *
 * @param array the source array
 * @param count max number of entries to return
 * @returns up to `count` random entries
 */
export function getRandomEntries<T>(array: T[], count: number): T[] {
  const shuffled = [...array];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled.slice(0, count);
}

/**
 * Truncates a string to the given length.
 *
 * @param string the string to truncate
 * @param length the length to truncate to
 * @returns the truncated string
 */
export function truncateString(string: string, length: number): string {
  return string.length > length ? string.substring(0, length) + "..." : string;
}
