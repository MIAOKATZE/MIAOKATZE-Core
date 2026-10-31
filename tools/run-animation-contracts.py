#!/usr/bin/env python3
"""Compile the production animation subset once and test consumers of that exact SDK.

No Minecraft stubs, dependency downloads, release builds or game processes.
Uses an explicitly selected real JDK 8. Output must be a fresh directory.
"""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdk-home", type=pathlib.Path, required=True)
parser.add_argument("--output", type=pathlib.Path, default=ROOT / "build/animation-sdk")
args = parser.parse_args()
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
receipts = []


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tool(name):
    return str(args.jdk_home / "bin" / (name + (".exe" if os.name == "nt" else "")))


def run(name, command):
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    log = output / (name + ".txt")
    log.write_bytes(result.stdout)
    receipts.append({"name": name, "command": command, "cwd": str(ROOT),
                     "exit_code": result.returncode, "log": log.name, "log_sha256": sha(log)})
    (output / "commands.json").write_text(json.dumps(receipts, indent=2), encoding="utf-8")
    print(name + ": exit=" + str(result.returncode), flush=True)
    if result.returncode:
        print(result.stdout.decode("utf-8", errors="replace"))
        raise SystemExit(result.returncode)
    return result.stdout


version = run("java-version", [tool("java"), "-version"])
if b'"1.8.' not in version:
    raise SystemExit("This receipt requires an actual JDK/JRE 8; choose --jdk-home explicitly.")
run("javac-version", [tool("javac"), "-version"])
base = "src/main/java/com/miaokatze/miaokatzecore/"
# Native renderers under client/animation/render require real Minecraft/LWJGL dependencies.
# This SDK contains only pure models and the direct, owner-driven client lifecycle seams.
sources = sorted([p for directory in ["api/animation", "api/miaovision"]
                  for p in (ROOT / (base + directory)).rglob("*.java")]
                 + list((ROOT / (base + "client/animation")).glob("*.java")))
test_root = ROOT / "src/test/java/com/miaokatze/miaokatzecore/api/animation"
tests = sorted(test_root.rglob("*.java"))
if not sources or not tests:
    raise SystemExit("Production sources and contracts must exist.")


def compile_sources(name, source_paths, destination, classpath=None):
    destination.mkdir()
    argfile = output / (name + "-sources.txt")
    argfile.write_text("\n".join('"' + p.as_posix() + '"' for p in source_paths), encoding="utf-8")
    command = [tool("javac"), "-J-Xmx512m", "-encoding", "UTF-8", "-source", "8", "-target", "8",
               "-d", str(destination)]
    if classpath:
        command += ["-classpath", str(classpath)]
    run(name, command + ["@" + str(argfile)])


classes = output / "classes"
compile_sources("production-compile", sources, classes)
sdk = output / "core-animation-sdk-local.jar"
with zipfile.ZipFile(sdk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(classes.rglob("*.class")):
        data = path.read_bytes()
        if int.from_bytes(data[6:8], "big") != 52:
            raise SystemExit("Non-Java-8 class: " + str(path))
        info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data)
test_classes = output / "test-classes"
compile_sources("contract-compile", tests, test_classes, sdk)
classpath = str(sdk) + os.pathsep + str(test_classes)
for suite in ["AnimationContracts", "RuntimeContracts", "VisualContracts", "QualityDemandContracts"]:
    run(suite, [tool("java"), "-Xmx256m", "-cp", classpath,
                "com.miaokatze.miaokatzecore.api.animation." + suite])
consumer_sources = sorted((ROOT / "tools/animation-consumers").glob("*.java"))
if len(consumer_sources) != 2:
    raise SystemExit("Exactly two independent SDK consumer probes are required.")
for consumer in consumer_sources:
    destination = output / consumer.stem
    compile_sources(consumer.stem + "-compile", [consumer], destination, sdk)
    run(consumer.stem, [tool("java"), "-Xmx64m", "-cp", str(sdk) + os.pathsep + str(destination),
                        consumer.stem])
class_names = [p.relative_to(classes).as_posix()[:-6].replace("/", ".")
               for p in sorted(classes.rglob("*.class"))]
run("public-signatures", [tool("javap"), "-classpath", str(sdk), "-public"] + class_names)
fixtures = sorted(p for p in (ROOT / "src/test/resources/animation").rglob("*") if p.is_file())
all_sources = sources + tests + consumer_sources + fixtures + [pathlib.Path(__file__).resolve()]
source_hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(all_sources)}
aggregate = "".join(path + " " + digest + "\n" for path, digest in sorted(source_hashes.items()))
manifest = {"kind": "local-production-sdk-not-release-jar", "sdk": sdk.name, "sdk_sha256": sha(sdk),
            "source_digest_sha256": hashlib.sha256(aggregate.encode("utf-8")).hexdigest(),
            "source_hashes": source_hashes, "class_count": len(class_names), "class_major": 52,
            "receipts": receipts, "native_fml_gl_audio": "NOT-RUN", "consumer_owner_acknowledgments": "PENDING"}
(output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print("PASS production SDK and two consumers; SHA256=" + sha(sdk))
