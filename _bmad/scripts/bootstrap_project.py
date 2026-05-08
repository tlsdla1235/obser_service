#!/usr/bin/env python3
"""Bootstrap a project-local _bmad runtime from the global Codex BMAD runtime.

This gives each project its own BMAD config/customization space while still
letting Codex discover the BMAD skills globally from ~/.codex/skills.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

EXCLUDE_NAMES = {
    '__pycache__',
    '.DS_Store',
}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description='Create or refresh a project-local _bmad runtime from the global Codex BMAD runtime.',
    )
    parser.add_argument(
        '--project-root',
        default='.',
        help='Project directory that should receive a local _bmad folder. Defaults to the current directory.',
    )
    parser.add_argument(
        '--force',
        action='store_true',
        help='Overwrite existing files under the destination _bmad runtime.',
    )
    return parser.parse_args(argv)


def copy_tree(src: Path, dst: Path, force: bool) -> tuple[list[str], list[str]]:
    created: list[str] = []
    skipped: list[str] = []
    for path in sorted(src.rglob('*')):
        rel = path.relative_to(src)
        if any(part in EXCLUDE_NAMES for part in rel.parts):
            continue
        target = dst / rel
        if path.is_dir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and not force:
            skipped.append(str(target))
            continue
        shutil.copy2(path, target)
        created.append(str(target))
    return created, skipped


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    project_root = Path(args.project_root).expanduser().resolve()
    if not project_root.is_dir():
        print(f'error: project root is not a directory: {project_root}', file=sys.stderr)
        return 2

    global_runtime = Path(__file__).resolve().parent.parent
    destination = project_root / '_bmad'
    destination.mkdir(parents=True, exist_ok=True)

    created, skipped = copy_tree(global_runtime, destination, force=args.force)

    print(f'Global runtime: {global_runtime}')
    print(f'Project root: {project_root}')
    print(f'Destination: {destination}')
    print(f'Copied files: {len(created)}')
    print(f'Skipped files: {len(skipped)}')
    if skipped:
        print('Tip: rerun with --force to refresh existing files.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1:]))
