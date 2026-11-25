#!/usr/bin/env python3
"""Builds "The Wise Kitchen.pdf" (the printable cookbook) from data/recipes.json.

Run from the repo root:  python3 tools/build_pdf.py
    --if-stale   only rebuild when the PDF was made from different recipe data
                 (the PDF keeps the data's content hash in its keywords)
Needs: pip install reportlab pypdf   (fonts are in tools/fonts, so every machine makes the same book)
"""
import json
import re
import sys
from datetime import date
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import inch
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, CondPageBreak, Flowable, Frame, KeepTogether, NextPageTemplate, PageBreak,
    PageTemplate, Paragraph, Spacer, Table, TableStyle,
)
from reportlab.platypus.flowables import BalancedColumns

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "recipes.json"
OUT = ROOT / "The Wise Kitchen.pdf"
FONTS = Path(__file__).resolve().parent / "fonts"

ACCENT = colors.HexColor("#A6462A")
INK = colors.HexColor("#2A231E")
MUTED = colors.HexColor("#6B5D52")
PANEL = colors.HexColor("#F6EFE4")
RULE = colors.HexColor("#DCCFBE")

PAGE_W, PAGE_H = letter
MARGIN_X = 0.85 * inch
MARGIN_TOP = 0.8 * inch
MARGIN_BOTTOM = 0.9 * inch
TEXT_W = PAGE_W - 2 * MARGIN_X


# ---------------------------------------------------------------- fonts

def register_fonts():
    for name, file in [
        ("Sans", "FiraSans-Regular.ttf"), ("Sans-Italic", "FiraSans-Italic.ttf"),
        ("Sans-Medium", "FiraSans-Medium.ttf"), ("Sans-SemiBold", "FiraSans-SemiBold.ttf"),
        ("Sans-SemiBoldItalic", "FiraSans-SemiBoldItalic.ttf"), ("Sans-Light", "FiraSans-Light.ttf"),
        ("Serif", "CrimsonText-Regular.ttf"), ("Serif-Italic", "CrimsonText-Italic.ttf"),
        ("Serif-SemiBold", "CrimsonText-SemiBold.ttf"), ("Serif-Bold", "CrimsonText-Bold.ttf"),
        ("CJK", "WenQuanYiZenHei-subset.ttf"),
    ]:
        pdfmetrics.registerFont(TTFont(name, str(FONTS / file)))
    pdfmetrics.registerFontFamily("Sans", normal="Sans", bold="Sans-SemiBold", italic="Sans-Italic", boldItalic="Sans-SemiBoldItalic")
    pdfmetrics.registerFontFamily("Serif", normal="Serif", bold="Serif-Bold", italic="Serif-Italic", boldItalic="Serif-Bold")


_cmaps = {}


def has_glyph(font, ch):
    if font not in _cmaps:
        _cmaps[font] = set(pdfmetrics.getFont(font).face.charToGlyph.keys())
    return ord(ch) in _cmaps[font]


def markup(text, font="Sans"):
    """Escapes text for a Paragraph, turns **bold** into <b>, and draws characters the font
    lacks (e.g. Chinese) with a fallback font."""
    parts = re.split(r"(\*\*.+?\*\*)", text)
    out = []
    for p in parts:
        bold = p.startswith("**") and p.endswith("**") and len(p) > 4
        s = p[2:-2] if bold else p
        buf, run, missing = [], [], None
        for ch in s:
            m = not has_glyph(font, ch) and has_glyph("CJK", ch)
            if missing is None or m == missing:
                run.append(ch)
            else:
                buf.append((missing, "".join(run)))
                run = [ch]
            missing = m
        if run:
            buf.append((missing, "".join(run)))
        seg = "".join(f'<font name="CJK">{escape(r)}</font>' if m else escape(r) for m, r in buf)
        out.append(f"<b>{seg}</b>" if bold else seg)
    return "".join(out)


# ---------------------------------------------------------------- styles

def S(name, **kw):
    base = dict(fontName="Sans", fontSize=10.5, leading=14.5, textColor=INK, alignment=TA_LEFT)
    base.update(kw)
    return ParagraphStyle(name, **base)


ST = {}


