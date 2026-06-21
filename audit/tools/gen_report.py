#!/usr/bin/env python3
# Generate a single self-contained HTML audit report from audit/all_findings.json + audit/metrics.json
import json, os, html, datetime

ROOT = r"D:\AYMAN\GRC"
def load(p):
    with open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return json.load(f)

findings = load("audit/all_findings.json")
M = load("audit/metrics.json")

SEVS = ["Critical","High","Medium","Low","Info"]
PHASES = ["Security","Quality","Performance","Functional","E2E","UX","Accessibility"]
PHASE_TITLE = {"Security":"Phase 1 — Security","Quality":"Phase 2 — Code Quality",
               "Performance":"Phase 3 — Stress / Performance","Functional":"Phase 4 — Functional (Unit + Integration)",
               "E2E":"Phase 5 — End-to-End & Workflow","UX":"Phase 6 — User Experience","Accessibility":"Phase 7 — Accessibility (WCAG 2.1 AA)"}
SEV_COLOR = {"Critical":"#7f1d1d","High":"#dc2626","Medium":"#d97706","Low":"#ca8a04","Info":"#2563eb"}
SEV_BG    = {"Critical":"#fee2e2","High":"#fee2e2","Medium":"#fef3c7","Low":"#fefce8","Info":"#dbeafe"}
def e(x): return html.escape(str(x if x is not None else ""))

# ---- counts ----
matrix = {p:{s:0 for s in SEVS} for p in PHASES}
for f in findings:
    matrix.setdefault(f["phase"],{s:0 for s in SEVS})
    matrix[f["phase"]][f["severity"]] += 1
sev_tot = {s: sum(matrix[p][s] for p in matrix) for s in SEVS}
total = len(findings)
present_phases = [p for p in PHASES if sum(matrix[p].values())>0]

# health rating
if sev_tot["Critical"]>0: rating, rcolor = "HIGH RISK", "#7f1d1d"
elif sev_tot["High"]>=5:  rating, rcolor = "ELEVATED RISK", "#dc2626"
elif sev_tot["High"]>0:   rating, rcolor = "MODERATE RISK", "#d97706"
else:                     rating, rcolor = "LOW RISK", "#16a34a"

now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

def badge(sev):
    return f'<span class="badge" style="background:{SEV_BG[sev]};color:{SEV_COLOR[sev]};border:1px solid {SEV_COLOR[sev]}">{e(sev)}</span>'

def code_block(label, text):
    if not text: return ""
    return f'<div class="cb-label">{e(label)}</div><pre class="code">{e(text)}</pre>'

