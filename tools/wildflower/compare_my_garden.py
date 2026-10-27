"""Compare unmodified Android captures with MG-V1; never generates app content."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw


def strip(paths, destination, width):
    panels = []
    for label, path in paths:
        with Image.open(path) as source:
            image = source.convert("RGB")
            image = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
        panels.append((label, image))
    result = Image.new("RGB", (width * len(panels), max(image.height for _, image in panels) + 34), "#e6decd")
    draw = ImageDraw.Draw(result)
    for index, (label, image) in enumerate(panels):
        result.paste(image, (index * width, 34))
        draw.text((index * width + 10, 10), label, fill="#24452e")
    result.save(destination)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    args = parser.parse_args()
    evidence = args.evidence
    strip([("MG-V1 target (design only)", args.reference / "my_garden_target.png")] +
          [(label, evidence / label / "garden-populated-393.png") for label in ("before", "round1", "round2", "final")],
          evidence / "comparison-393.png", 393)
    strip([(label, evidence / label / "garden-populated-320.png") for label in ("round1", "round2", "final")],
          evidence / "comparison-320.png", 320)
    checks = {}
    for viewport, top in (("393", 136), ("320", 113)):
        with Image.open(evidence / "before" / f"garden-empty-{viewport}.png") as old, \
                Image.open(evidence / "final" / f"garden-empty-{viewport}.png") as new:
            difference = ImageChops.difference(
                old.convert("RGB").crop((0, top, old.width, old.height)),
                new.convert("RGB").crop((0, top, new.width, new.height)))
            checks[viewport] = {
                "original_size": list(old.size), "final_size": list(new.size),
                "status_bar_excluded_rows": top,
                "content_pixel_identical": old.size == new.size and difference.getbbox() is None,
                "difference_bounds_below_status_bar": difference.getbbox(),
                "differing_pixels": sum(pixel != (0, 0, 0) for pixel in difference.getdata()),
            }
    (evidence / "empty-page-comparison.json").write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(checks))


if __name__ == "__main__":
    main()
