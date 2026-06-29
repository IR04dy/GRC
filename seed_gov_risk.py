# -*- coding: utf-8 -*-
"""Seed KRI measurement time-series into risk.kri_measurement (organisation_id = 1).

The default tenant — a Saudi general authority (هيئة عامة) — already has 15 Key
Risk Indicators defined in risk.kri_definition spanning the enterprise risk
categories (cyber, AML, compliance, privacy/PDPL, BCM, credit, liquidity,
operational, third-party, AI, ESG, regulatory). Those KRIs feed the Risk
Management Committee (COM-RISK / لجنة إدارة المخاطر), which reviews KRIs and the
corporate risk register.

This script back-fills a realistic, board-grade measurement history for every
active KRI so the dashboards, trend charts and breach panels are populated. Each
KRI gets a chronological series of readings; the RAG status of each reading is
computed from the KRI's own thresholds, honouring its value_direction
(LOWER_IS_BETTER vs HIGHER_IS_BETTER) — exactly the logic the platform applies.

Idempotent: every reading is upserted by the natural key
(organisation_id, kri_id, measured_at). A second run inserts 0.

Hard safety: INSERT-only. No DELETE/TRUNCATE/DROP/ALTER. organisation_id = 1
only. Touches only risk.kri_measurement (inside the assigned `risk` schema).

    Usage:  python seed_gov_risk.py
"""
from __future__ import annotations

import datetime as dt
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

_CONN_CANDIDATES = [
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;",
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Encrypt=no;TrustServerCertificate=yes;Unicode_Results=Yes;",
]


def connect() -> pyodbc.Connection:
    last = None
    for cs in _CONN_CANDIDATES:
        try:
            return pyodbc.connect(cs)
        except Exception as exc:  # noqa: BLE001 - try next driver
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1
RISK_MANAGER_UID = 2  # rmanager — owns enterprise risk; sensible "measured_by"

# Frequency -> spacing between consecutive readings (days), and how many readings
# to generate per KRI. Tuned so the total lands around the planned ~96 rows.
FREQ_PLAN = {
    "DAILY":     (1,  8),   # last 8 days
    "WEEKLY":    (7,  7),   # last 7 weeks
    "MONTHLY":   (30, 6),   # last 6 months
    "QUARTERLY": (91, 5),   # last 5 quarters
    "ANNUAL":    (365, 4),  # last 4 years
}

# Per-KRI realistic reading series, keyed by kri_code. Values are ordered
# OLDEST -> NEWEST. They are deliberately authored to exercise GREEN/AMBER/RED
# bands and tell a small Saudi-government risk story (improving, stable, or
# deteriorating trends). Lengths match FREQ_PLAN counts for the KRI's frequency.
SERIES: dict[str, list[float]] = {
    # Cyber — failed logins/day (DAILY, lower better; G<=50, A<=200, R>200)
    "KRI-CYBER-FLA-001":        [38, 44, 61, 57, 49, 41, 47, 52],
    # Cyber — endpoints unpatched >30d % (WEEKLY; G<=2, A<=5, R<=10/>10)
    "KRI-CYBER-PATCH-001":      [7.4, 6.1, 4.8, 3.9, 3.2, 2.6, 1.8],
    # AML alert aging days (WEEKLY; G<=7, A<=14, R<=21)
    "KRI-AML-AGE-001":          [16, 13, 11, 9, 8, 6, 7],
    # Overdue corrective actions % (MONTHLY; G<=5, A<=10, R<=20)
    "KRI-CAPA-OVERDUE-001":     [13, 11, 9, 8, 6, 4],
    # PDPL DSR response days (WEEKLY; G<=14, A<=25, R<=30)
    "KRI-PDPL-DSR-AGE-001":     [27, 24, 22, 19, 16, 13, 12],
    # Months since last full BCM exercise (MONTHLY; G<=6, A<=9, R<=12)
    "KRI-BCM-EXERCISE-001":     [10, 11, 1, 2, 3, 4],
    # NPL ratio % (MONTHLY; G<=2, A<=4, R<=6)
    "KRI-CREDIT-NPL-001":       [3.8, 3.5, 3.1, 2.7, 2.3, 1.9],
    # Operational incidents/quarter (QUARTERLY; G<=10, A<=20, R<=30)
    "KRI-OPS-INCIDENT-COUNT-001": [24, 19, 17, 13, 9],
    # Vendors RTO exceedance (MONTHLY; G=0, A<=2, R<=5)
    "KRI-VENDOR-RTO-EXCEED-001": [4, 3, 2, 1, 1, 0],
    # AI models with unacknowledged drift (WEEKLY; G=0, A<=1, R<=3)
    "KRI-AI-DRIFT-001":         [3, 2, 2, 1, 1, 0, 0],
    # Scope-3 financed emissions YoY % (ANNUAL; G<=-3, A<=0, R<=3)
    "KRI-ESG-EMISSIONS-001":    [2.5, 0.4, -1.2, -3.4],
    # Regulatory fines/quarter SAR (QUARTERLY; G=0, A<=100k, R<=500k)
    "KRI-REG-FINES-001":        [320000, 150000, 80000, 0, 0],
    # Regulatory obligations overdue % (MONTHLY; G<=2, A<=5, R<=10)
    "KRI-COMPLIANCE-OBLIG-001": [9, 7, 6, 4, 3, 2],
    # Cyber MTTR P1 hours (MONTHLY; G<=4, A<=12, R<=24)
    "KRI-CYBER-MTTR-001":       [22, 18, 14, 9, 6, 4],
    # Liquidity Coverage Ratio % (DAILY, HIGHER better; G>=130, A>=120, R>=110/<110)
    "KRI-LIQUIDITY-LCR-001":    [118, 122, 126, 129, 133, 137, 141, 144],
}


