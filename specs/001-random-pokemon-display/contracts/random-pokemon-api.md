# Contract: Random Pokemon API (Java backend ↔ browser)

This is the only interface the backend exposes, beyond serving the static
`index.html`/`style.css`/`app.js` files. It exists so the vanilla JS frontend can ask
for a new random Pokemon (spec FR-002) and re-display the current one in a different
language (spec FR-012), both without a page reload.

## Shared: the `lang` query parameter

Both endpoints below accept an optional `lang` query parameter.

- Valid values: `en`, `fr`, `de`, `es`, `it`, `ja`, `ko`, `zh-hans`, `zh-hant`
  (matching spec FR-011's fixed language set exactly).
- Missing, empty, or unrecognized value → defaults to `en`. This is never an error —
  language selection has no invalid-input failure mode.

## `GET /api/random-pokemon`

Requests one new randomly selected Pokemon, localized to `lang`. No request body.

### Success response

- **Status**: `200 OK`
- **Content-Type**: `application/json`
- **Body**:

  ```json
  {
    "slug": "pikachu",
    "name": "Pikachu",
    "imageUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png",
    "types": [{"slug": "electric", "name": "Electric"}]
  }
  ```

- `slug`: non-empty, language-independent identifier. The frontend stores this and
  passes it to `GET /api/pokemon/{slug}` when the visitor changes language without
  requesting a new Pokemon.
- `name`: non-empty string, localized to `lang` (falls back to English per FR-014).
- `imageUrl`: absolute URL string, or `null` if PokeAPI had no image for this entry
  (frontend handles this per `data-model.md`); language-independent.
- `types`: array of zero or more `{slug, name}` objects. `slug` is the English type
  identifier (e.g. `"electric"`) — language-independent, used by the frontend to pick
  the per-type badge color (FR-009) so colors stay correct regardless of `lang`.
  `name` is localized to `lang` (falls back to English per FR-014) and is what's
  displayed. Empty only in the rare case PokeAPI's response omitted type data
  entirely.

### Failure response

- **Status**: any non-2xx (e.g., `502 Bad Gateway` for an upstream PokeAPI failure,
  `504 Gateway Timeout` for a timeout)
- **Content-Type**: `application/json`
- **Body**: `{ "error": "Couldn't reach the Pokemon service — please try again." }`
- `error`: human-readable, visitor-facing message (FR-005). Never a stack trace or raw
  exception text.

### Behavioral notes

- Each call MUST pick uniformly at random from the full set of valid Pokemon slugs
  per `research.md`'s decision, excluding whichever Pokemon this endpoint most
  recently returned (FR-010) — unless the data source exposes only one Pokemon
  total, in which case that exclusion cannot apply. This exclusion is independent of
  `lang`.
- The frontend disables its button and the language dropdown before issuing this
  request and re-enables both only after the response (success or failure) is
  handled (FR-006/FR-007) — a frontend behavior, not something this endpoint
  enforces itself.

## `GET /api/pokemon/{slug}`

Re-fetches one specific, already-known Pokemon (identified by the `slug` a prior
response returned), localized to `lang`. Used when the visitor changes the language
dropdown, so the same Pokemon stays on screen (FR-012) instead of a new random one
being picked.

### Success response

Same shape as `GET /api/random-pokemon`'s success response, for the requested `slug`.

### Failure response

Same non-2xx/`{"error": "..."}` shape as `GET /api/random-pokemon` (covers both an
unrecognized `slug` and upstream PokeAPI failures alike — the frontend only ever
calls this with a `slug` it just received from `GET /api/random-pokemon`, so
"unrecognized slug" is not a normal-operation case worth a distinct status code).

### Behavioral notes

- Does **not** affect the no-consecutive-repeat tracking (`data-model.md`'s
  `lastShownSlug`) — re-localizing the Pokemon already on screen is not "showing a
  new one."
- Same frontend disable/re-enable behavior as the other endpoint while the request is
  in flight.
