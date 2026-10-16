# Presentation Design System Contract

This package is the only presentation entry point for the rebuilt visual system.

## Stable feature-facing APIs

- `AppTheme`: color, typography, and shape ownership.
- `AppSpacing`, `AppShapes`, `AppTypography`, `AppElevation`, and `AppTouchTarget`: geometry and text tokens.
- `AppMotion`: duration, easing, and animation specs.
- `rememberAppAdaptiveMetrics`: compact, medium, and expanded layout metrics.
- `AppImage`, `AppCoverImage`, `AppAvatarImage`, and `AppReaderImage`: image loading seams;
  feature code must not import a concrete image loader. All four use the application-scoped
  loader owned by `AppContainer`, so covers, avatars, reader images, and downloaded covers share
  the same memory/disk cache.
- `AppCoverImage.sourceState` preserves unknown, resolving, and confirmed missing metadata;
  actual image callbacks track loading, loaded, or a categorized failure internally. `coverFallbackResource()` uses `cover_network_error.jpg` only for confirmed
  network failures (including timeouts and HTTP errors); all other fallback states use
  `cover_missing.jpg`. Avatars, description images, and reader images use the same error policy.
  A successfully loaded fallback never marks the actual cover loaded.
- `AppFeedback`, `AppLoadingState`, `AppErrorState`, `AppOfflineState`, `AppDialog`, `AppBottomSheet`, `AppSideSheet`, `AppSnackbarHost`, and `AppSyncStatusDot`: feedback primitives.

Component APIs live in `ui.component` and are named `App*`: `AppNovel*`, `AppCommentItem`,
`AppDownloadItem`, `AppHistoryItem`, `AppBookshelfItem`, `AppBackHeader`, `AppSearchHeader`,
`AppSectionHeader`, and `AppGroup`. Use `appNovelKey`/`appNovelContentType`,
`appChapterRowKey`/`appChapterRowContentType`, and the item content-type constants when wiring
lazy lists. Keys must be derived from a stable server or local identifier, never the row index.

## Runtime ownership

There is no compatibility UI layer. Feature pages use the `App*` components directly, so the
rebuilt presentation has one visual implementation and one set of accessibility contracts.

## Surfaces and navigation

Reader offline badges and feedback blocks use Material 3 `Surface` with theme container
colors. The compact shell uses a Material bottom bar; medium and expanded shells use
`NavigationRail`.

The home weekly-popular carousel, forum, guestbook, and water-cooler cards and the profile settings card
use a 300 ms shared-bounds container transform into their full-page destinations.
Bounds and rounded corners interpolate together; source-card and destination
content keep their own final layout sizes while the outer container changes.
Corner animation state is read during clipping/layer updates, with a remembered
overlay clip and reusable path, rather than during composition. Back navigation reverses the
transform. Carousel navigation preserves the clicked pager instance in its saved
route so repeated books have distinct transition keys. Other novel-list entries
and routes retain their existing transitions.

Static hygiene: `python3 tools/qa/verify_static_contracts.py` and `git diff --check`.
These checks do not compile Kotlin or validate rendering on a device.

## Ownership rules

Feature code must not import legacy theme/component packages or Coil types. Business repositories,
network parsing, Room entities/migrations, download contracts, and reader algorithms remain outside
this package.