def rag(value: float, direction: str, green: float, amber: float, red: float) -> str:
    """Compute RAG status the way the platform does, honouring direction.

    Thresholds are interpreted as band boundaries. For LOWER_IS_BETTER a smaller
    value is healthier; for HIGHER_IS_BETTER a larger value is healthier.
    Any threshold may be NULL -> that band is skipped.
    """
    g = float(green) if green is not None else None
    a = float(amber) if amber is not None else None
    r = float(red) if red is not None else None

    if direction == "LOWER_IS_BETTER":
        if g is not None and value <= g:
            return "GREEN"
        if a is not None and value <= a:
            return "AMBER"
        return "RED"
    else:  # HIGHER_IS_BETTER
        if g is not None and value >= g:
            return "GREEN"
        if a is not None and value >= a:
            return "AMBER"
        return "RED"


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    # Load active KRI definitions for org 1.
    cur.execute(
        """SELECT id, kri_code, title_en, value_direction,
                  green_threshold, amber_threshold, red_threshold, frequency
             FROM risk.kri_definition
            WHERE organisation_id = ? AND is_deleted = 0 AND is_active = 1
            ORDER BY id""",
        ORG,
    )
    defs = cur.fetchall()
    print(f"Active KRI definitions for org {ORG}: {len(defs)}\n")

    # Anchor "now" to a stable date for deterministic, idempotent measured_at.
    # Use midnight so a re-run produces identical timestamps.
    now = dt.datetime(2026, 6, 27, 9, 0, 0)

    inserted = skipped_existing = 0
    by_status = {"GREEN": 0, "AMBER": 0, "RED": 0}

    for (kri_id, code, title, direction, green, amber, red, freq) in defs:
        series = SERIES.get(code)
        if series is None:
            print(f"  ! no series authored for {code} — skipped")
            continue
        spacing_days, planned = FREQ_PLAN.get(freq, (30, 6))
        # readings ordered oldest->newest; oldest is (n-1)*spacing days before now
        n = len(series)
        latest_status = None
        added = 0
        for i, value in enumerate(series):
            offset_days = (n - 1 - i) * spacing_days
            measured_at = now - dt.timedelta(days=offset_days)
            status = rag(value, direction, green, amber, red)
            latest_status = status

            # Idempotency guard: natural key (org, kri_id, measured_at).
            cur.execute(
                """SELECT 1 FROM risk.kri_measurement
                    WHERE organisation_id = ? AND kri_id = ? AND measured_at = ?""",
                ORG, kri_id, measured_at,
            )
            if cur.fetchone():
                skipped_existing += 1
                continue

            note_en = f"Periodic {freq.lower()} reading for {title}."
            note_ar = "قراءة دورية لمؤشر المخاطر الرئيسي ضمن متابعة لجنة إدارة المخاطر."

            cur.execute(
                """INSERT INTO risk.kri_measurement
                       (organisation_id, kri_id, measured_at, value, status,
                        source, measured_by_user_id, notes_en, notes_ar,
                        is_deleted, created_at, created_by)
                   VALUES (?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?, 0, SYSUTCDATETIME(), ?)""",
                ORG, kri_id, measured_at, value, status,
                RISK_MANAGER_UID, note_en, note_ar, RISK_MANAGER_UID,
            )
            inserted += 1
            added += 1
            by_status[status] += 1

        print(f"  + {code:<28} {added:>2} readings  (latest {latest_status})")

    print(f"\nDone. inserted={inserted}, skipped_existing={skipped_existing}")
    print(f"Status mix of inserted rows: {by_status}")

    # ── Verification ─────────────────────────────────────────────────────────
    cur.execute(
        "SELECT COUNT(*) FROM risk.kri_measurement WHERE organisation_id = ? AND is_deleted = 0",
        ORG,
    )
    total = cur.fetchone()[0]
    print(f"\nTotal live measurements for org {ORG}: {total}")

    print("\nLatest reading per KRI (RAG snapshot for the Risk Committee):\n")
    cur.execute(
        """WITH latest AS (
               SELECT m.kri_id, m.value, m.status, m.measured_at,
                      ROW_NUMBER() OVER (PARTITION BY m.kri_id
                                         ORDER BY m.measured_at DESC) AS rn
                 FROM risk.kri_measurement m
                WHERE m.organisation_id = ? AND m.is_deleted = 0
           )
           SELECT d.kri_code, d.title_en, l.value, l.status,
                  CONVERT(varchar(10), l.measured_at, 23) AS d
             FROM latest l
             JOIN risk.kri_definition d ON d.id = l.kri_id
            WHERE l.rn = 1
            ORDER BY CASE l.status WHEN 'RED' THEN 0 WHEN 'AMBER' THEN 1
                                   WHEN 'GREEN' THEN 2 ELSE 3 END, d.kri_code""",
        ORG,
    )
    for code, title, value, status, mdate in cur.fetchall():
        print(f"  [{status:<5}] {code:<28} {float(value):>12.2f}  ({mdate})  {title}")

    conn.close()


if __name__ == "__main__":
    main()
