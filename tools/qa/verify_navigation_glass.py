#!/usr/bin/env python3
"""Source contracts and a conservative palette model, NOT a rendered UI/compile test.

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

    def test_blur_is_real_and_does_not_mix_sharp_content_back(self):
        self.assertGreaterEqual(scalar(self.policy, "blurRadius"), 10)
        self.assertLessEqual(scalar(self.policy, "blurRadius"), 20)
        self.assertEqual(scalar(self.policy, "depth"), 1)
        self.assertEqual(scalar(self.policy, "alpha"), 1)
        self.assertIn("depth = spec.depth ?: 0.08f", self.surface)
        self.assertIn("blurRadius = spec.blurRadius ?: 0.dp", self.surface)
        self.assertNotIn(".blur(", self.policy + self.shell)

    def test_tint_not_source_fill_controls_legibility(self):
        dark, light = theme_pair(self.policy, "tintAlpha")
        for value in (dark, light):
            self.assertGreaterEqual(value, 0.55)
            self.assertLessEqual(value, 0.85)
        self.assertNotEqual(dark, light)
        self.assertGreaterEqual(scalar(self.policy, "fallbackAlpha"), 0.90)
        self.assertIn("spec.fallbackAlpha ?: spec.alpha", self.surface)

    def test_refraction_and_lighting_are_bounded(self):
        for name in ("refractionStrength", "refractionHeightFraction", "refractionFoldStrength"):
            self.assertGreaterEqual(scalar(self.policy, name), 0)
            self.assertLessEqual(scalar(self.policy, name), 1)
        self.assertLessEqual(scalar(self.policy, "borderWidth"), 1)
        self.assertEqual(scalar(self.policy, "chromaticAberrationStrength"), 0)
        self.assertEqual(scalar(self.policy, "contentNormalBlend"), 0)
        self.assertLessEqual(max(theme_pair(self.policy, "specularIntensity")), 0.55)
        self.assertIn("lightPosition = Alignment.TopStart", self.policy)
        self.assertIn("Modifier.fillMaxSize().background(sheen)", self.policy)
        self.assertNotIn("Color.White.copy(alpha = 0.35f)", self.shell)
        self.assertNotIn(".height(34.dp)", self.shell)

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

    def test_palette_model_on_uniform_black_and_white_backdrops(self):
        # Conservative analytic check: no screenshots are generated or claimed here.
        # Black/white bound neutral input after blur. Include maximum ambient lift;
        # actual shaped highlights, GPU optics and moving photos still need device QA.
        source = (UI / "designsystem/AppTheme.kt").read_text()
        schemes = re.findall(r"AppThemeVariant\.(\w+) -> build(Light|Dark)Scheme\((.*?)\n    \)", source, re.S)
        self.assertEqual(len(schemes), 8)
        tint_dark, tint_light = theme_pair(self.policy, "tintAlpha")
        ambient_dark, ambient_light = theme_pair(self.policy, "ambientResponse")
        selection_dark, selection_light = theme_pair(self.shell.split("val backgroundBottom by", 1)[1], "alpha")
        sheen_dark, sheen_light = theme_pair(self.policy.split("val sheen =", 1)[1], "alpha")
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
                    # Top sheen is the more demanding dark case; lower shadow is the
                    # more demanding light case. Both overestimate the label's overlay.
                    if dark:
                        background = blend((1.0,) * 3, background, sheen_dark)
                    else:
                        background = blend((0.0,) * 3, background, 0.015)
                    for selected in (False, True):
                        if selected:
                            # Bottom is the weakest selected fill in each theme.
                            background_item = blend(colors["primaryContainer"], background, selection_dark if dark else selection_light)
                            foreground = colors["onPrimaryContainer"]
                        else:
                            background_item = background
                            foreground = colors["onSurface"]
                        with self.subTest(theme=name, mode=mode, source=source_value, lift=lift, selected=selected):
                            self.assertGreaterEqual(contrast(foreground, background_item), 4.5)


if __name__ == "__main__":
    unittest.main(verbosity=2)
