# Dynamic UI runtime (phase 1)

## Decision

Horizon keeps Kotlin, Jetpack Compose and Material 3 as its native Android UI platform.
The Rust core will become the source of truth for the launcher description. Android will
render a versioned, data-only contract; it will not execute downloaded Kotlin, scripts,
HTML or arbitrary component code.

## Contract v1

The initial contract is implemented in
`app/src/main/java/dev/horizon/launcher/ui/dynamic/UiSchema.kt`.

Top-level fields:
- `schemaVersion`: required protocol version (currently 1).
- `title`: launcher title.
- `navigation[]`: IDs, labels and target page IDs.
- `pages[]`: page IDs, titles and ordered component trees.

Supported component types in v1: `heading`, `text`, `button`, `card`,
`divider`, `progress`, and `list`. Actions are identifiers only and are dispatched
by the Android host through an allowlisted adapter. The schema parser limits document
size, page count, component count, nesting and text length, rejects duplicate IDs and
rejects navigation targets that do not exist.

## Core protocol

The Rust core should expose these logical operations through one generic JNI surface:
- `uiDescribe()`: return the complete versioned document and advertised capabilities.
- `uiRead(sourceId)`: return JSON data for an advertised source.
- `uiCall(actionId, arguments)`: execute an advertised action and return a typed result.
- `uiEvents(jobId)` (or a poll/stream equivalent): progress and completion events.

These operations must be backed by the existing Rust functions. Existing JNI methods remain
the compatibility layer until each action has been mapped and tested. Never dispatch an
arbitrary symbol or method name received from JSON.

## Migration plan

1. Land the schema and isolated Compose renderer without changing the production launcher.
2. Add Rust-side descriptor export and generic JNI calls, keeping existing methods intact.
3. Add a compatibility adapter mapping current instances, mods, accounts, servers, settings,
   worlds, controls, browse and updater operations to stable action/source IDs.
4. Run a test where Rust adds/renames/reorders a page and navigation item and Android displays
   it without a Kotlin page enum or app rebuild.
5. Migrate one module at a time; keep the current UI as fallback for unsupported schema versions
   or missing native exports. Preserve the Zalith editor and game input bridge unchanged.

## Important limitation of phase 1

The renderer and protocol are groundwork, not yet the live app UI. The current launcher still
uses its existing screen implementation until Rust descriptor export and the adapter are wired.
This avoids introducing unresolved JNI symbols or a nonfunctional launcher in an intermediate
commit.
