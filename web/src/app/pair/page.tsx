import { Suspense } from "react";
import type { Metadata } from "next";
import PairClient from "./pair-client";

export const metadata: Metadata = {
  title: "Pair a TV",
};

export default function PairPage() {
  // useSearchParams inside the client component needs a Suspense boundary
  // for static prerendering.
  return (
    <Suspense fallback={null}>
      <PairClient />
    </Suspense>
  );
}
