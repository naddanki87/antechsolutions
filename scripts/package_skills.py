"""Validate every skill under plugins/*/skills/ and build an uploadable zip per skill in dist/.

Usage:  python scripts/package_skills.py
Output: dist/<skill-name>.zip  (upload in Claude: Settings > Capabilities > Skills > Upload skill)
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def read_frontmatter(skill_md: Path) -> dict:
    text = skill_md.read_text(encoding="utf-8")
    match = re.match(r"^---\r?\n(.*?)\r?\n---\r?\n", text, re.DOTALL)
    if not match:
        raise ValueError("SKILL.md must start with a --- frontmatter block")
    fields = {}
    for line in match.group(1).splitlines():
        if ":" in line and not line.startswith(" "):
            key, value = line.split(":", 1)
            fields[key.strip()] = value.strip()
    return fields


def validate(skill_dir: Path) -> str:
    fm = read_frontmatter(skill_dir / "SKILL.md")
    name, desc = fm.get("name", ""), fm.get("description", "")
    errors = []
    if not NAME_RE.match(name) or len(name) > 64:
        errors.append(f"name '{name}' must be lowercase letters/digits/hyphens, max 64 chars")
    if name != skill_dir.name:
        errors.append(f"name '{name}' must match folder name '{skill_dir.name}'")
    if not desc or len(desc) > 1024:
        errors.append(f"description must be 1-1024 chars (is {len(desc)})")
    if "<" in desc or ">" in desc:
        errors.append("description must not contain angle brackets")
    unknown = set(fm) - {"name", "description", "license", "allowed-tools", "metadata"}
    if unknown:
        errors.append(f"unexpected frontmatter keys: {sorted(unknown)}")
    if errors:
        raise ValueError("; ".join(errors))
    return name


def package(skill_dir: Path, name: str) -> Path:
    DIST.mkdir(exist_ok=True)
    out = DIST / f"{name}.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(skill_dir.rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                # Zip root is the skill folder itself: <name>/SKILL.md, <name>/references/...
                zf.write(path, Path(name) / path.relative_to(skill_dir))
    return out


def main() -> int:
    skills = sorted(p.parent for p in ROOT.glob("plugins/*/skills/*/SKILL.md"))
    if not skills:
        print("No skills found")
        return 1
    failed = False
    for skill_dir in skills:
        try:
            name = validate(skill_dir)
            out = package(skill_dir, name)
            print(f"OK   {name:<20} -> {out.relative_to(ROOT)} ({out.stat().st_size // 1024} KB)")
        except ValueError as e:
            failed = True
            print(f"FAIL {skill_dir.name}: {e}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
