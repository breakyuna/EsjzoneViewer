# Presentation Design System Contract

This package is the only presentation entry point for the rebuilt visual system.

## Stable feature-facing APIs

- `AppTheme`: color, typography, and shape ownership.
- `AppSpacing`, `AppShapes`, `AppTypography`, `AppElevation`, and `AppTouchTarget`: geometry and text tokens.
- `AppMotion`: duration, easing, and animation specs.
- `rememberAppAdaptiveMetrics`: compact, medium, and expanded layout metrics.
- `AppImage`: image loading seam; feature code must not import a concrete image loader.
- `AppFeedback`, `AppLoading`, `AppDialog`, `AppBottomSheet`, and `AppSnackbarHost`: feedback primitives.
- `glass.AppGlassSurface`: the only feature-facing glass surface.

## Experimental isolation

The `glass` package owns the future Haze adapter. Haze is intentionally not added to feature source
sets or the public feature API. Until the dependency is introduced and verified in CI, the surface
uses a translucent Material fallback with the same contract.

## Ownership rules

Feature code must not import legacy theme/component packages, Coil types, or any experimental glass
API. Business repositories, network parsing, Room entities/migrations, download contracts, and
reader algorithms remain outside this package.
