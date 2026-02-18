# Google Maps Setup (Phase 6)

Map streaming requires a Google Maps API key.

## Get an API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a project or select an existing one
3. Enable **Maps SDK for Android**
4. Create credentials → API key
5. (Optional) Restrict the key to your app's package name and SHA-1

## Add to Project

Add your API key to `gradle.properties` in the project root:

```
MAPS_API_KEY=your_api_key_here
```

Do **not** commit the key to version control. Add `gradle.properties` to `.gitignore` if it contains secrets, or use `local.properties` (which is typically gitignored):

```properties
# In local.properties (create if it doesn't exist)
MAPS_API_KEY=your_api_key_here
```

Then in `build.gradle.kts`, ensure the placeholder reads from the right property. The current setup uses `project.findProperty("MAPS_API_KEY")`, which reads from both `gradle.properties` and `local.properties`.
