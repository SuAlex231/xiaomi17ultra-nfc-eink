#!/usr/bin/env python3
"""Build with the installed SDK/JDK; no Gradle, downloads, or third-party app libs."""
import hashlib
import lzma
import os
import shutil
from pathlib import Path
import subprocess
import zipfile
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
sdk = Path(os.environ["ANDROID_HOME"])
jdk = Path(os.environ["JAVA_HOME"])
bt = sdk / "build-tools" / os.environ.get("ANDROID_BUILD_TOOLS", "34.0.0")
android = sdk / "platforms" / os.environ.get("ANDROID_PLATFORM", "android-34") / "android.jar"
if not android.is_file():
    raise RuntimeError("Missing Android platform: " + str(android) + "; set ANDROID_PLATFORM")
build_root = root / "build"
build_root.mkdir(parents=True, exist_ok=True)
build = Path(tempfile.mkdtemp(prefix="v2-", dir=build_root))
dist = root / "dist"
for directory in (build / "classes", build / "dex", dist):
    directory.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / "bin") + os.pathsep + os.environ.get("PATH", ""))
assets = build / "assets"
shutil.copytree(root / "assets", assets)
injector = root / "libs/frida-inject-17.24.0-android-arm64.xz"
if hashlib.sha256(injector.read_bytes()).hexdigest() != "261875c11ec37fa1d3c26c05f71c0390ef1176539b0545d0b253cdbf24ece0e5":
    raise RuntimeError("Frida injector checksum mismatch")
(assets / "frida-inject").write_bytes(lzma.decompress(injector.read_bytes()))
libraries = []
for aar in sorted((root / "libs").glob("*-13.1.5.aar")):
    jar = build / (aar.stem + ".jar")
    with zipfile.ZipFile(aar) as archive:
        jar.write_bytes(archive.read("classes.jar"))
    libraries.append(jar)
if len(libraries) != 4:
    raise RuntimeError("Expected pinned Shizuku api/provider/aidl/shared 13.1.5 AARs in libs/")

def run(*args, cwd=root):
    args = [str(arg) for arg in args]
    if os.name == "nt" and not Path(args[0]).suffix:
        args[0] += ".bat" if Path(args[0] + ".bat").is_file() else ".exe"
    subprocess.run(args, cwd=cwd, env=env, check=True)

run(jdk / "bin/javac", "-encoding", "UTF-8", "--release", "8",
    "-classpath", os.pathsep.join(str(p) for p in [android, *libraries]), "-d", build / "classes", *sorted((root / "src").rglob("*.java")))
with zipfile.ZipFile(build / "classes.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    for source in sorted((build / "classes").rglob("*.class")):
        archive.write(source, source.relative_to(build / "classes").as_posix())
run(bt / "d8", "--min-api", "26", "--lib", android, "--output", build / "dex", build / "classes.jar", *libraries)
run(bt / "aapt", "package", "-f", "-M", root / "AndroidManifest.xml", "-S", root / "res",
    "-A", assets, "-I", android, "-F", build / "unsigned.apk")
with zipfile.ZipFile(build / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as archive:
    archive.write(build / "dex/classes.dex", "classes.dex")
run(bt / "zipalign", "-f", "4", build / "unsigned.apk", build / "aligned.apk")
key = build_root / "debug.keystore"
if not key.exists():
    run(jdk / "bin/keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "qpolllab", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=QPoll Lab Local Debug", "-noprompt")
version = ET.parse(root / "AndroidManifest.xml").getroot().attrib["{http://schemas.android.com/apk/res/android}versionName"]
apk = dist / ("QPollLab-" + version + ".apk")
run(bt / "apksigner", "sign", "--ks", key, "--ks-key-alias", "qpolllab", "--ks-pass", "pass:android",
    "--key-pass", "pass:android", "--out", apk, build / "aligned.apk")
run(bt / "apksigner", "verify", "--verbose", apk)
run(bt / "aapt", "dump", "badging", apk.relative_to(root))
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(dist / (apk.name + ".sha256")).write_text(digest + "  " + apk.name + "\n", encoding="utf-8")
print("APK:", apk)
print("SHA256:", digest)
(build_root / "last-build-dir.txt").write_text(build.name, encoding="utf-8")
