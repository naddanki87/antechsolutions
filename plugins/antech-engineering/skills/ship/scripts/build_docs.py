"""Scribe's documentation builder: turns a product's Markdown docs into downloadable HTML + PDF files.

Usage (Python 3.9+, standard library only):
    python build_docs.py --project projects/my-app --name "My App" --version 1.0.0 \
        [--repo-url https://github.com/owner/repo/blob/main/projects/my-app]

Output in <project>/dist/docs/:
    <slug>-<version>-documentation.html/.pdf   full manual (README, Quick Start, spec, API, decisions,
                                               test report, security report + audit checklist)
    <slug>-<version>-quickstart.html/.pdf      step-by-step user guide
    <slug>-<version>-security-report.html/.pdf security report + audit checklist
    <slug>-<version>-docs.zip                  everything above plus the Markdown sources

PDFs are printed with headless Microsoft Edge or Google Chrome when one is installed; otherwise only HTML is
produced (open it in any browser and use Print > Save as PDF).
"""
import argparse
import datetime
import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

BUNDLES = {
    "documentation": ("Product Documentation", [
        ("Overview", "README.md"),
        ("Quick Start Guide", "docs/QUICKSTART.md"),
        ("Specification", "docs/SPEC.md"),
        ("API Reference", "docs/API.md"),
        ("Architecture Decisions", "docs/DECISIONS.md"),
        ("Test Report", "docs/TEST-REPORT.md"),
        ("Security Report", "docs/SECURITY-REPORT.md"),
        ("Security Audit Checklist", "docs/SECURITY-AUDIT.md"),
    ]),
    "quickstart": ("Quick Start Guide", [
        ("Quick Start Guide", "docs/QUICKSTART.md"),
    ]),
    "security-report": ("Security Report", [
        ("Security Report", "docs/SECURITY-REPORT.md"),
        ("Security Audit Checklist", "docs/SECURITY-AUDIT.md"),
    ]),
}

BLOCK_START = re.compile(r"^(```|#{1,6}\s|>|\||\s*([-*]|\d+\.)\s+|\s*(-{3,}|\*{3,})\s*$)")
LIST_ITEM = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")


