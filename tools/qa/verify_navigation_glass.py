#!/usr/bin/env python3
"""Crystal-glass source contracts and a halo-center model, NOT a UI/compile test.

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

    def test_changed_kotlin_delimiters_are_balanced(self):
        # Lexical guard only: deliberately no claim of Kotlin type checking.
        for source in (self.shell, self.surface, self.policy):
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
        self.assertIn("scene = scene", self.policy)
        self.assertIn("scene?.hazeState ?: LocalAppHazeState.current", self.surface)
        self.assertIn("input = HazeInput.Sources(hazeState)", self.surface)

    def test_clear_optics_preserve_background_detail(self):
        self.assertIn("material = AppGlassMaterial.CLEAR", self.policy)
        self.assertGreaterEqual(scalar(self.policy, "blurRadius"), 2)
        self.assertLessEqual(scalar(self.policy, "blurRadius"), 6)
        self.assertGreater(scalar(self.policy, "depth"), 0)
        self.assertLessEqual(scalar(self.policy, "depth"), 0.35)
        self.assertEqual(scalar(self.policy, "alpha"), 1)
        self.assertIn("depth = spec.depth ?: 0.08f", self.surface)
        self.assertIn("blurRadius = spec.blurRadius ?: 0.dp", self.surface)
        self.assertIn("spec.material != AppGlassMaterial.LENS && spec.blurRadius != null", self.surface)
        self.assertNotIn(".blur(", self.policy + self.shell)

    def test_clear_pane_tint_is_independent_of_opaque_fallback(self):
        dark, light = theme_pair(self.policy, "tintAlpha")
        for value in (dark, light):
            self.assertGreaterEqual(value, 0.06)
            self.assertLessEqual(value, 0.20)
        self.assertNotEqual(dark, light)
        self.assertGreaterEqual(scalar(self.policy, "fallbackAlpha"), 0.90)
        self.assertIn("spec.fallbackAlpha ?: spec.alpha", self.surface)

    def test_refraction_and_lighting_are_bounded(self):
        for name in ("refractionStrength", "refractionHeightFraction", "refractionFoldStrength"):
            self.assertGreaterEqual(scalar(self.policy, name), 0)
            self.assertLessEqual(scalar(self.policy, name), 1)
        self.assertLessEqual(scalar(self.policy, "borderWidth"), 1)
        self.assertGreaterEqual(scalar(self.policy, "chromaticAberrationStrength"), 0)
        self.assertLessEqual(scalar(self.policy, "chromaticAberrationStrength"), 0.04)
        self.assertEqual(scalar(self.policy, "contentNormalBlend"), 0)
        self.assertGreaterEqual(min(theme_pair(self.policy, "specularIntensity")), 0.55)
        self.assertLessEqual(max(theme_pair(self.policy, "specularIntensity")), 0.75)
        self.assertGreaterEqual(scalar(self.policy, "specularExponent"), 28)
        self.assertGreaterEqual(scalar(self.policy, "fresnelExponent"), 3)
        self.assertIn("lightPosition = Alignment.TopStart", self.policy)
        self.assertNotIn("Color.White.copy(alpha = 0.35f)", self.shell)
        self.assertNotIn(".height(34.dp)", self.shell)

    def test_decoration_cannot_expand_wrap_content_navigation(self):
        self.assertIn("Modifier.matchParentSize().background(sheen)", self.policy)
        # matchParentSize is a BoxScope member, NOT an importable package extension.
        self.assertNotIn("import androidx.compose.foundation.layout.matchParentSize", self.policy)
        self.assertNotIn("fillMaxSize", self.policy)
        self.assertIn("content: @Composable BoxScope.() -> Unit", self.surface)

    def test_reduced_motion_retains_blur_and_does_not_restore_dispersion(self):
        self.assertIn("chromaticAberrationStrength(if (reducedMotion) 0f else it)", self.surface)
        self.assertNotRegex(self.surface, r"blurRadius\s*=\s*if\s*\(reducedMotion\)")
        self.assertNotRegex(self.surface, r"depth\s*=\s*if\s*\(reducedMotion\)")

    def test_selection_and_shape_are_shared_and_accessible(self):
        self.assertEqual(self.shell.count("val colors = navigationItemColors(selected)"), 2)
        self.assertEqual(self.shell.count(".selectableGroup()"), 2)
        self.assertEqual(self.shell.count(".selectable(selected = selected"), 2)
        self.assertIn("colors.onPrimaryContainer else colors.onSurface", self.shell)
        self.assertNotIn(".clickable(", self.shell)
        self.assertIn(".height(68.dp)", self.shell)
        self.assertIn(".width(58.dp)", self.shell)
        self.assertIn(".size(48.dp)", self.shell)
        self.assertIn("selectedStack.lastOrNull() == tab.route", self.shell)

    def test_readability_support_is_local_not_another_opaque_pill(self):
        self.assertEqual(self.shell.count(".navigationContentHalo(colors.halo)"), 2)
        self.assertIn("Brush.radialGradient(", self.shell)
        self.assertIn("1f to color.copy(alpha = 0f)", self.shell)
        self.assertIn("scale(scaleX = horizontalScale, scaleY = 1f)", self.shell)
        self.assertIn("drawCircle(brush = brush, radius = radius)", self.shell)
        self.assertIn("shadow = Shadow(", self.shell)
        for field in ("backgroundTop", "backgroundBottom"):
            for alpha in theme_pair(self.shell.split(f"val {field} by", 1)[1], "alpha"):
                self.assertLessEqual(alpha, 0.30)

    def test_halo_center_model_on_uniform_black_and_white_backdrops(self):
        # Only the halo CENTER is modelled: feathered edges deliberately stay clear.
        # This is not whole-label WCAG validation. Photos, glyph edges, dynamic palette
        # transitions and GPU lighting must be checked on-device, including fallback.
        source = (UI / "designsystem/AppTheme.kt").read_text()
        schemes = re.findall(r"AppThemeVariant\.(\w+) -> build(Light|Dark)Scheme\((.*?)\n    \)", source, re.S)
        self.assertEqual(len(schemes), 8)
        tint_dark, tint_light = theme_pair(self.policy, "tintAlpha")
        ambient_dark, ambient_light = theme_pair(self.policy, "ambientResponse")
        selection_dark, selection_light = theme_pair(self.shell.split("val backgroundBottom by", 1)[1], "alpha")
        halo_dark, halo_light = theme_pair(self.shell.split("halo = colors.surface.copy", 1)[1], "alpha")
        contrast_adjustment = scalar(self.policy, "contrast")
        self.assertEqual(scalar(self.policy, "whitePoint"), 0)
        for name, mode, body in schemes:
            colors = {key: rgb(value) for key, value in re.findall(r"(\w+) = Color\(0x([0-9A-F]{8})\)", body)}
            dark = mode == "Dark"
            tint = tint_dark if dark else tint_light
            ambient = ambient_dark if dark else ambient_light
            for source_value in (0.0, 1.0):
                graded = (source_value - 0.5) * (1 + contrast_adjustment) + 0.5
                base = blend(colors["surfaceContainerLow"], (graded,) * 3, tint)
                # Include both no edge lift and its maximum, not just the center.
                for lift in (1.0, 1.0 + ambient):
                    background = tuple(min(1.0, c * lift) for c in base)
                    for selected in (False, True):
                        if selected:
                            # Bottom is the weakest selected fill in each theme.
                            background_item = blend(colors["primaryContainer"], background, selection_dark if dark else selection_light)
                            foreground = colors["onPrimaryContainer"]
                        else:
                            background_item = background
                            foreground = colors["onSurface"]
                        background_item = blend(colors["surface"], background_item, halo_dark if dark else halo_light)
                        with self.subTest(theme=name, mode=mode, source=source_value, lift=lift, selected=selected):
                            self.assertGreaterEqual(contrast(foreground, background_item), 4.5)

    def test_pane_preserves_color_separation_away_from_items(self):
        # In a uniform region refraction/blur cannot change the source; low tint must
        # preserve >=80% of the source difference. No glow/halo at the clear center.
        for tint in theme_pair(self.policy, "tintAlpha"):
            black = blend((0.5,) * 3, (0.0,) * 3, tint)
            white = blend((0.5,) * 3, (1.0,) * 3, tint)
            self.assertGreaterEqual(white[0] - black[0], 0.80)
        self.assertEqual(scalar(self.policy, "contrast"), 0)
        self.assertEqual(scalar(self.policy, "chromaMultiplier"), 1)
        self.assertIn("0.36f to Color.Transparent", self.policy)
        self.assertIn("0.80f to Color.Transparent", self.policy)


if __name__ == "__main__":
    unittest.main(verbosity=2)
