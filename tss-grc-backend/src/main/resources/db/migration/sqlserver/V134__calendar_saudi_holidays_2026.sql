-- V134: Saudi public holidays seed for 2026
--
-- Saudi public holidays come from two registers:
--   - Fixed Gregorian dates: Founding Day (Feb 22), National Day (Sep 23)
--   - Hijri-derived dates: Eid Al-Fitr (1 Shawwal), Eid Al-Adha
--     (10 Dhul-Hijjah). Hijri dates shift ~10 days earlier each
--     Gregorian year and the exact date can move ±1 day depending
--     on moon sighting — the Saudi Council of Senior Scholars makes
--     the official announcement ~24h before. This migration uses the
--     UMM AL-QURA tabular calendar's most-likely Gregorian dates for
--     1447 AH (covering most of 2026). Year-of-event refresh required
--     before each new Gregorian year — track via a quarterly NDI
--     calendar maintenance task.
--
-- Each holiday is stored with is_holiday=1, event_type='PUBLIC_HOLIDAY',
-- recurrence='NONE' (annual refresh, not RRULE-driven), severity NULL,
-- and organisation_id=1 (demo tenant). New tenants copy from this
-- baseline at provisioning time via the CalendarService.bootstrapTenant()
-- helper (idempotent on rule_code-equivalent uniqueness — see V133's
-- ux_calendar_events_external index).
--
-- The Saudi week pattern (Sun-Thu working, Fri-Sat weekend) is NOT
-- stored as data — that's a code-level constant in CalendarService.
-- Only actual no-work days (Eid days, National Day, etc.) sit here.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ── 2026 Saudi public holidays ───────────────────────────────────────

-- Founding Day — fixed 22 February. Established 2022 to mark the
-- founding of the First Saudi State in 1727. Day off for public sector.
IF NOT EXISTS (SELECT 1 FROM platform.calendar_events
               WHERE organisation_id = @org AND is_holiday = 1
                 AND starts_at = '2026-02-22T00:00:00')
BEGIN
    INSERT INTO platform.calendar_events
        (organisation_id, title_en, title_ar,
         description_en, description_ar,
         event_type, starts_at, ends_at, all_day, is_holiday)
    VALUES
        (@org,
         N'Founding Day',
         N'يوم التأسيس',
         N'Public holiday marking the founding of the First Saudi State (Diriyah, 1727). Established by Royal Order in 2022.',
         N'إجازة رسمية بمناسبة تأسيس الدولة السعودية الأولى (الدرعية، 1727م). صدر بأمر ملكي عام 2022م.',
         N'PUBLIC_HOLIDAY',
         '2026-02-22T00:00:00', '2026-02-22T23:59:59', 1, 1);
END

-- Eid Al-Fitr 1447 AH — likely 1 Shawwal lands ~20 March 2026.
-- Saudi public sector observes 4 consecutive days (29 Ramadan / Day-1
-- of Eid Al-Fitr + 3 days). Final dates confirmed ~24h before by
-- the Council of Senior Scholars based on moon sighting.
DECLARE @eid_fitr DATE;
SET @eid_fitr = '2026-03-20';  -- Provisional — refresh before Feb 2026.

IF NOT EXISTS (SELECT 1 FROM platform.calendar_events
               WHERE organisation_id = @org AND is_holiday = 1
                 AND title_en = N'Eid Al-Fitr (Day 1)')
