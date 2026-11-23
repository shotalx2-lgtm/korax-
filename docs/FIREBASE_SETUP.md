# Firebase status and future integration notes

## Current status on main (23 November 2026)

Firebase is **not integrated into the current Android app**.

Verified in the current `main` branch:

- `app/build.gradle.kts` has no Firebase Authentication, Firebase Messaging, Google Credential Manager, or Google Sign-In dependencies/plugin.
- `AndroidManifest.xml` has no Firebase Messaging service.
- The application has no active email/password or Google sign-in flow.
- The Android app does not register an FCM token with `korax-push-register`.
- The Android app does not use Firestore to save fantasy teams.
- `firestore.rules` and this document are setup remnants/documentation, not proof that those features are integrated.

Do not create or add Firebase secrets just to make the current 0.6.2 app build. The current app builds without `google-services.json`.

## Recommended architecture before adding accounts

Supabase already owns KORAX's backend data and server-side functions. Prefer evaluating **Supabase Auth** as the single identity system for email/password and Google sign-in. This avoids maintaining separate Firebase and Supabase user identities.

If remote Android push notifications are needed, Firebase Cloud Messaging can be evaluated as a delivery transport only. The app would need the FCM SDK, token registration/refresh, a messaging service, notification preferences, and a secure server-side sender. The existing `korax-push-register` and `korax_push_outbox` are backend groundwork only; they do not mean Android devices currently receive FCM.

Do not add Firebase Auth, Firestore, or FCM until the exact role of each service is agreed and an end-to-end test is defined.

## Safe integration requirements

When implementation is approved:

1. Keep service-role keys and service-account private keys server-side only.
2. Never commit `google-services.json`, private keys, or production tokens to the public repository.
3. Store required credentials in the appropriate secure provider settings, not in chat or Android source.
4. Add token registration, refresh, removal on sign-out, delivery retries, and user notification controls before claiming remote push is operational.
5. Verify the signing certificate and Google OAuth configuration for the actual release package.
6. Test account creation, login, Google callback, session refresh, sign-out, and data isolation before enabling account-dependent app features.
