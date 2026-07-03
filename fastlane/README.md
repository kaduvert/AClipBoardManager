# fastlane metadata (F-Droid listing)

Standard `fastlane/metadata/android/<locale>/` layout, read directly by
F-Droid's build/listing tooling (and compatible with `fastlane supply` /
Google Play if this is ever also distributed there).

```
fastlane/metadata/android/en-US/
├── title.txt
├── short_description.txt
├── full_description.txt
├── changelogs/
│   └── 1.txt                 one file per versionCode, matches app/build.gradle.kts
└── images/
    └── phoneScreenshots/
        ├── 1.png              ← placeholder: main screen
        └── 2.png              ← placeholder: settings screen
```

## Adding a new changelog entry

Bump `versionCode` in `app/build.gradle.kts`, then add a matching
`changelogs/<versionCode>.txt` with a short plain-text bullet list of what
changed in that release.

## Other locales

Duplicate `en-US/` as e.g. `de-DE/` with translated `title.txt`,
`short_description.txt`, `full_description.txt` if you want localized
listings - screenshots can be reused across locales or localized too.