def make_styles():
    ST.update(
        body=S("body"),
        note=S("note", fontName="Sans-Italic", textColor=MUTED, spaceBefore=2, spaceAfter=8),
        source=S("source", fontName="Sans-Italic", fontSize=9.5, leading=13, textColor=MUTED, spaceAfter=10),
        title=S("title", fontName="Serif-SemiBold", fontSize=26, leading=30, spaceAfter=4),
        h2=S("h2", fontName="Serif-SemiBold", fontSize=15, leading=19, spaceBefore=12, spaceAfter=4),
        group=S("group", fontName="Sans-SemiBold", fontSize=9.5, leading=13, textColor=ACCENT, spaceBefore=4),
        ing=S("ing", fontSize=10.5, leading=14),
        step=S("step", leftIndent=24, bulletIndent=0, spaceAfter=7, bulletFontName="Sans-SemiBold", bulletColor=ACCENT, bulletFontSize=10.5),
        sub=S("sub", leftIndent=40, bulletIndent=27, spaceAfter=4, bulletColor=ACCENT),
        bullet=S("bullet", leftIndent=14, bulletIndent=2, spaceAfter=5, bulletColor=ACCENT),
        cell=S("cell", fontSize=9.5, leading=12.5),
        cellhead=S("cellhead", fontName="Sans-SemiBold", fontSize=8.5, leading=11, textColor=ACCENT),
        toc_ch=S("toc_ch", fontName="Serif-SemiBold", fontSize=13, leading=17),
        toc_item=S("toc_item", fontSize=9.5, leading=12.5, leftIndent=14),
        toc_num=S("toc_num", fontSize=9.5, leading=12.5, alignment=2, textColor=MUTED),
        toc_chnum=S("toc_chnum", fontName="Serif-SemiBold", fontSize=13, leading=17, alignment=2),
        idx_letter=S("idx_letter", fontName="Serif-SemiBold", fontSize=13, leading=16, textColor=ACCENT, spaceBefore=6),
        idx=S("idx", fontSize=9, leading=12, leftIndent=10, firstLineIndent=-10),
        about=S("about", fontSize=11, leading=16, spaceAfter=10),
        blurb=S("blurb", fontName="Serif-Italic", fontSize=14, leading=19, textColor=MUTED, spaceAfter=18),
        opener_item=S("opener_item", fontSize=11, leading=15),
    )


# ---------------------------------------------------------------- flowables

class Label(Flowable):
    """A short line of spaced-out capitals (section labels like INGREDIENTS)."""

    def __init__(self, text, size=8.5, color=ACCENT, space_before=10, space_after=5, rule=False):
        super().__init__()
        self.text, self.size, self.color, self.rule = text.upper(), size, color, rule
        self.spaceBefore, self.spaceAfter = space_before, space_after

    def wrap(self, aw, ah):
        self.aw = aw
        return aw, self.size + (5 if self.rule else 1)

    def draw(self):
        c = self.canv
        c.setFont("Sans-SemiBold", self.size)
        c.setFillColor(self.color)
        c.drawString(0, (5 if self.rule else 1), self.text, charSpace=self.size * 0.22)
        if self.rule:
            c.setStrokeColor(RULE)
            c.setLineWidth(0.6)
            c.line(0, 0, self.aw, 0)


class Mark(Flowable):
    """Invisible: records the page it lands on, and which chapter the footer should name."""

    def __init__(self, key=None, chapter=None):
        super().__init__()
        self.key, self.chapter = key, chapter

    def wrap(self, aw, ah):
        return 0, 0

    def draw(self):
        doc = self.canv._doctemplate
        if self.chapter is not None:
            doc.chapter = self.chapter
        if self.key:
            doc.pages[self.key] = self.canv.getPageNumber()


# ---------------------------------------------------------------- document