def slugify(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-") or "section"


class Renderer:
    """Small, safe Markdown subset renderer: headings, paragraphs, emphasis, code, links, lists, tables,
    blockquotes, rules and fenced code (mermaid blocks are rendered as diagrams)."""

    def __init__(self, doc_rel, doc_id, link_map, repo_url):
        self.doc_rel = doc_rel
        self.doc_id = doc_id
        self.link_map = link_map
        self.repo_url = repo_url.rstrip("/") if repo_url else None
        self.toc = []
        self.ids = set()

    # ---------------------------------------------------------------- inline

    def href(self, target):
        if re.match(r"^(https?:|mailto:|#)", target):
            return target
        base = Path(self.doc_rel).parent
        resolved = os.path.normpath(str(base / target.split("#")[0])).replace("\\", "/")
        if resolved in self.link_map:
            return "#" + self.link_map[resolved]
        if self.repo_url and not resolved.startswith(".."):
            return f"{self.repo_url}/{resolved}"
        return None

    def inline(self, text):
        links = []

        def keep_link(m):
            label, target = m.group(1), m.group(2)
            url = self.href(target)
            rendered = self.inline_basic(label)
            links.append(f'<a href="{html.escape(url)}">{rendered}</a>' if url else rendered)
            return f"\x00{len(links) - 1}\x00"

        text = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", keep_link, text)
        out = self.inline_basic(text)
        return re.sub(r"\x00(\d+)\x00", lambda m: links[int(m.group(1))], out)

    @staticmethod
    def inline_basic(text):
        parts = re.split(r"(`[^`]+`)", text)
        out = []
        for p in parts:
            if len(p) > 1 and p.startswith("`") and p.endswith("`"):
                out.append("<code>" + html.escape(p[1:-1]) + "</code>")
            else:
                s = html.escape(p, quote=False)
                s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
                s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
                s = s.replace("&lt;br/&gt;", "<br>")
                out.append(s)
        return "".join(out)

    # ---------------------------------------------------------------- blocks

    def heading_id(self, text):
        base = f"{self.doc_id}-{slugify(text)}"
        hid, n = base, 2
        while hid in self.ids:
            hid, n = f"{base}-{n}", n + 1
        self.ids.add(hid)
        return hid

    def render(self, md):
        lines = md.replace("\r\n", "\n").split("\n")
        out, i = [], 0
        while i < len(lines):
            line = lines[i]
            if not line.strip():
                i += 1
                continue
            if line.startswith("```"):
                lang, buf = line[3:].strip(), []
                i += 1
                while i < len(lines) and not lines[i].startswith("```"):
                    buf.append(lines[i])
                    i += 1
                i += 1
                code = html.escape("\n".join(buf))
                if lang == "mermaid":
                    out.append(f'<figure class="diagram"><pre class="mermaid">{code}</pre></figure>')
                else:
                    out.append(f'<pre><code class="lang-{html.escape(lang or "text")}">{code}</code></pre>')
                continue
            m = re.match(r"^(#{1,6})\s+(.*?)\s*#*$", line)
            if m:
                level, text = len(m.group(1)), m.group(2)
                hid = self.heading_id(text)
                plain = re.sub(r"[`*]", "", re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text))
                if level <= 2:
                    self.toc.append((level, plain, hid))
                tag = f"h{min(level + 1, 6)}"  # the document title is the section's h2
                out.append(f'<{tag} id="{hid}">{self.inline(text)}</{tag}>')
                i += 1
                continue
            if re.match(r"^\s*(-{3,}|\*{3,})\s*$", line):
                out.append("<hr>")
                i += 1
                continue
            if line.lstrip().startswith("|"):
                rows = []
                while i < len(lines) and lines[i].lstrip().startswith("|"):
                    rows.append(lines[i].strip())
                    i += 1
                out.append(self.table(rows))
                continue
            if line.startswith(">"):
                buf = []
                while i < len(lines) and lines[i].startswith(">"):
                    buf.append(re.sub(r"^>\s?", "", lines[i]))
                    i += 1
                out.append(f"<blockquote>{self.render(chr(10).join(buf))}</blockquote>")
                continue
            if LIST_ITEM.match(line):
                block = []
                while i < len(lines) and (LIST_ITEM.match(lines[i]) or
                                          (lines[i].startswith("  ") and lines[i].strip() and block)):
                    block.append(lines[i])
                    i += 1
                out.append(self.list_block(block))
                continue
            buf = []
            while i < len(lines) and lines[i].strip() and not (buf and BLOCK_START.match(lines[i])):
                buf.append(lines[i].strip())
                i += 1
            out.append(f"<p>{self.inline(' '.join(buf))}</p>")
        return "\n".join(out)

    @staticmethod
    def split_row(row):
        row = row.strip()
        if row.startswith("|"):
            row = row[1:]
        if row.endswith("|"):
            row = row[:-1]
        cells, cur, in_code = [], "", False
        for ch in row:
            if ch == "`":
                in_code = not in_code
            if ch == "|" and not in_code:
                cells.append(cur.strip())
                cur = ""
            else:
                cur += ch
        cells.append(cur.strip())
        return cells

    def table(self, rows):
        if len(rows) >= 2 and re.match(r"^\|?\s*:?-{2,}", rows[1]):
            head, body = self.split_row(rows[0]), rows[2:]
        else:
            head, body = None, rows
        out = ['<div class="table-wrap"><table>']
        if head and any(head):
            out.append("<thead><tr>" + "".join(f"<th>{self.inline(c)}</th>" for c in head) + "</tr></thead>")
        out.append("<tbody>")
        for r in body:
            out.append("<tr>" + "".join(f"<td>{self.inline(c)}</td>" for c in self.split_row(r)) + "</tr>")
        out.append("</tbody></table></div>")
        return "".join(out)

    def list_block(self, lines):
        items = []  # (indent, ordered, text)
        for line in lines:
            m = LIST_ITEM.match(line)
            if m:
                items.append([len(m.group(1).expandtabs(4)), m.group(2)[0].isdigit(), m.group(3)])
            elif items:
                items[-1][2] += " " + line.strip()
        out, stack = [], []
        for indent, ordered, text in items:
            while stack and indent < stack[-1][0]:
                out.append("</li></%s>" % ("ol" if stack.pop()[1] else "ul"))
            if not stack or indent > stack[-1][0]:
                out.append("<ol>" if ordered else "<ul>")
                stack.append((indent, ordered))
            else:
                out.append("</li>")
            out.append(f"<li>{self.inline(text)}")
        while stack:
            out.append("</li></%s>" % ("ol" if stack.pop()[1] else "ul"))
        return "".join(out)


