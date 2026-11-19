#!/usr/bin/env python3
"""
Builds a standalone, shareable `.acc` Musical Scores Pack from `new_hymns.json`
and a directory of PDF sheet-music files.

The generated `.acc` archive is a standard ZIP containing:
  - hymns.json (the hymns that have matching PDF scores, tagged with category="new")
  - <SCORE>.pdf (only the PDFs referenced by hymns.json; unreferenced PDFs are excluded)

Users can download the single `.acc` file from GitHub Releases and tap it (or use
"Add / Import -> Import Hymn File" inside ACC) to install all musical scores offline.
"""

import argparse
import json
import os
import zipfile
from pathlib import Path


def build_pack(hymns_json: Path, pdf_dir: Path, output_path: Path) -> None:
    with hymns_json.open("r", encoding="utf-8") as f:
        hymns = json.load(f)

    manifest_hymns = []
    included_pdfs = []
    missing_pdfs = []

    for hymn in hymns:
        pdf_name = str(hymn.get("PDF_File", "")).strip()
        if not pdf_name:
            continue
        pdf_path = pdf_dir / os.path.basename(pdf_name)
        if not pdf_path.is_file():
            missing_pdfs.append((hymn.get("Title", ""), pdf_name))
            continue

        entry = dict(hymn)
        entry["PDF_File"] = pdf_path.name
        entry["category"] = "new"
        manifest_hymns.append(entry)
        if pdf_path not in included_pdfs:
            included_pdfs.append(pdf_path)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("hymns.json", json.dumps(manifest_hymns, ensure_ascii=False, indent=2).encode("utf-8"))
        for pdf_path in sorted(included_pdfs, key=lambda p: p.name.lower()):
            zf.write(pdf_path, arcname=pdf_path.name)

    total_raw = sum(p.stat().st_size for p in included_pdfs)
    pack_size = output_path.stat().st_size
    print(f"Built {output_path}")
    print(f"  Hymns with scores : {len(manifest_hymns)}")
    print(f"  PDFs packaged     : {len(included_pdfs)} (raw {total_raw / 1048576:.2f} MB -> pack {pack_size / 1048576:.2f} MB)")
    if missing_pdfs:
        print(f"  Missing PDFs ({len(missing_pdfs)}): {missing_pdfs}")


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Build downloadable ACC Musical Scores Pack (.acc)")
    parser.add_argument(
        "--hymns-json",
        type=Path,
        default=repo_root / "app/src/main/assets/new_hymns.json",
        help="Path to new_hymns.json",
    )
    parser.add_argument(
        "--pdf-dir",
        type=Path,
        default=repo_root / "local_pdfs",
        help="Directory containing source PDF score files",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=repo_root / "releases/acc_musical_scores.acc",
        help="Output .acc pack path",
    )
    args = parser.parse_args()
    build_pack(args.hymns_json, args.pdf_dir, args.output)


if __name__ == "__main__":
    main()