class Book(BaseDocTemplate):
    def __init__(self, path, **kw):
        super().__init__(str(path), pagesize=letter, leftMargin=MARGIN_X, rightMargin=MARGIN_X,
                         topMargin=MARGIN_TOP, bottomMargin=MARGIN_BOTTOM, invariant=1, **kw)
        self.pages = {}
        self.chapter = ""
        frame = Frame(MARGIN_X, MARGIN_BOTTOM, TEXT_W, PAGE_H - MARGIN_TOP - MARGIN_BOTTOM, id="f", leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
        self.addPageTemplates([
            PageTemplate("plain", [frame]),
            PageTemplate("body", [frame], onPage=self.footer),
        ])

    def footer(self, c, doc):
        c.saveState()
        y = MARGIN_BOTTOM - 0.45 * inch
        c.setStrokeColor(RULE)
        c.setLineWidth(0.5)
        c.line(MARGIN_X, y + 12, PAGE_W - MARGIN_X, y + 12)
        c.setFont("Sans-SemiBold", 7.5)
        c.setFillColor(MUTED)
        if self.chapter:
            c.drawString(MARGIN_X, y, self.chapter.upper(), charSpace=1.6)
        c.setFont("Sans", 9)
        c.drawRightString(PAGE_W - MARGIN_X, y, str(c.getPageNumber()))
        c.restoreState()


def nice_date(iso):
    try:
        d = date.fromisoformat(iso)
        return d.strftime("%B ") + str(d.day) + d.strftime(", %Y")
    except Exception:
        return iso or ""


def counts(book):
    recipes = sum(1 for c in book["chapters"] for i in c["items"] if i["kind"] == "recipe")
    guides = sum(1 for c in book["chapters"] for i in c["items"] if i["kind"] == "guide")
    return recipes, guides


def cover(book):
    recipes, guides = counts(book)
    title = S("cover", fontName="Serif-SemiBold", fontSize=52, leading=58, alignment=TA_CENTER)
    sub = S("coversub", fontName="Serif-Italic", fontSize=18, leading=24, alignment=TA_CENTER, textColor=MUTED)
    small = S("coversmall", fontSize=10, leading=14, alignment=TA_CENTER, textColor=MUTED)
    return [
        Spacer(1, 2.6 * inch),
        Paragraph(markup(book["title"], "Serif-SemiBold"), title),
        Spacer(1, 14),
        Paragraph("Recipes and kitchen notes", sub),
        Spacer(1, 3.2 * inch),
        Paragraph(f"{recipes} recipes · {guides} kitchen guides", small),
        Paragraph(f"Updated {escape(nice_date(book.get('updated')))}", small),
        NextPageTemplate("body"),
        PageBreak(),
    ]


def contents(book, pages):
    story = [Mark(chapter=""), Paragraph("Contents", ST["title"]), Spacer(1, 10)]
    rows, style = [], [
        ("VALIGN", (0, 0), (-1, -1), "BOTTOM"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0), ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 1.2), ("BOTTOMPADDING", (0, 0), (-1, -1), 1.2),
    ]
    for ch in book["chapters"]:
        r = len(rows)
        rows.append([Paragraph(f'<font color="#A6462A">{ch["number"]:02d}</font>&nbsp;&nbsp;{markup(ch["title"], "Serif-SemiBold")}', ST["toc_ch"]),
                     Paragraph(str(pages.get("ch:" + ch["id"], "")), ST["toc_chnum"])])
        style.append(("TOPPADDING", (0, r), (-1, r), 9))
        for it in ch["items"]:
            rows.append([Paragraph(markup(it["title"]), ST["toc_item"]), Paragraph(str(pages.get(it["id"], "")), ST["toc_num"])])
    r = len(rows)
    rows.append([Paragraph("Index", ST["toc_ch"]), Paragraph(str(pages.get("index", "")), ST["toc_chnum"])])
    style.append(("TOPPADDING", (0, r), (-1, r), 9))
    t = Table(rows, colWidths=[TEXT_W - 40, 40], style=TableStyle(style), splitByRow=1)
    story += [t, PageBreak()]
    return story


def about(book):
    story = [Paragraph("About this book", ST["title"]), Spacer(1, 10)]
    for p in book.get("about", []):
        story.append(Paragraph(markup(p), ST["about"]))
    story.append(PageBreak())
    return story


def chapter_opener(ch, pages):
    num = S("chnum", fontName="Serif-SemiBold", fontSize=64, leading=66, textColor=ACCENT)
    name = S("chname", fontName="Serif-SemiBold", fontSize=38, leading=44, spaceAfter=12)
    rows = [[Paragraph(markup(it["title"]), ST["opener_item"]), Paragraph(str(pages.get(it["id"], "")), ST["toc_num"])] for it in ch["items"]]
    t = Table(rows, colWidths=[TEXT_W - 40, 40], style=TableStyle([
        ("LEFTPADDING", (0, 0), (-1, -1), 0), ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
    ]))
    return [
        Mark(key="ch:" + ch["id"], chapter=ch["title"]),
        Spacer(1, 1.3 * inch),
        Paragraph(f"{ch['number']:02d}", num),
        Paragraph(markup(ch["title"], "Serif-SemiBold"), name),
        Paragraph(markup(ch["blurb"], "Serif-Italic"), ST["blurb"]) if ch.get("blurb") else Spacer(1, 1),
        t,
        PageBreak(),
    ]


