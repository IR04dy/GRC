"""Bootstrap the Fraud Risk Management module in the dev DB.

Sprint 14 PR #2. The dev profile has Flyway disabled (CLAUDE.md),
so this script creates the `fraud` schema, 3 tables, and seeds the
Saudi-bank-relevant typology catalog.

Saudi-native typologies seeded:
  - BEC ("Business Email Compromise" — fake CFO wire request, common pattern)
  - ATM_SKIMMING (card skimming via tampered ATM card readers)
  - PUSH_PAYMENT (SADAD / IPS push-payment fraud — customer tricked into
    sending real-time to a fraudster)
  - ACCOUNT_TAKEOVER (credential stuffing / SIM swap → IB/MB login)
  - INTERNAL_EMBEZZLEMENT (employee diverts customer funds)
  - CARD_NOT_PRESENT (online card fraud, CVV harvesting)
  - LOAN_APPLICATION (synthetic identity, document forgery on credit apps)
  - WIRE_TRANSFER (SWIFT MT103 fraud — corporate impersonation)
  - INVOICE_FRAUD (vendor-impersonation, payment redirect emails)
  - INSIDER_TRADING (employee misuse of material non-public information)
  - MONEY_MULE (account opened with intent to launder fraud proceeds)
  - PHISHING_VISHING (callers impersonating bank to extract OTPs)

Idempotent: each typology insert is guarded by IF NOT EXISTS on `code`
+ NULL organisation_id (i.e. the shared catalog row).

Usage:
  python setup_fraud_db.py
"""
from __future__ import annotations

import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

# ── DB connection ─────────────────────────────────────────────────────
conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


# ── Schema ────────────────────────────────────────────────────────────
def create_schema():
    cur.execute(
        "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'fraud') "
        "EXEC('CREATE SCHEMA fraud AUTHORIZATION dbo;')"
    )
    print("✓ schema fraud")


