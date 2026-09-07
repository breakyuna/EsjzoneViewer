# Baseline profile

This com.android.test module targets :app and provides a deterministic cold-start journey for
generating a baseline profile.

    ./gradlew :baselineprofile:generateBaselineProfile

Profile generation requires a connected Android device or emulator (API 28+) with USB debugging
enabled. The journey intentionally does not sign in or call the Esjzone service, so it does not
need credentials. Add generated profile output to the app's release packaging workflow when the
release build is ready.
