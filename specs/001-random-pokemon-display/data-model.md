# Phase 1 Data Model: Random Pokemon Display

This feature is stateless (constitution: no database/persistent storage), aside from one
narrow, deliberate exception described under Process State below — the "data model"
here otherwise describes in-memory/transfer shapes only, nothing persisted.

## Pokemon

Represents the single randomly selected Pokemon currently shown to the visitor
(spec Key Entities: Pokemon).

| Field | Type | Source | Notes |
|---|---|---|---|
| `slug` | String | PokeAPI `/pokemon/{id}` response `name` | Canonical, language-independent identifier (e.g., `"charizard-mega-x"`). Used to re-fetch the same Pokemon in a different language (FR-012) and as the no-consecutive-repeat key (FR-010). Never shown to the visitor. |
| `name` | String | PokeAPI `/pokemon-species/{id}` response `names[]`, filtered to the selected language (FR-011), with an English-appended form qualifier for variants (see Assumptions) | The **localized display name** shown on the card; also used as the image's alt text (FR-008) |
| `imageUrl` | String (URL) | PokeAPI response `sprites.front_default` | Absolute URL to the Pokemon's sprite image; language-independent |
| `types` | List\<PokemonType(slug, name)\> | `slug` from PokeAPI `/pokemon/{id}` `types[].type.name` (English, e.g. `"fire"`); `name` from `/type/{id}` response `names[]`, filtered to the selected language (FR-011) | One or two entries, in the order PokeAPI's `/pokemon/{id}` `types[]` returns them; `slug` drives the badge color (FR-009's per-type color, keyed by English slug so it stays correct regardless of language), `name` is what's displayed |

**Validation rules**:

- `slug` MUST be non-empty for a successful result; if PokeAPI's response cannot be
  parsed or is missing a name, the request is treated as a failure (FR-005), not a
  partially-filled success.
- `name` (localized): if the requested language has no entry in the species'
  `names[]`, fall back to the English entry; if even that is missing (should not
  happen in practice), fall back to `slug` (FR-014).
- For a variant/form (where the `/pokemon/{id}` response's `species.name` differs
  from `slug`), `name` is **always** formatted as `{translated species name}
  ({translated qualifier})` — e.g. `"Zamazenta (Crowned)"`, or in French
  `"Dracaufeu (Méga X)"` — resolved from `/pokemon-form/{slug}`, with an English
  qualifier only as a last resort when the data source has no translation at all for
  that language. See `research.md`'s "variant/form handling, revised" decision for
  the exact algorithm (including how a fused compound name like Japanese
  `"メガリザードンＸ"` still yields a separate, parenthesized qualifier).
- `imageUrl` MAY be null/absent for a given Pokemon entry. If absent, the name is
  still shown per the spec's edge case ("the page MUST still show what is available");
  the frontend renders the image element without a broken-image icon in this case
  (e.g., hidden or a neutral placeholder) — an implementation detail for the tasks
  phase, not a new requirement.
- `types` (localized): each type falls back to its English name if the requested
  language has no entry (FR-014); the list MUST contain at least one entry for a
  successfully parsed Pokemon (PokeAPI always returns at least one type).

**Lifecycle**: Transient — one `Pokemon` value exists per successful request/response
cycle; nothing is stored between requests.

## Error

Represents a failed attempt to retrieve a Pokemon (spec FR-005, Edge Cases).

| Field | Type | Notes |
|---|---|---|
| `error` | String | Human-readable message shown to the visitor (e.g., "Couldn't reach the Pokemon service — please try again.") |

**Lifecycle**: Transient — produced only for the request that failed; does not affect
any later request.

## Relationships

None — `Pokemon` and `Error` are mutually exclusive outcomes of a single request; there
is no relationship between them.

## Process State

| Field | Type | Notes |
|---|---|---|
| `lastShownSlug` | String (nullable) | The `slug` of the most recently successfully shown Pokemon, held in memory for the life of the running server process only. `null` until the first successful request. |

- Used exclusively to satisfy FR-010 (no two consecutive requests show the same
  Pokemon). Not returned in any API response, not written to disk, and reset to
  `null` on every server restart.
- Shared across all visitors hitting this server process — there is no per-visitor
  session, so "consecutive" means "the last request this server handled," not "the
  last request this particular visitor made" (see spec Assumptions).
- This is the one narrow exception to statelessness the constitution's Additional
  Constraints section explicitly carves out (v1.2.0).
- Language selection is intentionally **not** part of this process state — it lives
  only in the visitor's browser (a JS variable), per spec Assumptions ("not
  remembered across reloads").

## Reference Data Cache (implementation detail, not user-visible state)

The server may cache PokeAPI's raw `/pokemon-species/{id}`, `/type/{id}`, and (for
variant/form Pokemon) `/pokemon-form/{id}` responses in memory, keyed by slug, for the
life of the process. This is read-only, immutable
reference data (a species' or type's translations don't change while the server
runs) — the same category of caching already used for the full Pokemon name list
(`research.md`), not visitor-specific or request-specific state, so it does not
implicate the constitution's statelessness constraint the way `lastShownSlug` does.
Purpose: avoid re-fetching the same species/type translations from PokeAPI every time
a visitor switches language on the same Pokemon, or when a common type (e.g., "flying")
recurs across many different Pokemon.
