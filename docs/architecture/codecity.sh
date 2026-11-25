#!/usr/bin/env bash
# Draws Porthole's Java code as Victor Rentea's Code City (every class a building: area = lines, height = cognitive
# complexity, colour = a chosen metric, packages as districts) and stores one picture of it,
# docs/architecture/generated/codecity.jpg, which ARCHITECTURE.md shows. Run it before a PR that changed Java under
# server/ (CLAUDE.md says so); it needs Chrome and Python 3.10+, which CI does not have, so no test or
# workflow runs it. Usage: docs/architecture/codecity.sh   (from anywhere in the checkout)
#
# The generator is https://github.com/victorrentea/code-city (Unlicense), cloned at a pinned commit into .codecity/tool/.
# It reads only the sources and `git log`. Its one network use besides the clone is a crawl of GitHub issues when a token
# is set, so GITHUB_TOKEN and GH_TOKEN are unset for it. Everything but the picture stays in the gitignored .codecity/,
# and the live page, .codecity/out/codecity.html, is the interactive city (it also highlights this branch's changes).
# The script never commits and never pushes.
set -euo pipefail

pin=1f3a5f8
tool_url=https://github.com/victorrentea/code-city.git

fail() {
	echo "codecity: $*" >&2
	exit 1
}

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel) || fail "this script is not inside a git checkout."
work=$root/.codecity
tool=$work/tool
out=$work/out
picture=$root/docs/architecture/generated/codecity.jpg
mkdir -p "$work" "$out"

# The tool's tree-sitter needs Python 3.10 or newer, and the default macOS python3 is 3.9.
python=
for candidate in python3 python3.14 python3.13 python3.12 python3.11 python3.10; do
	path=$(command -v "$candidate" 2>/dev/null) || continue
	if "$path" -c 'import sys; sys.exit(sys.version_info < (3, 10))' 2>/dev/null; then
		python=$path
		break
	fi
done
[ -n "$python" ] || fail "no Python 3.10 or newer was found as python3 or python3.10 to python3.14; install one (brew install python@3.13)."

# The tool runs a bare python3, so give it a directory in which python3 is the one found above.
mkdir -p "$work/bin"
ln -sf "$python" "$work/bin/python3"

# sips turns Chrome's PNG into the JPEG, and Chrome draws the picture (CHROME overrides where it is looked for).
command -v sips >/dev/null 2>&1 || fail "sips was not found; this script runs on macOS."
chrome=${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}
[ -x "$chrome" ] || fail "Chrome was not found at $chrome; install Google Chrome or set CHROME to its executable."

# The pinned tool: cloned once, reused, and moved back to the pin if it was left elsewhere.
if [ -e "$tool" ] && [ ! -d "$tool/.git" ]; then
	fail "${tool#"$root"/} exists but is not a clone of the tool; remove it (rm -rf .codecity/tool) and run again."
fi
if [ ! -d "$tool/.git" ]; then
	echo "codecity: cloning the Code City tool at $pin into .codecity/tool."
	git clone --quiet "$tool_url" "$tool"
fi
if [ "$(git -C "$tool" rev-parse --short=7 HEAD)" != "$pin" ]; then
	git -C "$tool" cat-file -e "$pin^{commit}" 2>/dev/null || git -C "$tool" fetch --quiet origin || true
	git -C "$tool" checkout --quiet "$pin" 2>/dev/null ||
		fail "the pinned commit $pin is not in the tool's repository any more; move the pin."
fi

# Generate. The environment is cleaned of tokens, so the tool never crawls GitHub issues.
env -u GITHUB_TOKEN -u GH_TOKEN PATH="$work/bin:$PATH" CODECITY_TITLE="Porthole Code City" \
	bash "$tool/generate.sh" "$root" "$out"

# The stored picture is a reference, not a review of one branch, so it is drawn from a copy of the page that opens on
# "show everything", skips the first-visit hint and hides the shortcuts box, which explains the
# live page and would only cover the city in a picture. Each edit is a literal swap, counted before and after, because
# the tool is pinned and a miss means the pin moved.
neutral=$out/neutral.html
cp "$out/codecity.html" "$neutral"
swap() { # swap <what it does> <old> <new>
	"$python" -I - "$neutral" "$1" "$2" "$3" "$pin" <<'PY' || exit 1
import sys
path, what, old, new, pin = sys.argv[1:]
text = open(path, encoding="utf-8").read()
if text.count(old) != 1:
    sys.exit("codecity: the edit '%s' found %d matches of its marker in the page, not 1; the tool's page differs from "
             "the pinned commit %s." % (what, text.count(old), pin))
edited = text.replace(old, new)
if edited == text or new not in edited:
    sys.exit("codecity: the edit '%s' did not take effect in the page." % what)
open(path, "w", encoding="utf-8").write(edited)
PY
}
swap "make CHANGES open on show everything" '<option value="off">show everything</option>' '<option value="off" selected>show everything</option>'
swap "unselect CHANGES highlight" '<option value="highlight" selected>' '<option value="highlight">'
# buildIntro() draws the hint and outlines the dropdowns; returning at once leaves the page as it is after "Got it".
swap "skip the first-visit hint" 'if (introDone || buildings.length === 0) return;' 'if (true) return;'
# The panel keeps only what explains the picture (what a building is, area, height, colour, changes): the shortcuts
# box and the rows that steer the live page (preset, zoom, filter, package labels, streets) are hidden. The panel is
# one grid of siblings, so a row is its control plus the label just before it.
swap "hide the shortcuts box and the panel's steering rows" '</head>' \
	'<style>aside#shortcuts, .controls > span.knob:has(+ #presets), #presets, .controls > span.knob:has(+ .filterRow),
.filterRow, .controls > span.knob:has(+ #pkgLabelMode), #pkgLabelMode, #pkgLabelMode + span.spanRest, #streetsKnob,
#interPkgOnlyRow { display: none !important; }</style></head>'

# Chrome draws the page with software WebGL; the time budget lets the scene settle before the screenshot.
png=$out/codecity.png
rm -f "$png"
"$chrome" --headless=new --use-angle=swiftshader --enable-unsafe-swiftshader --window-size=1600,1000 \
	--virtual-time-budget=8000 --hide-scrollbars --screenshot="$png" "file://$neutral" >/dev/null 2>&1 || true
[ -s "$png" ] || fail "Chrome at $chrome made no screenshot of $neutral."
mkdir -p "$(dirname "$picture")"
sips -s format jpeg -s formatOptions 80 "$png" --out "$picture" >/dev/null

echo "codecity: wrote ${picture#"$root"/} ($(wc -c <"$picture" | tr -d ' ') bytes)."
echo "codecity: the live, interactive city is .codecity/out/codecity.html; nothing was committed."
