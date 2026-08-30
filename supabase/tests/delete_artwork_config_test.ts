import { createDeleteArtworkConfigHandler } from "../functions/delete-artwork-config/index.ts";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function jsonBody(response: Response): Promise<Record<string, unknown>> {
  return await response.json() as Record<string, unknown>;
}

function request(body: unknown): Request {
  return new Request("https://functions.test/delete-artwork-config", {
    method: "POST",
    headers: { Authorization: "Bearer user-token", "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

Deno.test("batch clear authenticates the user and deletes only artwork rows", async () => {
  const calls: Array<{ url: string; method: string }> = [];
  const handler = createDeleteArtworkConfigHandler({
    supabaseUrl: "https://project.test",
    anonKey: "anon-key",
    serviceRole: "service-role",
    fetchImpl: async (input, init) => {
      const url = input.toString();
      calls.push({ url, method: init?.method ?? "GET" });
      if (url.endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
      return new Response(null, { status: 204 });
    },
  });

  const response = await handler(request({ all: true }));
  assert(response.status === 200, `expected 200, got ${response.status}`);
  const body = await jsonBody(response);
  assert(body.ok === true && body.all === true, "expected successful batch response");
  assert(calls.length === 2, `expected auth and delete calls, got ${calls.length}`);
  assert(calls[0].url.endsWith("/auth/v1/user"), "authentication must happen first");
  assert(calls[1].method === "DELETE", "batch clear must use DELETE");
  assert(calls[1].url.includes("user_id=eq.user-a"), "delete must be scoped to the authenticated user");
  assert(calls[1].url.includes("provider_id=like.artwork.*"), "delete must use the exact artwork prefix");
  assert(!calls[1].url.includes("provider_id=like.%"), "delete must not use a generic provider prefix");
});

Deno.test("single provider deletion keeps catalog validation and response contract", async () => {
  const calls: string[] = [];
  const handler = createDeleteArtworkConfigHandler({
    supabaseUrl: "https://project.test",
    anonKey: "anon-key",
    serviceRole: "service-role",
    fetchImpl: async (input) => {
      const url = input.toString();
      calls.push(url);
      if (url.endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
      if (url.includes("/artwork_providers?")) return Response.json([{ id: "tmdb" }]);
      return new Response(null, { status: 204 });
    },
  });

  const response = await handler(request({ provider: "tmdb" }));
  assert(response.status === 200, `expected 200, got ${response.status}`);
  const body = await jsonBody(response);
  assert(body.ok === true && body.provider === "tmdb", "expected single-provider response");
  assert(calls.some((url) => url.includes("id=eq.tmdb")), "single delete must validate the catalog");
  assert(calls.some((url) => url.includes("provider_id=eq.artwork.tmdb")), "single delete target changed");
});

Deno.test("missing, false, and conflicting selectors are rejected before deletion", async () => {
  for (const body of [{}, { all: false }, { all: true, provider: "tmdb" }]) {
    const calls: string[] = [];
    const handler = createDeleteArtworkConfigHandler({
      supabaseUrl: "https://project.test",
      anonKey: "anon-key",
      serviceRole: "service-role",
      fetchImpl: async (input) => {
        const url = input.toString();
        calls.push(url);
        if (url.endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
        throw new Error("unexpected post-auth request");
      },
    });

    const response = await handler(request(body));
    assert(response.status === 400, `expected 400 for ${JSON.stringify(body)}`);
    const result = await jsonBody(response);
    assert(result.error === "unsupported_provider", "expected unsupported_provider");
    assert(calls.length === 1, "invalid selector must not reach catalog or delete");
  }
});

Deno.test("failed batch delete returns the existing delete_failed error", async () => {
  const handler = createDeleteArtworkConfigHandler({
    supabaseUrl: "https://project.test",
    anonKey: "anon-key",
    serviceRole: "service-role",
    fetchImpl: async (input) => {
      if (input.toString().endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
      return new Response("database unavailable", { status: 503 });
    },
  });

  const response = await handler(request({ all: true }));
  assert(response.status === 500, `expected 500, got ${response.status}`);
  const body = await jsonBody(response);
  assert(body.error === "delete_failed", "expected delete_failed");
});
