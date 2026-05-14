#!/usr/bin/env python3
"""Render every markdown deliverable in the integration package to PDF.

Strategy: parse markdown via the `markdown` library to HTML, then walk
the HTML tree and emit Platypus `Flowable`s for ReportLab. We don't use
a full HTML→PDF pipeline (weasyprint / wkhtmltopdf) because neither is
installed and reportlab is reliable, fast, and gives us full control
over Arabic/English typography in the deliverable.

Output: one .pdf per .md alongside the source (e.g.
`01-Product-Blueprint/product-blueprint.pdf`).

Bilingual concerns deferred: outputs are English-primary (matching the
spec docs). Arabic text in tables/inline renders via the same font
stack but right-to-left layout per paragraph isn't applied — that's a
known limitation of pure-reportlab and acceptable for a developer
hand-off deck.
"""
from __future__ import annotations
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

import markdown
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak,
    Table, TableStyle, ListFlowable, ListItem, KeepTogether,
)


# ─── Style sheet ──────────────────────────────────────────────────────────
PRIMARY = colors.HexColor('#062E2E')
ACCENT  = colors.HexColor('#1F5454')
SUBTLE  = colors.HexColor('#F0F2F4')
INK_1   = colors.HexColor('#1A1F23')
INK_3   = colors.HexColor('#4A5258')
BORDER  = colors.HexColor('#D6DADD')

styles = getSampleStyleSheet()
H1 = ParagraphStyle('H1', parent=styles['Heading1'],
                     fontName='Helvetica-Bold', fontSize=22, leading=28,
                     spaceBefore=18, spaceAfter=10, textColor=PRIMARY)
H2 = ParagraphStyle('H2', parent=styles['Heading2'],
                     fontName='Helvetica-Bold', fontSize=15, leading=20,
                     spaceBefore=14, spaceAfter=6, textColor=PRIMARY)
H3 = ParagraphStyle('H3', parent=styles['Heading3'],
                     fontName='Helvetica-Bold', fontSize=12, leading=16,
                     spaceBefore=10, spaceAfter=4, textColor=ACCENT)
H4 = ParagraphStyle('H4', parent=styles['Heading4'],
                     fontName='Helvetica-Bold', fontSize=11, leading=14,
                     spaceBefore=8, spaceAfter=3, textColor=ACCENT)
BODY = ParagraphStyle('Body', parent=styles['Normal'],
                       fontName='Helvetica', fontSize=10, leading=14,
                       spaceAfter=6, textColor=INK_1, alignment=TA_LEFT)
CODE = ParagraphStyle('Code', parent=styles['Code'],
                       fontName='Courier', fontSize=8.5, leading=11,
                       textColor=INK_1, backColor=SUBTLE,
                       leftIndent=4, rightIndent=4,
                       spaceBefore=4, spaceAfter=4)
TABLE_CELL_H = ParagraphStyle('TableHead', parent=styles['Normal'],
                               fontName='Helvetica-Bold', fontSize=8.5,
                               leading=11, textColor=colors.white,
                               alignment=TA_LEFT)
TABLE_CELL = ParagraphStyle('TableCell', parent=styles['Normal'],
                             fontName='Helvetica', fontSize=8.5,
                             leading=11, textColor=INK_1, alignment=TA_LEFT)
COVER_TITLE = ParagraphStyle('CoverTitle', parent=styles['Title'],
                              fontName='Helvetica-Bold', fontSize=28,
                              leading=34, textColor=PRIMARY,
                              alignment=TA_CENTER)
COVER_SUB = ParagraphStyle('CoverSub', parent=styles['Normal'],
                            fontName='Helvetica-Oblique', fontSize=12,
                            leading=16, textColor=INK_3, alignment=TA_CENTER)


def _strip(tag: str) -> str:
    return re.sub(r'<[^>]+>', '', tag)


