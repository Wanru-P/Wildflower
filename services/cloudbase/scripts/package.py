"""Build an allowlisted deployment ZIP with a Unix-executable bootstrap, never local credentials."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "wildflower-ai"
FILES = ("index.mjs", "app.mjs", "gate.mjs", "store.mjs", "protocol.mjs", "package.json", "package-lock.json", "scf_bootstrap")

def build():
    if not (SOURCE / "node_modules/@cloudbase/node-sdk/package.json").is_file():
        raise SystemExit("Run npm ci --omit=dev --ignore-scripts inside wildflower-ai first.")
    files = [SOURCE / name for name in FILES]
    files += sorted(p for p in (SOURCE / "node_modules").rglob("*") if p.is_file())
    if any(p.is_symlink() for p in files):
        raise SystemExit("Symlinks are not allowed in this deployment package.")
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    artifact = output / "wildflower-ai.zip"
    with zipfile.ZipFile(artifact, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in files:
            relative = path.relative_to(SOURCE).as_posix()
            if any(part.startswith(".env") or part in {".dev.vars", ".local-token", ".npmrc"} for part in Path(relative).parts):
                raise SystemExit("Unexpected private file in dependency tree.")
            content = path.read_bytes()
            if relative == "scf_bootstrap":
                content = content.replace(b"\r\n", b"\n")
            info = zipfile.ZipInfo(relative)
            info.create_system = 3
            info.external_attr = (0o100755 if relative == "scf_bootstrap" else 0o100644) << 16
            archive.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(artifact) as archive:
        if archive.testzip() is not None:
            raise SystemExit("ZIP integrity failure.")
    result = {"file": str(artifact), "bytes": artifact.stat().st_size, "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "files": len(files)}
    (output / "package-manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    build()