BEGIN
    INSERT INTO platform.calendar_events
        (organisation_id, title_en, title_ar,
         description_en, description_ar,
         event_type, starts_at, ends_at, all_day, is_holiday)
    VALUES
        (@org, N'Eid Al-Fitr (Day 1)', N'عيد الفطر — اليوم الأول',
         N'Eid Al-Fitr — feast marking the end of Ramadan. Provisional date — confirmed by Council of Senior Scholars on moon sighting.',
         N'عيد الفطر — احتفال نهاية شهر رمضان. التاريخ مبدئي — يُعتمد رسمياً بإعلان هيئة كبار العلماء حسب رؤية الهلال.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 0, @eid_fitr), DATEADD(second, -1, DATEADD(day, 1, @eid_fitr)), 1, 1),
        (@org, N'Eid Al-Fitr (Day 2)', N'عيد الفطر — اليوم الثاني',
         N'Second day of Eid Al-Fitr. Provisional date.',
         N'اليوم الثاني من عيد الفطر. التاريخ مبدئي.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 1, @eid_fitr), DATEADD(second, -1, DATEADD(day, 2, @eid_fitr)), 1, 1),
        (@org, N'Eid Al-Fitr (Day 3)', N'عيد الفطر — اليوم الثالث',
         N'Third day of Eid Al-Fitr.', N'اليوم الثالث من عيد الفطر.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 2, @eid_fitr), DATEADD(second, -1, DATEADD(day, 3, @eid_fitr)), 1, 1),
        (@org, N'Eid Al-Fitr (Day 4)', N'عيد الفطر — اليوم الرابع',
         N'Fourth day of Eid Al-Fitr.', N'اليوم الرابع من عيد الفطر.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 3, @eid_fitr), DATEADD(second, -1, DATEADD(day, 4, @eid_fitr)), 1, 1);
END

-- Eid Al-Adha 1447 AH — 10 Dhul-Hijjah ≈ 27 May 2026. Saudi public
-- sector observes 4 consecutive days (Day of Arafah + 3 Eid days).
DECLARE @eid_adha DATE;
SET @eid_adha = '2026-05-26';  -- Day of Arafah; Eid Day 1 = 27 May.

IF NOT EXISTS (SELECT 1 FROM platform.calendar_events
               WHERE organisation_id = @org AND is_holiday = 1
                 AND title_en = N'Day of Arafah')
BEGIN
    INSERT INTO platform.calendar_events
        (organisation_id, title_en, title_ar,
         description_en, description_ar,
         event_type, starts_at, ends_at, all_day, is_holiday)
    VALUES
        (@org, N'Day of Arafah', N'يوم عرفة',
         N'Day before Eid Al-Adha. Public holiday in Saudi Arabia.',
         N'اليوم السابق لعيد الأضحى. إجازة رسمية في المملكة العربية السعودية.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 0, @eid_adha), DATEADD(second, -1, DATEADD(day, 1, @eid_adha)), 1, 1),
        (@org, N'Eid Al-Adha (Day 1)', N'عيد الأضحى — اليوم الأول',
         N'Eid Al-Adha — feast of the sacrifice. Provisional date — confirmed by Council of Senior Scholars.',
         N'عيد الأضحى — احتفال النحر. التاريخ مبدئي — يُعتمد رسمياً بإعلان هيئة كبار العلماء.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 1, @eid_adha), DATEADD(second, -1, DATEADD(day, 2, @eid_adha)), 1, 1),
        (@org, N'Eid Al-Adha (Day 2)', N'عيد الأضحى — اليوم الثاني',
         N'Second day of Eid Al-Adha.', N'اليوم الثاني من عيد الأضحى.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 2, @eid_adha), DATEADD(second, -1, DATEADD(day, 3, @eid_adha)), 1, 1),
        (@org, N'Eid Al-Adha (Day 3)', N'عيد الأضحى — اليوم الثالث',
         N'Third day of Eid Al-Adha.', N'اليوم الثالث من عيد الأضحى.',
         N'PUBLIC_HOLIDAY',
         DATEADD(day, 3, @eid_adha), DATEADD(second, -1, DATEADD(day, 4, @eid_adha)), 1, 1);
END

-- Saudi National Day — fixed 23 September. Marks the unification of
-- the Kingdom in 1932 by King Abdulaziz. Single day off for the
-- public sector.
IF NOT EXISTS (SELECT 1 FROM platform.calendar_events
               WHERE organisation_id = @org AND is_holiday = 1
                 AND starts_at = '2026-09-23T00:00:00')
BEGIN
    INSERT INTO platform.calendar_events
        (organisation_id, title_en, title_ar,
         description_en, description_ar,
         event_type, starts_at, ends_at, all_day, is_holiday)
    VALUES
        (@org,
         N'Saudi National Day',
         N'اليوم الوطني السعودي',
         N'National Day — commemorates the unification of the Kingdom of Saudi Arabia by King Abdulaziz in 1932.',
         N'اليوم الوطني — إحياء ذكرى توحيد المملكة العربية السعودية على يد الملك عبدالعزيز عام 1932م.',
         N'PUBLIC_HOLIDAY',
         '2026-09-23T00:00:00', '2026-09-23T23:59:59', 1, 1);
END
GO

PRINT 'V134 Saudi public holidays for 2026 seeded (10 rows: Founding Day, Eid Al-Fitr x4, Day of Arafah, Eid Al-Adha x3, National Day).';
PRINT 'NOTE: Eid dates are PROVISIONAL — Council of Senior Scholars announces final dates ~24h before based on moon sighting.';
GO
