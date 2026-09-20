# Quickstart: Random Pokemon Display

## Prerequisites

- JDK 21 installed
- Maven installed
- Internet access (the app calls the public PokeAPI at `pokeapi.co` at runtime)

## Build

```bash
mvn clean package
```

## Run

```bash
mvn exec:java
# or, after packaging:
java -jar target/pokemon-randomizer-*.jar
```

Then open `http://localhost:8080` in a browser.

## Validation scenarios

Each scenario below maps to a spec requirement or success criterion — run through all
of them to confirm the feature works end-to-end.

1. **Initial load** (SC-001, FR-001): Open `http://localhost:8080`. A Pokemon name and
   image appear within ~3 seconds.
2. **Fetch another** (SC-002, FR-002, FR-004): Click "New Random Pokemon". A different
   Pokemon's name and image replace the previous ones within ~3 seconds, with no
   full-page reload.
3. **Button disabled while loading** (FR-006, FR-007): Click the button and, before the
   response arrives, confirm the button is disabled and shows a loading indication;
   confirm it re-enables once the new Pokemon (or an error) appears.
4. **Repeated clicks stay responsive and never repeat consecutively** (SC-004, FR-010):
   Click the button 10 times in a row (waiting for each to finish), noting the name
   shown each time. The page never becomes unresponsive and needs no reload, and no
   two consecutive names match.
5. **Upstream failure is handled gracefully** (FR-005, SC-003): Block network access to
   `pokeapi.co` (e.g., via firewall/hosts file) and reload the page, then try clicking
   the button. Both cases show a clear, human-readable error message — never a blank or
   broken page.
6. **Keyboard accessibility** (FR-008): Using only the keyboard (Tab to focus, Enter or
   Space to activate), trigger the button and confirm a new Pokemon request fires.
7. **Image alt text** (FR-008): Inspect the displayed image element and confirm its
   `alt` attribute contains the current Pokemon's name.
8. **Language switch re-labels the same Pokemon** (SC-005, FR-011, FR-012): With a
   Pokemon displayed, note its image, then pick a different language from the
   dropdown. Confirm the name/types update to that language within ~3 seconds, the
   image stays the same (same Pokemon, not a new random one), and no full-page reload
   occurs.
9. **New fetches respect the selected language** (FR-013): With a non-English
   language selected, click "New Random Pokemon" a few times. Confirm every newly
   shown Pokemon's name/types appear in that language, not English.
10. **Variant/form names are fully translated** (spec Assumptions, FR-014): In a
    non-English language, click through until a Mega Evolution or regional-form
    Pokemon appears (e.g. any "-mega", "-mega-x/y", "-alola", "-galar", "-hisui",
    "-crowned" slug). Confirm the name is always shown as
    "{translated base name} ({translated qualifier})" — e.g. French should show
    "Dracaufeu (Méga X)" or "Raichu (Forme d'Alola)", never a bare fused name like
    "Méga-Dracaufeu X" and never an English qualifier like "Dracaufeu (Mega X)"
    (that English fallback should be rare/edge-case only).
11. **Browser-language default** (FR-015): If your browser's language is one of the
    9 supported ones, load the page fresh (new tab) and confirm the language
    dropdown already shows that language, not English. If your browser's language
    isn't supported, confirm it defaults to English. This can also be checked via
    devtools: `Object.defineProperty(navigator, 'languages', {value: ['fr-FR']})`
    before the page's script runs, then reload.
12. **Button label translates too** (FR-016): Switch through several languages in
    the dropdown and confirm the "New Random Pokemon" button's own text changes
    each time, immediately (not waiting on the Pokemon data to finish loading).

## Run unit tests

```bash
mvn test
```

Expected: `PokemonServiceTest` passes, covering the random-selection range logic,
no-consecutive-repeat behavior, and PokeAPI response parsing (including the
missing-image case and translation fallback from `data-model.md`) — without starting
the HTTP server, per the constitution's Testability of Core Logic principle.