# ── Tables ────────────────────────────────────────────────────────────
TABLES = {
    'fraud_typology': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'fraud' AND t.name = 'fraud_typology')
        CREATE TABLE fraud.fraud_typology (
            id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id       BIGINT NULL,
            code                  NVARCHAR(64)  NOT NULL,
            name_en               NVARCHAR(256) NOT NULL,
            name_ar               NVARCHAR(256) NOT NULL,
            description_en        NVARCHAR(MAX) NULL,
            description_ar        NVARCHAR(MAX) NULL,
            sama_category_code    NVARCHAR(32)  NOT NULL,
            perpetrator_origin    NVARCHAR(16)  NOT NULL,
            primary_channel       NVARCHAR(32)  NOT NULL,
            typical_severity      NVARCHAR(16)  NOT NULL CONSTRAINT df_fraud_typ_sev DEFAULT 'MEDIUM',
            requires_str_filing   BIT           NOT NULL CONSTRAINT df_fraud_typ_str DEFAULT 0,
            is_active             BIT           NOT NULL CONSTRAINT df_fraud_typ_act DEFAULT 1,
            created_at            DATETIME2     NOT NULL CONSTRAINT df_fraud_typ_cat DEFAULT SYSUTCDATETIME(),
            updated_at            DATETIME2     NULL,
            created_by            BIGINT        NULL,
            updated_by            BIGINT        NULL,
            is_deleted            BIT           NOT NULL CONSTRAINT df_fraud_typ_del DEFAULT 0,
            deleted_at            DATETIME2     NULL,
            deleted_by            BIGINT        NULL,
            row_version           ROWVERSION    NOT NULL,
            CONSTRAINT uq_fraud_typ_code UNIQUE (organisation_id, code)
        );
    """,
    'fraud_case': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'fraud' AND t.name = 'fraud_case')
        CREATE TABLE fraud.fraud_case (
            id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id             BIGINT        NOT NULL,
            external_reference          NVARCHAR(32)  NOT NULL,
            typology_id                 BIGINT        NOT NULL,
            typology_code_snapshot      NVARCHAR(64)  NOT NULL,
            subject_en                  NVARCHAR(256) NOT NULL,
            subject_ar                  NVARCHAR(256) NOT NULL,
            narrative_en                NVARCHAR(MAX) NULL,
            narrative_ar                NVARCHAR(MAX) NULL,
            status                      NVARCHAR(32)  NOT NULL CONSTRAINT df_fraud_case_st DEFAULT 'DETECTED',
            severity                    NVARCHAR(16)  NOT NULL CONSTRAINT df_fraud_case_sev DEFAULT 'MEDIUM',
            detection_source            NVARCHAR(32)  NOT NULL,
            detection_reference         NVARCHAR(128) NULL,
            incident_date               DATE          NULL,
            detected_at                 DATETIME2     NOT NULL CONSTRAINT df_fraud_case_det DEFAULT SYSUTCDATETIME(),
            reported_at                 DATETIME2     NULL,
            estimated_loss_sar          DECIMAL(18,2) NULL,
            confirmed_loss_sar          DECIMAL(18,2) NULL,
            recovered_amount_sar        DECIMAL(18,2) NULL,
            loss_currency               NVARCHAR(3)   NOT NULL CONSTRAINT df_fraud_case_cur DEFAULT 'SAR',
            affected_customer_count     INT           NOT NULL CONSTRAINT df_fraud_case_acc DEFAULT 0,
            assigned_investigator_user_id BIGINT NULL,
            triaged_at                  DATETIME2     NULL,
            triaged_by_user_id          BIGINT        NULL,
            investigation_started_at    DATETIME2     NULL,
            outcome_set_at              DATETIME2     NULL,
            outcome_set_by_user_id      BIGINT        NULL,
            closed_at                   DATETIME2     NULL,
            closed_by_user_id           BIGINT        NULL,
            closure_notes_en            NVARCHAR(MAX) NULL,
            closure_notes_ar            NVARCHAR(MAX) NULL,
            str_filed                   BIT           NOT NULL CONSTRAINT df_fraud_case_strf DEFAULT 0,
            str_reference               NVARCHAR(100) NULL,
            str_filed_at                DATETIME2     NULL,
            law_enforcement_engaged     BIT           NOT NULL CONSTRAINT df_fraud_case_leg DEFAULT 0,
            law_enforcement_reference   NVARCHAR(100) NULL,
            created_at                  DATETIME2     NOT NULL CONSTRAINT df_fraud_case_cat DEFAULT SYSUTCDATETIME(),
            updated_at                  DATETIME2     NULL,
            created_by                  BIGINT        NULL,
            updated_by                  BIGINT        NULL,
            is_deleted                  BIT           NOT NULL CONSTRAINT df_fraud_case_del DEFAULT 0,
            deleted_at                  DATETIME2     NULL,
            deleted_by                  BIGINT        NULL,
            row_version                 ROWVERSION    NOT NULL,
            CONSTRAINT uq_fraud_case_ref UNIQUE (organisation_id, external_reference),
            CONSTRAINT fk_fraud_case_typology FOREIGN KEY (typology_id)
                REFERENCES fraud.fraud_typology(id)
        );
    """,
    'fraud_case_update': """
        IF NOT EXISTS (SELECT 1 FROM sys.tables t
                         JOIN sys.schemas s ON t.schema_id = s.schema_id
                        WHERE s.name = 'fraud' AND t.name = 'fraud_case_update')
        CREATE TABLE fraud.fraud_case_update (
            id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id     BIGINT       NOT NULL,
            case_id             BIGINT       NOT NULL,
            update_type         NVARCHAR(32) NOT NULL,
            occurred_at         DATETIME2    NOT NULL CONSTRAINT df_fraud_upd_occ DEFAULT SYSUTCDATETIME(),
            actor_user_id       BIGINT       NULL,
            actor_name          NVARCHAR(256) NULL,
            note_en             NVARCHAR(MAX) NULL,
            note_ar             NVARCHAR(MAX) NULL,
            status_after        NVARCHAR(32)  NULL,
            evidence_reference  NVARCHAR(512) NULL,
            created_at          DATETIME2    NOT NULL CONSTRAINT df_fraud_upd_cat DEFAULT SYSUTCDATETIME(),
            updated_at          DATETIME2    NULL,
            created_by          BIGINT       NULL,
            updated_by          BIGINT       NULL,
            is_deleted          BIT          NOT NULL CONSTRAINT df_fraud_upd_del DEFAULT 0,
            deleted_at          DATETIME2    NULL,
            deleted_by          BIGINT       NULL,
            row_version         ROWVERSION   NOT NULL,
            CONSTRAINT fk_fraud_upd_case FOREIGN KEY (case_id)
                REFERENCES fraud.fraud_case(id)
        );
    """,
}