def _inline_to_reportlab(html: str) -> str:
    """Convert inline HTML markup to reportlab paragraph markup."""
    out = html
    # markdown emits <strong>, <em>, <code>, <a>; map to reportlab tags.
    out = re.sub(r'<strong>(.+?)</strong>', r'<b>\1</b>', out, flags=re.DOTALL)
    out = re.sub(r'<em>(.+?)</em>', r'<i>\1</i>', out, flags=re.DOTALL)
    out = re.sub(r'<code>(.+?)</code>',
                  r'<font name="Courier" size="9" backColor="#F0F2F4">\1</font>',
                  out, flags=re.DOTALL)
    # links — keep text + add (url)
    out = re.sub(r'<a href="([^"]+)">(.+?)</a>',
                  r'<link href="\1" color="#1F5454">\2</link>', out,
                  flags=re.DOTALL)
    return out


class MdHtmlToFlowables(HTMLParser):
    """Walk markdown-derived HTML and build a list of reportlab flowables.

    Supports: h1..h6, p, ul/ol/li, blockquote, code fences, hr, table.
    """

    def __init__(self):
        super().__init__()
        self.flowables: list = []
        self._buf: list[str] = []
        self._mode_stack: list[str] = []
        self._list_stack: list[list] = []
        self._list_kind_stack: list[str] = []
        # Table state
        self._in_table = False
        self._rows: list[list[str]] = []
        self._row: list[str] = []
        self._in_cell = False
        self._cell_buf: list[str] = []
        self._cell_is_header = False
        self._row_is_header = False

    # ── helpers ──────────────────────────────────────────────────────────
    def _flush_para(self, style):
        text = ''.join(self._buf).strip()
        if not text:
            self._buf.clear()
            return
        text = _inline_to_reportlab(text)
        # reportlab's Paragraph chokes on raw ampersands not part of entities
        text = re.sub(r'&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)',
                       '&amp;', text)
        self.flowables.append(Paragraph(text, style))
        self._buf.clear()

    def _push_paragraph(self):
        if not self._buf:
            return
        text = ''.join(self._buf).strip()
        if not text:
            self._buf.clear()
            return
        self._flush_para(BODY)

    # ── HTMLParser overrides ─────────────────────────────────────────────
    def handle_starttag(self, tag, attrs):
        if self._in_table:
            if tag == 'tr':
                self._row = []
                self._row_is_header = False
            elif tag in ('th', 'td'):
                self._in_cell = True
                self._cell_buf = []
                self._cell_is_header = (tag == 'th')
                if self._cell_is_header:
                    self._row_is_header = True
            elif tag == 'thead':
                pass
            elif tag == 'tbody':
                pass
            return

        if tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            self._push_paragraph()
            self._mode_stack.append(tag)
            self._buf = []
        elif tag == 'p':
            self._push_paragraph()
            self._mode_stack.append('p')
            self._buf = []
        elif tag == 'ul':
            self._push_paragraph()
            self._list_stack.append([])
            self._list_kind_stack.append('bullet')
            self._mode_stack.append('ul')
        elif tag == 'ol':
            self._push_paragraph()
            self._list_stack.append([])
            self._list_kind_stack.append('1')
            self._mode_stack.append('ol')
        elif tag == 'li':
            self._buf = []
            self._mode_stack.append('li')
        elif tag == 'blockquote':
            self._push_paragraph()
            self._mode_stack.append('blockquote')
            self._buf = []
        elif tag == 'pre':
            self._push_paragraph()
            self._mode_stack.append('pre')
            self._buf = []
        elif tag == 'code' and self._mode_stack and self._mode_stack[-1] == 'pre':
            pass  # absorbed by <pre>
        elif tag == 'hr':
            self._push_paragraph()
            self.flowables.append(Spacer(1, 4))
            self.flowables.append(Table([['']], colWidths=[170 * mm], rowHeights=[1],
                                          style=TableStyle([('BACKGROUND', (0, 0), (-1, -1), BORDER)])))
            self.flowables.append(Spacer(1, 4))
        elif tag == 'table':
            self._push_paragraph()
            self._in_table = True
            self._rows = []
        elif tag in ('br',):
            self._buf.append('<br/>')
        elif tag in ('strong', 'em', 'code', 'a'):
            # rebuild inline tag verbatim (with attrs)
            attr_s = ''.join(f' {k}="{v}"' for k, v in attrs)
            self._buf.append(f'<{tag}{attr_s}>')

    def handle_endtag(self, tag):
        if self._in_table:
            if tag in ('th', 'td'):
                cell = ''.join(self._cell_buf).strip()
                self._row.append(cell)
                self._in_cell = False
            elif tag == 'tr':
                if self._row_is_header:
                    self._rows.insert(0, self._row)  # ensure header is first
                else:
                    self._rows.append(self._row)
            elif tag == 'table':
                self._emit_table()
                self._in_table = False
                self._rows = []
            return

        if tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            style = {'h1': H1, 'h2': H2, 'h3': H3, 'h4': H4,
                     'h5': H4, 'h6': H4}[tag]
            self._flush_para(style)
            if self._mode_stack:
                self._mode_stack.pop()
        elif tag == 'p':
            self._flush_para(BODY)
            if self._mode_stack:
                self._mode_stack.pop()
        elif tag == 'li':
            item_text = ''.join(self._buf).strip()
            if item_text:
                item_text = _inline_to_reportlab(item_text)
                item_text = re.sub(r'&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)',
                                    '&amp;', item_text)
                p = Paragraph(item_text, BODY)
                self._list_stack[-1].append(ListItem(p, leftIndent=12))
            self._buf = []
            if self._mode_stack and self._mode_stack[-1] == 'li':
                self._mode_stack.pop()
        elif tag in ('ul', 'ol'):
            items = self._list_stack.pop()
            kind = self._list_kind_stack.pop()
            if items:
                self.flowables.append(ListFlowable(items, bulletType=kind,
                                                     leftIndent=14,
                                                     spaceBefore=2, spaceAfter=4))
            if self._mode_stack:
                self._mode_stack.pop()
        elif tag == 'blockquote':
            text = ''.join(self._buf).strip()
            if text:
                p = Paragraph(_inline_to_reportlab(text), BODY)
                wrap = Table([[p]], colWidths=[170 * mm], style=TableStyle([
                    ('LEFTPADDING', (0, 0), (-1, -1), 12),
                    ('TOPPADDING', (0, 0), (-1, -1), 6),
                    ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
                    ('BACKGROUND', (0, 0), (-1, -1), SUBTLE),
                    ('LINEBEFORE', (0, 0), (-1, -1), 3, ACCENT),
                ]))
                self.flowables.append(wrap)
            self._buf = []
            if self._mode_stack:
                self._mode_stack.pop()
        elif tag == 'pre':
            text = ''.join(self._buf)
            text = re.sub(r'<[^>]+>', '', text)  # strip nested tags
            text = (text.replace('&amp;', '&').replace('&lt;', '<')
                          .replace('&gt;', '>').replace('&quot;', '"'))
            if text.strip():
                lines = text.split('\n')
                cells = [[Paragraph(_inline_to_reportlab(_escape(l)) or '&nbsp;', CODE)] for l in lines]
                tbl = Table(cells, colWidths=[170 * mm], style=TableStyle([
                    ('BACKGROUND', (0, 0), (-1, -1), SUBTLE),
                    ('LEFTPADDING', (0, 0), (-1, -1), 8),
                    ('RIGHTPADDING', (0, 0), (-1, -1), 8),
                    ('TOPPADDING', (0, 0), (-1, -1), 2),
                    ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
                ]))
                self.flowables.append(tbl)
            self._buf = []
            if self._mode_stack:
                self._mode_stack.pop()
        elif tag in ('strong', 'em', 'code', 'a'):
            self._buf.append(f'</{tag}>')

    def handle_data(self, data):
        if self._in_table and self._in_cell:
            self._cell_buf.append(data)
        else:
            self._buf.append(data)

    def handle_entityref(self, name):
        if self._in_table and self._in_cell:
            self._cell_buf.append(f'&{name};')
        else:
            self._buf.append(f'&{name};')

    def handle_charref(self, name):
        if self._in_table and self._in_cell:
            self._cell_buf.append(f'&#{name};')
        else:
            self._buf.append(f'&#{name};')

    def _emit_table(self):
        if not self._rows:
            return
        # Column count = max across rows
        n_cols = max(len(r) for r in self._rows)
        norm = [r + [''] * (n_cols - len(r)) for r in self._rows]
        # Make Paragraph cells so text wraps inside columns
        body_rows = []
        for r_idx, r in enumerate(norm):
            cells = []
            for c in r:
                # Strip any leftover html from cells
                c = re.sub(r'\s+', ' ', c).strip()
                c = _inline_to_reportlab(c)
                c = re.sub(r'&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)',
                            '&amp;', c)
                style = TABLE_CELL_H if r_idx == 0 else TABLE_CELL
                cells.append(Paragraph(c or '&nbsp;', style))
            body_rows.append(cells)
        # Even column widths capped at content width
        content_w = 170 * mm
        col_w = content_w / n_cols
        tbl = Table(body_rows, colWidths=[col_w] * n_cols, repeatRows=1,
                     hAlign='LEFT')
        tbl.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (-1, 0), PRIMARY),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
            ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
            ('FONTSIZE', (0, 0), (-1, -1), 8.5),
            ('LEFTPADDING', (0, 0), (-1, -1), 6),
            ('RIGHTPADDING', (0, 0), (-1, -1), 6),
            ('TOPPADDING', (0, 0), (-1, -1), 4),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
            ('VALIGN', (0, 0), (-1, -1), 'TOP'),
            ('GRID', (0, 0), (-1, -1), 0.4, BORDER),
            ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, SUBTLE]),
        ]))
        self.flowables.append(Spacer(1, 4))
        self.flowables.append(tbl)
        self.flowables.append(Spacer(1, 6))


