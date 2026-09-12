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

- Use explicit fixed clear optics with **zero blur and zero depth mixing**. Haze's built-in
  CLEAR preset still contains blur, so both overrides are required. Keep neutral contrast,
  white point and chroma, and no content-derived normals to preserve the source colors.
- Source-fill alpha is `1f` behind captured content. Whole-pane tint is only 3.5% in light
  mode and 5% in dark mode. The no-host fallback remains readable at 96% opacity.
- Refraction is concentrated in the outer 18% of the short axis: displacement is `18.dp`
  for the bottom capsule and `14.dp` for the side rail, strength `0.90f`, fold `0.38f`.
  Dispersion is modest (`0.035f`, Simple); reduced motion disables optical displacement.
- Pure-color input has no detail to refract. A separate Canvas bevel supplies reflected
  surroundings: a `5.5.dp` curved intensity band, a sharp outer rim, an inverted inner rim,
  and an edge-local strip reflection. Dark reflections reveal thickness over white;
  bright reflections reveal it over black. These are authored lighting cues, not a sampled
  environment map or a claim of physically accurate caustics.
- The main bevel light runs across the short axis, keeping the long edges coherent in
  either orientation. The outer rim adds diagonal lighting; start/end mirror in RTL.
  The interior remains clear, with no whole-pane sheen, noise, or white wash.
- The strip reflection follows the selected tab with a finite spring, honoring Compose's
  system animation duration scale. Its state is read in the draw phase: cached brushes and
  the Haze style are not rebuilt on every animation frame. There is no idle animation.
- Decoration uses **BoxScope's** `matchParentSize()` without a package-level import and
  cannot size the wrap-content rail. The outer surface clips the decoration to the same
  50%-rounded capsule as the optics. Shadows remain outside that clip.
- Selection is ONE persistent transparent lens in `NavigationSelectionLens.kt`, drawn after
  the outer bevel and before the tab Row/Column. It shares the page scene with the outer
  material and never samples the navigation itself. Zero blur/depth, 4.5–6% theme tint,
  a `2.75.dp` convex bevel, local shadow and `10.dp` edge displacement provide a raised
  water-drop appearance. The per-item background/border fades have been removed.
- The lens and outer strip light share one position spring. A slower follower produces
  at most 12% stretch along travel, with reciprocal compression across travel; it settles
  to the original shape and retargets from its current state on rapid taps. State is read
  in placement/layer scopes, not composition. System duration scale 0 snaps both springs.
- Lens measurement shares `NavigationGlassMetrics` with the actual items. The horizontal
  lens fits a weighted slot up to `72.dp` wide and `56.dp` high; the rail lens is `48.dp`.
  Placement clamps end positions and mirrors horizontally with `placeRelativeWithLayer`.
  The match-parent overlay cannot enlarge the wrap-content rail.
- Readability support remains local to icon/label groups, with theme-aware halos and text
  shadows. Glyphs and fixed selectable hit targets are sibling content ABOVE the lens;
  they do not stretch, refract or move. Selected semantics update immediately even while
  the lens travels. Existing insets and page-stack behavior are retained.
- Haze remains the single captured-background renderer. The bevel uses ordinary cached
  Compose drawing and remains available when older renderers omit refraction. There is no
  extra capture source, new dependency, or second blur layer. There is one additional small
  Haze effect for the moving lens; assess its GPU cost on-device, including during scrolling.

Official pinned references: [Glass guide](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/docs/effects/glass.md)
and [fixed optics contract](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassOptics.kt).

Static checks: `python3 tools/qa/verify_navigation_glass.py` and
`python3 tools/qa/verify_static_contracts.py`. These do **not** compile Kotlin or render Haze.
The palette model checks **halo centers only** across four themes in both modes against
uniform black/white inputs, both with and without the moving lens under each glyph group.
It does not prove whole-label contrast: feathered edges deliberately
stay transparent, and high-detail photography still requires visual acceptance on-device.
Source checks cover tint/blur budgets, unchanged capture ownership, reduced motion, selection
semantics and the decoration sizing regression; none substitute for compilation or GPU testing.

Device acceptance: scroll contrasting covers behind the capsule in light and dark mode;
expect sharp moving covers through the center and bending near the rounded edge. Confirm
that the inset rim and edge reflections read as clear glass on white, black and theme surfaces. Check icon/label readability
against bright, dark and high-frequency covers, including non-default themes and large text.
Check a plain/empty page (the bevel must supply thickness without source texture), both orientations,
selection/focus feedback, tab state retention, child-page hiding, and system animations disabled.
Tap across all four tabs rapidly and reverse direction mid-animation; confirm one continuous
lens, correct final alignment, fixed glyphs/hit targets, and both end positions in LTR/RTL.
Finally check the supported older-platform fallback and frame pacing on a release-like build.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
