#!/usr/bin/env python3
"""Assemble a local compilation-only Core candidate from verified compiled inputs.

This is not a release build and must never be installed, published or copied to A-Jar.
Unchanged classes/resources retain the pinned complete dev baseline; changed animation
classes come from the tested SDK and the real-dependency native renderer compilation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-jar", type=Path, required=True)
parser.add_argument("--sdk-dir", type=Path, required=True)
parser.add_argument("--native-dir", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=False)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


baseline_hash = "16f5b4d2adbdf3ed5416a6bef714210f6e5bbd84814eeeddac93c864f989b693"
assert sha(args.base_jar) == baseline_hash, "wrong complete Core baseline"
sdk_manifest = json.loads((args.sdk_dir / "manifest.json").read_text(encoding="utf-8"))
native_manifest = json.loads((args.native_dir / "manifest.json").read_text(encoding="utf-8"))
for manifest in [sdk_manifest, native_manifest]:
    for path, digest in manifest["source_hashes"].items():
        assert sha(ROOT / path) == digest, "source changed since compilation: " + path
sdk = args.sdk_dir / sdk_manifest["sdk"]
assert sha(sdk) == sdk_manifest["sdk_sha256"], "wrong SDK"
with zipfile.ZipFile(args.base_jar) as archive:
    entries = {n: archive.read(n) for n in archive.namelist() if not n.endswith("/")}
baseline_entries = {n: hashlib.sha256(b).hexdigest() for n, b in entries.items()}
replacements = {}
prefixes = ("com/miaokatze/miaokatzecore/api/animation/", "com/miaokatze/miaokatzecore/client/animation/")
# Remove obsolete inner-class bytecode in the recompiled packages as well as replacing current classes.
for name in list(entries):
    if name.startswith(prefixes) and name.endswith(".class"):
        del entries[name]
with zipfile.ZipFile(sdk) as archive:
    for name in archive.namelist():
        if name.startswith(prefixes) and name.endswith(".class"):
            entries[name] = archive.read(name)
            replacements[name] = {"origin": "tested-sdk", "sha256": hashlib.sha256(entries[name]).hexdigest()}
native_prefix = "com/miaokatze/miaokatzecore/client/animation/render/"
native_classes = args.native_dir / "classes"
for path in sorted((native_classes / native_prefix).rglob("*.class")):
    name = path.relative_to(native_classes).as_posix()
    data = path.read_bytes()
    # The native receipt includes every generated class, including optional dependency isolation helpers.
    assert hashlib.sha256(data).hexdigest() == native_manifest["compiled_classes"][name], name
    entries[name] = data
    replacements[name] = {"origin": "real-dependency-native-compile", "sha256": hashlib.sha256(data).hexdigest()}
assert any(n.startswith(native_prefix) for n in replacements), "native renderer missing"
candidate = args.output / "miaokatzecore-gap-close-link-only.jar"
with zipfile.ZipFile(candidate, "x", compression=zipfile.ZIP_DEFLATED) as archive:
    for name, data in sorted(entries.items()):
        if name.endswith(".class"):
            assert int.from_bytes(data[6:8], "big") == 52, name
        info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data)
untouched = [n for n in entries if n not in replacements]
assert all(hashlib.sha256(entries[n]).hexdigest() == baseline_entries[n] for n in untouched)
receipt = {"kind": "compilation-only-assembled-candidate-not-release", "baseline_core_sha": "522b63e9b2571d8e9164f9ba2fc473427b036800",
           "baseline_jar": str(args.base_jar), "baseline_jar_sha256": baseline_hash,
           "sdk_sha256": sha(sdk), "candidate": str(candidate.resolve()), "candidate_sha256": sha(candidate),
           "native_manifest_sha256": sha(args.native_dir / "manifest.json"), "recompiled_entries": replacements,
           "unchanged_entries": len(untouched), "all_classes_major52": True,
           "full_gradle_build": "NOT-RUN", "game_runtime": "NOT-RUN", "install_or_publish": False}
(args.output / "provenance.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
print("PASS link-only candidate SHA256=" + sha(candidate))