CSS = """
:root { --ink:#16202c; --muted:#5b6b7f; --line:#d9e1ea; --accent:#0b7fab; --accent-2:#0f3d5c; --soft:#f3f7fb; }
* { box-sizing: border-box; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { margin: 0; color: var(--ink); background: #fff; font: 15px/1.6 "Segoe UI", system-ui, -apple-system, Roboto, sans-serif; }
main { max-width: 900px; margin: 0 auto; padding: 0 24px 64px; }
.cover { min-height: 92vh; display: flex; flex-direction: column; justify-content: center; padding: 48px 24px; max-width: 900px; margin: 0 auto; }
.cover .brand { letter-spacing: .18em; font-weight: 700; color: var(--accent); font-size: 13px; }
.cover h1 { font-size: 44px; line-height: 1.1; margin: 16px 0 8px; color: var(--accent-2); }
.cover .kind { font-size: 22px; color: var(--muted); margin: 0 0 32px; }
.cover dl { display: grid; grid-template-columns: 160px 1fr; gap: 6px 16px; margin: 0; font-size: 14px; }
.cover dt { color: var(--muted); }
.cover dd { margin: 0; font-weight: 600; }
.cover .bar { height: 6px; width: 120px; background: linear-gradient(90deg, var(--accent), #38d6ff); border-radius: 3px; }
nav.toc { page-break-after: always; break-after: page; padding: 24px 0; }
nav.toc h2 { color: var(--accent-2); }
nav.toc ol { padding-left: 22px; }
nav.toc li { margin: 4px 0; }
nav.toc ol ol { list-style: none; padding-left: 14px; font-size: 14px; }
nav.toc a { color: var(--ink); text-decoration: none; }
section.doc { break-before: page; page-break-before: always; padding-top: 8px; }
section.doc:first-of-type { break-before: auto; page-break-before: auto; }
.doc-label { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--accent); font-weight: 700; margin-top: 24px; }
h2 { font-size: 30px; color: var(--accent-2); margin: 4px 0 16px; padding-bottom: 8px; border-bottom: 3px solid var(--accent); }
h3 { font-size: 21px; color: var(--accent-2); margin: 28px 0 10px; }
h4 { font-size: 17px; margin: 22px 0 8px; }
h2, h3, h4 { break-after: avoid; page-break-after: avoid; }
a { color: var(--accent); }
code { font-family: Consolas, "JetBrains Mono", monospace; font-size: .88em; background: var(--soft); border: 1px solid var(--line); border-radius: 4px; padding: 0 4px; }
pre { background: #0f1b2a; color: #e3edf7; padding: 14px 16px; border-radius: 8px; overflow-x: auto; font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; break-inside: avoid; }
pre code { background: none; border: 0; padding: 0; color: inherit; font-size: inherit; }
figure.diagram { margin: 16px 0; padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--soft); break-inside: avoid; }
figure.diagram pre { background: none; color: var(--ink); }
.table-wrap { overflow-x: auto; margin: 14px 0; }
table { border-collapse: collapse; width: 100%; font-size: 13.5px; }
th, td { border: 1px solid var(--line); padding: 7px 9px; text-align: left; vertical-align: top; }
th { background: var(--soft); color: var(--accent-2); }
tr { break-inside: avoid; page-break-inside: avoid; }
blockquote { margin: 14px 0; padding: 10px 16px; border-left: 4px solid var(--accent); background: var(--soft); color: #2b3a4c; }
hr { border: 0; border-top: 1px solid var(--line); margin: 24px 0; }
footer.end { margin-top: 48px; padding-top: 12px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; }
@page { size: A4; margin: 16mm 14mm; }
@media print { main { padding: 0; } .cover { min-height: 250mm; } a { text-decoration: none; } }
"""

MERMAID = ('<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>'
           '<script>if (window.mermaid) { mermaid.initialize({ startOnLoad: true, theme: "neutral" }); }</script>')


