# Phase 0 Research: Random Pokemon Display

## Translating the button label (FR-016, added post-implementation)

- **Decision**: A small in-`app.js` dictionary maps each of the 9 supported language
  codes to a button label string, applied to `#new-pokemon-button`'s `textContent` on
  initial load and immediately on every language change (not gated on the
  relocalize fetch succeeding).
- **Rationale**: Unlike Pokemon names/types, the button label is this application's
  own UI text — PokeAPI has no concept of it, so there's no endpoint to fetch a
  translation from. A static client-side dictionary is the simplest correct fix
  (Simplicity First) and keeps the button responsive to language changes instantly,
  with no network round trip or loading state needed for UI chrome.
- **Alternatives considered**: Asking PokeAPI or another translation API for this
  string — rejected, there's no such data for arbitrary application UI text, and
  introducing a translation service would be a large, unrequested scope increase for
  one button label. Leaving it English-only — rejected per the explicit request.

## Default language from the browser (FR-015, added post-implementation)

- **Decision**: On page load, before the first fetch, read `navigator.languages`
  (the browser's full ordered preference list; falls back to `[navigator.language]`
  if unavailable), map each entry in order to a supported code, and use the first
  match; if none match, default to English. Mapping: take the tag's base subtag
  (e.g., `"fr"` from `"fr-CA"`) and check it against the 9 supported codes, with a
  special case for `zh-*` — a region of `TW`/`HK`/`MO` or an explicit `Hant` script
  subtag maps to `zh-hant`, anything else `zh-*` maps to `zh-hans`.
- **Rationale**: `navigator.languages` reflects the visitor's actual ranked
  preferences (e.g., a browser set to `["fr-CA", "en-US"]` should try French before
  falling back), which a single `navigator.language` read would miss. This is
  browser-side and stateless — computed fresh on every load, matching the existing
  "not remembered across reloads" assumption, and needs no new backend endpoint
  since it just picks which value the existing `lang` query parameter starts as.
- **Alternatives considered**: `Accept-Language` header parsed server-side —
  rejected, `navigator.languages` is simpler to read client-side and this app has no
  server-side rendering step where the header would naturally arrive before the
  first client-side fetch. Persisting the detected/chosen language (cookie/
  `localStorage`) — rejected, not requested and conflicts with the existing
  no-persistence assumption.

## Multi-language display (FR-011–FR-014, added post-implementation)

- **Decision**: Localized name comes from `GET /pokemon-species/{slug}`'s `names[]`
  array (filtered by `language.name`); localized type names come from
  `GET /type/{typeSlug}`'s `names[]` array. Both were verified to cover all 9 target
  languages (`en, fr, de, es, it, ja, ko, zh-hans, zh-hant`) for arbitrary Pokemon
  (checked against `bulbasaur` and the `fire` type).
- **Rationale**: These are the only PokeAPI endpoints with genuinely complete
  translation coverage across all 9 target languages. `GET /pokemon/{slug}}`'s own
  `name` field is just the English slug (e.g., `"charizard-mega-x"`), never
  translated.
- **Alternatives considered**: None — these are PokeAPI's only translation-bearing
  endpoints for this data.

- **Decision (variant/form handling, revised)**: For a Pokemon whose `/pokemon/{slug}`
  response has `species.name` different from its own `slug` (i.e., it's a
  form/variant like a Mega Evolution or regional form), fetch
  `GET /pokemon-form/{slug}` (same slug as `/pokemon/{slug}`) and resolve the display
  name, **always** in the format `{translated species name} ({translated
  qualifier})`, in priority order:
  1. Prefer its `names[]` array's entry for the requested language if present — this
     is PokeAPI's own curated, natural full name (reliably covers en/fr/de).
  2. Else use its `form_names[]` array's entry for the requested language if present
     (reliably covers all 9 supported languages).
  3. Only if neither array has an entry for the language at all: fall back to an
     English qualifier derived from the slug.

  Whichever source string was found, extract just the qualifier before displaying:
  check (case-insensitively) whether it already contains the translated species name
  as a substring — if so (common for Mega Evolutions, e.g. Japanese
  `"メガリザードンＸ"` already means "Mega Charizard X" as one fused word), strip that
  substring out and use what's left as the qualifier; if not (common for regional
  forms, e.g. French `"Forme d'Alola"` = "Alola Form", without "Raichu"), the source
  string already *is* the qualifier, used as-is. Either way the result is always
  displayed as `{species} ({qualifier})` — never a bare fused compound name — for a
  visually consistent format across every language and form type.
- **Rationale**: Re-tested across 7 different forms (Mega Evolutions, an Alolan form,
  a Crowned form) and found `names[]` consistently covers only en/fr/de, but
  `form_names[]` consistently covers **all 9 supported languages** for every form
  tested — I'd previously dismissed `form_names[]` as unreliable without checking
  whether its "suffix vs. full name" behavior was at least *predictable* per form
  type; it is (Mega Evolutions → full compound name in ja/ko/zh-hans/zh-hant and
  even es/it since those often keep the English species name; regional/other forms →
  qualifier only), and the substring-containment check reliably distinguishes the
  two cases without needing to hardcode which form types behave which way. This
  makes the English-qualifier fallback a true rare last resort instead of the normal
  outcome for 6 of the 9 languages, which is what a user-reported issue
  ("variants aren't translated") flagged as a real gap in the original decision.
