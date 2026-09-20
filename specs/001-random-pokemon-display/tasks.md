---

description: "Task list template for feature implementation"
---

# Tasks: Random Pokemon Display

**Input**: Design documents from `/specs/001-random-pokemon-display/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/random-pokemon-api.md, quickstart.md

**Regenerated**: 2026-09-20, via `/speckit.analyze` → `/speckit.tasks`. The previous
version of this file only covered User Stories 1–2 (the original P1/P2 MVP); every
task below reflects what has since actually been built and tested — types (FR-009),
no-consecutive-repeat (FR-010), the full language-selection feature (FR-011–FR-015,
User Story 3), and button-label translation (FR-016). All tasks are already complete;
this file exists for traceability, not as a to-do list.

**Tests**: Unit tests for `PokemonService` are included — the constitution (Principle V:
Testability of Core Logic) commits to unit-testing this logic independent of the HTTP
server, so they're part of the Foundational phase rather than optional.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are relative to the repository root (`C:\Users\yohan\Pokemon-Randomise`)

## Path Conventions

Single Java project (Maven), per `plan.md`'s Structure Decision — no separate
frontend/backend split:

```text
pom.xml
src/main/java/com/pokemonrandomizer/
src/main/resources/static/
src/test/java/com/pokemonrandomizer/
```

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create the Maven project directory structure per `plan.md`: `pom.xml` at the
      repo root, `src/main/java/com/pokemonrandomizer/`,
      `src/main/resources/static/`, `src/test/java/com/pokemonrandomizer/`
- [X] T002 [P] Configure `pom.xml`: Java 21 source/target, the `org.json` dependency,
      the JUnit 5 (Jupiter) test dependency, the Maven Surefire plugin, the
      `exec-maven-plugin` (`mvn exec:java`), and the `maven-shade-plugin` so
      `java -jar target/pokemon-randomizer.jar` runs standalone with `org.json`
      bundled in, per `research.md`
- [X] T003 [P] Create a static resource skeleton: `src/main/resources/static/index.html`
      and `src/main/resources/static/style.css` with a base page layout

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core backend every user story depends on — PokeAPI access, translation
resolution, and the two JSON endpoints (`contracts/random-pokemon-api.md`).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Create the `Pokemon` data holder in
      `src/main/java/com/pokemonrandomizer/Pokemon.java`: fields `slug` (canonical,
      language-independent identifier), `name` (localized display name), `imageUrl`
      (nullable), `types` (`List<PokemonType>`) — per `data-model.md`
- [X] T005 [P] Create the `PokemonType` data holder in
      `src/main/java/com/pokemonrandomizer/PokemonType.java`: `slug` (English type
      identifier, drives badge color) and `name` (localized display text) — FR-009
- [X] T006 [P] Create the `PokemonDataSource` interface in
      `src/main/java/com/pokemonrandomizer/PokemonDataSource.java`:
      `fetchAllPokemonNames`, `fetchPokemonByName`, `fetchSpeciesByName`,
      `fetchTypeByName`, `fetchFormByName` — lets `PokemonServiceTest` use a fake
      instead of a real network call (constitution Principle V)
- [X] T007 [P] Create `PokemonApiException.java` in
      `src/main/java/com/pokemonrandomizer/PokemonApiException.java`: checked
      exception for any upstream PokeAPI failure or unparseable response
- [X] T008 [P] Create `PokemonClient.java` in
      `src/main/java/com/pokemonrandomizer/PokemonClient.java` implementing
      `PokemonDataSource` via `java.net.http.HttpClient`: `fetchAllPokemonNames` calls
      `GET /pokemon?limit=100000&offset=0` and extracts `results[].name` (the full,
      always-valid name list — not a numeric ID range, which turned out to have gaps;
      see `research.md`); `fetchPokemonByName` calls `GET /pokemon/{slug}`;
      `fetchSpeciesByName` calls `GET /pokemon-species/{slug}`; `fetchTypeByName`
      calls `GET /type/{slug}`; `fetchFormByName` calls `GET /pokemon-form/{slug}`
- [X] T009 `PokemonService.java` in
      `src/main/java/com/pokemonrandomizer/PokemonService.java` (depends on T004-T008):
      - Caches the full name list, plus species/type/form responses by slug, in memory
        for the process lifetime (`research.md`)
      - `pickNameExcluding` / `lastShownSlug`: picks a random slug that differs from
        the immediately previous one shown, across all visitors, with a single-entry
        guard so it can never loop forever (FR-010; the one stateful exception
        constitution v1.2.0 permits)
      - `buildLocalizedPokemon(slug, lang)`: fetches the Pokemon, resolves its
        localized name via the species endpoint (falling back to English per FR-014),
        resolves each type's localized name via the type endpoint, and — for a
        variant/form (slug's species differs from itself) — resolves the qualifier
        from the form endpoint's `names[]` (curated, en/fr/de) or `form_names[]` (all
        9 languages), always extracting just the qualifier (even from an already-fused
        compound name) so the result is consistently `"{species} ({qualifier})"`,
        falling back to an English slug-derived qualifier only if the data source has
        no translation at all
      - `normalizeLanguage`: validates the requested language against the 9 supported
        codes, defaulting to English (FR-011, FR-014)
      - On any network failure, timeout, or unparseable response, surfaces a clear,
        non-technical error instead of throwing an uncaught exception (FR-005)
- [X] T010 [P] `PokemonServiceTest.java` in
      `src/test/java/com/pokemonrandomizer/PokemonServiceTest.java` (depends on T009):
      16 tests covering the random-selection range, no-consecutive-repeat behavior
      (including the single-Pokemon edge case), successful translation with type
      badges, English fallback when a translation is missing, missing-image/no-types
      parsing, and all four variant-qualifier resolution paths (curated form name,
      fused compound name, qualifier-only descriptor, and the last-resort English
      fallback) — including a regression test for an apostrophe-preservation bug
      found and fixed during development — all without starting the HTTP server
      (constitution Principle V)
- [X] T011 [P] `App.java` entry point in
      `src/main/java/com/pokemonrandomizer/App.java` (depends on T009): starts
      `com.sun.net.httpserver.HttpServer` on port 8080; serves static files from
      `src/main/resources/static` for `GET /`; registers
      `GET /api/random-pokemon?lang=` and `GET /api/pokemon/{slug}?lang=`, both
      returning `200` with `{"slug","name","imageUrl","types":[{"slug","name"}]}` on
      success or a non-2xx status with `{"error"}` on failure, exactly per
      `contracts/random-pokemon-api.md`; includes startup/error logging

**Checkpoint**: Backend fully serves localized, non-repeating random Pokemon JSON at
both endpoints, and `index.html`/`style.css` skeleton exists. User story work can now
begin.

---

## Phase 3: User Story 1 - See a random Pokemon on arrival (Priority: P1) 🎯 MVP

**Goal**: A visitor who loads the page, without doing anything else, sees a randomly
selected Pokemon's name, image, and types.

**Independent Test**: Load the page with no further interaction; confirm a Pokemon
(name + image + type badges) appears (quickstart.md scenario 1); block network access
to `pokeapi.co` and reload to confirm a clear error message appears instead
(quickstart.md scenario 5).

### Implementation for User Story 1

- [X] T012 [US1] Build `src/main/resources/static/index.html`'s core structure: an
      `<h1 id="pokemon-name">` that displays the Pokemon's own name (not a static page
      title), an error message area, `<img id="pokemon-image">`, and a
      `<div id="pokemon-types">` container for type badges; presentation for all of it
      in `src/main/resources/static/style.css`, including a `[hidden]` override so the
      image correctly disappears (not a broken-image icon) when a Pokemon has no
      sprite — FR-001, FR-005, FR-008, FR-009
- [X] T013 [US1] Implement page-load fetch + render logic in
      `src/main/resources/static/app.js` (uses the element IDs from T012): on
      `DOMContentLoaded`, call `fetch('/api/random-pokemon?lang=...')`; on success, set
      the image `src`/`alt` and the name text (FR-001, FR-008), and render one
      colored `<span class="type-badge">` per type — colored via a client-side
      English-type-slug → hex-color map so the color stays correct regardless of
      display language (FR-009); on failure, display the returned `error` message
      instead of a blank/broken page (FR-005); the button is disabled for the duration
      of this fetch too, doubling as the sole loading indication (FR-007) — no
      separate loading text

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Request another random Pokemon (Priority: P2)

**Goal**: While viewing a Pokemon, the visitor clicks a button and a different,
never-immediately-repeated randomly selected Pokemon replaces it, without a full page
reload.

**Independent Test**: With a Pokemon displayed, click the button and confirm the
displayed Pokemon changes without page navigation (quickstart.md scenario 2); confirm
the button is disabled and re-enabled around the request (quickstart.md scenario 3);
click it 10+ times and confirm no two consecutive results match (quickstart.md
scenario 4); confirm keyboard-only activation works (quickstart.md scenario 6).

### Implementation for User Story 2

- [X] T014 [US2] Add a "New Random Pokemon" `<button>` element to
      `src/main/resources/static/index.html` (a native `<button>` is keyboard-focusable
      and Enter/Space-activatable by default — FR-008) and its default/hover/disabled
      styles to `src/main/resources/static/style.css` — FR-002, FR-006
- [X] T015 [US2] Implement the button's click handler in
      `src/main/resources/static/app.js`, sharing the same fetch/render function as
      T013 (`loadPokemon`): disables the button before the request, calls
      `GET /api/random-pokemon?lang=...` (the backend excludes the immediately
      previous Pokemon per FR-010), renders the result the same way as the page-load
      flow, and re-enables the button in a `finally` block regardless of outcome —
      FR-002, FR-006, FR-007, FR-010

**Checkpoint**: User Story 1 AND User Story 2 both work, independently and together.

---

## Phase 5: User Story 3 - Choose the display language (Priority: P3)

**Goal**: A visitor picks a language from a dropdown; the Pokemon already on screen is
instantly re-displayed in that language (no new Pokemon fetched), every subsequently
fetched Pokemon uses it too, and it defaults sensibly from the visitor's browser.

**Independent Test**: With a Pokemon displayed in the default language, select a
different language and confirm the same Pokemon's name/types update without a
different Pokemon appearing (quickstart.md scenario 8); click "New Random Pokemon" and
confirm the new one also appears in that language (scenario 9); confirm variant/form
Pokemon get a fully translated `"{species} ({qualifier})"` name, not just the base
species with an English qualifier (scenario 10); confirm a supported browser language
is auto-selected on first load (scenario 11); confirm the button's own label also
translates (scenario 12).

### Implementation for User Story 3

- [X] T016 [US3] Add a language `<select id="language-select">` to
      `src/main/resources/static/index.html` with 9 `<option>`s (English, Français,
      Deutsch, Español, Italiano, 日本語, 한국어, 简体中文, 繁體中文), defaulting to
      English in markup, plus its presentation in
      `src/main/resources/static/style.css` — FR-011
- [X] T017 [US3] Implement browser-language detection in
      `src/main/resources/static/app.js`: `detectBrowserLanguage()` reads
      `navigator.languages` (falling back to `navigator.language`), maps each
      preference in order to one of the 9 supported codes (with a Taiwan/Hong
      Kong/Macau/"Hant"-tag → Traditional vs. everything-else-`zh-*` → Simplified
      heuristic), defaulting to English if nothing matches; called once on
      `DOMContentLoaded`, before the first fetch — FR-015
- [X] T018 [US3] Implement `relocalizeCurrentPokemon()` in
      `src/main/resources/static/app.js` (depends on T013's `loadPokemon`): on the
      language `<select>`'s `change` event, calls
      `GET /api/pokemon/{currentSlug}?lang=...` for the Pokemon already on screen
      (tracked in a `currentSlug` variable, set by every successful render) and
      re-renders it — never triggers a new random pick — FR-012
- [X] T019 [US3] Wire `fetchRandomPokemon()` (page load and button click, T013/T015)
      to always include `?lang=` from the current `<select>` value, so every
      subsequently fetched Pokemon honors the selected language — FR-013
- [X] T020 [US3] Add the `BUTTON_LABELS` dictionary (9 languages) and
      `updateButtonLabel()` to `src/main/resources/static/app.js`, called on initial
      load (after T017's detection) and immediately on every language `change` event
      — independent of whether the relocalize fetch (T018) has resolved yet — FR-016

**Checkpoint**: All three user stories work, independently and together.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification across all three stories

- [X] T021 [P] Startup/error logging in `src/main/java/com/pokemonrandomizer/App.java`
      for local development visibility
- [X] T022 Manually run through all 12 validation scenarios in `quickstart.md` in a
      browser (via Chrome automation) and confirm each passes, including the full
      9-language sweep for a variant Pokemon
- [X] T023 Run `mvn test` and confirm all 16 `PokemonServiceTest` cases pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion only
- **User Story 2 (Phase 4)**: Depends on Foundational completion; reuses T013's render
  logic, so built after Phase 3 even though the backend has no per-story dependency
- **User Story 3 (Phase 5)**: Depends on Foundational completion (T009's translation
  logic) and reuses T013's render/fetch logic; built after Phases 3-4
- **Polish (Phase 6)**: Depends on all three user stories being complete

### Within Each Phase

- T004-T008 (data holders, interface, exception, client) before T009 (service that
  uses all of them)
- T009 before T010 (tests) and T011 (server wiring that calls the service)
- T012 (markup/CSS with element IDs) before T013 (JS that references those IDs)
- T014 (button element) before T015 (button's click handler)
- T016 (language select element) before T017-T020 (JS that references it)
- T017 (browser detection) conceptually before T020 (button label uses the detected
  language on first load), though both run inside the same `DOMContentLoaded` handler

### Parallel Opportunities

- T002 and T003 can run in parallel (different files, both only need T001)
- T004, T005, T006, T007, T008 can run in parallel (different files, no
  interdependency until T009 needs all of them)
- T010 and T011 can run in parallel once T009 is done (different files)
- T021 can run in parallel with Phase 3-5 work (different file, no dependency on the
  frontend tasks)

---

## Parallel Example: Phase 2 (Foundational)

```bash
# After T001-T003 are done, launch these together:
Task: "Create Pokemon.java data holder in src/main/java/com/pokemonrandomizer/Pokemon.java"
Task: "Create PokemonType.java data holder in src/main/java/com/pokemonrandomizer/PokemonType.java"
Task: "Create PokemonDataSource.java interface in src/main/java/com/pokemonrandomizer/PokemonDataSource.java"
Task: "Create PokemonApiException.java in src/main/java/com/pokemonrandomizer/PokemonApiException.java"
Task: "Create PokemonClient.java in src/main/java/com/pokemonrandomizer/PokemonClient.java"

# After T009 (PokemonService.java) is done, launch these together:
Task: "Write PokemonServiceTest.java in src/test/java/com/pokemonrandomizer/PokemonServiceTest.java"
Task: "Create App.java entry point in src/main/java/com/pokemonrandomizer/App.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run quickstart.md scenarios 1 and 5
5. This is a demoable MVP: the page shows a random Pokemon (with types) on load

### Incremental Delivery (what actually happened)

1. Setup + Foundational → backend and page skeleton ready
2. Add User Story 1 → validate independently → demoable MVP
3. Add User Story 2 → validate independently → full P1/P2 feature complete
4. Add User Story 3 (language selection) → validate independently → full feature
   complete, all 16 FRs and 5 SCs satisfied
5. Polish phase → run all 12 quickstart.md scenarios + `mvn test`

## Notes

- [P] tasks touch different files with no dependency on an incomplete task
- [Story] label maps each task to its user story for traceability
- Commit after each task or logical group
- Stop at any checkpoint to validate that story independently before continuing
