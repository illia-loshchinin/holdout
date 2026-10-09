#!/usr/bin/env python3
"""The design book, checked against the Compose theme.

    python scripts/design-book.py check     # tokens.json vs ui/theme/*.kt, plus raw colours in screens
    python scripts/design-book.py build     # docs/design/tokens.css + docs/design/gallery.html
    python scripts/design-book.py all       # both

`docs/design/` is a mirror of the Holdout design system artifact (see docs/design/SOURCE.md).
`check` exits 1 when a token and its Kotlin twin disagree or a token has no twin, so it can gate CI;
pass --report-only to always exit 0. Nothing here edits Kotlin: drift is fixed by hand, on purpose.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BOOK = ROOT / "docs" / "design"
THEME = ROOT / "kotlin" / "app" / "src" / "main" / "kotlin" / "holdout" / "app" / "ui" / "theme"
APP = ROOT / "kotlin" / "app" / "src" / "main" / "kotlin" / "holdout" / "app"

# Tokens with no Kotlin twin by design (CSS-only, or expressed another way in Compose).
NO_TWIN_OK = {"radius-pill", "size-border", "size-ring-stroke", "size-nav"}
# Tokens whose Kotlin twin is added only when a screen first needs it: reported as notes, not drift.
ADD_WHEN_NEEDED = {"inverse-surface", "palette-sky", "palette-charge", "palette-kelp", "palette-beach"}

SPACING = {  # token -> (Kotlin file, property)
    "space-gutter": "screenGutter",
    "space-card": "cardPadding",
    "space-section": "betweenSections",
    "space-items": "betweenItems",
    "space-tight": "tight",
    "size-touch": "touchTarget",
    "size-button": "primaryButtonHeight",
    "space-1": "base",
}
RADIUS = {"radius-chip": "small", "radius-card": "medium", "radius-sheet": "large"}
TYPE = {  # style -> regex anchor in Type.kt
    "counter": r"val CounterStyle\s*=",
    "display": r"val DisplayStyle\s*=",
    "amount": r"val AmountStyle\s*=",
    "label": r"val LabelStyle\s*=",
    "headline": r"headlineMedium\s*=",
    "title": r"titleMedium\s*=",
    "body": r"bodyLarge\s*=",
}


def kebab_to_pascal(name: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in name.split("-"))


def norm_hex(v: str) -> str:
    """#rgb/#rrggbb/#rrggbbaa -> RRGGBBAA, upper case."""
    h = v.lstrip("#").upper()
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        h += "FF"
    return h


def kt_argb_to_rgba(argb: str) -> str:
    argb = argb.upper()
    return argb[2:] + argb[:2]


def load_tokens() -> dict:
    return json.loads((BOOK / "tokens.json").read_text(encoding="utf-8"))


def resolved_colours(tokens: dict) -> dict[str, str]:
    raw = {}
    for t in tokens["color"]["tokens"]:
        v = t["value"]
        raw[t["name"]] = v.get("dark") if isinstance(v, dict) else v
    out = {}
    for name, v in raw.items():
        seen = set()
        while isinstance(v, str) and v.startswith("{") and v.endswith("}") and v not in seen:
            seen.add(v)
            v = raw.get(v[1:-1], v)
        out[name] = v
    return out


def kotlin_colours() -> dict[str, str]:
    src = (THEME / "Color.kt").read_text(encoding="utf-8")
    vals = {m[0]: kt_argb_to_rgba(m[1]) for m in re.findall(r"val (\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", src)}
    for alias, target in re.findall(r"val (\w+)\s*=\s*([A-Z]\w+)\s*$", src, re.M):
        if target in vals:
            vals[alias] = vals[target]
    return vals


def px(v: str) -> float:
    return float(re.sub(r"[a-z%]+$", "", str(v)))