def build_bundle(project, kind, product, version, repo_url, today):
    title, docs = BUNDLES[kind]
    present = [(label, rel) for label, rel in docs if (project / rel).is_file()]
    missing = [rel for _, rel in docs if not (project / rel).is_file()]
    link_map = {rel: "doc-" + slugify(Path(rel).stem) for _, rel in present}
    sections, toc_items = [], []
    for label, rel in present:
        doc_id = link_map[rel]
        r = Renderer(rel, doc_id, link_map, repo_url)
        body = r.render((project / rel).read_text(encoding="utf-8"))
        # Each Markdown file's own H1 duplicates the section label, so the label is the section title.
        sub = "".join(f'<li><a href="#{hid}">{html.escape(text)}</a></li>' for lvl, text, hid in r.toc if lvl == 2)
        toc_items.append(f'<li><a href="#{doc_id}">{html.escape(label)}</a>{"<ol>" + sub + "</ol>" if sub else ""}</li>')
        sections.append(f'<section class="doc" id="{doc_id}"><div class="doc-label">{html.escape(label)}</div>{body}</section>')
    has_mermaid = any('class="mermaid"' in s for s in sections)
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(product)} {html.escape(version)} - {html.escape(title)}</title>
<style>{CSS}</style>{MERMAID if has_mermaid else ""}</head>
<body>
<header class="cover">
  <div class="bar"></div>
  <div class="brand">ANTECH SOLUTIONS</div>
  <h1>{html.escape(product)}</h1>
  <p class="kind">{html.escape(title)} &middot; version {html.escape(version)}</p>
  <dl>
    <dt>Version</dt><dd>{html.escape(version)}</dd>
    <dt>Date</dt><dd>{today}</dd>
    <dt>Prepared by</dt><dd>Scribe, Antech /ship engineering team</dd>
    <dt>Prepared for</dt><dd>CEO Nageshwar</dd>
    <dt>Contents</dt><dd>{html.escape(", ".join(label for label, _ in present))}</dd>
  </dl>
</header>
<main>
<nav class="toc"><h2>Contents</h2><ol>{"".join(toc_items)}</ol></nav>
{"".join(sections)}
<footer class="end">{html.escape(product)} {html.escape(version)} &middot; {html.escape(title)} &middot; generated {today} by the Antech /ship skill (Scribe).</footer>
</main></body></html>"""
    return page, missing


def find_browser():
    candidates = [
        os.environ.get("ANTECH_PDF_BROWSER", ""),
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    ]
    candidates += [shutil.which(n) or "" for n in ("google-chrome", "chromium", "chromium-browser", "msedge", "chrome")]
    return next((c for c in candidates if c and Path(c).is_file()), None)


def print_pdf(browser, html_file, pdf_file):
    # Render in a short temp folder (long Windows paths break Chromium) with a throwaway profile.
    with tempfile.TemporaryDirectory(prefix="antech-docs-") as tmp:
        src = Path(tmp) / "doc.html"
        out = Path(tmp) / "doc.pdf"
        shutil.copyfile(html_file, src)
        cmd = [browser, "--headless=new", "--disable-gpu", "--no-first-run", "--no-pdf-header-footer",
               f"--user-data-dir={Path(tmp) / 'profile'}", "--virtual-time-budget=15000",
               f"--print-to-pdf={out}", src.as_uri()]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
        if not out.is_file() or out.stat().st_size == 0:
            return False
        shutil.copyfile(out, pdf_file)
        return True


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--project", required=True)
    ap.add_argument("--name", required=True, help="Product display name")
    ap.add_argument("--version", required=True)
    ap.add_argument("--slug", help="File name prefix (default: project folder name)")
    ap.add_argument("--repo-url", help="Blob URL of the project folder, used for links to source files")
    ap.add_argument("--no-pdf", action="store_true")
    args = ap.parse_args()

    project = Path(args.project).resolve()
    slug = args.slug or project.name
    out_dir = project / "dist" / "docs"
    out_dir.mkdir(parents=True, exist_ok=True)
    today = datetime.date.today().isoformat()
    browser = None if args.no_pdf else find_browser()
    produced = []
    for kind in BUNDLES:
        page, missing = build_bundle(project, kind, args.name, args.version, args.repo_url, today)
        for rel in missing:
            print(f"WARN {kind}: {rel} not found, skipped")
        html_file = out_dir / f"{slug}-{args.version}-{kind}.html"
        html_file.write_text(page, encoding="utf-8")
        produced.append(html_file)
        if browser:
            pdf_file = html_file.with_suffix(".pdf")
            if print_pdf(browser, html_file, pdf_file):
                produced.append(pdf_file)
            else:
                print(f"WARN {kind}: PDF printing failed; HTML is still available")
    if not browser and not args.no_pdf:
        print("WARN no Edge/Chrome found: PDFs skipped (open the HTML and use Print > Save as PDF)")

    zip_file = out_dir / f"{slug}-{args.version}-docs.zip"
    with zipfile.ZipFile(zip_file, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in produced:
            zf.write(f, f.name)
        for rel in ["README.md"] + sorted(str(p.relative_to(project)).replace("\\", "/") for p in (project / "docs").glob("*")):
            if (project / rel).is_file():
                zf.write(project / rel, f"markdown/{rel}")
    produced.append(zip_file)
    for f in produced:
        print(f"OK   {f.relative_to(project)} ({max(1, f.stat().st_size // 1024)} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
