# Google Maps Setup (Phase 6)

Map streaming requires a Google Maps API key.

## Get an API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a project or select an existing one
3. Enable **Maps SDK for Android**
4. Create credentials → API key
5. (Optional) Restrict the key to your app's package name and SHA-1

## Add to Project

Put the key **only** in `hud-android/CarHud/local.properties` (this file is gitignored):

```properties
MAPS_API_KEY=your_api_key_here
```

**Do not** add `MAPS_API_KEY=...` to `gradle.properties` in the repo. Committing it triggers secret scanners (e.g. GitHub) and exposes the key in history. If a key was ever pushed, **rotate it** in Google Cloud Console and create a new key.

`build.gradle.kts` uses `project.findProperty("MAPS_API_KEY")`, which reads `local.properties` and `gradle.properties` — use `local.properties` for secrets only.
