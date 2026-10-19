#!/usr/bin/env python3
"""
Measures WCAG contrast ratios for the CycleCare colour tokens so a design
change can be reviewed with numbers rather than a pass/fail from the
existing DesignTokenTest.

Reads the two scheme colour sets straight out of CycleCareTokens.kt so the
report cannot drift from the source of truth.

Usage:
    python3 scripts/contrast-report.py
    python3 scripts/contrast-report.py --tokens <path/to/CycleCareTokens.kt>
"""

import argparse
import re
import sys

# WCAG 2.1 relative luminance
def _channel(c: float) -> float:
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    r = int(hex_color[1:3], 16)
    g = int(hex_color[3:5], 16)
    b = int(hex_color[5:7], 16)
    return 0.2126 * _channel(r) + 0.7152 * _channel(g) + 0.0722 * _channel(b)


def ratio(fg: str, bg: str) -> float:
    l1, l2 = luminance(fg), luminance(bg)
    if l1 < l2:
        l1, l2 = l2, l1
    return (l1 + 0.05) / (l2 + 0.05)


def parse_tokens(path: str) -> dict:
    source = open(path, encoding="utf-8").read()
    return {
        name: value
        for name, value in re.findall(
            r"val\s+(Light|Dark)?(\w+)\s*=\s*(Color\(0x[0-9A-Fa-f]{8}\))", source
        )
        for name, value in [(name + (scheme or ""), value)]
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--tokens",
        default="core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt",
    )
    args = parser.parse_args()

    try:
        source = open(args.tokens, encoding="utf-8").read()
    except OSError:
        print(f"cannot read {args.tokens}", file=sys.stderr)
        return 1

    tokens = dict(
        re.findall(r"val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", source)
    )
    if not tokens:
        print("no Colour tokens found", file=sys.stderr)
        return 1

    # Pairs that represent real on-screen combinations, with the minimum
    # ratio each one has to clear. Thresholds are the project's own
    # CycleCareA11y constants, not invented here: CONTRAST_BODY is 4.5 and
    # CONTRAST_LARGE is 3.0. A threshold of None means the pair is reported
    # for information only, because no WCAG requirement applies to it.
    pairs = [
        ("onBackground", "background", 4.5, "body text on page"),
        ("onSurface", "surface", 4.5, "body text on card"),
        ("onSurfaceVariant", "surface", 4.5, "secondary text on card"),
        ("onPrimary", "primary", 4.5, "text on primary button"),
        ("onPrimaryContainer", "primaryContainer", 4.5, "text on primary chip"),
        ("onSecondary", "secondary", 4.5, "text on secondary button"),
        ("onSecondaryContainer", "secondaryContainer", 4.5, "text on secondary chip"),
        ("onTertiary", "tertiary", 4.5, "text on tertiary"),
        ("onTertiaryContainer", "tertiaryContainer", 4.5, "text on tertiary chip"),
        ("onError", "error", 4.5, "text on error"),
        ("onErrorContainer", "errorContainer", 4.5, "text on error chip"),
        ("primary", "background", 3.0, "primary accent on page"),
        ("outline", "background", 3.0, "outline on page"),
        # Decorative hairline separator. WCAG 1.4.11 applies to components
        # needed to identify a control, not to a divider that carries no
        # meaning on its own, and DesignTokenTest deliberately asserts on
        # outline rather than outlineVariant. Reported, not judged.
        ("outlineVariant", "surface", None, "hairline divider (decorative)"),
        ("periodActiveAccent", "surface", 3.0, "period accent on card"),
        ("periodActiveOnContainer", "periodActiveContainer", 4.5, "period chip text"),
    ]

    worst = []
    print(f"{'scheme':7} {'foreground':28} {'background':24} {'ratio':>7} {'need':>6}  {'verdict':8} use")
    print("-" * 104)
    failures = 0
    for scheme in ("Light", "Dark"):
        for fg, bg, minimum, label in pairs:
            fg_key = scheme + fg[0].upper() + fg[1:]
            bg_key = scheme + bg[0].upper() + bg[1:]
            if fg_key not in tokens or bg_key not in tokens:
                continue
            fg_hex = "#" + tokens[fg_key][2:]
            bg_hex = "#" + tokens[bg_key][2:]
            r = ratio(fg_hex, bg_hex)
            if minimum is None:
                verdict, need = "INFO", "-"
            else:
                ok = r >= minimum
                if not ok:
                    failures += 1
                    worst.append((scheme, label, round(r, 2), minimum))
                verdict, need = ("PASS" if ok else "FAIL"), f"{minimum:.1f}"
            print(
                f"{scheme:7} {fg:28} {bg:24} {r:7.2f} {need:>6}  "
                f"{verdict:8} {label}"
            )

    print()
    if worst:
        print(f"{failures} pair(s) below target:")
        for scheme, label, r, minimum in worst:
            print(f"  {scheme}: {label} = {r} (needs {minimum})")
    else:
        print("All pairs with a WCAG target meet it.")
        print("INFO rows have no applicable requirement; shown for reference.")
    return 1 if worst else 0


if __name__ == "__main__":
    raise SystemExit(main())