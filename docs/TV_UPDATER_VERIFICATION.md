# TV updater repair

Scope: TV and shared update infrastructure. Rules: SHR-ARC-09/10/11,
SHR-PROD-07, TV-FND-02, TV-NAV-02/04/05, QA-04/05/07.

The TV renderer omitted PermissionRequired, Waiting, and Installing, so those
states dismissed the visible dialog. The session receiver also ignored
STATUS_PENDING_USER_ACTION instead of delivering Intent.EXTRA_INTENT to the
resumed activity. The repair displays every active TV phase and retains the
confirmation intent until a resumed host successfully launches it.

Permission return is reconciled independently of the 30-minute foreground
check cooldown. Download/installer exceptions become visible failures; cancelling
cancels polling/session work; duplicate download clicks reuse the operation.
Metered consent is applied when enqueueing the replacement download. The APK is
verified again before committing a session, and stale callbacks are ignored.
Discovery cannot replace an active update flow. Audio and playback are untouched.

## Verification

- App unit tests and Android debug lint passed.
- Provider-neutrality check passed.
- Six TvUpdateFlowTest instrumentation tests passed on Television_4K,
  Android 16/API 36, ARM64, 3840×2160 output, 960×540dp.
- TV D-pad reaches and activates Open Settings and Cancel in the previously
  invisible permission/wait states.
- A real PackageInstaller session receives STATUS_PENDING_USER_ACTION via the
  app's receiver. The resumed Compose host opens the system package installer.
  Back cancels that system screen and produces a visible INSTALL_FAILED state.
- The test intentionally cancels before installation; it does not replace its
  running test package. Physical TV installation remains a manual check.

```sh
rtk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lamphaus.app.update.TvUpdateFlowTest --max-workers=2
```

Beta 3 uses a new immutable version/code (REL-02/03/07), the existing production
signer (REL-06), and the local production build pipeline (REL-05). This changes
the user-triggered update flow rather than startup, so startup profiles do not
need regeneration (REL-04); the production pipeline checks packaged profiles
(QA-08). Publication must still verify APK bytes, report, signed feed, and website
agreement (REL-08/09).

Existing beta 1/2 installations cannot acquire this fix through their broken
installer handoff. Install beta 3 manually over the existing app once; do not
uninstall or reset app data. Then verify that a future update presents Android's
confirmation normally.
