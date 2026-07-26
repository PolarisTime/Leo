#!/usr/bin/env python3
"""Enforce incremental Java top-level module boundaries without third-party packages."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SCHEMA_VERSION = 1
PACKAGE_PATTERN = re.compile(r"^\s*package\s+com\.leo\.erp\.([A-Za-z_][A-Za-z0-9_]*)", re.MULTILINE)
IMPORT_PATTERN = re.compile(
    r"^\s*import\s+(?:static\s+)?(com\.leo\.erp\.[A-Za-z0-9_.$*]+)\s*;",
    re.MULTILINE,
)
RESTRICTED_IMPORT_PATTERNS = (
    ("domain.entity", re.compile(r"\.domain\.entity(?:\.|$)")),
    ("repository", re.compile(r"\.repository(?:\.|$)")),
    ("web.dto", re.compile(r"\.web\.dto(?:\.|$)")),
)


@dataclass(frozen=True, order=True)
class RestrictedImport:
    source_module: str
    target_module: str
    kind: str
    source: str
    target: str

    def to_json(self) -> dict[str, str]:
        return {
            "sourceModule": self.source_module,
            "targetModule": self.target_module,
            "kind": self.kind,
            "source": self.source,
            "target": self.target,
        }

    @classmethod
    def from_json(cls, value: object) -> "RestrictedImport":
        if not isinstance(value, dict):
            raise ValueError("restrictedImports entries must be objects")

        keys = ("sourceModule", "targetModule", "kind", "source", "target")
        fields = []
        for key in keys:
            field = value.get(key)
            if not isinstance(field, str) or not field:
                raise ValueError(f"restrictedImports.{key} must be a non-empty string")
            fields.append(field)
        return cls(*fields)


@dataclass(frozen=True, order=True)
class ModuleEdge:
    source_module: str
    target_module: str

    def to_json(self) -> dict[str, str]:
        return {
            "sourceModule": self.source_module,
            "targetModule": self.target_module,
        }

    @classmethod
    def from_json(cls, value: object) -> "ModuleEdge":
        if not isinstance(value, dict):
            raise ValueError("cyclicEdges entries must be objects")

        source = value.get("sourceModule")
        target = value.get("targetModule")
        if not isinstance(source, str) or not source:
            raise ValueError("cyclicEdges.sourceModule must be a non-empty string")
        if not isinstance(target, str) or not target:
            raise ValueError("cyclicEdges.targetModule must be a non-empty string")
        return cls(source, target)


@dataclass(frozen=True)
class ScanResult:
    restricted_imports: frozenset[RestrictedImport]
    cyclic_edges: frozenset[ModuleEdge]
    java_file_count: int
    module_edge_count: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Reject new cross-module internal imports and cyclic module edges."
    )
    parser.add_argument("--source-root", type=Path, help="Java production source root")
    parser.add_argument("--baseline", type=Path, help="Committed JSON baseline")
    parser.add_argument(
        "--write-baseline",
        action="store_true",
        help="Replace the baseline with the current scan (disabled in CI)",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run isolated scanner checks in a temporary directory",
    )
    return parser.parse_args()


def target_module(import_name: str) -> str | None:
    parts = import_name.split(".")
    if len(parts) < 4 or parts[:3] != ["com", "leo", "erp"]:
        return None
    return parts[3]


def restricted_kind(import_name: str) -> str | None:
    for kind, pattern in RESTRICTED_IMPORT_PATTERNS:
        if pattern.search(import_name):
            return kind
    return None


def strongly_connected_components(edges: set[ModuleEdge]) -> list[set[str]]:
    adjacency: dict[str, set[str]] = {}
    for edge in edges:
        adjacency.setdefault(edge.source_module, set()).add(edge.target_module)
        adjacency.setdefault(edge.target_module, set())

    index = 0
    indices: dict[str, int] = {}
    low_links: dict[str, int] = {}
    stack: list[str] = []
    on_stack: set[str] = set()
    components: list[set[str]] = []

    def visit(module: str) -> None:
        nonlocal index
        indices[module] = index
        low_links[module] = index
        index += 1
        stack.append(module)
        on_stack.add(module)

        for target in sorted(adjacency[module]):
            if target not in indices:
                visit(target)
                low_links[module] = min(low_links[module], low_links[target])
            elif target in on_stack:
                low_links[module] = min(low_links[module], indices[target])

        if low_links[module] != indices[module]:
            return

        component: set[str] = set()
        while stack:
            member = stack.pop()
            on_stack.remove(member)
            component.add(member)
            if member == module:
                break
        components.append(component)

    for module in sorted(adjacency):
        if module not in indices:
            visit(module)
    return components


def scan_sources(source_root: Path) -> ScanResult:
    if not source_root.is_dir():
        raise ValueError(f"Java source root does not exist: {source_root}")

    restricted_imports: set[RestrictedImport] = set()
    module_edges: set[ModuleEdge] = set()
    java_files = sorted(source_root.rglob("*.java"))

    for java_file in java_files:
        try:
            content = java_file.read_text(encoding="utf-8")
        except UnicodeDecodeError as error:
            raise ValueError(f"Java source is not valid UTF-8: {java_file}") from error

        package_match = PACKAGE_PATTERN.search(content)
        if package_match is None:
            continue

        source_module = package_match.group(1)
        source_path = java_file.relative_to(source_root).as_posix()
        for import_name in IMPORT_PATTERN.findall(content):
            imported_module = target_module(import_name)
            if imported_module is None or imported_module == source_module:
                continue

            edge = ModuleEdge(source_module, imported_module)
            module_edges.add(edge)
            kind = restricted_kind(import_name)
            if kind is not None:
                restricted_imports.add(
                    RestrictedImport(
                        source_module,
                        imported_module,
                        kind,
                        source_path,
                        import_name,
                    )
                )

    cyclic_edges: set[ModuleEdge] = set()
    for component in strongly_connected_components(module_edges):
        if len(component) < 2:
            continue
        cyclic_edges.update(
            edge
            for edge in module_edges
            if edge.source_module in component and edge.target_module in component
        )

    return ScanResult(
        frozenset(restricted_imports),
        frozenset(cyclic_edges),
        len(java_files),
        len(module_edges),
    )


def baseline_payload(result: ScanResult) -> dict[str, object]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "restrictedImports": [item.to_json() for item in sorted(result.restricted_imports)],
        "cyclicEdges": [edge.to_json() for edge in sorted(result.cyclic_edges)],
    }


def write_baseline(path: Path, result: ScanResult, *, allow_ci: bool = False) -> None:
    if not allow_ci and os.environ.get("CI", "").lower() in {"1", "true", "yes"}:
        raise ValueError("Refusing to update the architecture baseline in CI")
    if not path.parent.is_dir():
        raise ValueError(f"Baseline parent directory does not exist: {path.parent}")

    payload = json.dumps(baseline_payload(result), indent=2, ensure_ascii=True) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        dir=path.parent,
        text=True,
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(payload)
        temporary_path.replace(path)
    finally:
        temporary_path.unlink(missing_ok=True)


def load_baseline(path: Path) -> tuple[frozenset[RestrictedImport], frozenset[ModuleEdge]]:
    if not path.is_file():
        raise ValueError(
            f"Architecture baseline does not exist: {path}. "
            "Generate it locally with --write-baseline after architecture review."
        )

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Architecture baseline is not valid UTF-8 JSON: {path}") from error
    if not isinstance(payload, dict) or payload.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError(f"Unsupported architecture baseline schema in {path}")

    restricted_values = payload.get("restrictedImports")
    cyclic_values = payload.get("cyclicEdges")
    if not isinstance(restricted_values, list) or not isinstance(cyclic_values, list):
        raise ValueError("Baseline restrictedImports and cyclicEdges must be arrays")

    return (
        frozenset(RestrictedImport.from_json(item) for item in restricted_values),
        frozenset(ModuleEdge.from_json(item) for item in cyclic_values),
    )


def describe_imports(imports: Iterable[RestrictedImport]) -> list[str]:
    return [
        (
            f"  {item.source}: {item.source_module} -> {item.target_module} "
            f"[{item.kind}] {item.target}"
        )
        for item in sorted(imports)
    ]


def describe_edges(edges: Iterable[ModuleEdge]) -> list[str]:
    return [
        f"  {edge.source_module} -> {edge.target_module}"
        for edge in sorted(edges)
    ]


def check_against_baseline(source_root: Path, baseline_path: Path) -> int:
    result = scan_sources(source_root)
    baseline_imports, baseline_edges = load_baseline(baseline_path)
    new_imports = result.restricted_imports - baseline_imports
    new_cyclic_edges = result.cyclic_edges - baseline_edges
    removed_imports = baseline_imports - result.restricted_imports
    removed_cyclic_edges = baseline_edges - result.cyclic_edges

    if not new_imports and not new_cyclic_edges and not removed_imports and not removed_cyclic_edges:
        print(
            "Architecture boundary check passed: "
            f"{result.java_file_count} Java files, {result.module_edge_count} module edges, "
            f"{len(result.restricted_imports)} baselined restricted imports, "
            f"{len(result.cyclic_edges)} baselined cyclic edges."
        )
        return 0

    messages = ["Architecture boundary check failed."]
    if new_imports:
        messages.append("New cross-module internal imports:")
        messages.extend(describe_imports(new_imports))
    if new_cyclic_edges:
        messages.append("New dependency edges participating in module cycles:")
        messages.extend(describe_edges(new_cyclic_edges))
    if removed_imports:
        messages.append("Resolved cross-module internal imports still present in the baseline:")
        messages.extend(describe_imports(removed_imports))
    if removed_cyclic_edges:
        messages.append("Resolved cyclic dependency edges still present in the baseline:")
        messages.extend(describe_edges(removed_cyclic_edges))
    if new_imports or new_cyclic_edges:
        messages.append(
            "Introduce a public module API or remove the cycle. "
            "Do not update the baseline to accept new violations."
        )
    else:
        messages.append(
            "Regenerate the baseline locally after review so resolved dependencies cannot return."
        )
    print("\n".join(messages), file=sys.stderr)
    return 1


def write_java(path: Path, package_name: str, imports: Iterable[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    import_lines = "\n".join(f"import {name};" for name in imports)
    path.write_text(
        f"package {package_name};\n{import_lines}\nfinal class Fixture {{}}\n",
        encoding="utf-8",
    )


def run_self_test() -> int:
    with tempfile.TemporaryDirectory(prefix="leo-module-boundary-self-test-") as directory:
        root = Path(directory)
        source_root = root / "src"
        baseline_path = root / "baseline.json"
        write_java(
            source_root / "com/leo/erp/alpha/Alpha.java",
            "com.leo.erp.alpha",
            ("com.leo.erp.beta.repository.BetaRepository",),
        )
        write_java(
            source_root / "com/leo/erp/beta/Beta.java",
            "com.leo.erp.beta",
            ("com.leo.erp.alpha.api.AlphaPort",),
        )

        initial = scan_sources(source_root)
        write_baseline(baseline_path, initial, allow_ci=True)
        if check_against_baseline(source_root, baseline_path) != 0:
            raise AssertionError("baseline-equivalent scan must pass")

        write_java(
            source_root / "com/leo/erp/gamma/Gamma.java",
            "com.leo.erp.gamma",
            ("com.leo.erp.alpha.domain.entity.AlphaEntity",),
        )
        changed = scan_sources(source_root)
        baseline_imports, _ = load_baseline(baseline_path)
        if not changed.restricted_imports - baseline_imports:
            raise AssertionError("new restricted import was not detected")

        write_java(
            source_root / "com/leo/erp/alpha/Alpha.java",
            "com.leo.erp.alpha",
            (
                "com.leo.erp.beta.repository.BetaRepository",
                "com.leo.erp.gamma.api.GammaPort",
            ),
        )
        changed = scan_sources(source_root)
        _, baseline_edges = load_baseline(baseline_path)
        new_cyclic_edges = changed.cyclic_edges - baseline_edges
        expected_edges = {ModuleEdge("alpha", "gamma"), ModuleEdge("gamma", "alpha")}
        if not expected_edges.issubset(new_cyclic_edges):
            raise AssertionError("new module cycle was not detected")

    print("Architecture boundary scanner self-test passed.")
    return 0


def main() -> int:
    args = parse_args()
    if args.self_test:
        return run_self_test()
    if args.source_root is None or args.baseline is None:
        raise ValueError("--source-root and --baseline are required unless --self-test is used")

    source_root = args.source_root.resolve()
    baseline_path = args.baseline.resolve()
    if args.write_baseline:
        result = scan_sources(source_root)
        write_baseline(baseline_path, result)
        print(
            f"Architecture baseline updated: {len(result.restricted_imports)} restricted imports, "
            f"{len(result.cyclic_edges)} cyclic edges."
        )
        return 0
    return check_against_baseline(source_root, baseline_path)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"Architecture boundary check error: {error}", file=sys.stderr)
        raise SystemExit(2) from error
