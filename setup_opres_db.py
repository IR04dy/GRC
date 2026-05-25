"""Bootstrap the Operational Resilience self-assessment module in dev.

Sprint 14 PR #8. Creates the `opres` schema, 3 tables, indexes, and
seeds the 5 SAMA Op-Res 2024 catalog pillars with a 5-level maturity
rubric stored as JSON on each row.

Idempotent — every pillar insert is guarded by IF NOT EXISTS on
(NULL organisation_id, code).

Usage:
  python setup_opres_db.py
"""
from __future__ import annotations

import json
import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


# ── Schema ────────────────────────────────────────────────────────────
def create_schema():
    cur.execute(
        "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'opres') "
        "EXEC('CREATE SCHEMA opres AUTHORIZATION dbo;')"
    )
    print("✓ schema opres")


# ── Tables ────────────────────────────────────────────────────────────
TABLES = {
    'opres_pillar': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'opres' AND t.name = 'opres_pillar')
        CREATE TABLE opres.opres_pillar (
            id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id       BIGINT NULL,
            code                  NVARCHAR(32)  NOT NULL,
            sort_order            INT           NOT NULL CONSTRAINT df_opres_pillar_so DEFAULT 0,
            name_en               NVARCHAR(256) NOT NULL,
            name_ar               NVARCHAR(256) NOT NULL,
            description_en        NVARCHAR(MAX) NULL,
            description_ar        NVARCHAR(MAX) NULL,
            sama_reference        NVARCHAR(64)  NULL,
            maturity_rubric_json  NVARCHAR(MAX) NULL,
            is_active             BIT           NOT NULL CONSTRAINT df_opres_pillar_act DEFAULT 1,
            created_at            DATETIME2     NOT NULL CONSTRAINT df_opres_pillar_cat DEFAULT SYSUTCDATETIME(),
            updated_at            DATETIME2     NULL,
            created_by            BIGINT        NULL,
            updated_by            BIGINT        NULL,
            is_deleted            BIT           NOT NULL CONSTRAINT df_opres_pillar_del DEFAULT 0,
            deleted_at            DATETIME2     NULL,
            deleted_by            BIGINT        NULL,
            row_version           ROWVERSION    NOT NULL,
            CONSTRAINT uq_opres_pillar_code UNIQUE (organisation_id, code)
        );
    """,
    'opres_assessment': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'opres' AND t.name = 'opres_assessment')
        CREATE TABLE opres.opres_assessment (
            id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id       BIGINT       NOT NULL,
            fiscal_year           INT          NOT NULL,
            title_en              NVARCHAR(256) NOT NULL,
            title_ar              NVARCHAR(256) NOT NULL,
            status                NVARCHAR(24) NOT NULL CONSTRAINT df_opres_a_st DEFAULT 'DRAFT',
            due_date              DATE         NULL,
            sponsor_user_id       BIGINT       NULL,
            avg_current_score     DECIMAL(4,2) NULL,
            avg_target_score      DECIMAL(4,2) NULL,
            completeness          NVARCHAR(16) NULL,
            submitted_at          DATETIME2    NULL,
            approved_at           DATETIME2    NULL,
            approved_by_user_id   BIGINT       NULL,
            closed_at             DATETIME2    NULL,
            executive_summary_en  NVARCHAR(MAX) NULL,
            executive_summary_ar  NVARCHAR(MAX) NULL,
            created_at            DATETIME2    NOT NULL CONSTRAINT df_opres_a_cat DEFAULT SYSUTCDATETIME(),
            updated_at            DATETIME2    NULL,
            created_by            BIGINT       NULL,
            updated_by            BIGINT       NULL,
            is_deleted            BIT          NOT NULL CONSTRAINT df_opres_a_del DEFAULT 0,
            deleted_at            DATETIME2    NULL,
            deleted_by            BIGINT       NULL,
            row_version           ROWVERSION   NOT NULL
        );
    """,
    'opres_assessment_cell': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'opres' AND t.name = 'opres_assessment_cell')
        CREATE TABLE opres.opres_assessment_cell (
            id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id        BIGINT       NOT NULL,
            assessment_id          BIGINT       NOT NULL,
            pillar_id              BIGINT       NOT NULL,
            pillar_code_snapshot   NVARCHAR(32) NOT NULL,
            current_score          INT          NULL,
            target_score           INT          NULL,
            rationale_en           NVARCHAR(MAX) NULL,
            rationale_ar           NVARCHAR(MAX) NULL,
            evidence_reference     NVARCHAR(512) NULL,
            last_edited_by_user_id BIGINT       NULL,
            created_at             DATETIME2    NOT NULL CONSTRAINT df_opres_c_cat DEFAULT SYSUTCDATETIME(),
            updated_at             DATETIME2    NULL,
            created_by             BIGINT       NULL,
            updated_by             BIGINT       NULL,
            is_deleted             BIT          NOT NULL CONSTRAINT df_opres_c_del DEFAULT 0,
            deleted_at             DATETIME2    NULL,
            deleted_by             BIGINT       NULL,
            row_version            ROWVERSION   NOT NULL,
            CONSTRAINT fk_opres_c_assessment FOREIGN KEY (assessment_id)
                REFERENCES opres.opres_assessment(id),
            CONSTRAINT fk_opres_c_pillar FOREIGN KEY (pillar_id)
                REFERENCES opres.opres_pillar(id),
            CONSTRAINT uq_opres_c_unique UNIQUE (assessment_id, pillar_id)
        );
    """,
}

INDEXES = [
    """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_opres_a_org_fy')
       CREATE INDEX ix_opres_a_org_fy ON opres.opres_assessment (organisation_id, fiscal_year DESC, created_at DESC)
       WHERE is_deleted = 0;""",
    """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_opres_c_assessment')
       CREATE INDEX ix_opres_c_assessment ON opres.opres_assessment_cell (organisation_id, assessment_id, pillar_id)
       WHERE is_deleted = 0;""",
]


def create_tables():
    for name, ddl in TABLES.items():
        cur.execute(ddl)
        print(f"  ✓ table opres.{name}")
    for idx in INDEXES:
        cur.execute(idx)
    print("  ✓ indexes")


# ── Maturity rubric — 5 levels, used identically across pillars ───────
# Stored as JSON on each pillar so the UI can render inline next to
# the Select. Each level: {"en": "...", "ar": "..."}
MATURITY_LEVELS = [
    {"level": 1, "label_en": "Initial",
     "en": "Ad-hoc, undocumented, reactive. The activity happens when someone notices it needs to.",
     "ar": "مرتجلة، غير موثّقة، تفاعلية. النشاط يحدث عندما يلاحظ شخص ضرورته."},
    {"level": 2, "label_en": "Developing",
     "en": "Documented but execution is inconsistent across business units.",
     "ar": "موثّقة لكن التنفيذ غير منتظم عبر وحدات الأعمال."},
    {"level": 3, "label_en": "Defined",
     "en": "Standardised process applied across the organisation; outputs are repeatable.",
     "ar": "إجراء قياسي مطبّق عبر المؤسسة؛ النتائج قابلة للتكرار."},
    {"level": 4, "label_en": "Managed",
     "en": "Metrics-driven, board-reported quarterly, performance against targets monitored.",
     "ar": "قائم على مؤشّرات، يُرفع للمجلس فصلياً، الأداء مقابل الأهداف يخضع للرصد."},
    {"level": 5, "label_en": "Optimising",
     "en": "Continuous improvement, industry-leading, embedded in strategy.",
     "ar": "تحسين مستمرّ، رائدة في الصناعة، مدمجة في الاستراتيجية."},
]


def rubric_for(name_en: str, name_ar: str) -> str:
    """Return the rubric JSON with the pillar name baked into the level text."""
    return json.dumps([
        {
            "level": lvl["level"],
            "labelEn": lvl["label_en"],
            "en": lvl["en"],
            "ar": lvl["ar"],
        }
        for lvl in MATURITY_LEVELS
    ], ensure_ascii=False)


# ── Seed: 5 SAMA Op-Res 2024 pillars ──────────────────────────────────
PILLARS = [
    ('GOVERNANCE', 1,
     'Governance & Strategy',
     'الحوكمة والاستراتيجية',
     'Board ownership of Op-Res, an accountable senior executive, written strategy, three-lines '
     'model with clear roles, integration of Op-Res into risk-appetite and policies.',
     'مسؤولية المجلس عن الصمود التشغيلي، تعيين تنفيذي أوّل مساءَل، استراتيجية مكتوبة، '
     'نموذج الخطوط الثلاثة بأدوار واضحة، دمج الصمود التشغيلي في شهية المخاطر والسياسات.',
     '§3.2'),

    ('IBS_MAPPING', 2,
     'Important Business Services (IBS)',
     'خدمات الأعمال المهمّة',
     'Identification and registration of services whose disruption would cause intolerable harm '
     'to customers, market integrity, financial stability, or the safety and soundness of the firm. '
     'IBS register is reviewed at least annually by the board.',
     'تحديد وتسجيل الخدمات التي يسبّب تعطّلها ضرراً غير محتمل على العملاء أو نزاهة السوق '
     'أو الاستقرار المالي أو سلامة وكفاءة المنشأة. سجلّ خدمات الأعمال المهمّة يُراجَع سنوياً على الأقل من المجلس.',
     '§4.1-4.3'),

    ('TOLERANCES', 3,
     'Impact Tolerances',
     'حدود التأثير المقبولة',
     'Per-IBS maximum tolerable disruption (in hours / data-loss MB / customer harm) set and '
     'approved by the board. Tolerances inform recovery-time objectives, scenario tests, and '
     'investment prioritisation.',
     'لكل خدمة أعمال مهمّة حدّ أقصى للتعطّل المقبول (بالساعات / فقدان البيانات / الضرر على العملاء) '
     'يُعتمد من المجلس. الحدود تُرشد أهداف زمن التعافي واختبارات السيناريو وأولوية الاستثمارات.',
     '§5.1-5.4'),

    ('MAPPING_TESTING', 4,
     'Mapping & Scenario Testing',
     'الربط واختبار السيناريوهات',
     'End-to-end mapping of each IBS to people, processes, technology, facilities, and third '
     'parties. Severe-but-plausible scenarios run at least annually to prove the firm can stay '
     'within impact tolerance under stress.',
     'الربط الشامل لكل خدمة أعمال مهمّة بالأشخاص والإجراءات والتقنية والمرافق والأطراف الثالثة. '
     'سيناريوهات قاسية لكن محتملة تُختبر سنوياً على الأقل لإثبات قدرة المنشأة على البقاء ضمن حدود '
     'التأثير تحت الضغط.',
     '§6.1-6.5'),

    ('LESSONS', 5,
     'Lessons Learned & Continuous Improvement',
     'الدروس المستفادة والتحسين المستمرّ',
     'Post-incident and post-exercise lessons captured, prioritised, and fed into a remediation '
     'backlog. Capability uplift is tracked across the annual cycle so the next assessment shows '
     'measurable progress.',
     'الدروس المستفادة من الحوادث والتمارين تُسجَّل وتُرتَّب وتُحوَّل إلى قائمة معالجة. تطوير '
     'القدرات يخضع للرصد عبر الدورة السنوية بحيث يُظهر التقييم التالي تقدّماً قابلاً للقياس.',
     '§7.1-7.3'),
]


def seed_pillars():
    sql = """
    IF NOT EXISTS (SELECT 1 FROM opres.opres_pillar
                    WHERE code = ? AND organisation_id IS NULL)
        INSERT INTO opres.opres_pillar
            (organisation_id, code, sort_order, name_en, name_ar,
             description_en, description_ar, sama_reference,
             maturity_rubric_json, is_active)
        VALUES
            (NULL, ?, ?, ?, ?, ?, ?, ?, ?, 1);
    """
    inserted = 0
    for code, sort_order, name_en, name_ar, desc_en, desc_ar, sama_ref in PILLARS:
        rubric = rubric_for(name_en, name_ar)
        cur.execute(sql, code, code, sort_order, name_en, name_ar,
                    desc_en, desc_ar, sama_ref, rubric)
        if cur.rowcount > 0:
            inserted += 1
    print(f"  ✓ pillars — {inserted} new (idempotent: existing rows kept)")


# ── Main ──────────────────────────────────────────────────────────────
def main():
    print(">>> Bootstrapping Operational Resilience self-assessment (Sprint 14 PR #8)")
    create_schema()
    print("--- Tables")
    create_tables()
    print("--- Seed (5 SAMA Op-Res 2024 pillars)")
    seed_pillars()
    print("✓ done — module/opres ready")


if __name__ == '__main__':
    main()
