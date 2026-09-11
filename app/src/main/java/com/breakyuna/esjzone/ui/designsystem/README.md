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
- `glass.AppNavigationGlassSurface`: navigation-only crystal material for bottom and side capsules.

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

- Start from Haze's `GlassStyle.clear`, with fixed `4.dp` blur and `depth = 0.24f`.
  Retaining mostly sharp input is intentional: the requested material is clear crystal, not frost.
  Reader glass defaults are unchanged; the adapter already applies explicit optics to CLEAR.
- Source-fill alpha is `1f`; it fills behind captured content, not over it. Theme tint controls
  pane coloration: light `0.10f`, dark `0.16f`. Do not restore the old 72–78% whole-pane tint.
  The no-host fallback alpha remains `0.96f` and need not imitate unsupported refraction.
- A `10.dp`/`12.dp` displacement budget, `0.22f` bezel fraction and `0.24f` edge fold provide
  optical thickness. Top-start highlights use a narrower exponent (`32f`) and a `0.75.dp` rim.
  Dispersion is only `0.025f` in Simple mode; reduced motion disables it and refraction.
  Neutral contrast/chroma preserve cover colors; the center sheen is fully transparent.
- The sheen uses **BoxScope's** `matchParentSize()` without a package-level import. Unlike
  `fillMaxSize`, it cannot expand the measured bottom capsule or wrap-content side rail.
- Selected fill is a faint primary-container gradient (10–28%), not an opaque inner button.
  Glyph colors retain `onSurface`/`onPrimaryContainer`; feathered, theme-aware radial backing
  sits only behind the icon/label group, plus a density-scaled text shadow. This preserves the
  clear pane between items. Selection borders and filled icons still identify the active tab.
  Ripples stay within the pill/circle; `selectable` exposes the actual selected state.
- Keep Haze's default adaptive performance policy. No second blur layer, per-item glass effect,
  continuous light animation, or global dependency upgrade is required.

Official pinned references: [Glass guide](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/docs/effects/glass.md)
and [fixed optics contract](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassOptics.kt).

Static checks: `python3 tools/qa/verify_navigation_glass.py` and
`python3 tools/qa/verify_static_contracts.py`. These do **not** compile Kotlin or render Haze.
The palette model checks **halo centers only** across four themes in both modes against
uniform black/white inputs. It does not prove whole-label contrast: feathered edges deliberately
stay transparent, and high-detail photography still requires visual acceptance on-device.
Source checks cover tint/blur budgets, unchanged capture ownership, reduced motion, selection
semantics and the decoration sizing regression; none substitute for compilation or GPU testing.

Device acceptance: scroll contrasting covers behind the capsule in light and dark mode;
expect recognizable moving covers through the center and bending near the rounded edge, without
the previous milky/black veil or oversized white highlight slab. Check icon/label readability
against bright, dark and high-frequency covers, including non-default themes and large text.
Check a plain/empty page (a uniform source cannot produce visible refraction), both orientations,
selection/focus feedback, tab state retention, child-page hiding, and system animations disabled.
Finally check the supported older-platform fallback and frame pacing on a release-like build.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