# ---- finding card ----
def card(f):
    locs = f.get("locations") or []
    loc_html = "".join(f'<div class="loc">{e(l)}</div>' for l in locs)
    refs = f.get("references") or []
    ref_html = " · ".join(e(r) for r in refs)
    search_blob = e(" ".join([f["id"],f["title"],f["category"],f["file"],f.get("description","")]).lower())
    ver = f.get("_verification")
    ver_chip = ""
    ver_line = ""
    if ver:
        st = ver.get("status","")
        vc = {"confirmed":"#16a34a","adjusted":"#d97706","refuted":"#64748b"}.get(st,"#64748b")
        vlabel = {"confirmed":"✓ Verified","adjusted":"✎ Adjusted","refuted":"✗ Refuted"}.get(st,st)
        ver_chip = f'<span class="chip" style="background:{vc};color:#fff" title="adversarially verified">{vlabel}</span>'
        moved = "" if ver.get("from")==ver.get("to") else f' (severity {e(ver.get("from"))}→{e(ver.get("to"))})'
        ver_line = f'<div class="verline"><b>Adversarial verification:</b> {e(st)}{moved} — {e(ver.get("rationale"))}</div>'
    patch = ""
    if f.get("patchBefore") or f.get("patchAfter"):
        patch = f'''<div class="patch">
          <div class="patch-col"><div class="cb-label bad">▼ Before (vulnerable)</div><pre class="code before">{e(f.get("patchBefore"))}</pre></div>
          <div class="patch-col"><div class="cb-label good">▲ After (fixed)</div><pre class="code after">{e(f.get("patchAfter"))}</pre></div>
        </div>'''
    return f'''
<div class="finding" data-sev="{e(f["severity"])}" data-phase="{e(f["phase"])}" data-search="{search_blob}">
  <div class="finding-head" onclick="this.parentElement.classList.toggle('collapsed')">
    <div class="fh-left">{badge(f["severity"])}<span class="fid">{e(f["id"])}</span><span class="ftitle">{e(f["title"])}</span></div>
    <div class="fh-right">{ver_chip}<span class="chip">{e(f["phase"])}</span><span class="chip">{e(f["category"])}</span><span class="caret">▾</span></div>
  </div>
  <div class="finding-body">
    {ver_line}
    <div class="meta-row">
      <span class="kv"><b>Location</b> <code>{e(f["file"])}:{e(f["lines"])}</code></span>
      <span class="kv"><b>Effort</b> {e(f.get("effort"))}</span>
      <span class="kv"><b>Risk</b> {e(f.get("cvss"))}</span>
    </div>
    {('<div class="extra-locs">'+loc_html+'</div>') if loc_html else ''}
    <h4>Description</h4><p>{e(f.get("description"))}</p>
    <h4>Root cause</h4><p>{e(f.get("rootCause"))}</p>
    {code_block("Evidence / proof", f.get("evidence"))}
    <h4>Real-world impact</h4><p>{e(f.get("impact"))}</p>
    <h4>Reproduction</h4><pre class="code">{e(f.get("reproduction"))}</pre>
    <h4>Resolution</h4><p>{e(f.get("resolution"))}</p>
    {patch}
    {('<div class="refs"><b>References:</b> '+ref_html+'</div>') if ref_html else ''}
  </div>
</div>'''

# ---- exec summary chart (pure CSS bars) ----
def sev_bar():
    out = ['<div class="sevbar">']
    mx = max(sev_tot.values()) or 1
    for s in SEVS:
        w = int(sev_tot[s]/mx*100)
        out.append(f'<div class="sb-row"><span class="sb-label">{badge(s)}</span>'
                   f'<span class="sb-track"><span class="sb-fill" style="width:{w}%;background:{SEV_COLOR[s]}"></span></span>'
                   f'<span class="sb-num">{sev_tot[s]}</span></div>')
    out.append('</div>')
    return "".join(out)

# severity x phase table
def matrix_table():
    rows = []
    for p in present_phases:
        cells = "".join(f'<td>{matrix[p][s] or "·"}</td>' for s in SEVS)
        rows.append(f'<tr><td class="lcell">{e(PHASE_TITLE[p])}</td>{cells}<td><b>{sum(matrix[p].values())}</b></td></tr>')
    head = "".join(f'<th>{badge(s)}</th>' for s in SEVS)
    foot = "".join(f'<td><b>{sev_tot[s]}</b></td>' for s in SEVS)
    return f'''<table class="matrix"><thead><tr><th>Phase</th>{head}<th>Total</th></tr></thead>
      <tbody>{''.join(rows)}</tbody>
      <tfoot><tr><td class="lcell"><b>All phases</b></td>{foot}<td><b>{total}</b></td></tr></tfoot></table>'''

# top actions = Critical then High, up to 12
top = [f for f in findings if f["severity"]=="Critical"] + [f for f in findings if f["severity"]=="High"]
top_actions = "".join(f'<li>{badge(f["severity"])} <code>{e(f["id"])}</code> {e(f["title"])} '
                      f'<span class="ta-loc">{e(f["file"])}:{e(f["lines"])}</span></li>' for f in top[:12])

# ---- metrics section ----
def kpi(label,val,sub=""):
    return f'<div class="kpi"><div class="kpi-val">{e(val)}</div><div class="kpi-label">{e(label)}</div>{("<div class=kpi-sub>"+e(sub)+"</div>") if sub else ""}</div>'
