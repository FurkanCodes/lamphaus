# Production setup

The debug app builds without cloud or Cast credentials. Production sign-in, cross-device pairing, encrypted provider sync, Cast Connect, and Play release require the following external configuration.

## Firebase

1. Create Android apps for `com.lamphaus.app`, `.staging`, and `.debug` as needed.
2. Place the release `google-services.json` in `app/` locally or inject it in CI. It is ignored by Git.
3. Enable Google and email-link authentication. Configure the HTTPS handler domain and set `lamphaus.emailLinkDomain` in a private Gradle property.
4. Set `lamphaus.webClientId` to the Web OAuth client ID used by Credential Manager.
5. Create a Cloud KMS symmetric key and expose its full resource name to Functions as `KMS_KEY_NAME`.
6. Deploy `firestore.rules`, indexes, and Functions. Run the Emulator Suite before production deployment.
7. Register App Check with Play Integrity. Begin with `ENFORCE_APP_CHECK=false`, review metrics, then redeploy with enforcement enabled.

## Cast Connect

1. Register the receiver and package in the Cast Developer Console.
2. Set `lamphaus.castAppId` in a private Gradle property.
3. Register development TV devices or install through a Play test track.

## Release

Use Play App Signing and inject upload credentials through CI secrets. Supply a verified privacy-policy URL, support contact, finalized package ownership, and legal approval before `bundleRelease` is promoted. Crash and performance collection remain disabled until the user opts in.

