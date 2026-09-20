# Implementation Plan: Random Pokemon Display

**Branch**: `001-random-pokemon-display` | **Date**: 2026-09-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-random-pokemon-display/spec.md`

## Summary

A single-page web app that shows a random Pokemon (name, image, and colored type
badges) on load, with a button to fetch another random Pokemon in place — never
repeating the immediately previous one — without a full page reload. A language
dropdown (defaulting to the visitor's browser language when supported) lets the
visitor instantly re-display the current Pokemon, and every subsequently fetched one,
in any of 9 languages, including the button's own label. Per the constitution: Java
owns all business logic (calling PokeAPI, parsing responses, picking/excluding random
entries, resolving translations), HTML/CSS provide structure and presentation, and a
small amount of vanilla JavaScript exists solely to call the Java backend and swap the
displayed content — no JS frameworks, no Node.js, no database beyond the one narrow
in-memory exception the constitution (v1.2.0) carves out for repeat-avoidance.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: JDK built-ins only for networking — `java.net.http.HttpClient`
(calling PokeAPI's `/pokemon`, `/pokemon-species`, `/type`, and `/pokemon-form`
endpoints) and `com.sun.net.httpserver.HttpServer` (serving the app, including the
`GET /api/random-pokemon` and `GET /api/pokemon/{slug}` JSON endpoints); `org.json`
(single small library, no transitive dependencies) for parsing PokeAPI's JSON responses,
since the JDK has no built-in JSON parser. Packaged as a runnable fat jar via the
`maven-shade-plugin` (needed once `org.json` had to be bundled for `java -jar`).
Frontend: static HTML/CSS + one vanilla JavaScript file (no libraries/frameworks) —
includes a language `<select>`, `navigator.languages`-based default-language
detection, and a small client-side dictionary for the button's own translated label
(the only UI text not sourced from PokeAPI).

**Storage**: N/A — stateless, with one narrow exception the constitution (v1.2.0)
explicitly permits: a single in-memory "last shown Pokemon slug" value, held for the
process lifetime, used only to satisfy FR-010 (no consecutive repeats)

**Testing**: JUnit 5 (Jupiter) for Java unit tests

**Target Platform**: Any OS with a JVM 21+ runtime (server process), accessed via any
modern web browser

**Project Type**: Web application — single Java project serving static HTML/CSS/JS
resources plus two small JSON API endpoints (no separate frontend build/toolchain)

**Performance Goals**: Pokemon displayed on load, swapped after a button click, and
re-localized after a language change, each within 3 seconds over a typical broadband
connection (spec SC-001, SC-002, SC-005)

**Constraints**: No database/persistent storage beyond the one `lastShownSlug` value
noted above; no authentication/API keys; JavaScript limited to DOM updates, the button's
own static translated label, and calling the Java backend only (no PokeAPI calls, no
random selection, no translation-resolution logic in JS, per constitution); PokeAPI
failures MUST surface as a clear visitor-facing error, never a blank/broken page or
crash (FR-005)

**Scale/Scope**: Single-page app, personal/hobby scale; no specific concurrent-user
target defined (spec clarification: scalability marked low-impact/out of scope for this
feature)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Fixed Technology Stack | Java for all logic; HTML/CSS for UI; vanilla JS only for DOM updates, calling the Java backend, reading the browser's language preference, and its own static button-label dictionary (no PokeAPI business logic in JS, no frameworks, no Node.js) | PASS |
| II. PokeAPI as the Single Data Source | Java backend calls PokeAPI at request time via `HttpClient`; no bundled dataset; failures produce a user-visible error (FR-005) | PASS |
| III. Simplicity First (YAGNI) | No web framework (e.g., Spring); JDK's built-in `HttpServer`; three backend routes (static assets + two JSON endpoints); single minimal JSON library | PASS |
| IV. Separation of Concerns | Java owns PokeAPI calls, JSON parsing, random selection, and translation resolution; HTML/CSS own presentation; JS is limited to `fetch` + DOM swap, browser-language detection, and its own static UI text | PASS |
| V. Testability of Core Logic | Random-selection and response-parsing logic lives in a plain Java service class, unit-tested via JUnit 5 without starting the HTTP server | PASS |
| Additional Constraints: no API key/DB; public REST endpoints; full-range random selection incl. variants; one narrow stateless exception | Uses PokeAPI's public `/pokemon`, `/pokemon-species`, `/type`, `/pokemon-form` endpoints (no key) to determine the valid name range including forms/variants and resolve translations; the only state is the single `lastShownSlug` value the constitution (v1.2.0) explicitly permits for FR-010 | PASS |

No violations. See `research.md` for the rationale behind the added dependency
(`org.json`), the `maven-shade-plugin` packaging fix, the multi-language/translation
design, the no-consecutive-repeat mechanism, and other implementation-detail decisions.

**Post-design re-check** (after Phase 1, and again after the type/no-repeat/language
features were added): `data-model.md` (Pokemon/PokemonType/Error, plus the documented
Process State and Reference Data Cache exceptions), `contracts/random-pokemon-api.md`
(two JSON endpoints, both JS-facing only, both accepting a `lang` parameter), and
`quickstart.md` introduce nothing beyond what the table above already covers — all
gates still PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-random-pokemon-display/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── random-pokemon-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml                              # Maven build: org.json, JUnit 5, surefire, exec-maven-plugin,
                                      # maven-shade-plugin (runnable fat jar)

src/
├── main/
│   ├── java/
│   │   └── com/pokemonrandomizer/
│   │       ├── App.java               # Entry point: HttpServer, static files, GET /api/random-pokemon
│   │       │                          # and GET /api/pokemon/{slug} (both accept ?lang=)
│   │       ├── PokemonService.java    # Core logic: no-repeat random pick (FR-010), PokeAPI calls,
│   │       │                          # translation resolution incl. variant-form qualifier extraction
│   │       │                          # (FR-011-FR-014), species/type/form response caching
│   │       ├── PokemonClient.java     # java.net.http.HttpClient wrapper for /pokemon, /pokemon-species,
│   │       │                          # /type, /pokemon-form
│   │       ├── PokemonDataSource.java # Interface PokemonClient implements — lets tests use fakes
│   │       │                          # instead of real network calls (constitution Principle V)
│   │       ├── PokemonApiException.java # Checked exception for any upstream/parsing failure
│   │       ├── Pokemon.java           # Data holder: slug, localized name, imageUrl, types
│   │       └── PokemonType.java       # Data holder: English type slug (for badge color) + localized name
│   └── resources/
│       └── static/
│           ├── index.html           # Page structure: language <select>, name (h1), image, type badges, button
│           ├── style.css            # Presentation, incl. per-type badge colors set inline by app.js
│           └── app.js               # Vanilla JS: fetch both endpoints, DOM swap, button disable/enable,
│                                     # browser-language detection, button-label translation dictionary
└── test/
    └── java/
        └── com/pokemonrandomizer/
            └── PokemonServiceTest.java  # Unit tests: random-selection range, no-repeat behavior,
                                          # translation + English fallback, variant-qualifier extraction,
                                          # species/type/form response caching
```

**Structure Decision**: Single Java project (Maven-managed), not a split frontend/backend
layout — there is no separate frontend build toolchain, since the "frontend" is just
static HTML/CSS/JS served directly by the Java process. This matches the constitution's
Simplicity First and Fixed Technology Stack principles.

## Complexity Tracking

*No constitution violations to justify — all gates above pass.*
