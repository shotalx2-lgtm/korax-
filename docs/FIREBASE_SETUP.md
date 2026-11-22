# Firebase and Google sign-in setup for KORAX

The Android project now contains the Google Credential Manager + Firebase Authentication flow. The sign-in button is shown in **More → KORAX Account** when Firebase is configured.

## One-time Firebase setup

1. Create a Firebase project for KORAX.
2. Add an Android app with package name `com.koora.app`.
3. In Firebase Authentication, enable the **Google** provider.
4. Add the SHA-1 and SHA-256 fingerprints for the certificate that signs the APK users will install. The current CI debug certificate is for testing only; production must use a stable release keystore.
5. Download the updated `google-services.json` for the Android app.

## GitHub Actions setup

Do not commit `google-services.json` to the public repository.

Encode the file as Base64 on your own computer and save the result as a GitHub Actions repository secret named:

- `FIREBASE_CONFIG_JSON_B64`

The Android workflow decodes this secret into `app/google-services.json` before Gradle runs. When the secret is absent, the project still builds, but Firebase sign-in remains unavailable and the account screen explains that configuration is required.

## Fantasy cloud saves

1. In the Firebase console, create a Cloud Firestore database.
2. Publish the repository's `firestore.rules` rules to the Firestore database.
3. The Android fantasy screen saves the signed-in user's squad, budget, team name, formation, captain, vice-captain, and transfer log to `fantasyTeams/{Firebase UID}`. The player catalogue remains cached separately and is not duplicated in every user's cloud document.

The rules intentionally allow each user to read and change only their own fantasy save. Multiplayer leagues, shared leaderboards, friend requests, and chat need additional collections and stricter membership-based rules before those features are enabled.

## Important boundaries

- Google sign-in authenticates a user with Firebase Authentication.
- Authentication alone does not create cloud fantasy saves, leagues, leaderboards, friend requests, or chat. Those require a database, security rules, and application screens; they must be completed and tested separately.
- Never place Sportmonks credentials, Firebase service-account private keys, or other server secrets in the Android APK or public source code.
- Before a public release, configure a stable release signing key and register its certificate fingerprints in Firebase.
