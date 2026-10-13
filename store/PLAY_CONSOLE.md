# SOUNDFRAME — Play Store submission kit

Everything needed for the Play Console listing, in English (`en-US`, default) and Spanish (`es-ES`).

```
store/
├── fastlane/metadata/android/<locale>/   ← upload-ready (fastlane `supply` layout)
│   ├── title.txt  short_description.txt  full_description.txt  changelogs/1.txt
│   └── images/  icon.png  featureGraphic.png  phoneScreenshots/01…07.png
├── graphics/      icon-512.png · feature-graphic-en-US.png · feature-graphic-es-ES.png
├── screenshots/   phone/<locale> (designed slides, 1080 × 1920) · raw/<locale> (device captures)
├── screenshot-designer/  template.html + render.sh (regenerates the designed slides)
└── privacy-policy/  static site deployed to Vercel
```

All graphics were rendered by the app's own generative engine
(`app/src/androidTest/.../StoreAssetGenerator.kt`, run with `-e storeAssets true`). The screenshots
were captured on a Pixel 9 Pro XL, with real microphone and imported audio.

## Store listing

| Field | en-US | es-ES | Limit |
|---|---|---|---|
| App name | Soundframe: Sound Wave Art (26) | Soundframe: Arte con Sonido (27) | 30 |
| Short description | `short_description.txt` (78) | `short_description.txt` (80) | 80 |
| Full description | `full_description.txt` (2,563) | `full_description.txt` (2,698) | 4,000 |
| App icon | `images/icon.png`: 512 × 512, 32-bit PNG | same | ≤ 1 MB |
| Feature graphic | `images/featureGraphic.png`: 1024 × 500, 24-bit PNG | localised headline | ≤ 15 MB |
| Phone screenshots | 7 × 1080 × 1920 designed slides (headline + device frame), 24-bit PNG | 7, Spanish copy and UI | 2–8 |

- **Category:** Art & Design
- **Tags** (up to 5): Art & design · Music & audio · Creativity · Photography · Personalization
- **Contact email:** carlosblayagarcia@gmail.com
- **Privacy policy:** https://soundframe-privacy.vercel.app/ (Spanish: https://soundframe-privacy.vercel.app/es/)
- **Release notes:** `release-notes-v1.0.txt`. Paste the whole file into the "Release notes" box when creating the release; the `<en-US>`/`<es-ES>` tags assign each language. The same text is in `changelogs/1.txt` for fastlane.

### Search optimisation (ASO)

The listing is written for Google Play's metadata policy as well as for search:
- **Title:** the brand plus the highest-intent phrase, "Sound Wave Art" / "Arte con Sonido". The title carries the most weight in Play search.
- **Short description:** leads with the core intent words (*voice, music, sound, generative art*) and ends with the strongest differentiator (*private, offline*).
- **Full description:**
  - The first ~170 characters, shown above "read more", contain the hook and the main keywords: *sound, art, voice, music, generative, sound wave*.
  - The secondary terms each appear 1–3 times in natural sentences:
    - English: *audio visualizer, sound visualization, music, wallpaper, offline, private*
    - Spanish: *visualizador de audio, onda de sonido, fondos de pantalla, sin conexión*
  - "Sound" appears often because it's the subject of the app, not filler.
- **Policy-safe:** there's no keyword stuffing, no "best" / "#1" / ranking claims, no emoji spam, and no other apps' brand names (social networks are referred to generically). Any of these can get a listing rejected or demoted.
- **Spanish is written natively,** not word-for-word, so it targets the phrases Spanish users actually search: *arte con sonido*, *visualizador de audio*, *arte generativo*.
- **After launch:** use Play Console's Store listing experiments to A/B test the short description and the first screenshot. The candidates I'd test first are the Bloom gallery shot and the Echo editor shot.

### Screenshot slides
Each slide pairs a benefit-led headline with the real app screen in a device frame, on that slide's own palette. The copy is localised, and every claim matches what the app actually ships.

| # | Headline (en / es) | Screen |
|---|---|---|
| 1 | Every sound has a *shape* / Cada sonido tiene una *forma* | Onboarding |
| 2 | Capture any sound in *10 seconds* / Captura cualquier sonido en *10 segundos* | Studio |
| 3 | Your sound, *alive* / Tu sonido, *vivo* | Live recording |
| 4 | Real audio analysis, *real art* / Análisis de audio real, *arte real* | Editor (Echo) |
| 5 | Make it *yours*, with chips: 5 styles · 7 palettes · ∞ variations / Hazlo *tuyo* | Palettes (Bloom) |
| 6 | Share it in *HD* / Compártelo en *HD* | Export |
| 7 | Private by *design*, with chips: 100% on-device · No account · No ads / Privada por *diseño* | Gallery |

The first three slides are the ones visible in search results, so they carry the core promise: what the app is, how fast it is, and how alive it feels.

To change the copy:
1. Edit `SLIDES` in `screenshot-designer/template.html`.
2. Run `./render.sh`.
3. Convert the output to 24-bit PNG, because Play rejects alpha channels.
4. Copy the files into `fastlane/…/phoneScreenshots`.

## Data safety form

Every answer below matches the code: there's no networking, analytics, ads, or third-party SDKs, and only the `RECORD_AUDIO` permission.

- **Does your app collect or share any of the required user data types?** → **No.** Audio is recorded and analysed only on the device and is never transmitted. Google's definition of "collection" excludes data processed only on-device.
- **Is all of the user data collected by your app encrypted in transit?** → Not applicable (no data collected).
- **Do you provide a way for users to request that their data is deleted?** → Not applicable. Users can still delete everything in-app (Settings → Delete all creations).
- **Ads:** the app contains **no ads**.

## App content

| Question | Answer |
|---|---|
| App access | All functionality is available without login or special access |
| Ads | No |
| Content rating (IARC questionnaire) | Category: *Utility / Productivity / Communication / Other*. No violence, sexuality, language, drugs or gambling. No user-to-user communication, no sharing of location, no digital purchases. Expected rating: **Everyone / PEGI 3** |
| Target audience | **13 and over** recommended. The app is safe for all ages, but choosing under-13 groups triggers the Families programme requirements |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None |
| Permissions declaration | `RECORD_AUDIO` is used only while recording; core functionality is shown in the listing |

## Remaining steps before you can publish (not done for you)

1. **Upload key and signing.** Create an upload keystore in Android Studio (*Build → Generate Signed App Bundle*). Keep it private and backed up, and enrol in **Play App Signing**. I deliberately haven't created or stored a signing key for you.
2. **Release bundle.** Build it with `./gradlew bundleRelease` once signing is configured. Increase `versionCode` for every upload.
3. **Testing track.** New personal developer accounts must run a closed test (at least 12 testers for 14 days) before production access is granted.
4. **Upload the listing.** Either paste the text into the Play Console fields and upload the images, or use `fastlane supply` with the `store/fastlane/metadata/android` folder.
