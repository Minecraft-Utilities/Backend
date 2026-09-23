import { defineConfig } from "eslint/config";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

export default defineConfig([
  ...nextCoreWebVitals,
  ...nextTypescript,

  {
    rules: {
      "@next/next/no-img-element": "off",
      "react-hooks/exhaustive-deps": "off",
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
]);
