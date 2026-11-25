#!/usr/bin/env python3
"""Minimal Markdown -> PDF converter (reportlab) for the ESS submission documents.

Supports exactly what the documents use: #/##/### headings, paragraphs, "- " bullet lists, pipe tables,
fenced code blocks, images on their own line, horizontal rules, and inline `code`, **bold**, *italic* and
[links](url).

Usage: python3 md2pdf.py input.md output.pdf
"""
import os
import re
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    Image,
    KeepTogether,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.lib.fonts import addMapping

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
for name, file in [
    ("Sans", "DejaVuSans.ttf"),
    ("Sans-Bold", "DejaVuSans-Bold.ttf"),
    ("Sans-Oblique", "DejaVuSans-Oblique.ttf"),
    ("Sans-BoldOblique", "DejaVuSans-BoldOblique.ttf"),
    ("Mono", "DejaVuSansMono.ttf"),
    ("Mono-Bold", "DejaVuSansMono-Bold.ttf"),
]:
    pdfmetrics.registerFont(TTFont(name, os.path.join(FONT_DIR, file)))
addMapping("Sans", 0, 0, "Sans")
addMapping("Sans", 1, 0, "Sans-Bold")
addMapping("Sans", 0, 1, "Sans-Oblique")
addMapping("Sans", 1, 1, "Sans-BoldOblique")
addMapping("Mono", 0, 0, "Mono")
addMapping("Mono", 1, 0, "Mono-Bold")
addMapping("Mono", 0, 1, "Mono")
addMapping("Mono", 1, 1, "Mono-Bold")

PAGE_W, PAGE_H = A4
MARGIN = 18 * mm
CONTENT_W = PAGE_W - 2 * MARGIN

ACCENT = colors.HexColor("#1f4e79")
RULE = colors.HexColor("#c8ccd2")
CODE_BG = colors.HexColor("#f4f5f7")
HEAD_BG = colors.HexColor("#e8ecf1")

body = ParagraphStyle("body", fontName="Sans", fontSize=9.5, leading=13.5, spaceAfter=5, alignment=TA_LEFT)
bullet = ParagraphStyle("bullet", parent=body, leftIndent=12, bulletIndent=2, spaceAfter=3)
cell = ParagraphStyle("cell", parent=body, fontSize=8, leading=10.5, spaceAfter=0)
cell_head = ParagraphStyle("cellhead", parent=cell, fontName="Sans-Bold")
code = ParagraphStyle("code", fontName="Mono", fontSize=7.6, leading=9.8, textColor=colors.HexColor("#1b1f24"))
h1 = ParagraphStyle("h1", parent=body, fontName="Sans-Bold", fontSize=18, leading=22, spaceAfter=8, textColor=ACCENT)
h2 = ParagraphStyle(
    "h2", parent=body, fontName="Sans-Bold", fontSize=13.5, leading=17, spaceBefore=6, spaceAfter=6, textColor=ACCENT
)
h3 = ParagraphStyle("h3", parent=body, fontName="Sans-Bold", fontSize=11, leading=14, spaceBefore=6, spaceAfter=4)
for _s in (h1, h2, h3):
    _s.keepWithNext = 1
label = ParagraphStyle("label", parent=body, keepWithNext=1)  # e.g. "**Prečo:**" introducing a list


def inline(text):
    """Markdown inline -> reportlab mini-HTML."""
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    codes = []

    def keep_code(m):
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", keep_code, text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![*\w])\*([^*\s][^*]*?)\*(?![*\w])", r"<i>\1</i>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<link href="\2" color="#1f4e79"><u>\1</u></link>', text)
    text = re.sub(
        r"\x00(\d+)\x00", lambda m: f'<font face="Mono" color="#8a1538">{codes[int(m.group(1))]}</font>', text
    )
    return text


def split_row(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def column_widths(rows, ncols, pad=9):
    """Each column gets its longest word (capped, long identifiers may wrap); the remaining width is shared by
    how much text the column holds on average, so descriptive columns get the space."""
    plain = lambda t: re.sub(r"[`*]", "", t)
    width = lambda t, font: pdfmetrics.stringWidth(t, font, cell.fontSize)
    mins, grow = [], []
    for i in range(ncols):
        col = [(plain(r[i]) if i < len(r) else "", k == 0) for k, r in enumerate(rows)]
        longest = max(
            max(width(w, "Sans-Bold" if head else "Sans"), 0 if head else width(w, "Mono"))
            for t, head in col
            for w in ([t] if len(t) <= 14 else t.split() or [""])  # short cells like "55,7 %" never wrap
        )
        mins.append(min(longest + pad, CONTENT_W * 0.25))
        body_cells = [t for t, head in col if not head] or [""]
        mean = sum(width(t, "Mono") for t in body_cells) / len(body_cells)
        grow.append(max(0.0, mean + pad - mins[-1]))
    spare = CONTENT_W - sum(mins)
    if spare <= 0:
        return [m * CONTENT_W / sum(mins) for m in mins]
    if sum(grow) == 0:
        return [m + spare / ncols for m in mins]
    return [m + spare * g / sum(grow) for m, g in zip(mins, grow)]


def make_table(rows):
    header, data = rows[0], rows[2:]
    ncols = len(header)
    widths = column_widths([header] + data, ncols)
    table_rows = [[Paragraph(inline(c), cell_head) for c in header]]
    for r in data:
        r = r + [""] * (ncols - len(r))
        table_rows.append([Paragraph(inline(c), cell) for c in r[:ncols]])
    t = Table(table_rows, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), HEAD_BG),
                ("LINEBELOW", (0, 0), (-1, 0), 0.8, ACCENT),
                ("LINEBELOW", (0, 1), (-1, -1), 0.3, RULE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 4),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                ("TOPPADDING", (0, 0), (-1, -1), 3),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
            ]
        )
    )
    return t


