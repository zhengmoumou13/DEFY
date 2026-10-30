#!/usr/bin/env python3
"""Check tracked public files without printing credentials or file contents.

This is a small repository hygiene check, not a substitute for a secret scanner
or manual review. Run gitleaks separately before publishing a release.
"""
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {".gradle", ".gradle-home", ".kotlin", ".idea", "build", "tools", "output", "__pycache__"}
FORBIDDEN_NAMES = {"local.properties", "secrets.properties", "key.properties", "keystore.properties", ".DS_Store"}
FORBIDDEN_SUFFIXES = {".apk", ".aab", ".apks", ".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".db", ".db-wal", ".db-shm", ".dwb", ".log", ".pyc"}
SECRETS = {
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "github-token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})\b"),
    "api-token": re.compile(r"\bsk-[A-Za-z0-9_-]{24,}\b"),
    "glm-token": re.compile(r"\b[0-9a-fA-F]{32}\.[A-Za-z0-9_-]{12,}\b"),
}

def main():
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    paths = [p for p in paths if p]
    problems = []
    for relative in paths:
        path = ROOT / relative
        if (set(Path(relative).parts) & FORBIDDEN_PARTS or path.name in FORBIDDEN_NAMES
                or path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name.startswith(".env")
                or re.fullmatch(r"regression-.*\.txt", path.name)):
            problems.append((relative, "local/private artifact"))
            continue
        if path.is_symlink():
            problems.append((relative, "symlink needs explicit review"))
            continue
        if not path.is_file():
            problems.append((relative, "tracked file missing"))
            continue
        if path.stat().st_size > 20 * 1024 * 1024:
            problems.append((relative, "file exceeds 20 MiB"))
        data = path.read_bytes()
        if b"\0" in data[:8192]:
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for kind, pattern in SECRETS.items():
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                problems.append((f"{relative}:{line}", kind))
        if path.suffix.lower() == ".md":
            links = re.findall(r"\]\(([^)]+)\)", text)
            links += re.findall(r'''(?:src|href)=["']([^"']+)["']''', text)
            for target in links:
                target = target.strip().split(' "', 1)[0].strip("<>")
                parsed = urlsplit(target)
                if parsed.scheme or target.startswith(("#", "//")):
                    continue
                linked = (path.parent / unquote(parsed.path)).resolve()
                if not linked.is_file() or not linked.is_relative_to(ROOT) or str(linked.relative_to(ROOT)) not in paths:
                    problems.append((relative, "missing/untracked local link: " + parsed.path))
    if problems:
        for path, reason in problems:
            print(f"FAIL {path}: {reason}")
        return 1
    print(f"PASS: {len(paths)} tracked files; no forbidden artifacts, known token patterns or broken local document links.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
