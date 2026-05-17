#!/usr/bin/env python3
"""Merge the per-output PDFs into one master deliverable
`Saudi-GRC-Platform-Integration.pdf`.

Order: README (index) → 01..18 by folder number. Adds a thin separator
between sections so they remain visually distinct.
"""
from __future__ import annotations
import sys
from pathlib import Path
from pypdf import PdfReader, PdfWriter


SECTION_ORDER = [
    'README.pdf',
    '01-Product-Blueprint/product-blueprint.pdf',
    '02-Module-Architecture/module-architecture.pdf',
    '03-Workflow-Catalogue/workflow-catalogue.pdf',
    '03-Workflow-Catalogue/workflow-lifecycles.pdf',
    '04-Data-Model/data-model.pdf',
    '05-UI-Screens/ui-screens.pdf',
    '06-Evidence-Library/evidence-library.pdf',
    '07-Regulatory-Mapping/regulatory-mapping.pdf',
    '08-RBAC/rbac-matrix.pdf',
    '09-Automation-Rules/automation-rules.pdf',
    '10-Reports-Dashboards/reports-dashboards.pdf',
    '11-Implementation-Backlog/implementation-backlog.pdf',
    '12-Developer-Handoff/developer-handoff.pdf',
    '13-API-Specification/api-specification.pdf',
    '14-Test-Cases/test-cases.pdf',
    '15-Sample-Data/sample-seed-data.pdf',
    '16-AI-Features/ai-features.pdf',
    '17-Frontend-Requirements/frontend-requirements.pdf',
    '18-Excel-Workbook/workbook-spec.pdf',
]


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    root = Path(__file__).parent
    writer = PdfWriter()
    total_pages = 0
    for rel in SECTION_ORDER:
        p = root / rel
        if not p.exists():
            print(f'  MISSING {rel}')
            continue
        reader = PdfReader(str(p))
        page_count = len(reader.pages)
        section_name = rel.split('/', 1)[0] if '/' in rel else 'README'
        section_label = rel.rsplit('/', 1)[-1].removesuffix('.pdf')
        # Add an outline entry per file
        first_page = total_pages
        for pg in reader.pages:
            writer.add_page(pg)
        try:
            writer.add_outline_item(section_label, first_page)
        except Exception:
            pass
        total_pages += page_count
        print(f'  + {rel} ({page_count} pages, total {total_pages})')
    out = root / 'Saudi-GRC-Platform-Integration.pdf'
    with open(out, 'wb') as f:
        writer.write(f)
    kb = out.stat().st_size / 1024
    print(f'\nWrote {out} ({total_pages} pages, {kb:.1f} KB)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