- **Alternatives considered**: The original species-name-only + English-qualifier
  approach (superseded — left below for the record of why it changed). Hardcoding a
  per-form-type rule ("Mega Evolutions always use `form_names` as-is") — rejected,
  the substring check achieves the same result generically without a hardcoded list
  that would need maintaining as new games/forms are added.

- **Superseded decision (kept for history)**: Build the display name as
  `{translated species name} ({English form qualifier})` unconditionally for any
  variant, where the qualifier is derived by stripping the species slug prefix off
  the Pokemon's own slug (e.g., `"charizard-mega-x"` minus `"charizard-"` →
  `"mega x"`, title-cased) — no extra API call, but left the qualifier in English
  for every language except where it happened to already be English. Replaced by the
  decision above once `pokemon-form`'s `form_names[]` coverage was properly checked.

- **Decision (caching)**: Cache raw `/pokemon-species/{slug}` and `/type/{typeSlug}`
  responses in memory (by slug), for the process lifetime, alongside the existing
  full-name-list cache.
- **Rationale**: These are small, immutable reference datasets (≈1000 species, 18
  types) — caching avoids re-fetching the same species/type translations every time a
  visitor switches language on the same Pokemon, or when a common type recurs across
  many different Pokemon, keeping language switches (SC-005) fast. See
  `data-model.md`'s Reference Data Cache section for why this doesn't implicate the
  constitution's statelessness constraint the way `lastShownSlug` does.
- **Alternatives considered**: No caching — rejected as it would add 1-3 avoidable
  PokeAPI round trips to every single request, risking the 3-second targets (SC-001,
  SC-002, SC-005) for no benefit.

- **Decision (language selection storage)**: The selected language lives only in a
  browser-side JS variable, not sent anywhere except as the `lang` query parameter on
  each request. Not stored server-side, not in a cookie, not in `localStorage`.
- **Rationale**: Matches spec Assumptions ("not remembered across reloads") and
  avoids yet another constitution exception — this one genuinely needs no state at
  all, unlike `lastShownSlug`.
- **Alternatives considered**: `localStorage` persistence across reloads — rejected,
  not requested and adds a persistence mechanism for a feature explicitly scoped to
  reset every page load.

## Avoiding consecutive repeats (FR-010, added post-implementation)

- **Decision**: `PokemonService` keeps one in-memory field, the `name` of the last
  Pokemon it successfully returned. On each call, it draws a random index as before,
  but re-draws (loop) if the picked name equals the stored last name, then updates the
  stored name to the new pick before returning. If the full name list has only one
  entry, the exclusion is skipped (nothing else to pick).
- **Rationale**: This is the minimal state needed to satisfy FR-010. It requires no
  database, no cookies/sessions, and no per-visitor tracking — a single shared
  in-memory value scoped to the server process, consistent with there being no
  accounts/sessions anywhere else in this feature. Required an explicit constitution
  amendment (v1.2.0) since the prior wording forbade any state between requests.
- **Alternatives considered**: A per-visitor cookie/session tracking recent picks —
  rejected as unnecessary complexity (violates Simplicity First) for a requirement
  that only needs "not the same as the very last one," not "not recently seen by this
  visitor." Retrying with no cap on attempts — rejected in favor of the single-entry
  bail-out check, to guarantee termination even in a degenerate data source.

## Language/runtime version

- **Decision**: Java 21 (LTS).
- **Rationale**: Current widely-supported LTS release; its standard library alone
  (`java.net.http.HttpClient`, `com.sun.net.httpserver.HttpServer`) is sufficient for
  this feature, so no newer non-LTS features are needed.
- **Alternatives considered**: Java 17 (older LTS, also viable) — Java 21 chosen for
  currency; no feature in this project requires anything past 21.

## Backend HTTP server

- **Decision**: JDK built-in `com.sun.net.httpserver.HttpServer` — no external web
  framework.
- **Rationale**: The app needs exactly two routes (serve static files, one JSON
  endpoint). A framework like Spring Boot would pull in far more than needed and
  conflicts with the constitution's Simplicity First and Fixed Technology Stack
  principles.
- **Alternatives considered**: Spring Boot, Javalin, Spark — all rejected as
  unnecessary weight for two routes and as additional frameworks requiring a
  constitution amendment.

## Calling PokeAPI

- **Decision**: JDK built-in `java.net.http.HttpClient` (available since Java 11).
- **Rationale**: No external HTTP client library needed; keeps the dependency set at
  zero for networking.
- **Alternatives considered**: OkHttp, Apache HttpClient — rejected as redundant given
  the JDK client is sufficient.

## Parsing PokeAPI's JSON responses

