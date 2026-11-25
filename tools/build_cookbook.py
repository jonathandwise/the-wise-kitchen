#!/usr/bin/env python3
"""Builds data/recipes.json (what the app shows) from the plain-text files in cookbook/.

Run from the repo root:  python3 tools/build_cookbook.py
Add --check to only validate (exit 1 if recipes.json is out of date or a file has a mistake).

The data version only changes when the content changes, so installed apps download it only then.
See cookbook/FORMAT.md for how the text files are written.
"""
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "cookbook"
OUT = ROOT / "data" / "recipes.json"
SCHEMA = 1

META_KEYS = {"serves", "makes", "time", "temp", "from", "note", "kind"}


class BookError(Exception):
    pass


def slug(text):
    s = text.lower().replace("&", "and")
    s = re.sub(r"[’'`]", "", s)
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s


def parse_source(value, where):
    if "|" in value:
        name, site = (p.strip() for p in value.split("|", 1))
    else:
        name, site = value.strip(), None
    if not name:
        raise BookError(f"{where}: empty 'from:'")
    return {"name": name, "site": site or None}


def new_item(title, chapter, where):
    return {
        "_where": where,
        "title": title,
        "chapter": chapter,
        "kind": "recipe",
        "meta": {},
        "ingredients": [],
        "method": [],
        "tips": [],
        "sections": [],
        "_mode": None,
    }


def parse_file(path, book):
    chapter = None
    item = None
    lines = path.read_text(encoding="utf-8").splitlines()
    for n, raw in enumerate(lines, 1):
        where = f"{path.name}:{n}"
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("#book "):
            book["title"] = line[6:].strip()
            continue
        if line.startswith("#about "):
            book["about"].append(line[7:].strip())
            continue
        if line.startswith("#chapter "):
            chapter = {"title": line[9:].strip(), "blurb": "", "items": []}
            book["chapters"].append(chapter)
            item = None
            continue
        if line.startswith("#blurb "):
            if chapter is None:
                raise BookError(f"{where}: #blurb before #chapter")
            chapter["blurb"] = line[7:].strip()
            continue
        if line.startswith("= "):
            if chapter is None:
                raise BookError(f"{where}: recipe before any #chapter")
            item = new_item(line[2:].strip(), chapter, where)
            chapter["items"].append(item)
            continue
        if item is None:
            raise BookError(f"{where}: text outside a recipe: {line!r}")

        m = re.fullmatch(r"\[(Ingredients|Method|Tips|Section)(?::\s*(.+))?\]", line.strip())
        if m:
            kind, title = m.group(1), (m.group(2) or "").strip() or None
            item["_mode"] = kind
            if kind == "Ingredients":
                item["ingredients"].append({"title": title, "items": []})
            elif kind == "Method":
                item["method"].append({"title": title, "steps": []})
            elif kind == "Section":
                item["sections"].append({"title": title, "blocks": []})
            elif title:
                raise BookError(f"{where}: [Tips] can't have a title")
            continue

        mode = item["_mode"]
        if mode is None:
            m = re.match(r"([a-z]+):\s*(.*)$", line)
            if not m or m.group(1) not in META_KEYS:
                raise BookError(f"{where}: expected a detail line like 'serves: 4', got {line!r}")
            key, value = m.group(1), m.group(2).strip()
            if key == "kind":
                if value not in ("recipe", "guide"):
                    raise BookError(f"{where}: kind must be recipe or guide")
                item["kind"] = value
            else:
                item["meta"][key] = value
            continue

        if mode == "Section":
            blocks = item["sections"][-1]["blocks"]
            add_guide_line(blocks, line, where)
            continue

        sub = re.match(r"\s{2,}- (.+)$", line)
        if sub:
            if mode != "Method" or not item["method"][-1]["steps"]:
                raise BookError(f"{where}: an indented '- ' line must follow a method step")
            item["method"][-1]["steps"][-1]["sub"].append(sub.group(1).strip())
            continue
        if not line.startswith("- "):
            raise BookError(f"{where}: lines under [{mode}] start with '- ', got {line!r}")
        text = line[2:].strip()
        if mode == "Ingredients":
            item["ingredients"][-1]["items"].append(text)
        elif mode == "Method":
            item["method"][-1]["steps"].append({"text": text, "sub": []})
        elif mode == "Tips":
            item["tips"].append(text)


def add_guide_line(blocks, line, where):
    def last(kind):
        return blocks[-1] if blocks and blocks[-1]["type"] == kind else None

    if line.startswith("p: "):
        blocks.append({"type": "p", "text": line[3:].strip()})
    elif line.startswith("h: "):
        blocks.append({"type": "h", "text": line[3:].strip()})
    elif line.startswith("- "):
        b = last("bullets") or {"type": "bullets", "items": []}
        if b not in blocks:
            blocks.append(b)
        b["items"].append(line[2:].strip())
    elif line.startswith("n: "):
        b = last("steps") or {"type": "steps", "items": []}
        if b not in blocks:
            blocks.append(b)
        b["items"].append(line[3:].strip())
    elif line.startswith("|! "):
        cells = [c.strip() for c in line[3:].split("|")]
        blocks.append({"type": "table", "header": cells, "rows": []})
    elif line.startswith("| "):
        t = last("table")
        if t is None:
            raise BookError(f"{where}: table row without a '|!' header row above it")
        cells = [c.strip() for c in line[2:].split("|")]
        if len(cells) != len(t["header"]):
            raise BookError(f"{where}: row has {len(cells)} cells, header has {len(t['header'])}")
        t["rows"].append(cells)
    else:
        raise BookError(f"{where}: unknown guide line {line!r} (use p:, h:, - , n:, |! or |)")