def facts_table(it):
    facts = [(k.upper(), it[k]) for k in ("serves", "makes", "time", "temp") if it.get(k)]
    if not facts:
        return None
    lab = S("factlab", fontName="Sans-SemiBold", fontSize=7.5, leading=10, textColor=ACCENT)
    val = S("factval", fontSize=10, leading=13)
    rows = [[Paragraph(" ".join(k), lab), Paragraph(markup(v), val)] for k, v in facts]
    return Table(rows, colWidths=[0.95 * inch, TEXT_W - 0.95 * inch], style=TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PANEL),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 10), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, 0), 8), ("BOTTOMPADDING", (0, -1), (-1, -1), 8),
        ("LEFTPADDING", (1, 0), (1, -1), 0),
    ]))


def ingredients_table(groups):
    items = [i for g in groups for i in g["items"]]
    two = len(items) >= 8 and max(len(i) for i in items) <= 48
    half = (TEXT_W - 20) / 2
    rows, style = [], [
        ("BACKGROUND", (0, 0), (-1, -1), PANEL),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 10), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 1.5), ("BOTTOMPADDING", (0, 0), (-1, -1), 1.5),
    ]
    for g in groups:
        if g.get("title"):
            r = len(rows)
            rows.append([Paragraph(markup(g["title"]).upper() if len(g["title"]) < 40 else markup(g["title"]), ST["group"])] + ([""] if two else []))
            if two:
                style.append(("SPAN", (0, r), (1, r)))
            style.append(("TOPPADDING", (0, r), (-1, r), 6))
        its = g["items"]
        if two:
            n = (len(its) + 1) // 2
            for a in range(n):
                b = a + n
                rows.append([Paragraph(markup(its[a]), ST["ing"]), Paragraph(markup(its[b]), ST["ing"]) if b < len(its) else ""])
        else:
            rows += [[Paragraph(markup(i), ST["ing"])] for i in its]
    style += [("TOPPADDING", (0, 0), (-1, 0), 9), ("BOTTOMPADDING", (0, -1), (-1, -1), 9)]
    return Table(rows, colWidths=[half + 10, half + 10] if two else [TEXT_W], style=TableStyle(style), splitByRow=1)


def recipe(it, ch):
    story = [Mark(key=it["id"], chapter=ch["title"]), Label(ch["title"], space_before=0, space_after=6),
             Paragraph(markup(it["title"], "Serif-SemiBold"), ST["title"])]
    if it.get("source"):
        s = it["source"]
        story.append(Paragraph("Adapted from " + markup(s["name"]) + (f" · {escape(s['site'])}" if s.get("site") else ""), ST["source"]))
    ft = facts_table(it)
    if ft:
        story += [ft, Spacer(1, 8)]
    if it.get("note"):
        story.append(Paragraph(markup(it["note"], "Sans-Italic"), ST["note"]))
    story += [Label("Ingredients", rule=True, space_before=8, space_after=6), ingredients_table(it["ingredients"])]
    story.append(Label("Method", rule=True, space_before=16, space_after=8))
    for g in it["method"]:
        if g.get("title"):
            story.append(Paragraph(markup(g["title"]).upper(), S("mg", parent=ST["group"], spaceBefore=6, spaceAfter=4)))
        for n, step in enumerate(g["steps"], 1):
            paras = [Paragraph(markup(step["text"]), ST["step"], bulletText=str(n))]
            paras += [Paragraph(markup(s), ST["sub"], bulletText="–") for s in step.get("sub", [])]
            story.append(KeepTogether(paras) if len(paras) > 1 else paras[0])
    if it.get("tips"):
        story.append(Label("Tips", rule=True, space_before=12, space_after=8))
        story += [Paragraph(markup(t), ST["bullet"], bulletText="•") for t in it["tips"]]
    story.append(PageBreak())
    return story


def guide_table(b):
    ncol = len(b["header"])
    if ncol == 2:
        widths = [TEXT_W * 0.38, TEXT_W * 0.62]
    else:
        first = TEXT_W * (0.28 if ncol <= 3 else 0.2)
        widths = [first] + [(TEXT_W - first) / (ncol - 1)] * (ncol - 1)
    rows = [[Paragraph(markup(h).upper(), ST["cellhead"]) for h in b["header"]]]
    rows += [[Paragraph(("<b>%s</b>" if i == 0 else "%s") % markup(c), ST["cell"]) for i, c in enumerate(r)] for r in b["rows"]]
    return Table(rows, colWidths=widths, repeatRows=1, style=TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), PANEL),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))


