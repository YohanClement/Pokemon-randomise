# Pokemon Randomizer

A single-page web app that shows a random Pokemon — name, sprite, and colored type
badges — with a button to fetch another one. Supports 9 display languages, including
translated variant/form names (e.g. Mega Evolutions, regional forms) and an
automatically detected default based on your browser's language.

Built with **Java** (backend + all business logic), **HTML/CSS**, and a small amount of
**vanilla JavaScript** (DOM updates and calling the backend only). Pokemon data comes
live from [PokeAPI](https://pokeapi.co) — nothing is bundled or hardcoded.

## Features

- Random Pokemon shown on load, with a button for another — never repeating the
  immediately previous one
- Name, sprite image, and type badges (colored to match the games)
- Choose a display language: English, Français, Deutsch, Español, Italiano, 日本語,
  한국어, 简体中文, 繁體中文 — defaults to your browser's language when supported
- Switching language instantly re-labels the Pokemon on screen (no new fetch)
- Graceful error handling if PokeAPI is unreachable
- Keyboard-accessible controls and descriptive image alt text

## Prerequisites

- JDK 21+
- Maven
- Internet access (the app calls the public PokeAPI at `pokeapi.co` at runtime)

## Build & run

```bash
mvn clean package
java -jar target/pokemon-randomizer.jar
```

Then open [http://localhost:8080](http://localhost:8080) in a browser.

Alternatively, run without packaging first:

```bash
mvn exec:java
```

## Run tests

```bash
mvn test
```

## Project structure

```text
src/main/java/com/pokemonrandomizer/   Backend: HTTP server, PokeAPI client, random
                                        selection, translation resolution
src/main/resources/static/             Frontend: index.html, style.css, app.js
src/test/java/com/pokemonrandomizer/   Unit tests
specs/001-random-pokemon-display/      Feature spec, plan, tasks, and design docs
  spec.md                              Requirements and user stories
  plan.md                              Technical design and architecture decisions
  tasks.md                             Implementation task breakdown
  research.md                          Decisions made during implementation, with rationale
.specify/memory/constitution.md        Project-wide engineering principles
```

This project was built using the [Spec Kit](https://github.com/github/spec-kit)
spec-driven workflow — see `specs/001-random-pokemon-display/` for the full
requirements and design history behind the app.