def finish(book, previous_added, today):
    chapters_out = []
    seen = set()
    for ci, ch in enumerate(book["chapters"], 1):
        items_out = []
        for it in ch["items"]:
            where = it["_where"]
            iid = slug(it["title"])
            if iid in seen:
                raise BookError(f"{where}: two recipes are called {it['title']!r}")
            seen.add(iid)
            meta = it["meta"]
            out = {
                "id": iid,
                "kind": it["kind"],
                "title": it["title"],
                "added": previous_added.get(iid, today),
            }
            for k in ("serves", "makes", "time", "temp"):
                if k in meta:
                    out[k] = meta[k]
            if "from" in meta:
                out["source"] = parse_source(meta["from"], where)
            if "note" in meta:
                out["note"] = meta["note"]
            if it["kind"] == "recipe":
                if it["sections"]:
                    raise BookError(f"{where}: [Section] is only for guides (add 'kind: guide')")
                if not any(g["items"] for g in it["ingredients"]):
                    raise BookError(f"{where}: {it['title']} has no ingredients")
                if not any(g["steps"] for g in it["method"]):
                    raise BookError(f"{where}: {it['title']} has no method steps")
                for g in it["ingredients"]:
                    if not g["items"]:
                        raise BookError(f"{where}: empty ingredient group {g['title']!r}")
                for g in it["method"]:
                    if not g["steps"]:
                        raise BookError(f"{where}: empty method group {g['title']!r}")
                out["ingredients"] = [{"title": g["title"], "items": g["items"]} for g in it["ingredients"]]
                out["method"] = [
                    {"title": g["title"], "steps": [
                        ({"text": s["text"], "sub": s["sub"]} if s["sub"] else {"text": s["text"]}) for s in g["steps"]
                    ]}
                    for g in it["method"]
                ]
                out["tips"] = it["tips"]
            else:
                if it["ingredients"] or it["method"] or it["tips"]:
                    raise BookError(f"{where}: guides use [Section] blocks, not ingredients/method/tips")
                if not it["sections"]:
                    raise BookError(f"{where}: guide {it['title']} has no [Section]")
                out["sections"] = it["sections"]
            items_out.append(out)
        if not items_out:
            raise BookError(f"chapter {ch['title']!r} has no recipes")
        chapters_out.append({
            "id": slug(ch["title"]),
            "number": ci,
            "title": ch["title"],
            "blurb": ch["blurb"],
            "items": items_out,
        })
    return chapters_out


def build():
    book = {"title": "The Wise Kitchen", "about": [], "chapters": []}
    files = sorted(SRC.glob("*.txt"))
    if not files:
        raise BookError("no cookbook/*.txt files")
    for f in files:
        parse_file(f, book)

    previous = None
    previous_added = {}
    if OUT.exists():
        previous = json.loads(OUT.read_text(encoding="utf-8"))
        for ch in previous.get("chapters", []):
            for it in ch.get("items", []):
                if "added" in it:
                    previous_added[it["id"]] = it["added"]

    now = dt.datetime.now(dt.timezone.utc)
    chapters = finish(book, previous_added, now.strftime("%Y-%m-%d"))
    content = {"title": book["title"], "about": book["about"], "chapters": chapters}
    digest = hashlib.sha256(json.dumps(content, ensure_ascii=False, sort_keys=True).encode()).hexdigest()

    if previous and previous.get("contentHash") == digest:
        version = previous["version"]
        updated = previous.get("updated")
    else:
        version = int(now.strftime("%Y%m%d%H%M"))
        if previous and version <= previous.get("version", 0):
            version = previous["version"] + 1
        updated = now.strftime("%Y-%m-%d")

    data = {
        "schema": SCHEMA,
        "version": version,
        "updated": updated,
        "contentHash": digest,
        **content,
    }
    return data, previous


def main():
    check = "--check" in sys.argv
    try:
        data, previous = build()
    except BookError as e:
        print(f"Cookbook error: {e}", file=sys.stderr)
        sys.exit(1)
    recipes = sum(1 for c in data["chapters"] for i in c["items"] if i["kind"] == "recipe")
    guides = sum(1 for c in data["chapters"] for i in c["items"] if i["kind"] == "guide")
    text = json.dumps(data, ensure_ascii=False, indent=1) + "\n"
    if check:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print("data/recipes.json is out of date: run python3 tools/build_cookbook.py", file=sys.stderr)
            sys.exit(1)
        print(f"OK: {len(data['chapters'])} chapters, {recipes} recipes, {guides} guides (version {data['version']})")
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    changed = not previous or previous.get("contentHash") != data["contentHash"]
    print(f"{'Updated' if changed else 'No changes:'} data/recipes.json: {len(data['chapters'])} chapters, "
          f"{recipes} recipes, {guides} guides (version {data['version']})")
    # Keep the printable cookbook in step (GitHub Actions also rebuilds it if this is skipped).
    try:
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import build_pdf
    except ImportError as e:
        print(f"PDF not rebuilt here ({e}); GitHub will rebuild The Wise Kitchen.pdf after the push.")
        return
    if build_pdf.OUT.exists() and build_pdf.pdf_hash() == data["contentHash"]:
        print("The Wise Kitchen.pdf is up to date.")
    else:
        build_pdf.main()


if __name__ == "__main__":
    main()
