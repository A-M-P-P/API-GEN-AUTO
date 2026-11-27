"""Regenerate the downloadable guide: pip install reportlab, then run this file.
Requires DejaVu Sans fonts (Debian: fonts-dejavu-core). Not an application build.
"""
from pathlib import Path
import re
from html import escape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle

ROOT = Path(__file__).resolve().parents[2]
PUBLIC = ROOT / 'frontend/public'
for name, filename in [('Guide', 'DejaVuSans.ttf'), ('GuideBold', 'DejaVuSans-Bold.ttf')]:
    pdfmetrics.registerFont(TTFont(name, '/usr/share/fonts/truetype/dejavu/' + filename))
pdfmetrics.registerFontFamily('Guide', normal='Guide', bold='GuideBold', italic='Guide', boldItalic='GuideBold')
styles = getSampleStyleSheet()
for name in ['Normal', 'Title', 'Heading1', 'Heading2', 'Heading3']:
    styles[name].fontName = 'GuideBold' if name != 'Normal' else 'Guide'
    styles[name].textColor = colors.HexColor('#243b53')
styles['Normal'].fontSize = 9
styles['Normal'].leading = 14
styles['Normal'].spaceAfter = 7
styles['Title'].fontSize = 23
styles['Title'].leading = 29
styles['Heading1'].fontSize = 15
styles['Heading1'].leading = 21
styles['Heading1'].spaceBefore = 17
styles['Heading2'].fontSize = 11
styles['Heading2'].leading = 16
styles['Heading2'].spaceBefore = 10
styles.add(ParagraphStyle('Cell', parent=styles['Normal'], fontSize=8, leading=12, spaceAfter=0))

def inline(text):
    text = escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'`([^`]+)`', r'<font color="#006b43">\1</font>', text)
    return text.replace('\\*', '*')

story = []
lines = (PUBLIC / 'GUIDE-UTILISATEUR.md').read_text().splitlines()
i = 0
while i < len(lines):
    line = lines[i].strip()
    i += 1
    if not line or line == '---':
        continue
    if line.startswith('|'):
        rows = [line]
        while i < len(lines) and lines[i].strip().startswith('|'):
            rows.append(lines[i].strip()); i += 1
        data = [[Paragraph(inline(cell.strip()), styles['Cell']) for cell in row.strip('|').split('|')]
                for row in rows if not re.fullmatch(r'[| :\-]+', row)]
        table = Table(data, colWidths=[155, 350], repeatRows=1, hAlign='LEFT')
        table.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#e8eef4')),
            ('VALIGN', (0, 0), (-1, -1), 'TOP'),
            ('GRID', (0, 0), (-1, -1), .3, colors.HexColor('#cbd5e1')),
            ('LEFTPADDING', (0, 0), (-1, -1), 7), ('RIGHTPADDING', (0, 0), (-1, -1), 7),
            ('TOPPADDING', (0, 0), (-1, -1), 6), ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ]))
        story.extend([table, Spacer(1, 10)])
        continue
    if line.startswith('# '):
        story.append(Paragraph(inline(line[2:]), styles['Title']))
        story.append(Spacer(1, 15))
    elif line.startswith('## '):
        story.append(Paragraph(inline(line[3:]), styles['Heading1']))
    elif line.startswith('### '):
        story.append(Paragraph(inline(line[4:]), styles['Heading2']))
    else:
        if line.startswith('- [ ] '):
            line = '☐ ' + line[6:]
        elif line.startswith('- '):
            line = '• ' + line[2:]
        story.append(Paragraph(inline(line), styles['Normal']))

def footer(canvas, doc):
    canvas.setFont('Guide', 8)
    canvas.setFillColor(colors.HexColor('#64748b'))
    canvas.drawString(44, 25, 'API-GEN Studio • Guide pratique')
    canvas.drawRightString(A4[0] - 44, 25, str(doc.page))

output = PUBLIC / 'GUIDE-UTILISATEUR.pdf'
SimpleDocTemplate(str(output), pagesize=A4, rightMargin=44, leftMargin=44,
                  topMargin=42, bottomMargin=44, title='API-GEN Studio — Guide pratique',
                  author='API-GEN Studio').build(story, onFirstPage=footer, onLaterPages=footer)
print(output)
