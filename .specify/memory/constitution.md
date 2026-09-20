<!--
Sync Impact Report
Version change: 1.1.0 → 1.2.0
Modified sections: Additional Constraints — carves out one narrow exception to the
  "stateless" constraint: a single in-memory "last shown Pokemon" value, kept only to
  stop the same Pokemon appearing twice in a row. Driven by a user request (spec
  001-random-pokemon-display, FR-010) that cannot be satisfied under a strictly
  stateless model, since avoiding a repeat requires remembering the previous pick.
Added sections: None
Removed sections: None
Deferred/TODO placeholders: None
Templates requiring follow-up review: plan-template.md, spec-template.md, tasks-template.md,
  checklist-template.md — not modified by this command; recommend a pass to confirm they don't
  assume a stack/workflow that conflicts with the principles below.
-->
# Pokemon Randomizer Constitution

## Core Principles

### I. Fixed Technology Stack
Application logic MUST be written in Java; the web UI MUST be built with HTML and CSS.
Plain (vanilla) JavaScript MAY be used, but ONLY for DOM updates (e.g., swapping the
displayed Pokemon's name/image) and for calling the Java backend — it MUST NOT contain
business logic such as calling PokeAPI directly, parsing PokeAPI responses, or performing
random selection; that logic MUST stay in Java. No JS framework/library (React, Angular,
jQuery, etc.), no Node.js backend service, and no additional language or build framework
beyond this MAY be introduced without a constitution amendment documenting the
justification. This keeps the stack small and predictable for a single-feature app while
still allowing a page to update without a full reload.

### II. PokeAPI as the Single Data Source
All Pokemon data (names, species, sprites, etc.) MUST be retrieved at runtime from the
public PokeAPI (https://pokeapi.co). The application MUST NOT bundle a static/hardcoded
Pokemon dataset as its primary data source. Network failures or non-2xx API responses
MUST be handled gracefully with a user-visible message; the application MUST NOT crash
or show a blank/broken page on API failure.

### III. Simplicity First (YAGNI)
The project starts as a minimal single-feature app: display one random Pokemon, with a
button to fetch another random one. Features beyond this (search, favorites, battling,
filtering, accounts, persistence, etc.) are out of scope until introduced via a new
specification. Do not add abstractions, frameworks, or infrastructure beyond what is
needed to call PokeAPI and render the result.

### IV. Separation of Concerns
Java code owns data fetching and business logic (calling PokeAPI, parsing the response,
selecting the random Pokemon). HTML/CSS own structure and presentation only. Business
logic (API calls, random selection, data parsing) MUST NOT be embedded in markup.

### V. Testability of Core Logic
Java logic responsible for calling PokeAPI and selecting a random Pokemon MUST be
unit-testable independent of a running web server, so correctness of random-selection
and response-parsing does not depend on manually clicking through the UI.

## Additional Constraints

- PokeAPI requires no API key/authentication; none MAY be introduced for this feature.
- Requests MUST use PokeAPI's public REST endpoints (e.g. `/api/v2/pokemon/{id-or-name}`).
- Random selection MUST draw from the full range of Pokemon IDs available via PokeAPI at
  request time (no arbitrary hardcoded subset) unless a future spec states otherwise.
- No database or persistent storage is required or permitted for this feature. The one
  narrow exception: the server MAY hold a single in-memory value naming the most
  recently shown Pokemon, for the life of the running process only (never written to
  disk, shared across all visitors since there are no accounts/sessions, reset on
  restart), used solely to avoid repeating that exact Pokemon on the very next request
  (FR-010). Beyond that one value, each "new random Pokemon" request remains
  independent and stateless.

## Development Workflow

- UI/visual changes MAY be verified manually in a browser.
- Java logic changes (API calls, parsing, random selection) SHOULD be covered by unit
  tests before being considered complete.
- Work MUST proceed through the Spec Kit workflow (`/speckit-specify` →
  `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`) rather than ad-hoc
  implementation, so the stack and scope constraints above are respected.

## Governance

This constitution supersedes any conflicting ad-hoc practice for this project. Amendments
require: (1) a documented reason for the change, (2) an update to this file including a
Sync Impact Report, and (3) a version bump per semantic versioning — MAJOR for backward
incompatible principle removals/redefinitions, MINOR for new principles or materially
expanded guidance, PATCH for clarifications and wording fixes. All future specs and plans
MUST be checked against these principles; any deviation MUST be justified in the relevant
plan's Complexity Tracking section or rejected in favor of a compliant approach.

**Version**: 1.2.0 | **Ratified**: 2026-09-20 | **Last Amended**: 2026-09-20