def check(report_only: bool) -> int:
    tokens = load_tokens()
    problems, notes = [], []

    # Colour
    kc = kotlin_colours()
    for name, value in resolved_colours(tokens).items():
        prop = kebab_to_pascal(name)
        if prop not in kc:
            (notes if name in ADD_WHEN_NEEDED else problems).append(f"colour  {name:<18} {value:<10} has no HoldoutColors.{prop}")
        elif norm_hex(value) != kc[prop]:
            problems.append(f"colour  {name:<18} book {norm_hex(value)} != Kotlin {kc[prop]} (HoldoutColors.{prop})")

    # Spacing and size
    shape_src = (THEME / "Shape.kt").read_text(encoding="utf-8")
    kt_dp = {k: float(v) for k, v in re.findall(r"val (\w+)\s*=\s*([\d.]+)\.dp", shape_src)}
    for fam in ("spacing", "size"):
        for t in tokens.get(fam, {}).get("tokens", []):
            name = t["name"]
            if name in NO_TWIN_OK:
                continue
            prop = SPACING.get(name)
            if prop is None or prop not in kt_dp:
                problems.append(f"{fam:<7} {name:<18} {t['value']:<10} has no Spacing.{prop or '?'}")
            elif px(t["value"]) != kt_dp[prop]:
                problems.append(f"{fam:<7} {name:<18} book {t['value']} != Kotlin {kt_dp[prop]}dp (Spacing.{prop})")

    # Radius
    kt_r = {k: float(v) for k, v in re.findall(r"(\w+)\s*=\s*RoundedCornerShape\(([\d.]+)\.dp\)", shape_src)}
    for t in tokens.get("radius", {}).get("tokens", []):
        slot = RADIUS.get(t["name"])
        if slot is None:
            continue
        if slot not in kt_r:
            problems.append(f"radius  {t['name']:<18} has no HoldoutShapes.{slot}")
        elif px(t["value"]) != kt_r[slot]:
            problems.append(f"radius  {t['name']:<18} book {t['value']} != Kotlin {kt_r[slot]}dp (shapes.{slot})")

    # Type: size / line height / weight
    type_src = (THEME / "Type.kt").read_text(encoding="utf-8")
    weights = {"Light": 300, "Normal": 400, "Medium": 500, "SemiBold": 600, "Bold": 700}
    for group in tokens["type"]["groups"]:
        for s in group["styles"]:
            anchor = TYPE.get(s["name"])
            m = re.search(anchor, type_src) if anchor else None
            if not m:
                problems.append(f"type    {s['name']:<18} has no Kotlin style")
                continue
            block = type_src[m.end(): m.end() + 600]
            block = block[: block.find("\n)") if "\n)" in block else len(block)]
            size = re.search(r"fontSize\s*=\s*([\d.]+)\.sp", block)
            line = re.search(r"lineHeight\s*=\s*([\d.]+)\.sp", block)
            wt = re.search(r"FontWeight\.(\w+)", block)
            if s["name"] == "display" and "CounterStyle.copy(" in block[:40]:
                # DisplayStyle = CounterStyle.copy(...) inherits the weight unless the copy overrides it
                block = block.split("\n")[0]
                wt = re.search(r"FontWeight\.(\w+)", block) or re.search(r"FontWeight\.(\w+)", type_src[type_src.find("val CounterStyle"):])
            # A property the Kotlin style lost is drift too, not a reason to skip the style.
            for prop, found in (("fontSize", size), ("lineHeight", line), ("fontWeight", wt)):
                if not found:
                    problems.append(f"type    {s['name']:<18} has no {prop} in Kotlin")
            if size and px(s["fontSize"]) != float(size[1]):
                problems.append(f"type    {s['name']:<18} size book {s['fontSize']} != Kotlin {size[1]}sp")
            if line and px(s["lineHeight"]) != float(line[1]):
                problems.append(f"type    {s['name']:<18} line book {s['lineHeight']} != Kotlin {line[1]}sp")
            if wt and int(s["fontWeight"]) != weights.get(wt[1], -1):
                problems.append(f"type    {s['name']:<18} weight book {s['fontWeight']} != Kotlin {wt[1]}")
    if "FontFamily.Monospace" in type_src:
        notes.append("type    amount uses FontFamily.Monospace; the book names JetBrains Mono (add res/font/ to match)")
    if "Roboto Flex" not in type_src and "roboto_flex" not in type_src:
        notes.append("type    counter/display use FontFamily.Default; the book names Roboto Flex 300")

    # Screens: raw colours and hand-typed hex outside the theme
    for kt in sorted(APP.rglob("*.kt")):
        if THEME in kt.parents or kt.name == "HoldoutLoader.kt":
            continue
        for i, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
            if re.search(r"\bColor\(0x[0-9A-Fa-f]{6,8}\)|\bColor\.(Red|Blue|Green|Yellow|Magenta|Cyan|Gray|White|Black)\b", line):
                notes.append(f"screen  {kt.relative_to(ROOT)}:{i} raw colour — use HoldoutColors")

    print(f"design-book check: {len(problems)} drift, {len(notes)} notes")
    for p in problems:
        print("  DRIFT ", p)
    for n in notes:
        print("  note  ", n)
    return 0 if report_only or not problems else 1


