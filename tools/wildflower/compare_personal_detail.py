"""Diagnostic contact sheets only: original Android captures remain untouched."""
import argparse
from pathlib import Path
from compare_my_garden import strip


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    args = parser.parse_args()
    strip([("Target (design only)", args.reference / "personal_detail_original_v2_target.png")] +
          [(label, args.evidence / label / "personal-detail-393.png")
           for label in ("before", "round1", "round2", "final")],
          args.evidence / "comparison-393.png", 393)
    strip([(label, args.evidence / label / "personal-detail-320.png")
           for label in ("before", "round1", "round2", "final")],
          args.evidence / "comparison-320.png", 320)


if __name__ == "__main__":
    main()
