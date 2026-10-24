"""Validate ST image-job gates on a connected Android device; no NFC operations."""
import os
from pathlib import Path
import subprocess
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ['ANDROID_HOME'])
jdk = Path(os.environ['JAVA_HOME'])
android = sdk / 'platforms' / os.environ.get('ANDROID_PLATFORM', 'android-35') / 'android.jar'
bt = sdk / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0')
compiled = root / 'build' / (root / 'build/last-build-dir.txt').read_text().strip()
out = root / 'build/android-st-tests'
(out / 'classes').mkdir(parents=True, exist_ok=True)
(out / 'dex').mkdir(exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + os.pathsep + os.environ.get('PATH', ''))

def run(tool, *args):
    tool = str(tool)
    if os.name == 'nt' and not Path(tool).suffix:
        tool += '.bat' if Path(tool + '.bat').is_file() else '.exe'
    subprocess.run([tool, *map(str, args)], cwd=root, env=env, check=True)

run(jdk / 'bin/javac', '--release', '8', '-encoding', 'UTF-8', '-cp',
    os.pathsep.join(map(str, [android, compiled / 'classes.jar'])), '-d', out / 'classes', root / 'tests/AndroidStJobTest.java')
with zipfile.ZipFile(out / 'test-classes.jar', 'w') as z:
    for source in (out / 'classes').rglob('*.class'):
        z.write(source, source.relative_to(out / 'classes').as_posix())
run(bt / 'd8', '--min-api', '26', '--lib', android, '--classpath', compiled / 'classes.jar',
    '--output', out / 'dex', out / 'test-classes.jar')
with zipfile.ZipFile(out / 'tests.jar', 'w') as z:
    z.write(out / 'dex/classes.dex', 'classes.dex')
adb = sdk / 'platform-tools/adb'
version=ET.parse(root/'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
run(adb, 'push', root / ('dist/QPollLab-'+version+'.apk'), '/data/local/tmp/qp17u.apk')
run(adb, 'push', out / 'tests.jar', '/data/local/tmp/qp17u-tests.jar')
run(adb, 'shell', 'CLASSPATH=/data/local/tmp/qp17u.apk:/data/local/tmp/qp17u-tests.jar '
    'app_process /system/bin org.nfcresearch.qpolllab.AndroidStJobTest /data/local/tmp/qp17u-job-test')
