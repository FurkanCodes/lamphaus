# Adding an artwork provider

Artwork providers are server-driven. The catalog controls what users see in Settings, while the resolver registry controls how a provider talks to its upstream API. A new provider normally requires one SQL migration, one adapter, registry wiring, tests, and an Edge Function deployment. Android code is not required for a provider that returns HTTPS image URLs and uses the existing catalog fields.

## Architecture

| Layer | Source | Responsibility |
|---|---|---|
| Catalog | `public.artwork_providers` | Stable ID, display copy, key URL, ordering, enabled state |
| Secret storage | `provider_configs.provider_id = artwork.<id>` | Per-user encrypted API key |
| Adapter registry | `supabase/functions/resolve-artwork/providers/registry.ts` | Provider lookup, dependency ordering, execution |
| Provider adapter | `supabase/functions/resolve-artwork/providers/<name>.ts` | Upstream request, response normalization, status, identifiers |
| Client | `ArtworkProviderStatus` and catalog-driven Settings UI | Renders provider metadata and accepts arbitrary valid IDs |

The provider ID is an encryption namespace. Treat it as immutable after release. Renaming an ID strands existing encrypted keys and artwork overrides unless an explicit migration is designed.

## 1. Choose and validate the provider ID

Use a lowercase opaque ID matching:

```text
^[a-z][a-z0-9_-]{0,63}$
```

Examples: `tvmaze`, `omdb`, `example_art`. Do not use a display name as the ID. Do not reuse an old ID for a different service.

Decide these properties before coding:

- API key format and maximum accepted length. The shared save function accepts nonblank keys up to 512 characters.
- Supported media types: `movie`, `series`, or both.
- Upstream identifier required by the API: IMDb, TMDB movie/TV ID, TVDB ID, or another identifier that must first be added to `IdentifierKind`.
- Image URL format and allowed image hostnames.
- Whether the provider depends on another adapter, such as Fanart.tv depending on TMDB/TVDB IDs.

## 2. Add the catalog row in a migration

Create a new timestamped migration under `supabase/migrations/`. Do not edit an applied migration or insert production rows manually through the dashboard.

```sql
insert into public.artwork_providers
  (id, display_name, purpose, help_text, key_page_url, sort_order, enabled)
values
  (
    'example_art',
    'Example Art',
    'Additional posters and backgrounds.',
    'Create an Example Art API key and paste it here.',
    'https://example.test/account/api',
    30,
    true
  );
```

Catalog constraints require:

- A valid lowercase provider ID.
- Nonblank `display_name`, `purpose`, `help_text`, and `key_page_url`, each at most 500 characters.
- An HTTPS `key_page_url`.
- A deterministic `sort_order`; ties are broken by ID.

The catalog table has RLS enabled and no client policies by design. Edge Functions read it with the service role. Keep the provider disabled until its adapter and deployment are ready if the rollout is staged:

```sql
update public.artwork_providers
set enabled = false
where id = 'example_art';
```

A disabled provider remains visible when a user already has it configured, so the user can remove the key. It is not used for new saves or artwork resolution.

## 3. Implement the adapter

Create `supabase/functions/resolve-artwork/providers/example_art.ts` and implement `ArtworkProviderAdapter` from `provider.ts`.

Required behavior:

- Set `id` to the exact catalog ID.
- Set `after` to adapter dependencies, or `[]` when none are required.
- Return only `success`, `no_match`, `missing_external_id`, `invalid_key`, or `lookup_failed`.
- Return assets with the same provider ID as the adapter.
- Return `identifiers` only for trusted, validated upstream identifiers that downstream adapters may use.
- Normalize malformed upstream data to empty lists instead of throwing for ordinary bad payloads.

Skeleton:

```ts
import {
  emptyArtworkLists,
  objectRecord,
  requestJson,
  type ArtworkProviderAdapter,
  type ArtworkRequest,
  type ProviderResolveInput,
  type ProviderResolution,
} from "../provider.ts";

const API_BASE = "https://api.example.test/v1";
const IMAGE_HOSTS = ["images.example.test"] as const;

type ArtworkAsset = { provider: "example_art"; reference: string };

function result(status: ProviderResolution["status"]): ProviderResolution {
  return { provider: "example_art", status, ...emptyArtworkLists(), identifiers: {} };
}

export const exampleArtAdapter: ArtworkProviderAdapter = {
  id: "example_art",
  after: [],
  allowedImageHosts: IMAGE_HOSTS,
  requestedIdentifiers: (request: ArtworkRequest) =>
    request.mediaType === "movie" ? ["imdb_id"] : [],
  async resolve({ request, apiKey, identifiers }: ProviderResolveInput): Promise<ProviderResolution> {
    const externalId = identifiers.imdb_id;
    if (!externalId) return result("missing_external_id");

    const response = await requestJson(
      `${API_BASE}/title/${encodeURIComponent(externalId)}?api_key=${encodeURIComponent(apiKey)}`,
      { headers: { accept: "application/json" } },
    );
    if (!response.response) return result("lookup_failed");
    if (response.response.status === 401 || response.response.status === 403) return result("invalid_key");
    if (response.response.status === 404) return result("no_match");
    if (!response.response.ok || response.payload === null) return result("lookup_failed");

    const root = objectRecord(response.payload);
    const poster = typeof root?.poster_url === "string" ? root.poster_url : null;
    const artwork = emptyArtworkLists();
    if (poster) (artwork.posters as ArtworkAsset[]).push({ provider: "example_art", reference: poster });
    return {
      provider: "example_art",
      status: poster ? "success" : "no_match",
      ...artwork,
      identifiers: {},
    };
  },
};
```

