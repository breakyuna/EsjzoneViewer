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
- `AppFeedback`, `AppLoadingState`, `AppEmptyState`, `AppErrorState`, `AppOfflineState`, `AppDialog`, `AppBottomSheet`, `AppSideSheet`, `AppSnackbarHost`, and `AppSyncStatusDot`: feedback primitives.
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

The shell captures only its page-content sibling into a dedicated `AppGlassScene`.
`NavigationGlass.kt` renders ONE source-backed material for either the bottom dock or
side rail. Decorations, the selection overlay, and selectable items are siblings above
that material. The navigation overlay must never become its own capture source.
Reader glass settings remain independent.

| Property | Dock material |
| --- | --- |
| Fixed refraction strength / displacement | 0.34 / 12.dp |
| Edge height fraction | 0.26 |
| Blur radius / depth mixing | 16.dp / 0.10 |
| Specular intensity / ambient response | 0.42 / 0.48 |
| Tint alpha, dark / light | 14% / 18% |
| Chromatic aberration | 0.02 |

- Fixed optics use `SurfaceProfile.Circle`; fold and content-derived normals are disabled.
  Contrast, white point, and chroma stay neutral. The small depth mix softens fine detail
  while keeping most of the sharp source. Source-fill alpha `1f` is BEHIND captured pixels,
  not whole-pane opacity. The no-host fallback is independently readable at 96% opacity;
  Haze chooses its platform rendering backend.
- Two cached directional strokes (2.dp band and 0.45.dp rim) and one local 6.dp shadow
  describe the dock on plain backgrounds. There is no inner duplicate rim, moving edge
  glint, selection shadow, or full-width bottom gradient. Logical light direction mirrors
  in RTL; it crosses the short axis of the side rail.
- `NavigationSelectionLens.kt` is a thin fill, not another Haze surface. Resting fill alpha
  is 8% dark / 7% light. A head/follower spring gives at most 8% travel stretch, reciprocal
  compression, and continuous fill/sheens; no moving/settled boolean switches optical styles.
  Animated values are read in placement/drawing. Reduced motion removes stretch and travel
  sheen; Compose's duration scale controls the finite springs. No idle animation is added.
- The dock is capped at 392.dp wide and is 60.dp high. Every weighted horizontal slot is
  selectable across its full width and 52.dp content height. The selection is inset 2.dp
  from its slot sides. The rail retains 48.dp items. Decorative `BoxScope.matchParentSize()`
  layers cannot expand the wrap-content rail. Glyphs and hit targets never stretch.
- Each keyed tab owns its interaction source. `NavigationGlassInteraction.kt` forwards
  presses with item-to-dock coordinate translation and forwards release, cancel, and focus
  events. Disposal cancels outstanding interactions. Haze gives the ONE dock localized
  press lighting; items also provide finite press fill and a keyboard-focus outline on
  all platforms. Selecting the active tab still produces feedback.
- On hardware-accelerated API 33+, `NavigationBackdrop.kt` applies a small GPU-only pass
  to the material BEFORE decorations/glyphs. Five local background samples estimate
  brightness and detail, adjusting theme-colored backing near each icon/label group.
  The perimeter and pixel alpha are preserved. Text stays in the monochrome theme color;
  this is background adaptation, not foreground color switching. No window screenshot,
  CPU readback, extra capture source, polling, or frame-driven Compose state is used.
- On older platforms, non-accelerated views, or shader creation failure, items retain a
  feathered local halo. A density-aware label shadow is present on both paths. Selection
  also uses filled icons and bold labels. Backing is independent of the traveling selection.
- Existing custom order, tab stacks, root-only visibility, modal suppression, padding tap
  interception, and system insets remain. No dependency or scroll-collapse behavior is added.
  Removing the second Haze node saves its capture/blur work, but the added small GPU pass
  has a cost; performance improvement is not established without device profiling.

References: [Apple Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/),
[Haze pinned guide](https://github.com/chrisbanes/haze/blob/2.0.0-beta02/docs/effects/glass.md),
and [Android AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl).

Static checks: `python3 tools/qa/verify_navigation_glass.py` and
`python3 tools/qa/verify_static_contracts.py`. These do **not** compile Kotlin, validate
AGSL on a device, or render Haze. The simplified palette model checks backing CENTERS
against uniform grayscale inputs in both themes, including selection/press overlays.
It does not establish whole-label contrast on covers or at feathered edges.

Device acceptance: scroll bright, dark, and busy covers in both themes; inspect plain
white/black pages, press/release/cancel and keyboard focus, rapid tab reversal, customized
order, LTR/RTL, narrow windows, side rail, large text, disabled animations, root/child pages,
modals, and older-platform fallback. Verify frame pacing and the shader on a release-like
build. Those runtime results remain unverified by static checks.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
