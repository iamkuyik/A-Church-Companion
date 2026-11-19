#!/usr/bin/env python3
"""
Builds a complete, shareable `.acc` Congregation & Musical Scores Pack containing:
  - hymns.json: categorized map {"soc": [...738 hymns...], "new": [...55 hymns...]}
  - bible_bilingual.db (optional/default): full 5-translation Holy Bible SQLite database (Efik, KJV, RSV, BBE, NIV)
  - <SCORE>.pdf: all 48 referenced sheet-music PDFs

Importing this single `.acc` file into either ACC (`com.trust.acc`) or ACC Open (`com.trust.acc.open`)
installs all 48 musical scores, all 738 Songs of the Church hymns, all 55 New Hymns, and unlocks
all 5 Bible translations (including NIV and RSV) offline in one step.
"""

import argparse
import json
import os
import zipfile
from pathlib import Path


def build_pack(
    soc_json: Path,
    new_json: Path,
    bible_db: Path,
    pdf_source: Path,
    output_path: Path,
    include_bible: bool = True,
) -> None:
    with soc_json.open("r", encoding="utf-8") as f:
        soc_hymns = json.load(f)
    with new_json.open("r", encoding="utf-8") as f:
        new_hymns = json.load(f)

    # Read PDFs either from a directory or from an existing .acc zip archive
    pdf_bytes_by_name = {}
    if pdf_source.is_file() and zipfile.is_zipfile(pdf_source):
        with zipfile.ZipFile(pdf_source, "r") as src_zf:
            for name in src_zf.namelist():
                if name.lower().endswith(".pdf"):
                    pdf_bytes_by_name[os.path.basename(name)] = src_zf.read(name)
    elif pdf_source.is_dir():
        for p in sorted(pdf_source.glob("*.pdf")):
            pdf_bytes_by_name[p.name] = p.read_bytes()

    referenced_pdfs = {}
    clean_new = []
    for hymn in new_hymns:
        entry = dict(hymn)
        pdf_name = os.path.basename(str(entry.get("PDF_File", "")).strip())
        entry["PDF_File"] = pdf_name
        entry["category"] = "new"
        clean_new.append(entry)
        if pdf_name and pdf_name in pdf_bytes_by_name:
            referenced_pdfs[pdf_name] = pdf_bytes_by_name[pdf_name]

    clean_soc = []
    for hymn in soc_hymns:
        entry = dict(hymn)
        entry["category"] = "soc"
        clean_soc.append(entry)

    manifest = {
        "soc": clean_soc,
        "new": clean_new,
    }

    tmp_out = output_path.with_suffix(".acc.tmp")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(tmp_out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("hymns.json", json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8"))
        if include_bible and bible_db.is_file():
            zf.write(bible_db, arcname="bible_bilingual.db")
        for pdf_name in sorted(referenced_pdfs.keys(), key=str.lower):
            zf.writestr(pdf_name, referenced_pdfs[pdf_name])

    os.replace(tmp_out, output_path)
    pack_size = output_path.stat().st_size
    print(f"Built {output_path} ({pack_size / 1048576:.2f} MB)")
    print(f"  Songs of the Church : {len(clean_soc)} hymns")
    print(f"  New Hymns           : {len(clean_new)} hymns")
    print(f"  PDF Scores          : {len(referenced_pdfs)} files")
    print(f"  5-Translation Bible : {'Included (' + f'{bible_db.stat().st_size / 1048576:.2f} MB raw)' if (include_bible and bible_db.is_file()) else 'Omitted'}")


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Build ACC Full Congregation & Musical Scores Pack (.acc)")
    parser.add_argument("--soc-json", type=Path, default=repo_root / "app/src/main/assets/soc_hymns.json")
    parser.add_argument("--new-json", type=Path, default=repo_root / "app/src/main/assets/new_hymns.json")
    parser.add_argument("--bible-db", type=Path, default=repo_root / "app/src/main/assets/bible_bilingual.db")
    parser.add_argument("--pdf-source", type=Path, default=repo_root / "releases/acc_musical_scores.acc")
    parser.add_argument("--output", type=Path, default=repo_root / "releases/acc_musical_scores.acc")
    parser.add_argument("--no-bible", action="store_true", help="Omit bible_bilingual.db from the pack")
    args = parser.parse_args()
    build_pack(args.soc_json, args.new_json, args.bible_db, args.pdf_source, args.output, not args.no_bible)


if __name__ == "__main__":
    main()
