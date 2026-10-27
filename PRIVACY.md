# Wildflower Lens（草木知时）— Privacy

FloraLens has two identification modes, chosen at onboarding and changeable
anytime in **About & Help**. The privacy story is different in each, and this
document describes both honestly. New installations default to Pl@ntNet online
recognition, explicitly disclosed before completing onboarding; choosing offline
keeps recognition on the device. Updates preserve any saved mode preference.
Wildflower Lens also provides separately
consented AI nature writing and plant-context questions; these are independent
of the identification-mode switch and are described below.

**On-device identification: the recognition photo stays on your phone.**
**Online services process only the information described below. Baidu maps,
AI writing and plant-context questions each require their own consent.**

The app adds no standalone analytics, crash-reporting or advertising service and
has no user-account system. The separately consented Baidu SDK processes necessary
device information and service diagnostics under its own policy. Existing online identification/details services
use the original integrations. Optional AI writing instead uses a configured,
authenticated proxy; its DeepSeek provider key is kept only on the server,
never compiled into the Android APK. No public writing proxy is configured
or deployed by default.

---

## On-device mode

Identification runs on a bundled TensorFlow Lite model (4.8 MB, in the APK).
Leaf health checks use an on-device color heuristic. The map is a local
illustration rendered from your saved coordinates. Species details are
available only if previously saved while online (see *Offline copies* below).

On-device identification does not make a recognition network request.
It does not prevent a separately consented AI-writing request. Merely opening
a Culture page reads approved local literature, a valid local AI cache, or
local observation guidance; it does not automatically contact DeepSeek.

## Optional AI nature writing

Only after tapping Generate/Regenerate and accepting its privacy prompt,
the app sends the normalized scientific name, a curated Chinese plant name
when available, and the chosen writing style to the configured proxy and
DeepSeek. It does **not** send photos, coordinates, observation dates,
plant nicknames, private observation notes, or the personal archive.

Requests include a scoped proxy access credential for authentication; this
is not the DeepSeek provider key. The app encrypts that credential with
Android Keystore. The supplied proxy code does not log credentials, request
text or provider response bodies and persists only its rate-budget counters.
DeepSeek and any deployed hosting provider process requests under their own
policies; this app does not promise that third-party processing has no retention.

Accepted AI text is marked as modern AI original, not a classical quotation,
scientific fact, or proof of an observation. It is cached privately by plant
identity/style for up to 30 days, with at most 128 cache entries. Re-entry
uses valid cache without another generation request. Errors or rejected text
are not cached; local guidance remains available. Private user-written card
text is never sent to the writing service.

## Optional plant-context questions

"花间问答" has a separate consent scope because it sends free-form text. Only
after the user accepts its prompt does the app send the controlled plant name,
the user's question and up to five recent question/answer turns through the
same authenticated proxy. A question may itself contain personal information,
so the consent prompt asks users not to type sensitive details. The app never
automatically appends photos, coordinates, observation dates, nicknames,
private notes, observation history or account identifiers.

Chat messages are held only by the current navigation-session ViewModel and
are not written to Room, the plant archive, or the 30-day nature-writing
cache. Leaving and removing the chat destination clears that state; process
death may also clear it. The consent can be withdrawn from the chat page.
The supplied proxy does not persist chat content, but DeepSeek and any chosen
hosting provider process requests under their own policies. AI answers are
labelled as generated and are not treated as approved literature, scientific
proof, medical advice, edibility advice, or toxicity identification.

## Online mode

Choosing online mode (recommended for accuracy) sends specific data to
specific services, only at the moments described:

