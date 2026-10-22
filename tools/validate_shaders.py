#!/usr/bin/env python3
"""Validate DraStic-format shaders (.dfx + .dsd) with glslang ES 1.00.

Assembles shader sources the same way the SeedlessDS core does:
  vertex_src   = vheader + header + include + <vertex> block
  fragment_src = fheader + header + include + <fragment> block
then compiles each with glslang --glsl-version 100.
"""
import re
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SHADERS = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "shaders"
GLSLANG = os.environ.get("GLSLANG", "glslangValidator")


def block(text, tag):
    # The core's read_text_block realloc-appends every occurrence of a tag,
    # so multiple <header>/<vheader>/<fheader> blocks are concatenated.
    parts = [m.group(1).strip() + "\n" for m in re.finditer(r"<%s>(.*?)</%s>" % (tag, tag), text, re.S)]
    return "".join(parts)


def validate_dfx(dfx_path):
    text = dfx_path.read_text(encoding="utf-8", errors="replace")
    fheader = block(text, "fheader")
    vheader = block(text, "vheader")
    header = block(text, "header")
    include = ""
    for m in re.finditer(r"<include>\s*file=([^\s<]+)", text):
        inc = (dfx_path.parent / m.group(1))
        include += inc.read_text(encoding="utf-8", errors="replace")

    passes = re.findall(r"<pass>(.*?)</pass>", text, re.S)
    if not passes:
        return [(dfx_path.name, "SKIP", "no passes")]

    results = []
    for p in passes:
        shader = re.search(r"shader=([^\r\n]+)", p).group(1).strip()
        dsd = dfx_path.parent / shader
        if not dsd.exists():
            results.append((shader, "FAIL", "dsd file missing"))
            continue
        dtext = dsd.read_text(encoding="utf-8", errors="replace")
        vsrc = vheader + header + include + block(dtext, "vertex")
        fsrc = fheader + header + include + block(dtext, "fragment")

        # check that every bound sampler is declared in the fragment
        for sm in re.finditer(r"sampler:(\w+)=\d+", p):
            if sm.group(1) not in fsrc:
                results.append((shader, "FAIL", "sampler '%s' not declared in fragment" % sm.group(1)))

        for stage, src in (("vert", vsrc), ("frag", fsrc)):
            with tempfile.NamedTemporaryFile("w", suffix="." + stage, delete=False, encoding="utf-8") as f:
                f.write(src)
                tmp = f.name
            r = subprocess.run([str(GLSLANG), "--glsl-version", "100", tmp],
                               capture_output=True, text=True)
            out = (r.stdout + r.stderr).strip()
            if r.returncode != 0:
                results.append((shader, "FAIL", "[%s] %s" % (stage, out.replace("\n", " | ")[:300])))
            else:
                results.append((shader, "OK", stage))
    return results


def main():
    only = sys.argv[1:] if len(sys.argv) > 1 else None
    dfxs = sorted(SHADERS.glob("*.dfx"))
    if only:
        dfxs = [d for d in dfxs if d.stem in only]
    fails = 0
    for dfx in dfxs:
        for name, status, msg in validate_dfx(dfx):
            mark = "PASS" if status == "OK" else status
            print("%-4s %-28s %s" % (mark, name, "" if status == "OK" else msg))
            if status == "FAIL":
                fails += 1
    print("\n%s (failures: %d)" % ("ALL OK" if fails == 0 else "FAILED", fails))
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
