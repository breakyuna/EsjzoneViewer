# Presentation Design System Contract

This package is the only presentation entry point for the rebuilt visual system.

## Stable feature-facing APIs

- `AppTheme`: color, typography, and shape ownership.
- `AppSpacing`, `AppShapes`, `AppTypography`, `AppElevation`, and `AppTouchTarget`: geometry and text tokens.
- `AppMotion`: duration, easing, and animation specs.
- `rememberAppAdaptiveMetrics`: compact, medium, and expanded layout metrics.
- `AppImage`: image loading seam; feature code must not import a concrete image loader.
- `AppFeedback`, `AppLoadingState`, `AppEmptyState`, `AppErrorState`, `AppOfflineState`, `AppDialog`, `AppBottomSheet`, and `AppSnackbarHost`: feedback primitives.
- `glass.AppGlassSurface`: the only feature-facing glass surface.

Component APIs live in `ui.component` and are named `App*`: `AppNovel*`, `AppCommentItem`,
`AppDownloadItem`, `AppHistoryItem`, `AppBookshelfItem`, `AppBackHeader`, `AppSearchHeader`,
`AppSectionHeader`, and `AppGroup`. Use `appNovelKey`/`appNovelContentType`,
`appChapterRowKey`/`appChapterRowContentType`, and the item content-type constants when wiring
lazy lists. Keys must be derived from a stable server or local identifier, never the row index.

## Runtime ownership

There is no compatibility UI layer. Feature pages use the `App*` components directly, so the
rebuilt presentation has one visual implementation and one set of accessibility contracts.

## Experimental isolation

The `glass` package owns the optional future blur adapter. No Haze, Telephoto, Lottie, Shimmer, or
other experimental dependency is added until a feature has a verified use case. Until then the
surface uses a translucent Material fallback with the same contract.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
