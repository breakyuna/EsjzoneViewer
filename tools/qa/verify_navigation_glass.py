#!/usr/bin/env python3
"""Navigation glass source budgets and a local-backing center model, NOT a UI/compile test.

Run with Python only; no Gradle, Android SDK, emulator, or network is required.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/breakyuna/esjzone/ui"


def without_comments(source: str) -> str:
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.S)


def scalar(source: str, name: str) -> float:
    match = re.search(rf"\b{re.escape(name)}\s*=\s*(-?[\d.]+)(?:f|\.dp)\s*,", source)
    if not match:
        raise AssertionError(f"Missing explicit numeric policy: {name}")
    return float(match[1])


def theme_pair(source: str, name: str) -> tuple[float, float]:
    match = re.search(
        rf"\b{re.escape(name)}\s*=\s*if \(dark\) ([\d.]+)f else ([\d.]+)f", source
    )
    if not match:
        raise AssertionError(f"Missing dark/light policy: {name}")
    return float(match[1]), float(match[2])


def rgb(hex_value: str) -> tuple[float, ...]:
    return tuple(int(hex_value[i:i + 2], 16) / 255 for i in (2, 4, 6))


def blend(foreground, background, alpha):
    return tuple(f * alpha + b * (1 - alpha) for f, b in zip(foreground, background))


def luminance(color):
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in color]
    return sum(c * w for c, w in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


class NavigationGlassContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = without_comments((UI / "navigation/AdaptiveShell.kt").read_text())
        cls.surface = without_comments((UI / "designsystem/glass/AppGlassSurface.kt").read_text())
        cls.policy = without_comments((UI / "designsystem/glass/NavigationGlass.kt").read_text())
        cls.backdrop = without_comments((UI / "designsystem/glass/NavigationBackdrop.kt").read_text())
        cls.interactions = without_comments((UI / "designsystem/glass/NavigationGlassInteraction.kt").read_text())
        cls.lens = without_comments((UI / "designsystem/glass/NavigationSelectionLens.kt").read_text())

    def test_changed_kotlin_delimiters_are_balanced(self):
        # Lexical guard only: deliberately no claim of Kotlin type checking.
        for source in (self.shell, self.surface, self.policy, self.lens, self.backdrop, self.interactions):
            source = re.sub(r'""".*?"""', '""', source, flags=re.S)
            source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
            stack = []
            for char in source:
                if char in "([{":
                    stack.append(char)
                elif char in ")]}":
                    self.assertTrue(stack, "Unexpected closing delimiter")
                    self.assertEqual(stack.pop(), {")": "(", "]": "[", "}": "{"}[char])
            self.assertFalse(stack, "Unclosed delimiter")

    def test_source_and_overlay_share_one_dedicated_scene(self):
        self.assertEqual(self.shell.count("val navigationGlassScene = rememberAppGlassScene()"), 1)
        self.assertEqual(self.shell.count(".appGlassSource(navigationGlassScene)"), 2)
        self.assertEqual(self.shell.count("glassScene = navigationGlassScene"), 2)
        self.assertEqual(self.shell.count("AppNavigationGlassSurface("), 2)
        self.assertNotIn(".appGlassSource(", self.policy)
        self.assertNotIn(".appGlassSource(", self.lens)
        self.assertIn("scene = scene", self.policy)
        self.assertNotIn("scene = scene", self.lens)
        self.assertEqual(self.policy.count("NavigationSelectionLens("), 1)
        self.assertEqual(self.lens.count("AppGlassSurface("), 0)
        self.assertEqual(self.policy.count("AppGlassSurface("), 1)
        self.assertIn("scene?.hazeState ?: LocalAppHazeState.current", self.surface)
        self.assertIn("input = HazeInput.Sources(hazeState)", self.surface)

    def test_thin_glass_limits_blurred_depth_mixing(self):
        self.assertIn("material = AppGlassMaterial.REGULAR", self.policy)
        self.assertNotIn("AppGlassMaterial", self.lens)
        # A modest blurred sample quiets fine detail; most of the sharp input remains.
        self.assertTrue(12 <= scalar(self.policy, "blurRadius") <= 18)
        self.assertTrue(0.08 <= scalar(self.policy, "depth") <= 0.12)
        self.assertEqual(scalar(self.policy, "alpha"), 1)
        self.assertIn("depth = spec.depth ?: 0.08f", self.surface)
        self.assertIn("blurRadius = spec.blurRadius ?: 0.dp", self.surface)
        self.assertIn("spec.material != AppGlassMaterial.LENS && spec.blurRadius != null", self.surface)
        self.assertNotIn(".blur(", self.policy + self.shell + self.lens)

    def test_translucent_pane_tint_is_independent_of_opaque_fallback(self):
        dark, light = theme_pair(self.policy, "tintAlpha")
        for value in (dark, light):
            self.assertGreaterEqual(value, 0)
            self.assertLessEqual(value, 0.20)
        self.assertNotEqual(dark, light)
        self.assertGreaterEqual(scalar(self.policy, "fallbackAlpha"), 0.90)
        self.assertIn("spec.fallbackAlpha ?: spec.alpha", self.surface)

    def test_refraction_and_lighting_are_bounded(self):
        budgets = {
            "refractionStrength": (0.28, 0.38),
            "refractionDisplacement": (8, 14),
            "refractionHeightFraction": (0.22, 0.30),
            "refractionFoldStrength": (0, 0),
            "specularIntensity": (0.35, 0.50),
            "ambientResponse": (0.40, 0.55),
        }
        for name, (low, high) in budgets.items():
            self.assertTrue(low <= scalar(self.policy, name) <= high, name)
        self.assertNotIn("refractionDisplacement", self.lens)
        self.assertNotIn(".shadow(", self.lens)
        self.assertLessEqual(scalar(self.policy, "borderWidth"), 1)
        self.assertGreaterEqual(scalar(self.policy, "chromaticAberrationStrength"), 0)
        self.assertLessEqual(scalar(self.policy, "chromaticAberrationStrength"), 0.03)
        self.assertEqual(scalar(self.policy, "contentNormalBlend"), 0)
        self.assertGreaterEqual(scalar(self.policy, "specularExponent"), 28)
        self.assertGreaterEqual(scalar(self.policy, "fresnelExponent"), 2)
        self.assertIn("lightPosition = Alignment.TopStart", self.policy)
        self.assertNotIn("Color.White.copy(alpha = 0.35f)", self.shell)
        self.assertNotIn(".height(34.dp)", self.shell)

    def test_decoration_cannot_expand_wrap_content_navigation(self):
        self.assertIn("Modifier.matchParentSize().navigationCrystalBevel(", self.policy)
        # matchParentSize is a BoxScope member, NOT an importable package extension.
        self.assertNotIn("import androidx.compose.foundation.layout.matchParentSize", self.policy)
        self.assertNotIn("import androidx.compose.foundation.layout.matchParentSize", self.lens)
        self.assertNotIn("fillMaxSize", self.policy)
        self.assertIn("content: @Composable BoxScope.() -> Unit", self.surface)

    def test_reduced_motion_retains_blur_and_does_not_restore_dispersion(self):
        self.assertIn("chromaticAberrationStrength(if (reducedMotion) 0f else it)", self.surface)
        self.assertNotRegex(self.surface, r"blurRadius\s*=\s*if\s*\(reducedMotion\)")
        self.assertNotRegex(self.surface, r"depth\s*=\s*if\s*\(reducedMotion\)")
        self.assertIn("val energy = if (reducedMotion) 0f else", self.lens)
        self.assertIn("val stretch = if (reducedMotion) 0f else", self.lens)
        self.assertIn(".coerceIn(0f, 0.08f)", self.lens)
        self.assertNotIn("derivedStateOf", self.lens)
        self.assertNotIn("movingSpec", self.lens)
        self.assertNotIn("rememberInfiniteTransition", self.policy + self.lens)

    def test_selection_and_shape_are_shared_and_accessible(self):
        self.assertEqual(self.shell.count("val colors = navigationItemColors(selected)"), 2)
        self.assertEqual(self.shell.count(".selectableGroup()"), 2)
        self.assertEqual(
            len(re.findall(r"\.selectable\(\s*selected = selected", self.shell)),
            2,
        )
        self.assertIn("colors.onPrimaryContainer else colors.onSurface", self.shell)
        # The two outer panes consume taps in the gaps between tab hit targets;
        # actual tab actions remain selectable for accessibility semantics.
        self.assertEqual(self.shell.count(".clickable("), 2)
        self.assertEqual(self.shell.count("onClick = {}"), 2)
        self.assertIn(".height(NavigationGlassMetrics.bottomHeight)", self.shell)
        self.assertIn(".width(NavigationGlassMetrics.railWidth)", self.shell)
        self.assertIn(".size(NavigationGlassMetrics.railItemSize)", self.shell)
        self.assertNotIn(".selectable(", self.lens)
        self.assertNotIn(".clickable(", self.lens)
        self.assertIn("selectedStack.lastOrNull() == tab.route", self.shell)

    def test_readability_support_is_local_not_another_opaque_pill(self):
        self.assertEqual(self.shell.count(".navigationContentHalo(colors.halo)"), 2)
        self.assertIn("Brush.radialGradient(", self.shell)
        self.assertIn("1f to color.copy(alpha = 0f)", self.shell)
        self.assertIn("scale(scaleX = horizontalScale, scaleY = 1f)", self.shell)
        self.assertIn("drawCircle(brush = brush, radius = radius)", self.shell)
        self.assertIn("shadow = Shadow(", self.shell)
        self.assertNotIn(".background(colors.background)", self.shell)
        self.assertNotIn("Brush.verticalGradient", self.shell)
        self.assertNotIn(".background(", self.lens)
        self.assertIn("renderEffect = backdrop?.effect(size)", self.policy)
        self.assertIn("if (adaptive) Color.Transparent", self.shell)

    def test_backing_centers_on_uniform_backdrops(self):
        # Simplified center model only, not whole-label WCAG or device validation.
        source = (UI / "designsystem/AppTheme.kt").read_text()
        schemes = re.findall(
            r"private val Monochrome(Light|Dark)Colors = (?:light|dark)ColorScheme\((.*?)\n\)",
            source, re.S,
        )
        self.assertEqual(len(schemes), 2)
        tint_dark, tint_light = theme_pair(self.policy, "tintAlpha")
        halo_dark, halo_light = theme_pair(self.shell.split("halo = if (adaptive)", 1)[1], "alpha")
        # Keep the modeled GPU coefficients connected to the shader policy.
        self.assertIn("conflict * 0.70 + detail * 0.08", self.backdrop)
        self.assertIn("0.04, 0.74", self.backdrop)

        def smoothstep(low, high, value):
            t = max(0.0, min(1.0, (value - low) / (high - low)))
            return t * t * (3 - 2 * t)

        for mode, body in schemes:
            colors = {key: rgb(value) for key, value in re.findall(r"(\w+) = Color\(0x([0-9A-F]{8})\)", body)}
            dark = mode == "Dark"
            for source_value in (0.0, 0.25, 0.5, 0.75, 1.0):
                base = blend(colors["surfaceContainerLow"], (source_value,) * 3, tint_dark if dark else tint_light)
                for lift in (1.0, 1.0 + scalar(self.policy, "ambientResponse")):
                    background = tuple(min(1.0, c * lift) for c in base)
                    for adaptive in (False, True):
                        average = sum(c * w for c, w in zip(background, (0.2126, 0.7152, 0.0722)))
                        conflict = smoothstep(0.10, 0.62, average) if dark else 1 - smoothstep(0.18, 0.72, average)
                        protection = min(0.74, 0.04 + conflict * 0.70)
                        for lens_under_glyph in (False, True):
                            item = background
                            if adaptive:
                                item = blend(colors["surface"], item, protection)
                            if lens_under_glyph:
                                # Include maximum travel sheen and local press feedback.
                                item = blend(colors["onSurface"], item, (0.08 if dark else 0.07) + 0.025)
                                item = blend((1.0,) * 3, item, 0.03)
                            item = blend(colors["onSurface"], item, 0.05)
                            if not adaptive:
                                item = blend(colors["surface"], item, halo_dark if dark else halo_light)
                            for foreground in (colors["onSurface"], colors["onPrimaryContainer"]):
                                with self.subTest(mode=mode, source=source_value, adaptive=adaptive, lens=lens_under_glyph, lift=lift):
                                    self.assertGreaterEqual(contrast(foreground, item), 4.5)

    def test_full_slots_and_interaction_cleanup(self):
        self.assertNotIn("bottomItemMaxWidth", self.shell + self.lens)
        self.assertIn("bottomMaxWidth = 392.dp", self.lens)
        self.assertEqual(self.shell.count("interactionSource = interaction.source"), 2)
        self.assertEqual(self.shell.count(".onGloballyPositioned(interaction::onPlaced)"), 2)
        self.assertEqual(self.shell.count("key(tab)"), 2)
        self.assertIn("interaction.pressPosition + item.origin - dock.origin", self.interactions)
        for name in ("Release", "Cancel"):
            self.assertIn(f"PressInteraction.{name}", self.interactions)
        self.assertIn("finally", self.interactions)
        self.assertIn("tryEmit(PressInteraction.Cancel(it))", self.interactions)
        self.assertIn("tryEmit(FocusInteraction.Unfocus(it))", self.interactions)
        self.assertIn("collectIsPressedAsState", self.shell)
        self.assertIn("collectIsFocusedAsState", self.shell)
        self.assertIn("if (spec.interactive)", self.surface)

    def test_gpu_pass_is_gated_and_preserves_alpha(self):
        self.assertIn("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU", self.backdrop)
        self.assertIn("isHardwareAccelerated", self.backdrop)
        self.assertIn("runCatching", self.backdrop)
        self.assertIn("return half4(rgb * pixel.a, pixel.a)", self.backdrop)
        self.assertIn("if (shield <= 0.001) return pixel", self.backdrop)
        for forbidden in ("PixelCopy", "toImageBitmap", "readPixels", "while (true)", "delay("):
            self.assertNotIn(forbidden, self.backdrop)

    def test_pane_preserves_color_separation_away_from_items(self):
        # In a uniform region refraction/blur cannot change the source; modest tint must
        # preserve >=80% of the source difference away from local halos and edge light.
        for tint in theme_pair(self.policy, "tintAlpha"):
            black = blend((0.5,) * 3, (0.0,) * 3, tint)
            white = blend((0.5,) * 3, (1.0,) * 3, tint)
            self.assertGreaterEqual(white[0] - black[0], 0.80)
        self.assertEqual(scalar(self.policy, "contrast"), 0)
        self.assertEqual(scalar(self.policy, "chromaMultiplier"), 1)
        # Surface reflection is confined to inset rims and the edge glint; no whole-pane fill.
        self.assertNotIn(".background(", self.policy)
        self.assertNotIn("drawRect(", self.policy)
        self.assertIn("style = Stroke(width)", self.policy)


if __name__ == "__main__":
    unittest.main(verbosity=2)
