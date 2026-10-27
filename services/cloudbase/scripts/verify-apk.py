"""Inspect uncompressed APK entries; private needles arrive only via stdin, never argv or reports."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

apk = Path(sys.argv[1]).resolve()
private = json.loads(sys.stdin.read())
base = "https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai".encode()
old = b"https://wildflower-nature.pengwanru62.workers.dev"
secrets = [value.encode() for value in private.values() if isinstance(value, str) and len(value) >= 16]
matches = 0
found_base = False
found_old = False
private_files = []
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.endswith((".jks", ".keystore", ".p12", ".pfx")) or Path(name).name.startswith(".env"):
            private_files.append(name)
        data = archive.read(name)
        matches += sum(secret in data for secret in secrets)
        if name.endswith(".dex"):
            found_base |= base in data
            found_old |= old in data
report = {"apk": str(apk), "bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "actualServerSecretMatches": matches, "privateFiles": private_files,
          "cloudbaseBaseInDex": found_base, "oldWorkerUrlInDex": found_old}
print(json.dumps(report, indent=2))
raise SystemExit(0 if matches == 0 and not private_files and found_base and not found_old else 1)
