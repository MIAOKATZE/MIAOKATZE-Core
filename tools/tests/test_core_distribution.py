"""Offline fixtures for the read-only Maven verifier; no network or MC build."""

import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
import zipfile


SPEC = importlib.util.spec_from_file_location("core_distribution", Path(__file__).parents[1] / "verify-core-distribution.py")
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)
VERSION = "0.2.3"
SOURCE_SHA = "a" * 40
MAVEN_URL = "https://raw.githubusercontent.com/MIAOKATZE/MIAOKATZE-Core/" + "b" * 40
PREFIX = "miaokatzecore-" + VERSION
RELATIVE = "com/miaokatze/miaokatzecore/miaokatzecore/" + VERSION + "/"
SOURCE_URL = VERIFIER.SOURCE_REPOSITORY + "/" + SOURCE_SHA + "/"


def jar(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return result.getvalue()


class Response(io.BytesIO):
    def __init__(self, data, url):
        super().__init__(data)
        self.url = url

    def geturl(self):
        return self.url


class DistributionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.directory = self.root / RELATIVE
        self.directory.mkdir(parents=True)
        self.properties = b"modId = miaokatzecore\nRELEASE_VERSION = 0.2.3\n"
        self.source_entries = {
            "mcmod.info": json.dumps([{"modid": "miaokatzecore", "version": "@CORE_RELEASE@"}]).encode(),
            "META-INF/miaokatze_core_marker.json": json.dumps({"identifier": "falsepatternlib_dependencies", "bundledArtifacts": [VERIFIER.GROUP + ":miaokatzecore:@CORE_RELEASE@"]}).encode(),
            VERIFIER.PACKAGE + "MiaokatzeCore.java": b"package com.miaokatze.miaokatzecore; public class MiaokatzeCore {}\n",
        }
        self.remote_sources = {SOURCE_URL + "gradle.properties": self.properties}
        for name, data in self.source_entries.items():
            self.remote_sources[SOURCE_URL + ("src/main/java/" if name.endswith(".java") else "src/main/resources/") + name] = data
        resources = {name: data.replace(b"@CORE_RELEASE@", VERSION.encode()) for name, data in self.source_entries.items() if not name.endswith(".java")}
        for suffix, bytecode in ((".jar", b"srg"), ("-dev.jar", b"mcp")):
            entries = dict(resources)
            for name in ("MiaokatzeCore.class", "bootstrap/CoreLoadingPlugin.class"):
                entries[VERIFIER.PACKAGE + name] = b"\xca\xfe\xba\xbe" + bytecode
            self.write(PREFIX + suffix, jar(entries))
        self.write(PREFIX + "-sources.jar", jar(self.source_entries))
        self.write(PREFIX + ".pom", ("<project xmlns='http://maven.apache.org/POM/4.0.0'><groupId>" + VERIFIER.GROUP + "</groupId><artifactId>miaokatzecore</artifactId><version>" + VERSION + "</version></project>").encode())
        variants = []
        for role, suffix, obfuscation in (("apiElements", "-dev.jar", "mcp"), ("reobfElements", ".jar", "srg"), ("sourcesElements", "-sources.jar", "mcp")):
            name = PREFIX + suffix
            data = (self.directory / name).read_bytes()
            attributes = {"com.gtnewhorizons.retrofuturagradle.obfuscation": obfuscation, "org.gradle.usage": "java-api" if role == "apiElements" else "java-runtime"}
            if role == "sourcesElements":
                attributes["org.gradle.docstype"] = "sources"
            variants.append({"name": role, "attributes": attributes, "files": [{"name": name, "url": name, "size": len(data), **{a: hashlib.new(a, data).hexdigest() for a in VERIFIER.ALGORITHMS}}]})
        self.module = {"component": {"group": VERIFIER.GROUP, "module": "miaokatzecore", "version": VERSION}, "variants": variants}
        self.write_module()

    def write(self, name, data):
        (self.directory / name).write_bytes(data)
        for algorithm in VERIFIER.ALGORITHMS:
            (self.directory / (name + "." + algorithm)).write_text(hashlib.new(algorithm, data).hexdigest(), encoding="ascii")

    def write_module(self):
        self.write(PREFIX + ".module", json.dumps(self.module).encode())

    def rewrite_jar(self, suffix, change):
        name = PREFIX + suffix
        with zipfile.ZipFile(self.directory / name) as archive:
            entries = {n: archive.read(n) for n in archive.namelist()}
        change(entries)
        data = jar(entries)
        self.write(name, data)
        for variant in self.module["variants"]:
            for entry in variant["files"]:
                if entry["name"] == name:
                    entry.update({"size": len(data), **{a: hashlib.new(a, data).hexdigest() for a in VERIFIER.ALGORITHMS}})
        self.write_module()

    def urlopen(self, url, timeout):
        self.assertEqual(timeout, 30)
        data = self.remote_sources.get(url)
        if data is None and url.startswith(MAVEN_URL + "/"):
            path = self.root / url[len(MAVEN_URL) + 1:]
            if path.is_file():
                data = path.read_bytes()
        if data is None:
            raise HTTPError(url, 404, "Not Found", {}, None)
        return Response(data, url)

    def verify(self, mode="local", extra=()):
        output = io.StringIO()
        with patch.object(VERIFIER, "urlopen", side_effect=self.urlopen), contextlib.redirect_stdout(output):
            code = VERIFIER.main(["--mode", mode, "--maven-root", str(self.root) if mode == "local" else MAVEN_URL,
                                  "--version", VERSION, "--source-sha", SOURCE_SHA, *extra])
        report = json.loads(output.getvalue())
        self.assertEqual(report["status"], "PASS" if code == 0 else "FAIL")
        return code, report

    def assert_failure(self, message):
        code, report = self.verify()
        self.assertEqual(code, 1)
        self.assertIn(message, report["error"])

    def test_valid_local_receipt_and_sources_template(self):
        output = self.root / "receipt.json"
        code, report = self.verify(extra=("--output", str(output)))
        self.assertEqual(code, 0, report.get("error"))
        self.assertEqual(report, json.loads(output.read_text(encoding="utf-8")))
        self.assertEqual(report["mode"], "local")
        self.assertEqual(report["source"]["sha"], SOURCE_SHA)
        self.assertEqual(len(report["files"]), 5)
        self.assertEqual(len(report["files"][0]["checksums"]), 4)
        self.assertEqual(report["jarChecks"][-1]["metadataVersion"], "@CORE_RELEASE@")
        self.assertIn("does not prove", report["provenance"])

    def test_valid_mock_remote(self):
        code, report = self.verify("remote")
        self.assertEqual(code, 0, report.get("error"))
        self.assertEqual(report["mode"], "remote")
        self.assertTrue(all(f["location"].startswith(MAVEN_URL) for f in report["files"]))

    def test_missing_each_primary_file(self):
        for suffix in (".pom", ".jar", "-dev.jar", "-sources.jar", ".module"):
            with self.subTest(suffix=suffix):
                path = self.directory / (PREFIX + suffix)
                data = path.read_bytes()
                path.unlink()
                self.assert_failure("Missing required file")
                path.write_bytes(data)

    def test_required_sha256_missing(self):
        (self.directory / (PREFIX + ".pom.sha256")).unlink()
        self.assert_failure("Missing required file")

    def test_optional_checksums_may_be_absent(self):
        for path in self.directory.glob("*.md5"):
            path.unlink()
        self.assertEqual(self.verify()[0], 0)

    def test_each_present_checksum_is_verified(self):
        for algorithm in VERIFIER.ALGORITHMS:
            with self.subTest(algorithm=algorithm):
                path = self.directory / (PREFIX + ".pom." + algorithm)
                original = path.read_bytes()
                path.write_text("0" * len(original), encoding="ascii")
                self.assert_failure("Checksum " + algorithm + " mismatch")
                path.write_bytes(original)

    def test_wrong_pom_coordinates(self):
        path = self.directory / (PREFIX + ".pom")
        self.write(path.name, path.read_bytes().replace(b"<version>0.2.3", b"<version>0.2.4"))
        self.assert_failure("POM version mismatch")

    def test_wrong_module_coordinates(self):
        self.module["component"]["version"] = "0.2.4"
        self.write_module()
        self.assert_failure("Module coordinates mismatch")

    def test_module_size_hash_url_and_classifier(self):
        entry = self.module["variants"][0]["files"][0]
        for field, wrong in (("size", 1), ("sha512", "0" * 128), ("url", "../wrong.jar"), ("name", PREFIX + ".jar")):
            with self.subTest(field=field):
                original = entry[field]
                entry[field] = wrong
                self.write_module()
                self.assert_failure("Module")
                entry[field] = original

    def test_module_missing_variant(self):
        self.module["variants"].pop()
        self.write_module()
        self.assert_failure("Module must describe")

    def test_binary_mcmod_wrong_version(self):
        def change(entries):
            entries["mcmod.info"] = entries["mcmod.info"].replace(b"0.2.3", b"0.2.4")
        self.rewrite_jar("-dev.jar", change)
        self.assert_failure("Jar mcmod.info version mismatch")

    def test_binary_marker_wrong_version(self):
        def change(entries):
            name = "META-INF/miaokatze_core_marker.json"
            entries[name] = entries[name].replace(b"0.2.3", b"0.2.4")
        self.rewrite_jar(".jar", change)
        self.assert_failure("Jar marker version mismatch")

    def test_sources_must_match_fixed_source(self):
        def change(entries):
            entries[VERIFIER.PACKAGE + "MiaokatzeCore.java"] += b"// wrong source\n"
        self.rewrite_jar("-sources.jar", change)
        self.assert_failure("Sources jar differs from fixed source commit")

    def test_binary_content_identity(self):
        def change(entries):
            entries.pop(VERIFIER.PACKAGE + "MiaokatzeCore.class")
        self.rewrite_jar(".jar", change)
        self.assert_failure("Core compiled entry missing")

    def test_remote_missing_main_and_checksum(self):
        for suffix in (".jar", ".pom.sha256"):
            with self.subTest(suffix=suffix):
                path = self.directory / (PREFIX + suffix)
                data = path.read_bytes()
                path.unlink()
                code, report = self.verify("remote")
                self.assertEqual(code, 1)
                self.assertIn("HTTP 404", report["error"])
                path.write_bytes(data)

    def test_remote_server_failure_is_not_an_absent_optional_checksum(self):
        def failed(url, timeout):
            if url.endswith(".md5"):
                raise HTTPError(url, 503, "Unavailable", {}, None)
            return self.urlopen(url, timeout)
        output = io.StringIO()
        with patch.object(VERIFIER, "urlopen", side_effect=failed), contextlib.redirect_stdout(output):
            code = VERIFIER.main(["--mode", "remote", "--maven-root", MAVEN_URL, "--version", VERSION, "--source-sha", SOURCE_SHA])
        self.assertEqual(code, 1)
        self.assertIn("HTTP 503", json.loads(output.getvalue())["error"])

    def test_source_version_and_full_sha_required(self):
        self.remote_sources[SOURCE_URL + "gradle.properties"] = b"RELEASE_VERSION=0.2.4\n"
        self.assert_failure("Source RELEASE_VERSION")
        code, report = self.verify(extra=("--source-sha", "main"))
        self.assertEqual(code, 1)
        self.assertIn("full 40-digit", report["error"])

    def test_local_source_commit_blob(self):
        def git(command, capture_output, check):
            self.assertEqual(command[:3], ["git", "-C", str(self.root)])
            if "rev-parse" in command:
                data = (SOURCE_SHA + "\n").encode()
            else:
                path = command[-1].split(":", 1)[1]
                data = self.remote_sources[SOURCE_URL + path]
            return subprocess.CompletedProcess(command, 0, stdout=data, stderr=b"")
        with patch.object(VERIFIER.subprocess, "run", side_effect=git), patch.object(VERIFIER, "read_https", side_effect=AssertionError("Local source must not access HTTPS")):
            code, report = self.verify(extra=("--source-root", str(self.root)))
        self.assertEqual(code, 0, report.get("error"))
        self.assertEqual(report["sourceMode"], "local-git-object")
        self.assertEqual(report["source"]["sha"], SOURCE_SHA)


if __name__ == "__main__":
    unittest.main()
