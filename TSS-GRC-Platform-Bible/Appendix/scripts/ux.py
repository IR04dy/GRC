# Measure the FE's real bilingual/RTL/UX posture.
import os, re, collections, json
FE = r"D:\AYMAN\GRC\tss-grc-ui-v2\src"

print("=" * 92)
print("1. i18n plumbing — does a translation layer exist, and how big is it?")
cands = []
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if re.search(r"(i18n|locale|translat|lang|messages|ar\.json|en\.json)", fn, re.I):
            p = os.path.join(dp, fn)
            cands.append((os.path.relpath(p, FE).replace("\\", "/"),
                          open(p, encoding="utf-8", errors="replace").read().count("\n") + 1))
for c in sorted(cands, key=lambda x: -x[1])[:20]: print(f"   {c[1]:6}  {c[0]}")
if not cands: print("   NO i18n / locale / translation file found anywhere in src/")

print("=" * 92)
print("2. How is language actually switched? (search for the mechanism)")
pat = collections.Counter()
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if not fn.endswith((".ts", ".tsx")): continue
        src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
        for k, rx in [("useTranslation()", r"useTranslation\("), ("t('key')", r"\bt\(\s*['\"]"),
                      ("i18n.", r"\bi18n\."), ("lang ===", r"lang(uage)?\s*===\s*['\"]"),
                      ("dir=", r"\bdir\s*=\s*[{\"']"), ("dir: 'rtl'", r"rtl"),
                      ("isArabic", r"isArabic|isAr\b"), ("_ar field", r"\w+Ar\b|_ar\b"),
                      ("LanguageContext", r"Language(Context|Provider)")]:
            n = len(re.findall(rx, src))
            if n: pat[k] += n
for k, v in pat.most_common(): print(f"   {v:6}  {k}")

print("=" * 92)
print("3. Hardcoded user-visible English in page JSX (untranslatable strings)")
# JSX text nodes and common label props with Latin-only content
jsx_text = re.compile(r">\s*([A-Z][A-Za-z][A-Za-z \-/&'(),.:%]{3,60})\s*<")
label_prop = re.compile(r"(?:label|title|placeholder|header)\s*=\s*[\"']([A-Z][A-Za-z][^\"']{3,60})[\"']")
tot_text = tot_lbl = 0; worst = collections.Counter(); arabic_pages = 0; pages = 0
AR = re.compile(r"[\u0600-\u06FF]")
for dp, _, fns in os.walk(os.path.join(FE, "pages")):
    for fn in fns:
        if not fn.endswith(".tsx"): continue
        p = os.path.join(dp, fn); rel = os.path.relpath(p, FE).replace("\\", "/")
        src = open(p, encoding="utf-8", errors="replace").read()
        pages += 1
        if AR.search(src): arabic_pages += 1
        a = len(jsx_text.findall(src)); b = len(label_prop.findall(src))
        tot_text += a; tot_lbl += b; worst[rel] = a + b
print(f"   pages scanned: {pages}")
print(f"   pages containing ANY Arabic character: {arabic_pages}  ({100*arabic_pages/pages:.0f}%)")
print(f"   hardcoded JSX text nodes starting with a capital Latin letter: {tot_text}")
print(f"   hardcoded label/title/placeholder props: {tot_lbl}")
print("   worst offenders:")
for f, n in worst.most_common(10): print(f"      {n:5}  {f}")

print("=" * 92)
print("4. RTL: is direction set anywhere, and how?")
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if not fn.endswith((".ts", ".tsx", ".css")): continue
        p = os.path.join(dp, fn)
        src = open(p, encoding="utf-8", errors="replace").read()
        for mm in re.finditer(r".{0,60}(dir\s*[=:]\s*[\"'{][^\n]{0,40}|direction\s*:\s*rtl).{0,30}", src):
            print(f"   {os.path.relpath(p, FE)}: {mm.group(0).strip()[:110]}")
idx = os.path.join(os.path.dirname(FE), "index.html")
if os.path.exists(idx):
    h = open(idx, encoding="utf-8", errors="replace").read()
    print("   index.html <html ...>:", re.search(r"<html[^>]*>", h).group(0) if re.search(r"<html[^>]*>", h) else "n/a")

print("=" * 92)
print("5. Page size distribution — are screens monoliths?")
sizes = []
for dp, _, fns in os.walk(os.path.join(FE, "pages")):
    for fn in fns:
        if fn.endswith(".tsx"):
            p = os.path.join(dp, fn)
            sizes.append((open(p, encoding="utf-8", errors="replace").read().count("\n") + 1,
                          os.path.relpath(p, FE).replace("\\", "/")))
sizes.sort(reverse=True)
print(f"   total page LoC: {sum(s[0] for s in sizes):,}   mean {sum(s[0] for s in sizes)//len(sizes)}   median {sizes[len(sizes)//2][0]}")
print(f"   pages > 500 lines: {sum(1 for s in sizes if s[0] > 500)}   > 1000 lines: {sum(1 for s in sizes if s[0] > 1000)}")
for n, f in sizes[:12]: print(f"      {n:5}  {f}")

print("=" * 92)
print("6. Shared component reuse — is there a design system?")
comp = collections.Counter()
cdir = os.path.join(FE, "components")
if os.path.isdir(cdir):
    for dp, _, fns in os.walk(cdir):
        for fn in fns:
            if fn.endswith((".tsx", ".ts")):
                rel = os.path.relpath(os.path.join(dp, fn), cdir).replace("\\", "/")
                comp[rel.split("/")[0]] += 1
    print("   components/ subtree:", dict(comp))
    total = sum(comp.values())
    print(f"   component files: {total}   vs {pages} pages  -> ratio {total/pages:.2f}")
imports = collections.Counter()
for dp, _, fns in os.walk(os.path.join(FE, "pages")):
    for fn in fns:
        if not fn.endswith(".tsx"): continue
        src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
        for mm in re.finditer(r"from\s+['\"][^'\"]*components/([A-Za-z0-9_/]+)['\"]", src):
            imports[mm.group(1)] += 1
print("   most-imported shared components:", dict(imports.most_common(12)))
print(f"   pages importing NO shared component: ", end="")
none = 0
for dp, _, fns in os.walk(os.path.join(FE, "pages")):
    for fn in fns:
        if fn.endswith(".tsx"):
            src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
            if "components/" not in src: none += 1
print(none)
