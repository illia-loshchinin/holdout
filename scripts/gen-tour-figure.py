#!/usr/bin/env python3
"""Generate the tour figure's data file from the canvas board "TourCounting".

The figure on tour card 3 is generated from the Holdout Rig, frame by frame; it is
never redrawn by hand (docs/design/components/TourFigure/README.md). The canvas
board holds the Rig's 36 poses as SVG groups (`.hf0` ... `.hf35`) switched by CSS
keyframes. This script reads the board's HTML and writes one text file that the app
parses at runtime (kotlin/app/.../tour/FigureData.kt) and draws in a Compose Canvas:

    python scripts/gen-tour-figure.py <TourCounting.dc.html> [<out>]

Default <out>: kotlin/app/src/main/assets/tour_figure.txt

Format (one record per line, `#` lines are comments):

    V 1
    D <id> <svg path data>        a path-data dictionary entry (outline + fill share one)
    K <startMs> <endMs> <frame>   the frame schedule over the 12 s loop
    N <name>                      starts a section: scene | outline | knee | frame0 ... frame35
    G <tx> <ty> <rot>             push a transform (translate, then rotate degrees)
    Z                             pop it
    P <did> [style]               path           R <x> <y> <w> <h> <rx> [style]   rect
    C <cx> <cy> <r> [style]       circle         E <cx> <cy> <rx> <ry> <rot> [style]   ellipse
    T <x> <y> <rot> <size> <weight> <spacing> <anchor mb|mc|sb|sc> <family mono|sans> [style] | <text>

style = space-separated key=value: f fill, fo fill opacity, s stroke, so stroke opacity,
sw stroke width, sd dash "a/b", lc linecap (round), lj linejoin (round), o opacity.
Colours are #AARRGGBB, or @accent / @success when the canvas used those tokens, so the
app draws the UI around the figure in its own theme colours; the body keeps the
figure's colours (not tokens).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

LOOP_MS = 12000
TOKENS = {"#80ECFF": "@accent", "#14F195": "@success"}
INHERITED = ("fill", "fill-opacity", "stroke", "stroke-opacity", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "opacity")


def color(value):
    value = value.strip()
    if value == "none":
        return None
    if value.upper() in TOKENS:
        return TOKENS[value.upper()]
    m = re.fullmatch(r"rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)", value)
    if m:
        r, g, b = (int(m.group(i)) for i in (1, 2, 3))
        a = round(float(m.group(4)) * 255)
        return "#%02X%02X%02X%02X" % (a, r, g, b)
    m = re.fullmatch(r"#([0-9a-fA-F]{6})", value)
    if m:
        return "#FF" + m.group(1).upper()
    raise ValueError("colour " + value)


def num(value):
    text = ("%.3f" % float(value)).rstrip("0").rstrip(".")
    return text if text not in ("-0", "") else "0"


class Writer:
    def __init__(self):
        self.dict = {}
        self.dict_order = []
        self.sections = []  # (name, [lines])
        self.cur = None

    def section(self, name):
        self.cur = []
        self.sections.append((name, self.cur))

    def did(self, d):
        d = re.sub(r"\s+", " ", d.strip())
        if d not in self.dict:
            self.dict[d] = len(self.dict)
            self.dict_order.append(d)
        return self.dict[d]

    def add(self, line):
        self.cur.append(line)


def style_of(attrs, inherited):
    st = dict(inherited)
    for k in INHERITED:
        if k in attrs:
            st[k] = attrs[k]
    return st


def style_tokens(st, kind):
    out = []
    fill = st.get("fill", "none")
    if kind in ("path", "rect", "circle", "ellipse", "text"):
        c = color(fill)
        if c:
            out.append("f=" + c)
            if "fill-opacity" in st:
                out.append("fo=" + num(st["fill-opacity"]))
    stroke = st.get("stroke")
    if stroke and kind != "text":
        c = color(stroke)
        if c:
            out.append("s=" + c)
            if "stroke-opacity" in st:
                out.append("so=" + num(st["stroke-opacity"]))
            out.append("sw=" + num(st.get("stroke-width", "1")))
            if "stroke-dasharray" in st:
                parts = re.split(r"[ ,]+", st["stroke-dasharray"].strip())
                out.append("sd=" + "/".join(num(p) for p in parts))
            if st.get("stroke-linecap") == "round":
                out.append("lc=round")
            if st.get("stroke-linejoin") == "round":
                out.append("lj=round")
    if "opacity" in st:
        out.append("o=" + num(st["opacity"]))
    return " ".join(out)


def parse_transform(t):
    """translate(x y) rotate(a) or rotate(a cx cy) -> list of ops (kind, args)."""
    ops = []
    for m in re.finditer(r"(translate|rotate)\(([^)]*)\)", t or ""):
        ops.append((m.group(1), [float(x) for x in re.split(r"[ ,]+", m.group(2).strip())]))
    return ops


def walk(el, w, inherited, depth=0):
    tag = el.tag.split("}")[-1]
    attrs = dict(el.attrib)
    st = style_of(attrs, inherited)
    pushed = 0
    own = parse_transform(attrs.get("transform"))
    if tag in ("g", "svg"):
        for kind, args in own:
            if kind == "translate":
                w.add("G %s %s 0" % (num(args[0]), num(args[1])))
                pushed += 1
            elif kind == "rotate" and len(args) == 1:
                w.add("G 0 0 %s" % num(args[0]))
                pushed += 1
            else:
                raise ValueError("group transform " + attrs.get("transform"))
        for child in el:
            walk(child, w, st, depth + 1)
        for _ in range(pushed):
            w.add("Z")
        return
    sty = style_tokens(st, tag)
    if tag == "path":
        w.add(("P %d %s" % (w.did(attrs["d"]), sty)).rstrip())
    elif tag == "rect":
        w.add(("R %s %s %s %s %s %s" % (num(attrs.get("x", 0)), num(attrs.get("y", 0)), num(attrs["width"]), num(attrs["height"]), num(attrs.get("rx", 0)), sty)).rstrip())
    elif tag == "circle":
        w.add(("C %s %s %s %s" % (num(attrs["cx"]), num(attrs["cy"]), num(attrs["r"]), sty)).rstrip())
    elif tag == "ellipse":
        rot = 0
        for kind, args in own:
            if kind == "rotate":
                rot = args[0]
        w.add(("E %s %s %s %s %s %s" % (num(attrs["cx"]), num(attrs["cy"]), num(attrs["rx"]), num(attrs["ry"]), num(rot), sty)).rstrip())
    elif tag == "text":
        rot = 0
        for kind, args in own:
            if kind == "rotate":
                rot = args[0]
        family = "mono" if "mono" in attrs.get("font-family", "") else "sans"
        # Two letters: x anchor (m middle, s start) and baseline (c centred on y, b the alphabetic baseline).
        anchor = ("m" if attrs.get("text-anchor") == "middle" else "s") + ("c" if attrs.get("dominant-baseline") == "middle" else "b")
        w.add(
            "T %s %s %s %s %s %s %s %s %s | %s"
            % (
                num(attrs["x"]),
                num(attrs["y"]),
                num(rot),
                num(attrs.get("font-size", 11)),
                num(attrs.get("font-weight", 400)),
                num(attrs.get("letter-spacing", 0)),
                anchor,
                family,
                sty,
                "".join(el.itertext()),
            )
        )
    else:
        raise ValueError("unsupported element " + tag)


def schedule(css):
    """The CSS keyframes hf0..hf35 (steps(1,end)) as spans of the 12 s loop, made contiguous."""
    spans = []
    for n in range(36):
        stops = re.findall(r"([\d.]+)%\s*\{\s*opacity:\s*([\d.]+);\s*\}", css[css.index("@keyframes hf%d " % n):].split("\n", 1)[0])
        if not stops:
            raise ValueError("no keyframes for hf%d" % n)
        stops = [(float(p), float(o)) for p, o in stops]
        start = None
        for pct, op in stops:
            if op >= 0.5 and start is None:
                start = pct
            elif op < 0.5 and start is not None:
                spans.append((round(start * LOOP_MS / 100), round(pct * LOOP_MS / 100), n))
                start = None
        if start is not None:
            spans.append((round(start * LOOP_MS / 100), LOOP_MS, n))
    spans = [s for s in spans if s[1] > s[0]]
    spans.sort()
    if spans[0][0] != 0:
        raise ValueError("schedule does not start at 0")
    out = []
    for i, (a, b, n) in enumerate(spans):
        end = spans[i + 1][0] if i + 1 < len(spans) else LOOP_MS
        out.append((a, end, n))
    return out


def main():
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parent.parent / "kotlin/app/src/main/assets/tour_figure.txt"
    html = src.read_text(encoding="utf8")
    css = html[: html.index("</style>")]
    i = html.index('<svg class="ho-anim"')
    j = html.index("</svg>", i) + len("</svg>")
    svg = html[i:j]
    svg = re.sub(r' aria-label="[^"]*"', "", svg)
    svg = svg.replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" ', 1)
    root = ET.fromstring(svg)
    w = Writer()
    base = {"fill": "none"}
    w.section("scene")
    for child in root:
        cls = child.attrib.get("class", "")
        m = re.fullmatch(r"hf(\d+)", cls)
        if m:
            continue
        if cls in ("ho-dash", "ho-ok", "ho-panel", "ho-knee", "ho-count") or (child.tag.endswith("g") and "font-size" in child.attrib and "64" in child.attrib["font-size"]):
            continue
        walk(child, w, base)
    # The outline rectangle (drawn dashed accent, then solid success) and the knees tag.
    outline = next(c for c in root if c.attrib.get("class") == "ho-dash")
    w.section("outline")
    w.add("R %s %s %s %s %s" % tuple(num(outline.attrib[k]) for k in ("x", "y", "width", "height", "rx")))
    knee = next(c for c in root if c.attrib.get("class") == "ho-knee")
    w.section("knee")
    for child in knee:
        walk(child, w, base)
    frames = {}
    for child in root:
        m = re.fullmatch(r"hf(\d+)", child.attrib.get("class", ""))
        if m:
            frames[int(m.group(1))] = child
    assert sorted(frames) == list(range(36)), sorted(frames)
    for n in range(36):
        w.section("frame%d" % n)
        walk(frames[n], w, base)
    sched = schedule(css)
    lines = ["# Generated by scripts/gen-tour-figure.py from the canvas board TourCounting. Do not edit by hand.", "V 1"]
    for k, d in enumerate(w.dict_order):
        lines.append("D %d %s" % (k, d))
    for a, b, n in sched:
        lines.append("K %d %d %d" % (a, b, n))
    for name, body in w.sections:
        lines.append("N " + name)
        lines.extend(body)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text("\n".join(lines) + "\n", encoding="utf8", newline="\n")
    print("wrote %s: %d bytes, %d paths, %d spans, %d sections" % (dst, dst.stat().st_size, len(w.dict_order), len(sched), len(w.sections)))


if __name__ == "__main__":
    main()
