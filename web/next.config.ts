import path from "node:path";
import type { NextConfig } from "next";

/**
 * Static export for GitHub Pages (`<user>.github.io/<repo>/`).
 *
 * The deploy workflow sets PAGES_BASE_PATH=/&lt;repo&gt; so assets resolve under
 * the project sub-path; local dev stays at the root. The same value is re-
 * exported as NEXT_PUBLIC_BASE_PATH because the OAuth redirect URL is built
 * in client components.
 */
const basePath = process.env.PAGES_BASE_PATH ?? "";

const nextConfig: NextConfig = {
  output: "export",
  // Pin the workspace root: stray lockfiles above this folder must not
  // confuse Next's tracing root (they also break CI determinism).
  outputFileTracingRoot: path.join(import.meta.dirname),
  basePath,
  assetPrefix: basePath || undefined,
  trailingSlash: true,
  images: { unoptimized: true },
  eslint: { ignoreDuringBuilds: true },
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
};

export default nextConfig;
