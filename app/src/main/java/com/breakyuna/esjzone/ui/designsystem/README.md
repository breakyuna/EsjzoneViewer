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
- `AppFeedback`, `AppLoadingState`, `AppEmptyState`, `AppErrorState`, `AppOfflineState`, `AppDialog`, `AppBottomSheet`, and `AppSnackbarHost`: feedback primitives.
- `glass.AppGlassSurface`: shared glass rendering adapter.
- `glass.AppNavigationGlassSurface`: navigation-only frosted material for bottom and side capsules.

Component APIs live in `ui.component` and are named `App*`: `AppNovel*`, `AppCommentItem`,
`AppDownloadItem`, `AppHistoryItem`, `AppBookshelfItem`, `AppBackHeader`, `AppSearchHeader`,
`AppSectionHeader`, and `AppGroup`. Use `appNovelKey`/`appNovelContentType`,
`appChapterRowKey`/`appChapterRowContentType`, and the item content-type constants when wiring
lazy lists. Keys must be derived from a stable server or local identifier, never the row index.

## Runtime ownership

There is no compatibility UI layer. Feature pages use the `App*` components directly, so the
rebuilt presentation has one visual implementation and one set of accessibility contracts.

## Experimental isolation

The `glass` package isolates the Haze core and experimental Glass API (currently
`2.0.0-beta02`). Feature code must use the adapters instead of importing Haze directly.
The renderer selects its supported optical implementation; a missing host uses a Material
surface fallback. Essential information must remain readable without refraction or blur.

### Navigation glass

The shell captures only its page-content sibling into a dedicated `AppGlassScene` and passes
that same scene to the overlay. Do not put the navigation overlay inside its own capture source.
`NavigationGlass.kt` owns both orientations' material and lighting; reader surface defaults
remain independent.

- Fixed `14.dp` blur with `depth = 1f` avoids mixing the original sharp covers back into labels.
  In the pinned renderer, fixed blur is capped at 38.5 physical pixels after density conversion.
- Source-fill alpha is `1f`; it fills behind captured content, not over it. Theme tint controls
  diffusion/legibility: light `0.72f`, dark `0.78f`. The no-host fallback alpha is `0.96f`.
- A narrow `6.dp`/`8.dp` refraction bezel, restrained top-start light, sub-dp gradient rim and
  low-opacity full-height sheen replace the old bright fixed-height highlight slab.
- Label color and selected fill use the theme's `onSurface` and `onPrimaryContainer` pairs.
  Selection ripples stay within the pill/circle; `selectable` exposes the actual selected state.
- Keep Haze's default adaptive performance policy. No second blur layer, per-item glass effect,
  continuous light animation, or global dependency upgrade is required.

Official pinned references: [Glass guide](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/docs/effects/glass.md)
and [fixed optics contract](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassOptics.kt).

Static checks: `python3 tools/qa/verify_navigation_glass.py` and
`python3 tools/qa/verify_static_contracts.py`. These do **not** compile Kotlin or render Haze.
The palette model checks all four themes in both modes against uniform black/white inputs;
it is not a claim of measured screen contrast or GPU correctness.

Device acceptance: scroll contrasting covers behind the capsule in light and dark mode;
expect soft moving colors without legible cover lettering beneath the navigation text.
Check a plain/empty page (a uniform source cannot produce visible refraction), both orientations,
selection/focus feedback, tab state retention, child-page hiding, and system animations disabled.
Finally check the supported older-platform fallback and frame pacing on a release-like build.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
