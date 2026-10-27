"""Read-only source images -> explicitly labelled proportional review panels.

Never overwrite/retouch the reference or the real Android screenshot. Different
photographs make raw pixel-error scores meaningless; compare structure by eye.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/wildflower/evidence/phase5-final"
REFERENCES = ROOT / "docs/wildflower/target-ui/Wildflower_Phase5_Visual_Pack/references"
PAGES = (
    ("01_explore", "explore"), ("02_garden_empty", "garden-empty"),
    ("03_garden_populated", "garden-populated"),
    ("04_personal_plant_detail", "personal-detail"),
    ("05_species_detail", "species-detail"), ("06_seasons", "seasons"),
    ("07_culture", "culture"), ("08_observation_gallery", "observation-gallery"),
    ("09_scan", "scan"), ("10_card_editor", "botanical-card-editor"),
)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def scaled(path):
    with Image.open(path) as source:
        source.load()
        original = source.size
        result = source.convert("RGB").resize((360, round(source.height * 360 / source.width)), Image.Resampling.LANCZOS)
        return result, original

def main():
    destination = EVIDENCE / "before-after"
    destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    for reference, actual in PAGES:
        target = REFERENCES / f"{reference}.png"
        screenshot = EVIDENCE / "screenshots" / f"{actual}.png"
        left, left_size = scaled(target)
        right, right_size = scaled(screenshot)
        panel = Image.new("RGB", (756, max(left.height, right.height) + 64), "#eeeade")
        draw = ImageDraw.Draw(panel)
        draw.text((12, 10), f"TARGET: {reference}", fill="#24452e")
        draw.text((390, 10), f"REAL APP: {actual}", fill="#24452e")
        panel.paste(left, (12, 32)); panel.paste(right, (390, 32))
        draw.text((12, panel.height - 22), "Proportional scaling only. Original screenshots retained.", fill="#725b46")
        panel.save(destination / f"{reference}-comparison.png")
        manifest.append({"page": reference, "reference": str(target.relative_to(ROOT)), "reference_size": left_size,
                         "reference_sha256": digest(target), "screenshot": str(screenshot.relative_to(ROOT)),
                         "screenshot_size": right_size, "screenshot_sha256": digest(screenshot)})
    (destination / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name in ("botanical-atlas", "classical-poetry", "modern-journal"):
        path = EVIDENCE / "exports" / f"{name}.png"
        with Image.open(path) as image:
            image.load()
            assert image.size == (1600, 2400), (path, image.size)
            assert image.format == "PNG"
    print(f"Compared {len(manifest)} pages without modifying sources; exports decode at 1600x2400.")

if __name__ == "__main__":
    main()