The skeleton is illustrative; adjust the upstream request and payload normalization to the provider's real API.

### Image reference rules

`sanitizeProviderResolution` runs after every adapter. For new providers, image references must be absolute HTTPS URLs whose host is listed in `allowedImageHosts`. Relative paths are accepted only for the existing TMDB adapter. References from another provider or unapproved hosts are discarded. Keep the allow-list narrow and use the exact hostname, not a user-controlled suffix match.

## 4. Register the adapter

Import the adapter and add it to the registry in `supabase/functions/resolve-artwork/index.ts`:

```ts
import { exampleArtAdapter } from "./providers/example_art.ts";

const registry = createProviderRegistry([tmdbAdapter, fanartAdapter, exampleArtAdapter]);
```

The registry rejects duplicate IDs, malformed IDs, and dependency cycles. `after: ["tmdb"]` means the provider runs after TMDB when TMDB is configured and can receive identifiers requested by the downstream provider. If the dependency is not configured, the adapter must handle missing identifiers itself.

If the provider needs a new identifier type, add it to `IdentifierKind`, then update the upstream adapter that can supply it and test the complete dependency path.

## 5. Add focused tests

Add tests beside the affected Edge Function and cover behavior, not source text. At minimum test:

- Catalog validation accepts the new ID and metadata.
- The adapter maps a valid upstream payload to posters, backdrops, and/or logos.
- Invalid, missing, and duplicate image URLs are rejected or deduplicated.
- `401`/`403`, `404`, malformed payloads, and network failures map to the expected status.
- Missing dependency identifiers return `missing_external_id`.
- Dependency ordering passes identifiers to the new adapter.
- The v2 status response exposes the catalog `id`, and the Android wire model can decode it.

Useful local checks:

```bash
deno test \
  supabase/functions/resolve-artwork/provider_test.ts \
  supabase/functions/resolve-artwork/registry_test.ts \
  supabase/functions/artwork-key-status/catalog_test.ts

deno check supabase/functions/resolve-artwork/index.ts
```

If the response contract changes, run the corresponding Android mapping test:

```bash
./gradlew :core:data:testDebugUnitTest --tests com.lamphaus.core.data.cloud.ProviderConfigMappingTest
```

## 6. Deploy in dependency order

From the repository root:

```bash
supabase link --project-ref uhxfalgfcutwrvlgjgen
supabase db push
supabase functions deploy resolve-artwork
```

The migration must be applied before enabling the catalog row. `resolve-artwork` must contain the adapter before users can successfully resolve artwork. Redeploy these generic functions only when their source changed:

```bash
supabase functions deploy artwork-key-status
supabase functions deploy save-artwork-config
supabase functions deploy delete-artwork-config
```

`artwork-key-status` and `save-artwork-config` read the catalog dynamically; they do not need provider-specific code. `delete-artwork-config` accepts any catalog ID, including a disabled one, so configured users can remove retired providers.

For a normal new adapter, the deployment sequence is:

1. Apply the migration with the provider row disabled.
2. Deploy `resolve-artwork` with the adapter.
3. Run the deployed smoke test with an authenticated account and a test key.
4. Set `enabled = true` in a follow-up migration if the result is correct.
5. Verify `supabase functions list` and the provider status response.

Never place API keys in migrations, source code, logs, or the Android build. User keys are encrypted by `save-artwork-config` under the stable namespace `artwork.<id>`; see the [production provider-config key rotation runbook](PRODUCTION_SETUP.md#provider-config-key-rotation) for secret handling and rotation.

## 7. Verify the end-to-end path

Use an authenticated test account and confirm all of the following:

1. Artwork Settings displays the new catalog entry in the configured `sort_order`.
2. The key-page and help actions use the catalog copy and HTTPS URL.
3. Saving a key succeeds and creates `provider_id = artwork.<id>`.
4. Status reports the provider as configured.
5. Artwork resolution returns a provider result with the new ID and expected status.
6. Returned asset references load over HTTPS.
7. Removing the key succeeds.
8. Disabling the catalog row hides it from new resolution while retaining it for removal when configured.

The current Android client is catalog-driven: valid provider IDs are parsed generically, provider names and copy come from the status response, and absolute HTTPS asset references are accepted for non-TMDB providers. Add Android code only for a provider-specific behavior that the shared model does not cover, such as a new reference format or special UI interaction; keep that exception isolated and add a focused test.

## Retiring a provider

Do not delete a catalog row while foreign-key references or user configurations may still exist. Set `enabled = false`, deploy the migration, and leave the row available for removal. Delete the row only after all dependent configurations and artwork overrides have been intentionally migrated or removed. Because the ID is an encryption namespace, do not recycle the retired ID.