def make_code(lines):
    chars_per_line = int((CONTENT_W - 14) / pdfmetrics.stringWidth("M", "Mono", code.fontSize))
    pre = Preformatted(
        "\n".join(lines), code, maxLineLength=chars_per_line, splitChars=" ,/'", newLineChars="    "
    )
    t = Table([[pre]], colWidths=[CONTENT_W], hAlign="LEFT")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), CODE_BG),
                ("BOX", (0, 0), (-1, -1), 0.4, RULE),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    return t


def make_image(path, base_dir):
    full = os.path.join(base_dir, path)
    img = Image(full)
    scale = min(1.0, CONTENT_W / img.imageWidth)
    img.drawWidth = img.imageWidth * scale
    img.drawHeight = img.imageHeight * scale
    img.hAlign = "LEFT"
    return img


def convert(md_path, pdf_path):
    base_dir = os.path.dirname(os.path.abspath(md_path))
    lines = open(md_path, encoding="utf-8").read().splitlines()
    story = []
    title = "Dokument"
    para = []
    def flush_para():
        if para:
            text = " ".join(para)
            p = Paragraph(inline(text), label if re.fullmatch(r"\*\*[^*]+:\*\*", text) else body)
            para.clear()
            story.append(p)

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("```"):
            flush_para()
            j = i + 1
            block = []
            while j < len(lines) and not lines[j].strip().startswith("```"):
                block.append(lines[j])
                j += 1
            story.append(make_code(block))
            story.append(Spacer(1, 6))
            i = j + 1
            continue
        if not stripped:
            flush_para()
            i += 1
            continue
        if stripped == "---":
            flush_para()
            story.append(Spacer(1, 4))
            story.append(HRFlowable(width="100%", thickness=0.6, color=RULE, spaceBefore=2, spaceAfter=8))
            i += 1
            continue
        m = re.match(r"^(#{1,3}) (.*)$", stripped)
        if m:
            flush_para()
            level = len(m.group(1))
            if level == 1:
                title = m.group(2)
            story.append(Paragraph(inline(m.group(2)), {1: h1, 2: h2, 3: h3}[level]))
            i += 1
            continue
        m = re.match(r"^!\[[^\]]*\]\(([^)]+)\)$", stripped)
        if m:
            caption = None
            if para:
                caption = Paragraph(inline(" ".join(para)), body)
                para.clear()
            elif story and isinstance(story[-1], Paragraph) and story[-1].style is body:
                caption = story.pop()
            img = make_image(m.group(1), base_dir)
            story.append(KeepTogether([caption, img, Spacer(1, 8)] if caption else [img, Spacer(1, 8)]))
            i += 1
            continue
        if stripped.startswith("|"):
            flush_para()
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            story.append(make_table(rows))
            story.append(Spacer(1, 7))
            continue
        if stripped.startswith("- "):
            flush_para()
            item = [stripped[2:]]
            i += 1
            while i < len(lines) and lines[i].startswith("  ") and lines[i].strip() and not lines[i].strip().startswith("- "):
                item.append(lines[i].strip())
                i += 1
            story.append(Paragraph(inline(" ".join(item)), bullet, bulletText="•"))
            continue
        para.append(stripped)
        i += 1
    flush_para()

    def on_page(canvas, doc):
        canvas.saveState()
        canvas.setFont("Sans", 7.5)
        canvas.setFillColor(colors.HexColor("#6b7280"))
        canvas.drawString(MARGIN, 10 * mm, title)
        canvas.drawRightString(PAGE_W - MARGIN, 10 * mm, f"{doc.page}")
        canvas.restoreState()

    doc = SimpleDocTemplate(
        pdf_path,
        pagesize=A4,
        leftMargin=MARGIN,
        rightMargin=MARGIN,
        topMargin=16 * mm,
        bottomMargin=18 * mm,
        title=title,
        author="Adam Mojžiš",
    )
    doc.build(story, onFirstPage=on_page, onLaterPages=on_page)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    convert(sys.argv[1], sys.argv[2])
