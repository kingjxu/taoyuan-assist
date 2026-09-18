"""Check built APK version labels, signing and receiver permission declaration.

Usage: python3 scripts/check_apk.py SDK_BUILD_TOOLS_DIR APK
Requires JAVA_HOME for apksigner. This is static validation, not a device test.
"""
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
tools, apk = map(Path, sys.argv[1:])
def aapt(*args):
    return subprocess.check_output([str(tools / 'aapt'), *args], text=True)

version = re.search(r'versionName\s+"([^"]+)"', (root / 'app/build.gradle').read_text()).group(1)
label = ET.parse(root / 'app/src/main/res/values/strings.xml').find("string[@name='app_version']").text
assert label == 'v' + version, (label, version)
badging = aapt('dump', 'badging', str(apk))
assert "versionName='" + version + "'" in badging
resources = aapt('dump', '--values', 'resources', str(apk))
values = re.findall(r'resource [^\n]*:string/app_version: t=[^\n]*\n\s*\(string\d+\) "([^"]+)"', resources)
assert values and all(value == label for value in values), values
manifest = aapt('dump', 'xmltree', str(apk), 'AndroidManifest.xml')
permission = 'com.example.screencaptureapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
assert re.search(r'E: permission[^\n]*\n[^\n]*' + re.escape(permission)
                 + r'[^\n]*\n[^\n]*protectionLevel[^\n]*0x2\b', manifest)
assert re.search(r'E: uses-permission[^\n]*\n[^\n]*' + re.escape(permission), manifest)
subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', str(apk)], check=True)
print('PASS: APK and UI version ' + version + '; signature; dynamic receiver signature permission')
