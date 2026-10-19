#!/usr/bin/env python3
"""
Splits an oversized Kotlin file into several, by top-level declaration.

Used for the screen file decomposition in spec 15 §12. Each block is located
by the text of its declaration rather than by line number, so the script
keeps working as the source changes underneath it.

Every generated file gets a proper Kotlin header: the file-level OptIn, the
package declaration, and only the imports its own body actually references.

Usage:
    python3 scripts/split-kotlin-file.py <source.kt> <out-dir> <name:marker> ...
"""

import os
import re
import subprocess
import sys

OPT_IN = """@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

"""


def find_block(lines, marker):
    """Returns (start, end) line indices of the top-level block at marker."""
    decl = None
    for index, line in enumerate(lines):
        if line.startswith(marker):
            decl = index
            break
    if decl is None:
        raise SystemExit(f"marker not found: {marker!r}")

    start = decl
    while start > 0 and lines[start - 1].lstrip().startswith("@"):
        start -= 1

    for index in range(decl + 1, len(lines)):
        line = lines[index]
        if line and not line[0].isspace() and not line.startswith(("}", ")", "]", "/*", " *")):
            return start, index
    return start, len(lines)


def package_of(path):
    for line in open(path, encoding="utf-8"):
        if line.startswith("package "):
            return line.strip()
    return ""


def opt_in_of(path):
    """
    Returns the file-level OptIn block of a file, or an empty string.

    A short name such as `ExperimentalLayoutApi::class` is qualified with
    its package, because the OptIn block sits above the imports and so
    cannot rely on one of them.
    """
    text = open(path, encoding="utf-8").read()
    if not text.startswith("@file:OptIn("):
        return ""
    end = text.index(")")
    block = text[: end + 1] + "\n\n"
    known = {
        "ExperimentalLayoutApi": "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        "ExperimentalMaterial3Api": "androidx.compose.material3.ExperimentalMaterial3Api",
    }
    for short, qualified in known.items():
        block = block.replace(f"{short}::class", f"{qualified}::class")
    return block


def imports_of(path):
    return [line for line in open(path, encoding="utf-8").read().splitlines() if line.startswith("import ")]


def imports_from_git(path, ref="HEAD"):
    """
    The import list of the committed version of the file.

    Splitting prunes imports out of the source as it goes, so the source
    stops listing imports that have already moved to a sibling file. A
    later extraction would then not be able to re-import them. Reading the
    list from the committed version keeps it stable across repeated runs.
    """
    try:
        result = subprocess.run(
            ["git", "show", f"{ref}:{path}"],
            capture_output=True,
            text=True,
            check=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return [line for line in result.stdout.splitlines() if line.startswith("import ")]


def prune_imports(imports, body):
    """Keeps the imports the body actually references."""
    kept = []
    for line in imports:
        simple = line.split(".")[-1].strip()
        if simple == "*":
            kept.append(line)
            continue
        # getValue and setValue are operator delegates and are referenced
        # through `by`, so they never appear in the body text.
        if simple in ("getValue", "setValue"):
            kept.append(line)
            continue
        if re.search(r"\b" + re.escape(simple) + r"\b", body):
            kept.append(line)
    return kept


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 1

    args = sys.argv[1:]
    import_ref = "HEAD"
    if args and args[0].startswith("--imports-ref="):
        import_ref = args[0].split("=", 1)[1]
        args = args[1:]
    source_path, out_dir = args[0], args[1]
    specs = args[2:]

    original_text = open(source_path, encoding="utf-8").read()
    lines = original_text.splitlines(keepends=True)
    package = package_of(source_path)
    opt_in = opt_in_of(source_path)
    all_imports = imports_from_git(source_path, import_ref) or imports_of(source_path)

    by_file = {}
    for spec in specs:
        name, marker = spec.split(":", 1)
        start, end = find_block(lines, marker)
        by_file.setdefault(name, []).append((start, end, "".join(lines[start:end])))

    for start, end, _ in sorted(
        [b for blocks in by_file.values() for b in blocks], key=lambda b: b[0], reverse=True
    ):
        del lines[start:end]

    open(source_path, "w", encoding="utf-8").write("".join(lines))

    for name, blocks in by_file.items():
        body = "".join(block for _, _, block in sorted(blocks, key=lambda b: b[0]))
        path = os.path.join(out_dir, f"{name}.kt")
        if os.path.exists(path):
            # Merge into the existing file rather than replacing it, so that
            # a second extraction into the same target does not discard what
            # the first one put there.
            existing = open(path, encoding="utf-8").read()
            lines = existing.splitlines(keepends=True)
            # The body starts after the import block. Locating it by counting
            # blank lines is fragile: a file whose header has a different
            # shape loses its first declarations.
            last_import = -1
            for index, line in enumerate(lines):
                if line.startswith("import "):
                    last_import = index
            body_start = last_import + 1
            while body_start < len(lines) and not lines[body_start].strip():
                body_start += 1
            header = "".join(lines[:body_start])
            previous_body = "".join(lines[body_start:]).rstrip()
            combined = previous_body + "\n\n" + body
            # The merged body may reference symbols the first pass did not
            # import, so the import set is recomputed over both bodies.
            imports = prune_imports(all_imports, combined)
            header = opt_in + package + "\n\n" + "\n".join(imports) + "\n\n"
            merged = header + combined
        else:
            header = opt_in + package + "\n\n"
            header += "\n".join(prune_imports(all_imports, body)) + "\n\n"
            merged = header + body
        open(path, "w", encoding="utf-8").write(merged)
        print(f"wrote {path} ({len(merged.splitlines())} lines)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())