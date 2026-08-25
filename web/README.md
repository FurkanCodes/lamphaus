# Lamphaus Web

Marketing page + TV pairing auth surface (plan §8). Next.js App Router,
Tailwind v4, fully static-exported for GitHub Pages.

## Routes

| Route          | Purpose |
|----------------|---------|
| `/`            | Product page |
| `/pair?code=X` | QR scan target: sign in with Google → claim the TV's code |
| `/auth/callback` | OAuth PKCE return leg; bounces back to `/pair` |
| `/privacy`     | Launch blocker for public pairing — owner review pending |

## Local development

```bash
cp .env.example .env.local   # fill in the two publishable values
npm install
npm run dev
```

Without env vars every page renders but `/pair` shows its explicit
"Setup needed" state instead of failing silently.

## Deploy (GitHub Pages)

`.github/workflows/web-deploy.yml` builds on pushes to `main` that touch
`web/**`:

- sets `PAGES_BASE_PATH=/<repo-name>` so assets resolve under
  `<user>.github.io/<repo>/`
- injects Supabase values from repo secrets
  (`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`)
- publishes `web/out` via the official Pages actions

Enable once in repo settings: **Settings → Pages → Source: GitHub Actions**.

## Launch checklist (from the master plan)

- [ ] Add `<site>/auth/callback` + the Pages URL to the Supabase redirect allow-list (§9.3)
- [ ] Rasterize an og-image PNG from `public/logo.svg` (crawlers ignore SVG)
- [ ] Privacy copy legal review + working contact address (§11)
- [ ] Publish OAuth consent screen before public pairing (§11)
- [ ] `claim-pairing-session` Edge Function lands in M4 — until then the pair
      page honestly reports "Pairing service isn't live yet"