- **Decision**: `org.json` — a single small library with no transitive dependencies,
  used only to read a handful of fields (`name`, `sprites.front_default`, and the
  listing endpoint's `count`).
- **Rationale**: The JDK has no built-in JSON parser (`javax.json` was a Java EE API,
  not part of the standard JDK). A minimal, single-purpose parsing library is not a
  "language" or "framework" under constitution Principle I, so it does not require an
  amendment — but it is the one dependency beyond the bare JDK, so it's called out
  explicitly here for transparency.
- **Alternatives considered**: Gson/Jackson (more features than needed, heavier),
  hand-rolled string/regex JSON parsing (rejected — fragile and error-prone for a
  format with nesting and escaping, not worth the risk to save one small dependency).

## Build/dependency management

- **Decision**: Maven (`pom.xml`).
- **Rationale**: Needed to pull in `org.json` and JUnit 5 and to run tests/builds
  reproducibly. A build tool is not a "framework" under the constitution; it's tooling,
  and the alternative (manually managing jars on a classpath) adds friction with no
  benefit.
- **Alternatives considered**: Gradle (equally valid; Maven chosen for its single
  declarative `pom.xml`), plain `javac` + manual classpath (rejected — painful
  dependency/test management).

## Unit testing framework

- **Decision**: JUnit 5 (Jupiter).
- **Rationale**: Standard modern Java testing framework; lets `PokemonService`'s
  random-selection and response-parsing logic be tested without starting the HTTP
  server, satisfying constitution Principle V (Testability of Core Logic).
- **Alternatives considered**: JUnit 4 (older, no benefit here), TestNG (unnecessary
  extra complexity for this scope).

## Determining the valid Pokemon ID range (including forms/variants)

- **Original decision (superseded — left here for the record)**: call
  `GET /api/v2/pokemon?limit=1` to read `count`, cache it, and pick a uniform-random
  integer in `[1, count]` as the ID to fetch.
- **Why it was wrong**: implementation testing (`GET /api/v2/pokemon?limit=100000`)
  showed PokeAPI's numeric Pokemon IDs are **not contiguous** — `count` was `1351`,
  but IDs range up to `10326` (alternate forms/Mega Evolutions live at IDs 10000+,
  with large gaps below that). Picking a random integer in `[1, count]` therefore hit
  unused IDs constantly, and `GET /api/v2/pokemon/{id}` 404'd on roughly half of all
  requests in manual testing — violating FR-004 (every request should be able to
  surface a valid random Pokemon) even though FR-005's error handling masked it as a
  generic "couldn't reach the service" message instead of a crash.
- **Corrected decision**: call `GET /api/v2/pokemon?limit=100000&offset=0` once (a
  single ~90 KB response covering all 1351 entries as of this writing), extract every
  entry's `name` from `results[].name` into an in-memory list, and cache that list for
  the process lifetime. Each "random Pokemon" request picks a uniform-random **index**
  into that list (`random.nextInt(list.size())`) and fetches
  `GET /api/v2/pokemon/{name}` — every entry in the list is guaranteed to be a real,
  fetchable Pokemon, so this can never 404.
- **Rationale**: This still matches the spec clarification (random selection includes
  every entry PokeAPI's listing exposes, forms/variants included) and still avoids a
  hardcoded, staleness-prone number — it just indexes by list position instead of by a
  numeric ID range that turned out not to be contiguous. One listing call is cached for
  the process lifetime, so this adds no extra per-click network cost versus the
  original plan (SC-002 still holds).
- **Alternatives considered**: Hardcoding a fixed ID range — still rejected, same
  staleness problem as before, and it doesn't fix the gap issue either. Retrying with a
  new random ID on 404 — rejected as a wasteful, unbounded-latency workaround for a
  problem the listing-by-name approach avoids entirely.

## Frontend update mechanism (vanilla JS)

- **Decision**: One static `app.js` file (no bundler, no libraries) that: on page load
  and on button click, calls `fetch('/api/random-pokemon')`; disables the button
  immediately before the call and re-enables it in a `finally` block; on success,
  updates the image `src`/`alt` and the name text; on failure, displays the returned
  error message in a visible status area.
- **Rationale**: Satisfies the constitution's restriction that JS may only perform DOM
  updates and call the backend (no business logic in JS), and directly implements the
  clarified behavior that the button is disabled while a request is in flight
  (FR-006/FR-007) and that images carry descriptive alt text (FR-008).
- **Alternatives considered**: None needed — this is the minimal approach that meets
  every functional requirement.

## Backend↔frontend contract shape

- **Decision**: `GET /api/random-pokemon` returns `200` with
  `{"name": string, "imageUrl": string}` on success, or a non-2xx status with
  `{"error": string}` on failure (PokeAPI unreachable, timeout, or unexpected
  response shape). Documented in detail in `contracts/random-pokemon-api.md`.
- **Rationale**: Keeps a small, explicit contract between the Java backend and the
  vanilla JS frontend; keeps all error-message construction in Java (Separation of
  Concerns) so JS only needs to display whatever string it receives.
- **Alternatives considered**: N/A — this is the natural minimal contract for the
  feature's two outcomes (success/failure).

**Output**: All unknowns from Technical Context are resolved above; none remain marked
`NEEDS CLARIFICATION`.
