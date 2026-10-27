"""Labelled target/real-app panels. Originals are never cropped or overwritten."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
KIT = ROOT / "docs/wildflower/Wildflower_Complete_Visual_Asset_Kit_v2/visual-kit-v2"
PAGES = (
    ("01_explore", "explore"), ("02_garden_empty", "garden-empty"),
    ("03_garden_populated", "garden-populated"), ("04_personal_detail", "personal-detail"),
    ("05_species_detail", "species-detail"), ("06_seasons", "seasons"),
    ("07_culture", "culture"), ("08_gallery", "observation-gallery"),
    ("09_scan", "scan"), ("10_card_editor", "botanical-card-editor"),
)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def scaled(path, top=0):
    with Image.open(path) as source:
        source.load()
        if top:
            source = source.crop((0, top, source.width, source.height))
        return source.convert("RGB").resize((393, round(source.height * 393 / source.width)), Image.Resampling.LANCZOS)

def panel(target, screenshot, label, destination, target_top=0, app_top=0):
    left, right = scaled(target, target_top), scaled(screenshot, app_top)
    canvas = Image.new("RGB", (822, max(left.height, right.height) + 64), "#eeeade")
    draw = ImageDraw.Draw(canvas)
    draw.text((12, 10), f"TARGET: {label}", fill="#24452e")
    draw.text((417, 10), f"REAL APP: {screenshot.stem}", fill="#24452e")
    canvas.paste(left, (12, 32)); canvas.paste(right, (417, 32))
    draw.text((12, canvas.height - 22), "393-wide proportional comparison; originals and hashes retained.", fill="#725b46")
    canvas.save(destination / f"{label}.png")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument("--target-status-px", type=int, default=0)
    parser.add_argument("--app-status-px", type=int, default=0)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    destination = evidence / "comparisons"
    destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    for reference, actual in PAGES:
        target = KIT / "reference_screens" / f"{reference}.png"
        screenshot = evidence / "screenshots" / f"{actual}.png"
        panel(target, screenshot, reference, destination)
        if args.target_status_px and args.app_status_px:
            content_destination = evidence / "content-comparisons"
            content_destination.mkdir(parents=True, exist_ok=True)
            panel(target, screenshot, reference, content_destination, args.target_status_px, args.app_status_px)
        manifest.append({"target": str(target.relative_to(ROOT)), "target_sha256": digest(target),
                         "screenshot": str(screenshot.relative_to(ROOT)), "screenshot_sha256": digest(screenshot)})
    (destination / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    installed = []
    kit_manifest = json.loads((KIT / "docs/ASSET_MANIFEST.json").read_text(encoding="utf-8"))
    for asset in kit_manifest["assets"]:
        if asset["file"].startswith("android_assets/"):
            path = ROOT / "app/src/main/res" / asset["file"].removeprefix("android_assets/")
            assert digest(path) == asset["sha256"], path
            installed.append({"file": str(path.relative_to(ROOT)), "sha256": digest(path)})
    (destination / "installed-assets.json").write_text(json.dumps(installed, indent=2) + "\n", encoding="utf-8")
    exports = []
    for path in sorted((evidence / "exports").glob("*.png")):
        with Image.open(path) as artwork:
            artwork.load()
            assert artwork.format == "PNG" and artwork.size == (1600, 2400), path
            exports.append({"file": path.name, "sha256": digest(path), "width": artwork.width, "height": artwork.height})
    (destination / "export-manifest.json").write_text(json.dumps(exports, indent=2) + "\n", encoding="utf-8")
    (destination / "comparison-settings.json").write_text(json.dumps({"comparison_width": 393,
        "content_only_target_status_px": args.target_status_px, "content_only_app_status_px": args.app_status_px,
        "note": "Manual status-region guides, not pixel-equality or an acceptance score. Full originals retained."}, indent=2) + "\n", encoding="utf-8")
    print(f"Compared {len(manifest)} actual screens; verified {len(installed)} installed resource hashes.")

if __name__ == "__main__":
    main()