def build() -> None:
    tokens = load_tokens()
    lines = [f"/* {tokens.get('name', 'Holdout')} — generated from tokens.json by scripts/design-book.py; do not edit */",
             ":root {"]
    colours = resolved_colours(tokens)
    for t in tokens["color"]["tokens"]:
        v = t["value"].get("dark") if isinstance(t["value"], dict) else t["value"]
        css = f"var(--{v[1:-1]})" if v.startswith("{") else v
        lines.append(f"  --{t['name']}: {css};")
    for fam in ("spacing", "radius", "size"):
        for t in tokens.get(fam, {}).get("tokens", []):
            lines.append(f"  --{t['name']}: {t['value']};")
    for k, v in tokens["type"]["families"].items():
        lines.append(f"  --font-{k}: {v};")
    for g in tokens["type"]["groups"]:
        fam = g["family"]
        for s in g["styles"]:
            f = s.get("family") or fam
            lines.append(f"  --text-{s['name']}: {s['fontWeight']} {s['fontSize']}/{s['lineHeight']} var(--font-{f});")
    lines.append("}")
    (BOOK / "tokens.css").write_text("\n".join(lines) + "\n", encoding="utf-8")

    cards = []
    for d in sorted((BOOK / "components").iterdir()):
        p = d / "preview.html"
        if not p.is_file() or d.name == "Cover":
            continue
        html = p.read_text(encoding="utf-8")
        body = re.search(r"<body[^>]*>(.*)</body>", html, re.S)
        style = "".join(re.findall(r"<style[^>]*>.*?</style>", html, re.S))
        cards.append(f'<section id="{d.name}"><h2>{d.name}</h2>{style}{body[1] if body else html}</section>')
    gallery = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Holdout components</title>
<link rel="stylesheet" href="tokens.css"><link rel="stylesheet" href="components/bundle.css">
<style>body{{max-width:412px;margin:0 auto;padding-bottom:48px}} h2{{font:500 13px/16px var(--font-sans);letter-spacing:.02em;
text-transform:uppercase;color:var(--text-tertiary);margin:32px 20px 0}}</style></head>
<body><!-- generated by scripts/design-book.py build; 412dp wide, like a phone -->
{''.join(cards)}
</body></html>
"""
    (BOOK / "gallery.html").write_text(gallery, encoding="utf-8")
    print(f"design-book build: tokens.css ({len(colours)} colours) and gallery.html ({len(cards)} components)")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    ro = "--report-only" in sys.argv
    if cmd in ("build", "all"):
        build()
    if cmd in ("check", "all"):
        sys.exit(check(ro))