INDEXES = [
    """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fraud_case_org_status')
       CREATE INDEX ix_fraud_case_org_status ON fraud.fraud_case (organisation_id, status, detected_at DESC)
       WHERE is_deleted = 0;""",
    """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fraud_case_org_detected')
       CREATE INDEX ix_fraud_case_org_detected ON fraud.fraud_case (organisation_id, detected_at DESC)
       WHERE is_deleted = 0;""",
    """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fraud_upd_case_occ')
       CREATE INDEX ix_fraud_upd_case_occ ON fraud.fraud_case_update (organisation_id, case_id, occurred_at DESC)
       WHERE is_deleted = 0;""",
]


def create_tables():
    for name, ddl in TABLES.items():
        cur.execute(ddl)
        print(f"  ✓ table fraud.{name}")
    for idx in INDEXES:
        cur.execute(idx)
    print("  ✓ indexes")


# ── Seed: Saudi-bank-relevant typologies ──────────────────────────────
# Format: (code, name_en, name_ar, description_en, description_ar,
#          sama_category_code, perpetrator_origin, primary_channel,
#          typical_severity, requires_str_filing)
TYPOLOGIES = [
    ('BEC',
     'Business Email Compromise',
     'الاحتيال عبر البريد الإلكتروني للأعمال',
     'A fraudster impersonates a CFO or supplier and instructs a finance '
     'staffer to wire funds to an attacker-controlled account. Common in '
     'Saudi corporates with offshore vendor payments.',
     'ينتحل المحتال شخصية مدير مالي أو مورّد ويوجّه موظف الحسابات '
     'لتحويل أموال إلى حساب يسيطر عليه المهاجم. شائع لدى الشركات '
     'السعودية مع مدفوعات الموردين الخارجيين.',
     'CYBER',  'EXTERNAL', 'CORPORATE', 'HIGH',     1),

    ('ATM_SKIMMING',
     'ATM Card Skimming',
     'نسخ بطاقات الصراف الآلي',
     'Skimming device + pinhole camera installed at an ATM clone the magnetic '
     'stripe and PIN; cards are then cloned and used at offshore ATMs. '
     'SAMA mandates EMV chip-only acceptance + ATM tamper sensors.',
     'يُركّب جهاز نسخ وكاميرا دقيقة عند الصراف الآلي لنسخ الشريط '
     'المغناطيسي ورقم التعريف؛ تُستنسخ البطاقات وتُستخدم في صرافات '
     'خارجية. تُلزم ساما بقبول الشرائح فقط وأجهزة استشعار العبث.',
     'CARD',   'EXTERNAL', 'RETAIL',    'MEDIUM',   1),

    ('PUSH_PAYMENT',
     'SADAD/IPS Push-Payment Fraud',
     'الاحتيال بدفع فوري عبر سداد/IPS',
     'Victim is deceived (impersonation call, fake invoice, romance scam) '
     'into pushing funds via SADAD or instant payments. Once executed, '
     'reversal is operationally impossible. SAMA Op-Res 2024 §4.7 expects '
     'pre-send confirmation prompts for new beneficiaries.',
     'يُخدع الضحية (مكالمة انتحال، فاتورة مزيّفة، عملية احتيال عاطفي) '
     'لتحويل أموال عبر سداد أو المدفوعات الفورية. بعد التنفيذ يتعذّر '
     'الاسترجاع. يتطلّب إطار الصمود التشغيلي 2024 §4.7 رسائل تأكيد '
     'مسبقة للمستفيدين الجدد.',
     'PAYMENT', 'EXTERNAL', 'RETAIL',   'HIGH',     1),

    ('ACCOUNT_TAKEOVER',
     'Account Takeover (ATO)',
     'الاستيلاء على الحساب',
     'Attacker obtains credentials via phishing/credential-stuffing or '
     'SIM-swap, then performs IB/MB login + executes transactions or '
     'changes beneficiary. SAMA Cyber Subdomain 3.3.13 requires step-up '
     'auth on risky session changes.',
     'يحصل المهاجم على بيانات الدخول عبر التصيّد أو تكرار بيانات '
     'الاعتماد أو الاستيلاء على الشريحة، ثم يسجّل الدخول للقنوات '
     'الإلكترونية وينفّذ معاملات أو يغيّر المستفيد. يتطلّب القسم '
     '3.3.13 من إطار الأمن السيبراني مصادقة معزّزة.',
     'CYBER',   'EXTERNAL', 'RETAIL',   'HIGH',     1),

    ('INTERNAL_EMBEZZLEMENT',
     'Internal Embezzlement',
     'الاختلاس الداخلي',
     'Branch teller, treasury operator, or back-office staff diverts customer '
     'funds or bank-owned funds via ghost accounts, suspended ledger '
     'manipulation, or split-transaction patterns. Detection often via '
     'GL reconciliation breaks.',
     'يحوّل موظف الفرع أو غرفة الخزينة أو الإدارات المساندة أموال '
     'العملاء أو أموال البنك عبر حسابات وهمية أو تلاعب القيود '
     'المعلّقة أو أنماط معاملات مجزّأة. يُكتشف عادةً عبر فروقات '
     'المطابقة في دفتر الأستاذ العام.',
     'INTERNAL', 'INTERNAL', 'OPERATIONS', 'CRITICAL', 1),

    ('CARD_NOT_PRESENT',
     'Card-Not-Present (CNP) Fraud',
     'احتيال البطاقة غير الحاضرة',
     'Online merchant transaction using stolen PAN+CVV without physical card. '
     'SAMA mandates 3-D Secure 2 / SAFI for ecommerce above SAR 100.',
     'معاملة عبر تاجر إلكتروني باستخدام رقم بطاقة و CVV مسروقَين دون '
     'بطاقة مادية. تُلزم ساما باستخدام 3-D Secure 2 أو SAFI للتجارة '
     'الإلكترونية فوق 100 ريال.',
     'CARD',    'EXTERNAL', 'CARDS',    'MEDIUM',   0),

    ('LOAN_APPLICATION',
     'Loan Application Fraud',
     'الاحتيال في طلبات القروض',
     'Synthetic identity, document forgery, or salary-letter forgery used to '
     'obtain a personal/auto/mortgage loan. SIMAH data tampering or '
     'employer impersonation are common patterns.',
     'هوية مركّبة أو تزوير مستندات أو تزوير خطاب راتب للحصول على '
     'قرض شخصي/سيارة/عقاري. التلاعب ببيانات سمة أو انتحال جهة عمل '
     'أنماط شائعة.',
     'EXTERNAL', 'EXTERNAL', 'RETAIL', 'HIGH',     0),

    ('WIRE_TRANSFER',
     'Wire Transfer Fraud (SWIFT MT103)',
     'احتيال التحويلات البرقية (سويفت)',
     'Fraudulent international wire executed by impersonating a corporate '
     'customer to the SWIFT operator, or by compromising the customer\'s '
     'authorised signer device. SAMA requires callback + multi-factor on '
     'wires above corporate threshold.',
     'تحويل دولي احتيالي يُنفَّذ بانتحال شخصية عميل شركات تجاه مشغّل '
     'سويفت أو باختراق جهاز المفوّض بالتوقيع لدى العميل. تشترط ساما '
     'مكالمة تحقق ومصادقة متعدّدة العوامل للتحويلات فوق حد الشركات.',
     'PAYMENT',  'EXTERNAL', 'CORPORATE', 'CRITICAL', 1),

    ('INVOICE_FRAUD',
     'Invoice Redirection Fraud',
     'احتيال إعادة توجيه الفواتير',
     'Attacker emails finance team posing as supplier, asking to update bank '
     'details. Next payment goes to attacker. Mitigated by callback to '
     'a known supplier number, not the new one in the email.',
     'يرسل المهاجم بريداً إلى فريق المالية منتحلاً شخصية المورّد ويطلب '
     'تحديث بيانات البنك. الدفعة التالية تذهب للمهاجم. يخفّف عبر مكالمة '
     'تحقق على رقم مورّد معروف لا الرقم الجديد في الإيميل.',
     'CYBER',    'EXTERNAL', 'CORPORATE', 'HIGH',     1),

    ('INSIDER_TRADING',
     'Insider Trading',
     'تداول بمعلومات داخلية',
     'Employee with material non-public information (M&A, earnings, '
     'regulatory action) trades the bank\'s or a client\'s securities '
     'before disclosure. Reportable to CMA.',
     'موظف لديه معلومات جوهرية غير معلنة (اندماج، أرباح، إجراء '
     'رقابي) يتداول أوراق البنك أو العميل قبل الإفصاح. تجب '
     'الإفادة لهيئة السوق المالية.',
     'INTERNAL', 'INTERNAL', 'TREASURY', 'CRITICAL', 1),

    ('MONEY_MULE',
     'Money Mule Account',
     'حساب وسيط لغسيل أموال',
     'Account opened (often by a student or low-income individual paid a '
     'small fee) used to receive and forward fraud proceeds. SAMA AML '
     'monitoring rules typically flag rapid in-out patterns.',
     'حساب يُفتح (غالباً من قِبل طالب أو فرد محدود الدخل بمقابل بسيط) '
     'لاستقبال وتمرير عائدات الاحتيال. تكشف قواعد رقابة غسل الأموال '
     'في ساما عادةً أنماط الإيداع/السحب السريع.',
     'EXTERNAL', 'EXTERNAL', 'RETAIL',   'HIGH',     1),

    ('PHISHING_VISHING',
     'Phishing / Vishing',
     'التصيّد الإلكتروني / الهاتفي',
     'Caller or SMS/email impersonates a SAMA-licensed bank to extract OTP, '
     'PIN, or card details from the customer. Often combined with '
     'ACCOUNT_TAKEOVER or PUSH_PAYMENT as the exfil vector.',
     'مكالمة أو رسالة/بريد تنتحل شخصية بنك مرخّص من ساما لاستخراج '
     'كلمة المرور لمرّة واحدة أو رقم التعريف أو بيانات البطاقة. '
     'يُستخدم كثيراً مع الاستيلاء على الحساب أو الدفع الفوري كقناة سحب.',
     'CYBER',    'EXTERNAL', 'RETAIL',   'MEDIUM',   0),
]


def seed_typologies():
    sql = """
    IF NOT EXISTS (SELECT 1 FROM fraud.fraud_typology
                    WHERE code = ? AND organisation_id IS NULL)
        INSERT INTO fraud.fraud_typology
            (organisation_id, code, name_en, name_ar, description_en, description_ar,
             sama_category_code, perpetrator_origin, primary_channel,
             typical_severity, requires_str_filing, is_active)
        VALUES
            (NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1);
    """
    inserted = 0
    for row in TYPOLOGIES:
        code = row[0]
        cur.execute(sql, code, *row)
        inserted += 1 if cur.rowcount > 0 else 0
    print(f"  ✓ typologies — {inserted} new (idempotent: existing rows kept)")


# ── Main ──────────────────────────────────────────────────────────────
def main():
    print(">>> Bootstrapping Fraud Risk Management module (Sprint 14)")
    create_schema()
    print("--- Tables")
    create_tables()
    print("--- Seed")
    seed_typologies()
    print("✓ done — module/fraud is ready")


if __name__ == '__main__':
    main()