def _escape(s: str) -> str:
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def render_md(md_path: Path, pdf_path: Path):
    text = md_path.read_text(encoding='utf-8')
    md = markdown.Markdown(extensions=['extra', 'sane_lists', 'tables', 'fenced_code'])
    html = md.convert(text)

    parser = MdHtmlToFlowables()
    parser.feed(html)
    parser.close()

    doc = SimpleDocTemplate(str(pdf_path), pagesize=A4,
                              leftMargin=20 * mm, rightMargin=20 * mm,
                              topMargin=18 * mm, bottomMargin=18 * mm,
                              title=md_path.stem.replace('-', ' ').title(),
                              author='TSS GRC Platform')

    cover = [
        Spacer(1, 60 * mm),
        Paragraph('Saudi GRC Platform', COVER_SUB),
        Paragraph(md_path.stem.replace('-', ' ').title(), COVER_TITLE),
        Spacer(1, 14),
        Paragraph(f'Source: <font name="Courier" size="10">{md_path.name}</font>',
                   COVER_SUB),
        PageBreak(),
    ]

    def _footer(canv, _doc):
        canv.saveState()
        canv.setFont('Helvetica', 8)
        canv.setFillColor(INK_3)
        canv.drawString(20 * mm, 10 * mm, f'TSS GRC Platform · {md_path.stem}')
        canv.drawRightString(A4[0] - 20 * mm, 10 * mm,
                              f'Page {canv.getPageNumber()}')
        canv.restoreState()

    doc.build(cover + parser.flowables, onFirstPage=_footer, onLaterPages=_footer)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    root = Path(__file__).parent
    md_files = sorted(root.rglob('*.md'))
    if not md_files:
        print('No markdown files found')
        return 1
    for md in md_files:
        # Skip generated artifacts
        if md.name.startswith('.'):
            continue
        pdf = md.with_suffix('.pdf')
        try:
            render_md(md, pdf)
            kb = pdf.stat().st_size / 1024
            print(f'  OK  {md.relative_to(root)} -> {pdf.relative_to(root)} ({kb:.1f} KB)')
        except Exception as exc:
            print(f'  ERR {md.relative_to(root)}: {exc}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