def guide(it, ch):
    story = [Mark(key=it["id"], chapter=ch["title"]), Label(ch["title"], space_before=0, space_after=6),
             Paragraph(markup(it["title"], "Serif-SemiBold"), ST["title"])]
    if it.get("source"):
        s = it["source"]
        story.append(Paragraph("From " + markup(s["name"]) + (f" · {escape(s['site'])}" if s.get("site") else ""), ST["source"]))
    if it.get("note"):
        story.append(Paragraph(markup(it["note"], "Sans-Italic"), ST["note"]))
    for sec in it["sections"]:
        if sec.get("title"):
            story += [CondPageBreak(1.2 * inch), Label(sec["title"], rule=True, space_before=14, space_after=8)]
        for b in sec["blocks"]:
            t = b["type"]
            if t == "p":
                story.append(Paragraph(markup(b["text"]), S("gp", parent=ST["body"], spaceAfter=7)))
            elif t == "h":
                story.append(Paragraph(markup(b["text"]), S("gh", parent=ST["group"], fontSize=10, spaceBefore=6, spaceAfter=4)))
            elif t == "bullets":
                story += [Paragraph(markup(x), ST["bullet"], bulletText="•") for x in b["items"]]
            elif t == "steps":
                story += [Paragraph(markup(x), ST["step"], bulletText=str(n)) for n, x in enumerate(b["items"], 1)]
            elif t == "table":
                story += [guide_table(b), Spacer(1, 8)]
    story.append(PageBreak())
    return story


def index(book, pages):
    items = sorted((it for c in book["chapters"] for it in c["items"]), key=lambda i: re.sub(r"^the ", "", i["title"].lower()))
    flow, letter_now = [], None
    for it in items:
        key = re.sub(r"^the ", "", it["title"].lower())
        L = key[0].upper() if key[0].isalpha() else "#"
        if L != letter_now:
            letter_now = L
            flow.append(Paragraph(L, ST["idx_letter"]))
        flow.append(Paragraph(f'{markup(it["title"])}&nbsp;&nbsp;<font color="#6B5D52">{pages.get(it["id"], "")}</font>', ST["idx"]))
    return [Mark(key="index", chapter="Index"), Paragraph("Index", ST["title"]), Spacer(1, 6), BalancedColumns(flow, nCols=2, spaceBefore=0, innerPadding=14)]


def story_for(book, pages):
    story = cover(book) + contents(book, pages) + about(book)
    for ch in book["chapters"]:
        story += chapter_opener(ch, pages)
        for it in ch["items"]:
            story += recipe(it, ch) if it["kind"] == "recipe" else guide(it, ch)
    story += index(book, pages)
    return story


def build(path=OUT):
    register_fonts()
    make_styles()
    book = json.loads(DATA.read_text(encoding="utf-8"))
    recipes, guides = counts(book)
    pages = {}
    # Page numbers in the contents and index come from the previous pass; repeat until they settle.
    for _ in range(4):
        doc = Book(path, title=book["title"], author="Jonathan Wise",
                   subject=f"{recipes} recipes and {guides} kitchen guides",
                   keywords=f"contentHash={book['contentHash']} version={book['version']}",
                   creator="tools/build_pdf.py")
        doc.build(story_for(book, pages))
        if doc.pages == pages:
            break
        pages = dict(doc.pages)
    return book, len(set(pages.values()))


def pdf_hash(path=OUT):
    try:
        from pypdf import PdfReader
        kw = (PdfReader(str(path)).metadata or {}).get("/Keywords", "") or ""
        m = re.search(r"contentHash=(\w+)", str(kw))
        return m.group(1) if m else None
    except Exception:
        return None


def main():
    if "--if-stale" in sys.argv:
        want = json.loads(DATA.read_text(encoding="utf-8"))["contentHash"]
        if OUT.exists() and pdf_hash() == want:
            print("The Wise Kitchen.pdf is up to date.")
            return
    book, _ = build()
    from pypdf import PdfReader
    n = len(PdfReader(str(OUT)).pages)
    print(f"Built The Wise Kitchen.pdf: {n} pages (recipes version {book['version']})")


if __name__ == "__main__":
    main()