t=M["tests"]; q=M["quality"]; dep=M["dependencies"]; a=M["accessibility"]; lo=M["loc"]; be=M["backend"]; k=M.get("load_test_k6",{})
metrics_kpis = "".join([
    kpi("Total app LOC", f'{lo["total_app"]:,}', f'{lo["java"]:,} Java · {lo["tsx"]+lo["ts"]:,} TS/TSX · {lo["sql"]:,} SQL'),
    kpi("REST endpoints", f'{be["endpoints"]:,}', f'{be["controllers"]} controllers · {be["module_packages"]} modules'),
    kpi("Backend tests", f'{t["backend_tests"]:,} pass', f'{t["backend_fail"]} fail · {t["backend_skipped"]} skipped'),
    kpi("Backend coverage", f'{t["coverage_line_pct"]}% line', f'{t["coverage_instruction_pct"]}% instr · {t["coverage_branch_pct"]}% branch'),
    kpi("Frontend tests", f'{t["frontend_tests"]}', f'no runner · {t["frontend_ts_errors"]} TS errors'),
    kpi("Code duplication", f'{q["duplication_pct"]}%', f'{q["clones"]:,} clones · {q["duplicated_lines"]:,} lines'),
    kpi("Dependency CVEs", f'{dep["trivy_vulns"]} (Trivy)', f'{dep["npm_audit_total"]} npm ({dep["npm_audit_high"]}H/{dep["npm_audit_moderate"]}M/{dep["npm_audit_low"]}L)'),
    kpi("Secrets (gitleaks)", f'{M["secrets"]["gitleaks_hits"]} hits', 'mostly dev defaults'),
    kpi("a11y (axe)", f'{a["axe_color_contrast_instances"]} contrast', f'across {a["axe_pages_scanned"]} pages · login 0'),
    kpi("Lighthouse /login", f'Perf {a["lighthouse_login"]["performance"]}', f'A11y {a["lighthouse_login"]["accessibility"]} · BP {a["lighthouse_login"]["best_practices"]}'),
    kpi("Load test p95", f'{k.get("latency_p95_ms","?")} ms', f'{k.get("avg_req_per_s","?")} req/s · {k.get("error_rate_pct","?")}% err'),
])
tools_list = "".join(f'<li>{e(x)}</li>' for x in M["tools_used"])

# ---- remediation plan (all findings, prioritized) ----
def remediation():
    rows=[]
    for f in [x for s in SEVS for x in findings if x["severity"]==s]:
        rows.append(f'<tr data-sev="{e(f["severity"])}"><td>{badge(f["severity"])}</td><td><code>{e(f["id"])}</code></td>'
                    f'<td>{e(f["phase"])}</td><td>{e(f["title"])}</td><td><code>{e(f["file"])}:{e(f["lines"])}</code></td><td>{e(f.get("effort"))}</td></tr>')
    return ("<table class='matrix remed'><thead><tr><th>Sev</th><th>ID</th><th>Phase</th><th>Finding</th><th>Location</th><th>Effort</th></tr></thead>"
            f"<tbody>{''.join(rows)}</tbody></table>")

# ---- sidebar ----
side = ['<a href="#summary">Executive Summary</a>','<a href="#metrics">Metrics &amp; Scope</a>']
for p in present_phases:
    side.append(f'<a href="#phase-{p}">{e(PHASE_TITLE[p])} <span class="navcount">{sum(matrix[p].values())}</span></a>')
side.append('<a href="#remediation">Remediation Plan</a>')
sidebar = "".join(side)

# ---- phase sections ----
sections=[]
for p in present_phases:
    cards = "".join(card(f) for f in findings if f["phase"]==p)
    sub = " · ".join(f'{matrix[p][s]} {s}' for s in SEVS if matrix[p][s])
    sections.append(f'''<section id="phase-{p}"><div class="sec-head"><h2>{e(PHASE_TITLE[p])}</h2><div class="sec-sub">{sum(matrix[p].values())} findings — {sub}</div></div>{cards}</section>''')
sections_html = "".join(sections)

CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1e293b;background:#f1f5f9;line-height:1.5}
code,pre,.fid{font-family:ui-monospace,SFMono-Regular,Consolas,Menlo,monospace}
a{color:#2563eb;text-decoration:none}
.layout{display:flex}
.sidebar{position:sticky;top:0;align-self:flex-start;height:100vh;overflow:auto;width:280px;background:#0f172a;color:#cbd5e1;padding:20px 14px;flex:0 0 280px}
.sidebar h1{font-size:15px;color:#fff;margin:0 0 4px}
.sidebar .sub{font-size:11px;color:#64748b;margin-bottom:16px}
.sidebar a{display:flex;justify-content:space-between;gap:8px;color:#cbd5e1;padding:7px 10px;border-radius:6px;font-size:13px;margin-bottom:2px}
.sidebar a:hover{background:#1e293b;color:#fff}
.navcount{background:#334155;color:#e2e8f0;border-radius:10px;padding:0 7px;font-size:11px}
.main{flex:1;min-width:0;padding:28px 34px;max-width:1200px}
.title-block{background:linear-gradient(135deg,#0f172a,#1e3a5f);color:#fff;padding:28px 30px;border-radius:14px;margin-bottom:22px}
.title-block h1{margin:0 0 6px;font-size:26px}
.title-block .meta{font-size:13px;color:#cbd5e1;display:flex;flex-wrap:wrap;gap:18px;margin-top:12px}
.rating{display:inline-block;padding:6px 16px;border-radius:8px;font-weight:700;letter-spacing:.05em;color:#fff;margin-top:14px}
.controls{position:sticky;top:0;z-index:20;background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:12px 14px;margin-bottom:20px;display:flex;flex-wrap:wrap;gap:10px;align-items:center;box-shadow:0 1px 3px rgba(0,0,0,.06)}
.controls input[type=search]{flex:1;min-width:200px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:13px}
.controls button,.fbtn{padding:6px 11px;border:1px solid #cbd5e1;background:#fff;border-radius:7px;font-size:12px;cursor:pointer}
.fbtn.active{background:#0f172a;color:#fff;border-color:#0f172a}
.fbtn[data-sev]{font-weight:600}
section{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:8px 22px 22px;margin-bottom:22px}
.sec-head{position:sticky;top:64px;background:#fff;padding:16px 0 10px;border-bottom:2px solid #e2e8f0;margin-bottom:14px;z-index:10}
.sec-head h2{margin:0;font-size:20px}
.sec-sub{font-size:12px;color:#64748b;margin-top:3px}
h2,h3{color:#0f172a}
.kpis{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px;margin:16px 0}
.kpi{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:14px}
.kpi-val{font-size:22px;font-weight:700;color:#0f172a}
.kpi-label{font-size:12px;color:#475569;margin-top:2px;font-weight:600}
.kpi-sub{font-size:11px;color:#94a3b8;margin-top:4px}
.badge{display:inline-block;padding:1px 9px;border-radius:11px;font-size:11px;font-weight:700;letter-spacing:.02em}
.matrix{width:100%;border-collapse:collapse;font-size:13px;margin:12px 0}
.matrix th,.matrix td{border:1px solid #e2e8f0;padding:7px 10px;text-align:center}
.matrix th{background:#f1f5f9}
.matrix td.lcell{text-align:left}
.matrix tbody tr:nth-child(even){background:#f8fafc}
.matrix tfoot td{background:#f1f5f9;font-weight:700}
.remed td:nth-child(4){text-align:left}
.sevbar{margin:14px 0}
.sb-row{display:flex;align-items:center;gap:10px;margin-bottom:7px}
.sb-label{width:90px}.sb-num{width:42px;text-align:right;font-weight:700}
.sb-track{flex:1;background:#f1f5f9;border-radius:6px;overflow:hidden;height:18px}
.sb-fill{display:block;height:100%}
.two-col{display:grid;grid-template-columns:1fr 1fr;gap:20px}
.panel{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:16px}
.panel h3{margin-top:0;font-size:15px}
.top-actions{margin:0;padding-left:0;list-style:none}
.top-actions li{padding:7px 0;border-bottom:1px solid #eef2f7;font-size:13px}
.ta-loc{color:#94a3b8;font-size:11px;font-family:monospace}
.finding{border:1px solid #e2e8f0;border-left-width:5px;border-radius:10px;margin-bottom:12px;overflow:hidden}
.finding[data-sev=Critical]{border-left-color:#7f1d1d}
.finding[data-sev=High]{border-left-color:#dc2626}
.finding[data-sev=Medium]{border-left-color:#d97706}
.finding[data-sev=Low]{border-left-color:#ca8a04}
.finding[data-sev=Info]{border-left-color:#2563eb}
.finding-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:12px 16px;cursor:pointer;background:#fff}
.finding-head:hover{background:#f8fafc}
.fh-left{display:flex;align-items:center;gap:10px;min-width:0}
.fid{font-size:12px;color:#64748b;font-weight:600}
.ftitle{font-weight:600;font-size:14px}
.fh-right{display:flex;align-items:center;gap:6px;flex:0 0 auto}
.chip{background:#f1f5f9;color:#475569;border-radius:6px;padding:2px 8px;font-size:11px}
.caret{color:#94a3b8;transition:transform .15s}
.finding.collapsed .caret{transform:rotate(-90deg)}
.finding.collapsed .finding-body{display:none}
.finding-body{padding:6px 18px 18px;border-top:1px solid #f1f5f9}
.finding-body h4{margin:14px 0 4px;font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:#64748b}
.finding-body p{margin:0;font-size:13.5px}
.meta-row{display:flex;flex-wrap:wrap;gap:18px;margin:10px 0;font-size:12.5px}
.kv b{color:#64748b;font-weight:600;margin-right:4px;text-transform:uppercase;font-size:11px}
.extra-locs{margin:6px 0}.loc{font-family:monospace;font-size:12px;color:#475569}
.code{background:#0f172a;color:#e2e8f0;padding:12px 14px;border-radius:8px;overflow:auto;font-size:12px;white-space:pre-wrap;word-break:break-word;margin:6px 0}
.cb-label{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:#64748b;margin-top:8px}
.cb-label.bad{color:#dc2626}.cb-label.good{color:#16a34a}
.patch{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:8px}
.code.before{background:#1f1416;border:1px solid #7f1d1d}
.code.after{background:#0f1f16;border:1px solid #166534}
.refs{margin-top:12px;font-size:12px;color:#475569}
.verline{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:7px;padding:8px 12px;font-size:12.5px;margin:8px 0;color:#14532d}
.hide{display:none!important}
@media(max-width:900px){.two-col,.patch,.kpis{grid-template-columns:1fr}.sidebar{display:none}}
@media print{
  .sidebar,.controls{display:none!important}
  body{background:#fff}.main{max-width:100%;padding:0}
  .finding-body{display:block!important}.finding{break-inside:avoid;border:1px solid #999}
  .code{background:#f5f5f5;color:#000;border:1px solid #ccc}.code.before,.code.after{background:#f5f5f5;color:#000}
  section,.title-block{box-shadow:none}
}
"""

JS = """
const $=s=>document.querySelectorAll(s);
let activeSev='all', q='';
function apply(){
  document.querySelectorAll('.finding').forEach(f=>{
    const okS = activeSev==='all' || f.dataset.sev===activeSev;
    const okQ = !q || f.dataset.search.includes(q);
    f.classList.toggle('hide', !(okS&&okQ));
  });
  // hide empty sections
  document.querySelectorAll('section[id^=phase-]').forEach(sec=>{
    const any=[...sec.querySelectorAll('.finding')].some(f=>!f.classList.contains('hide'));
    sec.classList.toggle('hide', !any);
  });
}
function setSev(b,sev){document.querySelectorAll('.fbtn[data-sev]').forEach(x=>x.classList.remove('active'));b.classList.add('active');activeSev=sev;apply();}
function search(v){q=v.toLowerCase().trim();apply();}
function expandAll(x){document.querySelectorAll('.finding').forEach(f=>f.classList.toggle('collapsed',!x));}
document.addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('.finding').forEach(f=>f.classList.add('collapsed'));});
"""

filter_btns = '<button class="fbtn active" data-sev="all" onclick="setSev(this,\'all\')">All ('+str(total)+')</button>' + \
    "".join(f'<button class="fbtn" data-sev="{s}" onclick="setSev(this,\'{s}\')" style="color:{SEV_COLOR[s]}">{s} ({sev_tot[s]})</button>' for s in SEVS)

HTML = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TSS GRC Platform — Audit Report</title><style>{CSS}</style></head>
<body><div class="layout">
<nav class="sidebar"><h1>TSS GRC Audit</h1><div class="sub">{e(M['commit'])} · {now}</div>{sidebar}</nav>
<div class="main">
<div class="title-block">
  <h1>TSS GRC Platform — Comprehensive Audit Report</h1>
  <div>Security · Code Quality · Performance · Functional · E2E · UX · Accessibility</div>
  <div class="meta">
    <span><b>Project:</b> {e(M['project'])}</span><span><b>Commit:</b> {e(M['commit'])}</span>
    <span><b>Generated:</b> {now}</span><span><b>Total findings:</b> {total}</span>
  </div>
  <div><b>Scope:</b> <span style="font-size:13px;color:#cbd5e1">{e(M['scope'])}</span></div>
  <div class="rating" style="background:{rcolor}">OVERALL: {rating}</div>
</div>

<div class="controls">
  <input type="search" placeholder="Search findings by keyword, file, id…" oninput="search(this.value)">
  {filter_btns}
  <button class="fbtn" onclick="expandAll(true)">Expand all</button>
  <button class="fbtn" onclick="expandAll(false)">Collapse all</button>
</div>

<section id="summary"><div class="sec-head"><h2>Executive Summary</h2><div class="sec-sub">{total} findings · {sev_tot['Critical']} Critical · {sev_tot['High']} High · {sev_tot['Medium']} Medium · {sev_tot['Low']} Low · {sev_tot['Info']} Info</div></div>
  <div class="two-col">
    <div class="panel"><h3>Severity distribution</h3>{sev_bar()}</div>
    <div class="panel"><h3>Findings by phase &amp; severity</h3>{matrix_table()}</div>
  </div>
  <div class="panel" style="margin-top:18px"><h3>Top prioritized actions (Critical &amp; High)</h3><ul class="top-actions">{top_actions}</ul></div>
</section>

<section id="metrics"><div class="sec-head"><h2>Metrics &amp; Scope</h2><div class="sec-sub">Quantified baseline across the codebase and all tool runs</div></div>
  <div class="kpis">{metrics_kpis}</div>
  <div class="two-col">
    <div class="panel"><h3>Tools executed</h3><ul style="font-size:13px;margin:0;padding-left:18px">{tools_list}</ul></div>
    <div class="panel"><h3>Tooling notes &amp; honesty caveats</h3><p style="font-size:13px">{e(M['tool_notes'])}</p>
      <p style="font-size:13px">{e(M['load_test_k6']['note'])}</p></div>
  </div>
</section>

{sections_html}

<section id="remediation"><div class="sec-head"><h2>Consolidated Remediation Plan</h2><div class="sec-sub">All {total} findings, prioritized by severity. Filter buttons above also filter this table.</div></div>
{remediation()}
</section>

<div style="text-align:center;color:#94a3b8;font-size:12px;padding:20px">Generated offline · single self-contained file · no external dependencies</div>
</div></div>
<script>{JS}
// also filter remediation rows
const _apply=apply;apply=function(){{_apply();document.querySelectorAll('.remed tbody tr').forEach(r=>{{const okS=activeSev==='all'||r.dataset.sev===activeSev;r.classList.toggle('hide',!okS);}});}};
</script>
</body></html>"""

os.makedirs(os.path.join(ROOT,"reports"), exist_ok=True)
out = os.path.join(ROOT,"reports","audit-report.html")
with open(out,"w",encoding="utf-8") as f:
    f.write(HTML)
print(f"WROTE {out}  ({len(HTML):,} bytes, {total} findings)")
print(f"Severity totals: " + " · ".join(f"{s}={sev_tot[s]}" for s in SEVS))
