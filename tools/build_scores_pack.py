#!/usr/bin/env python3
"""
Builds two separate, order-independent `.acc` packs for A Church Companion (ACC):

  1. `acc_hymns_and_bibles.acc` (~6.2 MB):
       - hymns.json: all 738 Songs of the Church hymns + all 55 New Hymns (with PDF_File metadata)
       - bible_bilingual.db: full 5-translation Holy Bible SQLite database (Efik, KJV, RSV, BBE, NIV)

  2. `acc_musical_scores.acc` (~31.9 MB):
       - hymns.json: the 48 New Hymns that have matching musical scores
       - <SCORE>.pdf: all 48 referenced zoomable sheet-music PDFs

Users can install either pack independently, or both packs in any order.
"""

import argparse
import json
import os
import zipfile
from pathlib import Path


def load_pdfs(pdf_source: Path) -> dict[str, bytes]:
    pdf_bytes_by_name: dict[str, bytes] = {}
    if pdf_source.is_file() and zipfile.is_zipfile(pdf_source):
        with zipfile.ZipFile(pdf_source, "r") as src_zf:
            for name in src_zf.namelist():
                if name.lower().endswith(".pdf"):
                    pdf_bytes_by_name[os.path.basename(name)] = src_zf.read(name)
    elif pdf_source.is_dir():
        for p in sorted(pdf_source.glob("*.pdf")):
            pdf_bytes_by_name[p.name] = p.read_bytes()
    return pdf_bytes_by_name


def build_hymns_and_bibles_pack(soc_json: Path, new_json: Path, bible_db: Path, output_path: Path) -> None:
    with soc_json.open("r", encoding="utf-8") as f:
        soc_hymns = json.load(f)
    with new_json.open("r", encoding="utf-8") as f:
        new_hymns = json.load(f)

    clean_soc = [dict(h, category="soc") for h in soc_hymns]
    clean_new = []
    for h in new_hymns:
        entry = dict(h)
        entry["PDF_File"] = os.path.basename(str(entry.get("PDF_File", "")).strip())
        entry["category"] = "new"
        clean_new.append(entry)

    manifest = {"soc": clean_soc, "new": clean_new}

    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_out = output_path.with_suffix(".acc.tmp")
    with zipfile.ZipFile(tmp_out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("hymns.json", json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8"))
        if bible_db.is_file():
            zf.write(bible_db, arcname="bible_bilingual.db")

    os.replace(tmp_out, output_path)
    print(f"Built {output_path} ({output_path.stat().st_size / 1048576:.2f} MB)")
    print(f"  Songs of the Church : {len(clean_soc)} hymns")
    print(f"  New Hymns           : {len(clean_new)} hymns")
    print(f"  5-Translation Bible : {'Included' if bible_db.is_file() else 'Omitted'}")


def build_scores_only_pack(new_json: Path, pdf_source: Path, output_path: Path) -> None:
    with new_json.open("r", encoding="utf-8") as f:
        new_hymns = json.load(f)

    pdf_bytes_by_name = load_pdfs(pdf_source)
    referenced_pdfs: dict[str, bytes] = {}
    score_hymns = []

    for h in new_hymns:
        pdf_name = os.path.basename(str(h.get("PDF_File", "")).strip())
        if pdf_name and pdf_name in pdf_bytes_by_name:
            entry = dict(h)
            entry["PDF_File"] = pdf_name
            entry["category"] = "new"
            score_hymns.append(entry)
            referenced_pdfs[pdf_name] = pdf_bytes_by_name[pdf_name]

    manifest = {"new": score_hymns}

    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_out = output_path.with_suffix(".acc.tmp")
    with zipfile.ZipFile(tmp_out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.writestr("hymns.json", json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8"))
        for pdf_name in sorted(referenced_pdfs.keys(), key=str.lower):
            zf.writestr(pdf_name, referenced_pdfs[pdf_name])

    os.replace(tmp_out, output_path)
    print(f"Built {output_path} ({output_path.stat().st_size / 1048576:.2f} MB)")
    print(f"  New Hymns w/ Scores : {len(score_hymns)} hymns")
    print(f"  PDF Scores          : {len(referenced_pdfs)} files")


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Build split ACC Packs (.acc)")
    parser.add_argument("--mode", choices=["both", "hymns-bibles", "scores"], default="both")
    parser.add_argument("--soc-json", type=Path, default=repo_root / "app/src/main/assets/soc_hymns.json")
    parser.add_argument("--new-json", type=Path, default=repo_root / "app/src/main/assets/new_hymns.json")
    parser.add_argument("--bible-db", type=Path, default=repo_root / "app/src/main/assets/bible_bilingual.db")
    parser.add_argument("--pdf-source", type=Path, default=repo_root / "releases/acc_musical_scores.acc")
    parser.add_argument("--out-dir", type=Path, default=repo_root / "releases")
    args = parser.parse_args()

    if args.mode in ("both", "hymns-bibles"):
        build_hymns_and_bibles_pack(
            args.soc_json, args.new_json, args.bible_db, args.out_dir / "acc_hymns_and_bibles.acc"
        )
    if args.mode in ("both", "scores"):
        build_scores_only_pack(
            args.new_json, args.pdf_source, args.out_dir / "acc_musical_scores.acc"
        )


if __name__ == "__main__":
    main()
