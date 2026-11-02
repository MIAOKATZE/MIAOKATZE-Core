#!/usr/bin/env python3
"""Read-only Core Maven verification; writes only the requested JSON receipt.

Example:
  python tools/verify-core-distribution.py --mode remote \
    --maven-root https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/MAVEN_SHA \
    --version 0.2.3 --source-sha SOURCE_SHA --output distribution.json

Local mode takes a Maven repository root, not a version directory. Source files
come from the official repository at the exact SHA, or from local Git objects
when --source-root is supplied. urllib uses the standard proxy environment.
No build, publication or Git mutation runs.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import urlopen
import xml.etree.ElementTree as ET
import zipfile


GROUP = "com.miaokatze.miaokatzecore"
ARTIFACT = "miaokatzecore"
SOURCE_REPOSITORY = "https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core"
ALGORITHMS = ("sha256", "sha512", "sha1", "md5")
PACKAGE = "com/miaokatze/miaokatzecore/"
SOURCE_ENTRIES = ("mcmod.info", "META-INF/miaokatze_core_marker.json", PACKAGE + "MiaokatzeCore.java")
PROVENANCE = (
    "The source commit's RELEASE_VERSION and selected sources are verified using the recorded source mode. "
    "Artifact hashes identify the inspected distribution; this does not prove that historical binaries "
    "were built from that commit. A trusted build/publication receipt is needed for that claim."
)


class VerificationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_https(url, required=True):
    try:
        with urlopen(url, timeout=30) as response:
            require(urlsplit(response.geturl()).scheme == "https", "Non-HTTPS redirect: " + url)
            return response.read()
    except HTTPError as error:
        if error.code == 404 and not required:
            return None
        raise VerificationError("HTTP {}: {}".format(error.code, url)) from error


def release_version(data):
    versions = re.findall(r"^\s*RELEASE_VERSION\s*=\s*(\S+)\s*$", data.decode("utf-8"), re.MULTILINE)
    require(len(versions) == 1, "Expected one RELEASE_VERSION in source gradle.properties")
    return versions[0]


def normalized(data):
    return data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")


def verify_source(args, report):
    require(re.fullmatch(r"[0-9a-fA-F]{40}", args.source_sha), "--source-sha must be a full 40-digit Git SHA")
    source_sha = args.source_sha.lower()
    if args.source_root:
        def git(*arguments):
            result = subprocess.run(["git", "-C", str(args.source_root), *arguments], capture_output=True, check=False)
            require(result.returncode == 0, "Local source Git check failed: " + result.stderr.decode("utf-8", errors="replace").strip())
            return result.stdout

        commit = git("rev-parse", source_sha + "^{commit}").decode("ascii").strip()
        require(commit == source_sha, "Local source commit differs from expected SHA")
        report["sourceMode"] = "local-git-object"
        source_root = str(args.source_root.resolve())

        def read_source(path):
            return git("show", source_sha + ":" + path)

        properties_location = "git:" + source_root + "@" + source_sha + ":gradle.properties"
    else:
        report["sourceMode"] = "remote-https"
        source_url = SOURCE_REPOSITORY + "/" + source_sha + "/"

        def read_source(path):
            return read_https(source_url + path)

        properties_location = source_url + "gradle.properties"
    properties = read_source("gradle.properties")
    version = release_version(properties)
    report["source"] = {"sha": source_sha, "releaseVersion": version, "propertiesLocation": properties_location,
                        "propertiesBytes": len(properties), "propertiesSha256": sha256(properties),
                        "scope": "Selected commit blobs; the working tree and build provenance are not attested."}
    require(version == args.version, "Source RELEASE_VERSION {} != {}".format(version, args.version))
    sources = {}
    for entry in SOURCE_ENTRIES:
        prefix = "src/main/java/" if entry.endswith(".java") else "src/main/resources/"
        sources[entry] = read_source(prefix + entry)
    report["source"]["selectedFiles"] = [
        {"name": name, "bytes": len(data), "sha256": sha256(data)} for name, data in sources.items()
    ]
    return sources


def repository_reader(args, report):
    relative = GROUP.replace(".", "/") + "/" + ARTIFACT + "/" + args.version + "/"
    if args.mode == "remote":
        parsed = urlsplit(args.maven_root)
        require(parsed.scheme == "https" and parsed.hostname and not parsed.username and not parsed.password
                and not parsed.query and not parsed.fragment, "Remote Maven root must be a public HTTPS URL without credentials, query or fragment")
        root = args.maven_root.rstrip("/")

        def read(name, required=True):
            location = root + "/" + relative + name
            return read_https(location, required), location
    else:
        require("://" not in args.maven_root, "Local Maven root must be a filesystem directory")
        root = str(Path(args.maven_root).resolve())

        def read(name, required=True):
            path = Path(root) / relative / name
            try:
                return path.read_bytes(), str(path)
            except FileNotFoundError as error:
                if not required:
                    return None, str(path)
                raise VerificationError("Missing required file: " + str(path)) from error
    report["mavenRoot"] = root
    return read


def verify_pom(data, version):
    pom = ET.fromstring(data)
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    require(pom.tag == "{http://maven.apache.org/POM/4.0.0}project", "Invalid Maven POM project")
    for field, expected in (("groupId", GROUP), ("artifactId", ARTIFACT), ("version", version)):
        require(pom.findtext("m:" + field, namespaces=namespace) == expected, "POM " + field + " mismatch")


def verify_module(data, version, files, report):
    module = json.loads(data)
    require(isinstance(module, dict), "Invalid Gradle module metadata")
    component = module.get("component", {})
    require(isinstance(component, dict) and all(component.get(k) == v for k, v in
            (("group", GROUP), ("module", ARTIFACT), ("version", version))), "Module coordinates mismatch")
    variants = module.get("variants")
    require(isinstance(variants, list) and variants, "Module variants missing")
    prefix = ARTIFACT + "-" + version
    expected_jars = {prefix + ".jar", prefix + "-dev.jar", prefix + "-sources.jar"}
    referenced = set()
    roles = set()
    report["moduleVariants"] = []
    for variant in variants:
        require(isinstance(variant, dict), "Invalid module variant")
        attributes, entries = variant.get("attributes", {}), variant.get("files", [])
        require(isinstance(attributes, dict) and isinstance(entries, list) and entries, "Module variant attributes/files missing")
        names = []
        for entry in entries:
            require(isinstance(entry, dict) and entry.get("name") in expected_jars, "Unexpected module file")
            name = entry["name"]
            require(entry.get("url") == name, "Module file URL mismatch: " + name)
            require(type(entry.get("size")) is int and entry["size"] == len(files[name]), "Module file size mismatch: " + name)
            require("sha256" in entry, "Module file SHA-256 missing: " + name)
            for algorithm in ALGORITHMS:
                if algorithm in entry:
                    require(entry[algorithm] == hashlib.new(algorithm, files[name]).hexdigest(), "Module " + algorithm + " mismatch: " + name)
            names.append(name)
            referenced.add(name)
        obfuscation = attributes.get("com.gtnewhorizons.retrofuturagradle.obfuscation")
        if attributes.get("org.gradle.docstype") == "sources":
            require(names == [prefix + "-sources.jar"], "Module sources variant must use sources jar")
            roles.add("sources")
        elif obfuscation == "mcp":
            require(names == [prefix + "-dev.jar"], "Module mcp variant must use dev jar")
            if attributes.get("org.gradle.usage") == "java-api":
                roles.add("dev")
        elif obfuscation == "srg":
            require(names == [prefix + ".jar"], "Module srg variant must use normal jar")
            roles.add("normal")
        else:
            raise VerificationError("Module variant missing supported mcp/srg/source identity")
        report["moduleVariants"].append({"name": variant.get("name"), "obfuscation": obfuscation, "files": names})
    require(referenced == expected_jars and roles == {"dev", "normal", "sources"}, "Module must describe dev, normal and sources jars")


def verify_jars(files, version, sources, report):
    prefix = ARTIFACT + "-" + version
    inventories = {}
    report["jarChecks"] = []
    for suffix, role in ((".jar", "normal"), ("-dev.jar", "dev"), ("-sources.jar", "sources")):
        name = prefix + suffix
        with zipfile.ZipFile(io.BytesIO(files[name])) as archive:
            names = [n for n in archive.namelist() if not n.endswith("/")]
            require(len(names) == len(set(names)), "Duplicate jar entries: " + name)
            require(archive.testzip() is None, "Jar CRC mismatch: " + name)
            require(all(n in names for n in SOURCE_ENTRIES[:2]), "Jar metadata missing: " + name)
            mods = json.loads(archive.read("mcmod.info"))
            require(isinstance(mods, list), "Invalid mcmod.info: " + name)
            core = [m for m in mods if isinstance(m, dict) and m.get("modid") == ARTIFACT]
            require(len(core) == 1, "Core mod identity missing or duplicated: " + name)
            marker = json.loads(archive.read(SOURCE_ENTRIES[1]))
            require(isinstance(marker, dict) and marker.get("identifier") == "falsepatternlib_dependencies", "Jar marker identity mismatch: " + name)
            token = core[0].get("version")
            allowed = (version, "@CORE_RELEASE@") if role == "sources" else (version,)
            require(token in allowed, "Jar mcmod.info version mismatch: " + name)
            require(marker.get("bundledArtifacts") == [GROUP + ":" + ARTIFACT + ":" + token], "Jar marker version mismatch: " + name)
            classes = {n for n in names if n.endswith(".class")}
            java = {n for n in names if n.endswith(".java")}
            if role == "sources":
                require(java and not classes, "Sources jar must contain Java sources without compiled classes")
                for entry, original in sources.items():
                    require(entry in names, "Sources jar entry missing: " + entry)
                    expected = original if token == "@CORE_RELEASE@" or entry.endswith(".java") else original.replace(b"@CORE_RELEASE@", version.encode("utf-8"))
                    require(normalized(archive.read(entry)) == normalized(expected), "Sources jar differs from fixed source commit: " + entry)
            else:
                require(classes and not java, "Binary jar must contain compiled classes without Java sources: " + name)
                for entry in (PACKAGE + "MiaokatzeCore.class", PACKAGE + "bootstrap/CoreLoadingPlugin.class"):
                    require(entry in classes and archive.read(entry).startswith(b"\xca\xfe\xba\xbe"), "Core compiled entry missing or invalid: " + name + ":" + entry)
                inventories[role] = classes
            report["jarChecks"].append({"name": name, "role": role, "metadataVersion": token,
                                        "classCount": len(classes), "javaCount": len(java)})
    require(inventories["normal"] == inventories["dev"], "Normal/dev jar class inventories differ")
    require(files[prefix + ".jar"] != files[prefix + "-dev.jar"], "Normal/dev jars are identical; obfuscation identities are unverified")


def verify(args, report):
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+_-]*", args.version), "Invalid Maven version")
    sources = verify_source(args, report)
    read = repository_reader(args, report)
    prefix = ARTIFACT + "-" + args.version
    files = {}
    for suffix in (".pom", ".jar", "-dev.jar", "-sources.jar", ".module"):
        name = prefix + suffix
        data, location = read(name)
        files[name] = data
        record = {"name": name, "location": location, "bytes": len(data), "sha256": sha256(data), "checksums": {}}
        report["files"].append(record)
        for algorithm in ALGORITHMS:
            checksum, checksum_location = read(name + "." + algorithm, required=algorithm == "sha256")
            if checksum is None:
                continue
            expected = checksum.decode("ascii").strip().lower()
            require(expected == hashlib.new(algorithm, data).hexdigest(), "Checksum " + algorithm + " mismatch: " + name)
            record["checksums"][algorithm] = {"location": checksum_location, "bytes": len(checksum),
                                               "sha256": sha256(checksum), "value": expected}
    verify_pom(files[prefix + ".pom"], args.version)
    verify_module(files[prefix + ".module"], args.version, files, report)
    verify_jars(files, args.version, sources, report)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", required=True, choices=("local", "remote"))
    parser.add_argument("--maven-root", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--source-root", type=Path, help="Read source version/resources from this local Git commit instead of remote HTTPS")
    parser.add_argument("--output", type=Path, help="Write a JSON receipt as well as stdout")
    args = parser.parse_args(argv)
    report = {"status": "FAIL", "mode": args.mode, "mavenRoot": args.maven_root,
              "coordinate": GROUP + ":" + ARTIFACT + ":" + args.version,
              "source": {"sha": args.source_sha}, "files": [], "provenance": PROVENANCE}
    try:
        verify(args, report)
        report["status"] = "PASS"
    except (VerificationError, OSError, URLError, ValueError, ET.ParseError, zipfile.BadZipFile) as error:
        report["error"] = str(error)
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    sys.stdout.write(output)
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
