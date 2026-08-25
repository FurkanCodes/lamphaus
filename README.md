# Lamphaus

Lamphaus is one native Android application with purpose-built phone/tablet and television interfaces. Users add their own lawful HTTPS-compatible media providers; Lamphaus supplies no content source.

## Local build

1. Install Android SDK platform 36 and Build Tools 36.0.0 or newer.
2. Use JDK 17 or newer and run `./gradlew assembleDebug`.
3. Install the standard debug APK on a phone or television emulator. Each launcher opens its form-factor activity.

The debug build works without cloud credentials and exposes local account/provider state. To enable production authentication and synchronization, add the Supabase credentials described in `docs/PRODUCTION_SETUP.md`.

## Project map

- `app`: mobile and TV Compose roots, navigation, account/provider setup, and system integration.
- `core:model`: stable product, account, provider, and playback types.
- `core:provider`: HTTPS provider client, protocol parsing, and aggregation.
- `core:data`: Room, preferences, synchronization boundaries, and repositories.
- `core:player`: Media3 playback service and controller.

Run `./scripts/check-neutrality.sh` before shipping.