| Service | What is sent | When |
|---|---|---|
| **Pl@ntNet** (plantnet.org) | The photo you capture on the Identify screen | When you tap capture in online mode |
| **OpenAI** (openai.com) | The species' scientific/common name (details); your leaf photo plus the local check's one-line result (AI insights) | When you open a species' details; when you run a leaf health check |
| **Baidu Maps Android SDK** (北京百度网讯科技有限公司) | Necessary device/app information including Android ID, network state/IP and map requests for the visible area; an optional cached location when the location button is used | Only after separate map consent, while an online observation/distribution map is open |
| **Nominatim** (OpenStreetMap) | Region *names* (e.g. "Western Ghats, India") for geocoding — never your location | When a species' details load |
| **GBIF** (gbif.org) | The species' scientific name | When a species' details load, for the occurrence chart |
| **YouTube Data API** | A search query built from the species name | When a species' details load |

Each service processes this data under its own privacy policy. FloraLens
sends the minimum each feature needs. HTTPS services can see the connecting
IP address; AI writing also requires the scoped proxy authentication described above.

If you're in online mode without connectivity, identification falls back to
the on-device model automatically — nothing is queued for later upload.

## Optional Baidu online maps

Before its first use the app explains the provider, purpose, information processing,
and [Baidu privacy policy](https://lbsyun.baidu.com/docs/pcsa?title=compliance/openprivacy).
The SDK is not initialized before that separate choice. Consent can be withdrawn
from the map or Settings & Help; the current map is released and subsequent map
use is blocked until consent is granted again. Android location permission is a
different setting. Viewing saved places never requires granting precise location.
No Baidu location SDK, background location or continuous tracking is added.

The local archive retains original coordinates. New framework-location records
have a local coordinate-source sidecar. Display conversion does not rewrite Room.
Historical coordinates without a confirmed source remain in local records and are
not guessed onto an online street map. Map requests reveal the viewed geographic
area, which may correspond to an observation. Therefore online maps are not
described as keeping every observation location exclusively on-device.

## Locally stored archive

- **Your plant collection** — nicknames, photos, care schedules (Room database, app-private storage)
- **Care history** — every watering, note, and leaf check (the `Observation` table)
- **Observation locations** — original coordinates are stored locally. Offline viewing makes no map request; separately consented online maps request the corresponding visible area and can process the optional current location as described above. No coordinates are appended to AI writing/questions.
- **Offline copies** — species details viewed online are auto-saved to the local database so they remain readable offline. This is a local cache; nothing about your saving habits is transmitted.
- **Your mode choice and settings** (DataStore, local)

## Permissions

| Permission | Why | Required? |
|---|---|---|
| `CAMERA` | Identifying plants and checking leaves | Yes — it's the whole app |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Online features, separately consented AI writing/questions, and connectivity checks | Not needed for on-device identification or local Culture reading |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Optional pin on the observation map; "my location" dot on the online map | No — skippable at onboarding; app is fully functional without it |
| `POST_NOTIFICATIONS` (Android 13+) | Watering reminders | No — reminders simply stay silent without it |

## Data deletion

Everything FloraLens stores lives in app-private storage: uninstalling the
app deletes local app storage (subject to Android backup/restore settings).
There is no app account to close. The supplied writing proxy persists budget
counters, not a copy of the archive or generated poems. Data sent to third-party services is
governed by their respective policies (Pl@ntNet, OpenAI, Baidu, Google/YouTube, OSM/Nominatim,
GBIF, YouTube, DeepSeek and the chosen proxy host).

## Verify, don't trust

The codebase is small enough to audit:

- `data/settings/` — mode gating for identification and the original online integrations; AI writing uses its separate consent
- `data/culture/NatureExpressionRepository.kt` — nature-writing minimal payload, local cache and failures
- `data/chat/PlantChatRepository.kt` — chat consent boundary, bounded recent context and no archive fields
- `data/culture/NatureServiceConfig.kt` — encrypted scoped proxy credentials, no provider key
- `services/nature-proxy/` — authentication, budget, bounded input/output and provider handling
- `ml/HybridIdentifier.kt` — online/offline routing and the offline fallback
- `data/repo/PlantInfoRepository.kt` — the local offline-copy store
- `AndroidManifest.xml` + `app/build.gradle.kts` — permission/dependency list; Baidu SDK is separately consented and described above
