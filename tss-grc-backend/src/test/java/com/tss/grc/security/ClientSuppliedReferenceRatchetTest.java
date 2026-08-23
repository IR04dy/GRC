package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>F2-208 — no create path takes its business reference from the caller.</b>
 *
 * <h3>Why this is a gate and not a seventh fix</h3>
 * F2-172(d) established the rule: <em>only the server may issue a business reference.</em> It has
 * then been applied <b>one instance per slice, seven slices running</b> — the intake substrate,
 * F2-202's PDPL DSR cases, F2-203's audit findings, F2-204's ERM treatment plans, F2-205's NCA ECC
 * vulnerabilities, F2-206's approval requests and audit engagements. Each of those slices found its
 * instance by grepping {@code req.getId() != null ?}, which CLAUDE.md instructs and which was
 * four-for-four. Running that grep once more here returned <b>six live sites</b>: BCP crisis
 * invocations, board packs, CAPA tasks, knowledge articles, the <b>NCA ECC self-assessment
 * filing</b> and regulatory changes.
 *
 * <p>🔑 <b>Six survivors after seven fixes is not bad luck, it is what fixing instances of a shape
 * produces.</b> The reusable move is the one this class makes: sweep the population and assert the
 * shape at zero, so the eighth instance fails the build instead of waiting for the eighth slice.
 *
 * <h3>⚠ The grep everyone has been running cannot see the whole family</h3>
 * {@code req.getId() != null ?} is one <em>spelling</em>. Measured across all 48
 * {@code setExternalId(} / {@code setRefNumber(} call sites in main sources, the caller's reference
 * also reaches a column as {@code req.externalId()} (a record accessor — no {@code get}, no null
 * check) and as {@code in.getExternalId()} (an entity bound straight from the body). A grep for the
 * shape of the last fix is blind to both. This is F2-192's headline turned on CLAUDE.md's own
 * instruction: <b>a detector scoped to the spelling that produced the finding reports a smaller
 * population, which reads exactly like "the rest are fine."</b>
 *
 * <p>So the assertion here is written on the <em>defect's</em> signature rather than on a spelling:
 * inside a method that creates a record, the argument to a reference setter may not contain a
 * null-coalesce. "Use the caller's value if they sent one, otherwise mint" is the whole bug, in any
 * spelling, and it is the only reason such an argument would test for null.
 *
 * <p>⚠ And having widened the net, <b>all five caller-derived sites outside create paths were
 * refuted by reading</b> — every one carries an <em>upstream system's</em> identifier (a SIEM event
 * id, a Splunk alert id, a scanner finding id, a threat-intel advisory id), which is what that
 * column is for. They are listed in {@link #UPSTREAM_OWNED} with a reason each and asserted in both
 * directions, because "this reference belongs to someone else's system" is a claim that can stop
 * being true. F2-188's rule: a named map proves a reason exists, never that it is true — these five
 * were each read, not assumed.
 *
 * <h3>What is deliberately NOT asserted</h3>
 * Nothing here says a generator is <em>good</em>. {@code NcaEccFilingService} mints
 * {@code NCA-<year>-001} with a constant suffix, which would be F2-48's collision bug on any other
 * table; it is correct there only because {@code uk_ncaecc_year} is UNIQUE on
 * {@code (organisation_id, fiscal_year)}, so a tenant can hold at most one filing per year. That
 * was checked against {@code sys.indexes}, and it is recorded at the call site rather than asserted
 * here, because this test is hermetic and cannot see an index.
 */
class ClientSuppliedReferenceRatchetTest {

    private static final Path MAIN = Path.of("src/main/java");

    /**
     * A setter that writes a <b>business reference</b> — the string a human, an auditor or a
     * regulator cites to say <em>which</em> record they mean. Deliberately not {@code setCode} or
     * {@code setSlug}: those are frequently the caller's to choose (a KB slug is checked for
     * uniqueness and belongs to the author), and banning them would make this gate fail on correct
     * code, which trains the next reader to edit the assertion instead of the code.
     */
    private static final Pattern REFERENCE_SETTER = Pattern.compile("\\bset(?:ExternalId|RefNumber)\\s*\\(");

    /**
     * F2-266 — the wider shape, <b>counted rather than banned</b>.
     *
     * <p>The narrowing above is a recorded decision and it is right: {@code *Code} and
     * {@code *Ref} are <em>often</em> the caller's, so banning them would fail on correct
     * code (F2-214). What nobody did was <b>count</b> them — so six slices each filed one
     * instance by hand (F2-250 {@code aml_alerts.alert_code}, probed: a duplicate of a real
     * code was accepted; F2-253 {@code subprocessorCode}; F2-258 three
     * {@code AiLifecycleService} creates; F2-259 {@code snapshotCode}, read and found
     * collision-safe; F2-260 {@code regulatorAckReference}, read and found CORRECT; F2-264
     * {@code CreateAssetRequest.reference}). <b>That is what a population looks like when
     * nothing counts it</b> (F2-208).
     *
     * <p>🔑 <b>Why the zero above reads as complete:</b> 118 tables declare
     * {@code external_id} — server-minted and covered by the ban — <em>beside</em> a
     * caller-named {@code *_code}. The covering mechanism is visibly present on the same
     * row, so a reader has no reason to ask which of the row's TWO references it covers.
     * F2-215's two-of-N at its most literal.
     *
     * <p><b>Three kinds, and only one is a defect</b> — which is why this leg records no
     * verdicts (F2-188 measured what happens when a slice writes reasons for sites it has
     * not read: five claims, five false):
     * <ol>
     *   <li><b>Authority-issued</b> — {@code regulatorAckReference},
     *       {@code samaLicenceRef}, {@code samaNonObjectionRef},
     *       {@code lawEnforcementReference}, {@code strReference}. Correct to be
     *       caller-supplied: F2-209's rule is that a reference only an outside authority can
     *       issue is <em>received and recorded, or absent</em> — the server may not mint it
     *       either.</li>
     *   <li><b>Names another record</b> — {@code linkedAssetRef}, {@code ropaCode},
     *       {@code retentionRuleRef}, {@code contractRef}. That is F2-242/F2-252's question
     *       (does the reference resolve, and to what?), not minting.</li>
     *   <li><b>The record's OWN identifier</b> — {@code alertCode},
     *       {@code subprocessorCode}, {@code bowtieCode}, {@code kriCode}. This is
     *       F2-172(d)'s family and the only defect bucket.</li>
     * </ol>
     *
     * <p>⚠ {@code Status} is excluded by name and that exclusion is the negative control:
     * {@code ObService#recordApiCall} writes {@code setStatusCode} from the request and that
     * is an <b>HTTP status on an API-call log</b>, not a reference. A first draft of this
     * scan counted it, which is how a {@code *Code} pattern over-reports.
     */
    private static final Pattern BUSINESS_REFERENCE_SETTER = Pattern.compile(
        "\\bset(?!Status)(?:[A-Z]\\w*)?(?:Code|Reference|Ref|ExternalId|RefNumber)\\s*\\(");

    /** Create-ish entry points. A reference is chosen when a record is born. */
    private static final Pattern CREATE_ISH = Pattern.compile(
        "\\b(?:public|protected|private)\\s+[\\w<>,.\\[\\]?]+\\s+"
        + "(create\\w*|submit\\w*|start\\w*|open\\w*|record\\w*|register\\w*"
        + "|import\\w*|add\\w*)\\s*\\(");

    /**
     * ⚠⚠ F2-306 — the UPDATE-side mirror of the census above, which nobody had measured.
     *
     * <p>{@link #CREATE_ISH} is a verb list and {@code update} is deliberately absent, because that
     * leg's claim is <em>"a reference is chosen when a record is born"</em>. So the <b>rename</b>
     * question was outside this class's population <b>by construction</b> — and renaming is arguably
     * the sharper harm: the record already exists, an auditor may already cite the code, and the
     * citation breaks <b>silently</b>.
     *
     * <h4>Measured: 42 sites across 34 services, in three kinds</h4>
     *
     * Only one kind is a defect, which is why this ships as a count with <b>no per-site verdicts</b>
     * ([[F2-188]]):
     *
     * <ol>
     *   <li><b>The record's own identifier</b> — {@code setEngagementCode}, {@code setChangeCode},
     *       {@code setCaseCode}, {@code setDisclosureCode}, {@code setGapCode}, {@code setPlanCode},
     *       {@code setReportCode}. Renaming one breaks every citation of it. This is the defect, and
     *       F2-306 closed the two in {@code ethics}; {@code setGapCode} is [[F2-302]]'s filing.</li>
     *   <li><b>A pointer to another record</b> — {@code setEvidenceRef} (nine sites),
     *       {@code setClauseRef}, {@code setSourceRef}, {@code setContractRef}. Correcting which
     *       clause a finding maps to is <b>legitimate</b>, and refusing it would fail on working
     *       usage ([[F2-214]]). Whether the pointer RESOLVES is a different question ([[F2-242]],
     *       [[F2-252]]).</li>
     *   <li><b>An outside authority's reference</b> — {@code setSamaLicenceRef},
     *       {@code setSamaNonObjectionRef}, {@code setBoardResolutionReference},
     *       {@code setFilingArtifactReference}. Caller-supplied is <b>correct</b>: a reference only
     *       an authority can issue is received and recorded, or absent ([[F2-209]], [[F2-269]]), and
     *       the server may not mint it either.</li>
     * </ol>
     *
     * <p>⚠ The subtraction is <b>per field, not per method</b>. A site guarded by
     * {@code ServerOwnedState.requireImmutableReference} keeps its setter inside the guard, so a
     * pattern keyed on the setter alone still matches it — but a method-level subtraction removes
     * every OTHER reference setter in the same method too. Both {@code apply} helpers F2-306 closed
     * also write {@code setClauseRef} and {@code setRegulatorReference}, which are kinds 2 and 3 and
     * must stay counted: a method-scoped check reported <b>39</b> where the answer is <b>42</b>.
     * 🔑 <b>An over-subtraction reads exactly like progress.</b>
     *
     * <p>Asserted exactly rather than as a ceiling: a new rename site must fail, and a closed one
     * must lower the number in the same commit (house rule 3).
     */
    private static final int UPDATE_PATH_REFERENCE_WRITES = 42;

    private static final Pattern UPDATE_ISH = Pattern.compile(
        "\\b(?:public|protected|private)\\s+[\\w<>,.\\[\\]?]+\\s+"
        + "(update\\w*|edit\\w*|patch\\w*|apply\\w*|modify\\w*|save\\w*|merge\\w*)\\s*\\(");

    @Test
    void theUpdatePathRenameShapeIsCountedToo() throws IOException {
        List<String> sites = new ArrayList<>();
        for (Path f : javaSources()) {
            if (!f.getFileName().toString().endsWith("Service.java")) { continue; }
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            Matcher um = UPDATE_ISH.matcher(src);
            while (um.find()) {
                String body = balancedBody(src, um.end());
                if (body == null) { continue; }
                Matcher sm = BUSINESS_REFERENCE_SETTER.matcher(body);
                while (sm.find()) {
                    String arg = argumentAt(body, sm.end());
                    if (arg.isEmpty()
                        || !Pattern.compile("\\b(?:req|request|in|dto|body|cmd|src|patch)\\b\\s*\\.")
                                   .matcher(arg).find()) {
                        continue;
                    }
                    // per-FIELD subtraction: the primitive's guard leaves the setter in place, and a
                    // method-scoped check would also drop the other reference setters beside it
                    String field = sm.group().replace("set", "").replace("(", "").trim();
                    int at = body.indexOf("requireImmutableReference");
                    if (at >= 0 && body.substring(at, Math.min(body.length(), at + 400))
                                       .contains("get" + field + "()")) {
                        continue;
                    }
                    sites.add(f.getFileName().toString().replace(".java", "") + "#" + um.group(1)
                              + " :: " + sm.group().replace("(", "").trim());
                }
            }
        }
        List<String> sorted = sites.stream().distinct().sorted().toList();

        assertThat(sorted.size()).as("""
            Update paths writing a business reference from the request: %d, against %d.

            This is the RENAME question, and it is outside the create-side census above by             construction: CREATE_ISH excludes `update` because a reference is chosen when a record             is born. Renaming is the sharper harm — the record exists and a citation of the code             breaks silently.

            Only ONE of the three kinds is a defect (the record's own identifier); a pointer to             another record is legitimately editable and an authority-issued reference is correctly             caller-supplied. Read the site before closing it, and use             ServerOwnedState.requireImmutableReference where it is kind 1.

            If it went UP, a new rename site shipped. If it went DOWN, lower the number here in the             same commit.

            %s""".formatted(sorted.size(), UPDATE_PATH_REFERENCE_WRITES,
                            String.join("\n            ", sorted)))
            .isEqualTo(UPDATE_PATH_REFERENCE_WRITES);
    }

    /** Where the census lands, so no document has to restate it (F2-265). */
    private static final Path CENSUS = Path.of("target", "caller-named-references.txt");

    /**
     * Decrease-only. Measured on this tree by the leg below; a fix lowers it, and a new
     * caller-named reference has to be a deliberate raise with a reason.
     */
    private static final int BASELINE_CALLER_NAMED_REFERENCES = 92;   // F2-267 closed scm.supplier_resilience_tests.test_code

    /** A method header, used only to attribute a call site to its enclosing method. */
    private static final Pattern METHOD_HEADER = Pattern.compile(
        "(?:public|protected|private)\\s+(?:static\\s+)?[\\w<>,\\[\\].? ]+?\\s+(\\w+)\\s*\\([^;{]*\\)\\s*\\{");

    /**
     * The five sites where a reference legitimately arrives from outside — measured, then read.
     *
     * <p>Each value must name the system that owns the identifier. A site that stops reading a
     * caller-supplied reference has to lose its entry in the same commit
     * ({@link #everyUpstreamOwnedReferenceIsStillOneAndIsNamed()} enforces the reverse), so this
     * map cannot quietly outlive its reasons.
     */
    private static final Map<String, String> UPSTREAM_OWNED = new LinkedHashMap<>() {{
        put("SocOpsIngestService#ingestSiemEvent",
            "The SIEM's own event id. It is the dedup key one line above — "
            + "findByOrganisationIdAndSourceSystemAndExternalId(org, sourceSystem, externalId) — so "
            + "minting our own would make every re-ingest of one alert a new row.");
        put("AppsecFindingService#copyMutable",
            "The scanner's finding id, carried alongside scanner/scanType/cwe/cve. The method's own "
            + "javadoc records that id, organisationId and the audit columns are never copied; the "
            + "external id is copied on purpose because the scanner issued it.");
        put("ThreatIntelService#copyMutable",
            "The advisory id from the issuing body (NCA HASEEN / SAMA ISAC). Note the mirror defect "
            + "is in the frontend, not here: F2-207 filed lib/cyberLifecycle.ts minting NCA-AD- and "
            + "SAMA-CTI- references with Math.random, i.e. inventing an authority's advisory number.");
        put("SplunkWebhookController#alertReceived",
            "The Splunk alert id read from the inbound webhook payload — an inbound integration, so "
            + "the sender owns the reference.");
        put("SplunkWebhookController#recordExternalLink",
            "The same Splunk alert id, written onto the link row that ties our record to theirs.");
    }};

    // ── measured floors ───────────────────────────────────────────────────────
    // Measured 2026-08-13 on this tree: 48 reference-setter call sites, 25 of them inside a
    // create* method. These are FLOORS, not equalities: the point is that a rotted regex fails
    // here loudly rather than reporting a clean zero in the leg below. F2-195's lesson — a corpus
    // assertion passes both when the code is clean and when the detector has stopped working.
    //
    // ⚠ Re-measured 2026-08-15: 39 sites. This leg FAILED at 39 against the old floor of 40, which
    // is the leg doing its job — but the drop is legitimate and the reason is worth keeping. F2-213
    // converted nine services from "persist the request object" to "apply the editable fields to
    // the loaded row". Each of those nine carried a `req.setExternalId(existing.getExternalId())`
    // line whose ONLY purpose was to carry the reference across a full replace: compensation for
    // the defect, not a reference write. Removing the defect removed the need for them.
    // 🔑 A population that falls after a fix is not automatically rot — but it is always worth
    // asking which it is, which is exactly what this floor forced.
    private static final int MIN_REFERENCE_SETTER_SITES = 33;
    private static final int MIN_CREATE_PATH_SITES      = 20;

    private record Site(String file, String method, String argument) {
        String key() {
            String cls = file.substring(file.lastIndexOf('/') + 1).replace(".java", "");
            return cls + "#" + method;
        }
    }

    @Test
    @DisplayName("no create path takes its business reference from the caller (hard zero, any spelling)")
    void noCreatePathTakesItsBusinessReferenceFromTheCaller() {
        List<String> offenders = scan().stream()
            .filter(s -> s.method().startsWith("create"))
            .filter(s -> s.argument().contains("!= null"))
            .map(s -> s.key() + "  ->  " + s.argument())
            .toList();

        assertThat(offenders)
            .as("""
                A create path is choosing between the caller's reference and a minted one. That is \
                F2-172(d), and this gate exists because it had been fixed one instance per slice \
                for seven slices while six more sat open — including the NCA ECC self-assessment \
                filing. Mint the reference unconditionally and ignore the request field; if the \
                caller genuinely owns the identifier because an upstream system issued it, the \
                write does not belong in a create path — add it to UPSTREAM_OWNED with the system \
                that owns it, and say so.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the scan still resolves the population it claims to cover")
    void theScanStillResolvesThePopulationItClaimsToCover() {
        List<Site> all = scan();
        assertThat(all.size())
            .as("the reference-setter scan found %d sites; it found 48 when written. A collapse "
                + "here is why the hard zero above cannot be trusted on its own.", all.size())
            .isGreaterThanOrEqualTo(MIN_REFERENCE_SETTER_SITES);
        assertThat(all.stream().filter(s -> s.method().startsWith("create")).count())
            .as("no create* method appears to set a reference at all — the method attribution has "
                + "rotted, so the hard zero above is vacuous")
            .isGreaterThanOrEqualTo((long) MIN_CREATE_PATH_SITES);
    }

    @Test
    @DisplayName("the detector fires on the defect — a positive control, not a corpus assertion")
    void theDetectorFiresOnTheDefect() {
        // ⚠ Both legs above pass on a clean tree AND on a tree where the regexes have stopped
        // matching. This one cannot: it feeds the detector the exact shape the six swept sites had
        // and requires it to be caught. The negative half matters just as much — an unconditional
        // mint must NOT be reported, or the gate would fail on every correct create path.
        String defective = """
            public class Probe {
                public FooDto create(FooDto req) {
                    Foo f = new Foo();
                    f.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
                    return FooDto.from(f);
                }
            }""";
        String clean = """
            public class Probe {
                public FooDto create(FooDto req) {
                    Foo f = new Foo();
                    f.setExternalId(generateExternalId());
                    return FooDto.from(f);
                }
            }""";
        List<Site> onDefective = new ArrayList<>();
        collect(defective, "Probe.java", onDefective);
        List<Site> onClean = new ArrayList<>();
        collect(clean, "Probe.java", onClean);

        assertThat(onDefective).hasSize(1);
        assertThat(onDefective.get(0).method()).isEqualTo("create");
        assertThat(onDefective.get(0).argument()).contains("!= null");

        assertThat(onClean).hasSize(1);
        assertThat(onClean.get(0).argument())
            .as("an unconditional mint must not look like the defect, or this gate fails on all 25 "
                + "correct create paths")
            .doesNotContain("!= null");
    }

    @Test
    @DisplayName("every reference that arrives from outside names the system that owns it")
    void everyUpstreamOwnedReferenceIsStillOneAndIsNamed() {
        List<Site> callerDerived = scan().stream()
            .filter(s -> !s.method().startsWith("create"))
            .filter(s -> Pattern.compile("\\b(?:req|request|in|dto)\\b\\s*\\.").matcher(s.argument()).find()
                      || s.argument().equals("externalId"))
            .toList();

        // Forward: nothing new may appear unexplained.
        assertThat(callerDerived.stream().map(Site::key).distinct().toList())
            .as("a reference setter outside a create path is reading a caller-supplied value with "
                + "no recorded owner. Read it: if an upstream system issued the identifier, add it "
                + "to UPSTREAM_OWNED naming that system; if the platform issues it, mint it.")
            .allSatisfy(key -> assertThat(UPSTREAM_OWNED).containsKey(key));

        // Reverse — the direction nobody writes first (F2-199). An entry whose site no longer
        // reads a caller value is a reason that has outlived its subject.
        List<String> live = callerDerived.stream().map(Site::key).distinct().toList();
        assertThat(UPSTREAM_OWNED.keySet())
            .as("an UPSTREAM_OWNED entry no longer matches a site that reads a caller-supplied "
                + "reference — delete the entry in the commit that changed the code")
            .allSatisfy(key -> assertThat(live).contains(key));

        // A reason must actually name something. F2-188: six of its first-draft reasons were bare
        // labels, and fluency reads as verification — so at minimum demand substance and a system.
        assertThat(UPSTREAM_OWNED).allSatisfy((key, reason) -> {
            assertThat(reason.length())
                .as("%s: the reason is too short to be a reason", key)
                .isGreaterThan(80);
            assertThat(reason.toLowerCase())
                .as("%s: the reason must name the system or body that owns the identifier", key)
                .containsAnyOf("siem", "scanner", "splunk", "advisory", "webhook", "sender");
        });
    }

    // ── scanning ──────────────────────────────────────────────────────────────

    private static List<Site> scan() {
        List<Site> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    collect(Files.readString(p), MAIN.relativize(p).toString().replace('\\', '/'), out);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * Attribute every reference-setter call to its enclosing method and capture the argument.
     *
     * <p>⚠ Comments are stripped first, and the alternation puts the line-comment branch FIRST —
     * F2-198's landmine. Written the other way round, a {@code //} comment whose prose contains a
     * literal {@code /*} makes the block pattern run to the next close delimiter and swallow real
     * code, which yields a <em>smaller</em> population and reads exactly like "nothing to fix".
     * That matters more than usual here: this file's own javadoc quotes the defect it bans.
     */
    private static void collect(String source, String path, List<Site> sink) {
        String src = source.replaceAll("//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");
        List<int[]> heads = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher hm = METHOD_HEADER.matcher(src);
        while (hm.find()) {
            heads.add(new int[]{ hm.start() });
            names.add(hm.group(1));
        }
        Matcher sm = REFERENCE_SETTER.matcher(src);
        while (sm.find()) {
            String method = "(none)";
            for (int i = 0; i < heads.size(); i++) {
                if (heads.get(i)[0] < sm.start()) method = names.get(i);
                else break;
            }
            sink.add(new Site(path, method, argumentAt(src, sm.end())));
        }
    }

    /** Text between the opening paren and its match, so a nested call is captured whole. */
    private static String argumentAt(String src, int from) {
        int depth = 1;
        StringBuilder arg = new StringBuilder();
        for (int i = from; i < src.length() && depth > 0; i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) break;
            arg.append(c);
        }
        return arg.toString().trim().replaceAll("\\s+", " ");
    }


    @Test
    @DisplayName("F2-266: the business-reference shape is COUNTED, since it cannot be banned")
    void theBusinessReferenceShapeIsCountedEvenThoughItCannotBeBanned() throws IOException {
        List<String> sites = new ArrayList<>();
        for (Path f : javaSources()) {
            if (!f.getFileName().toString().endsWith("Service.java")) { continue; }
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            Matcher cm = CREATE_ISH.matcher(src);
            while (cm.find()) {
                String body = balancedBody(src, cm.end());
                if (body == null) { continue; }
                Matcher sm = BUSINESS_REFERENCE_SETTER.matcher(body);
                while (sm.find()) {
                    String arg = argumentAt(body, sm.end());
                    if (arg.isEmpty()
                        || !Pattern.compile("\\b(?:req|request|in|dto|body|cmd)\\b\\s*\\.")
                                   .matcher(arg).find()) {
                        continue;
                    }
                    sites.add(f.getFileName().toString().replace(".java", "") + "#" + cm.group(1)
                              + " :: " + sm.group().replace("(", "").trim());
                }
            }
        }
        List<String> sorted = sites.stream().distinct().sorted().toList();

        List<String> out = new ArrayList<>();
        out.add("# Caller-named business references on create paths - GENERATED by");
        out.add("# ClientSuppliedReferenceRatchetTest. Do not hand-edit, and do not copy the count");
        out.add("# into prose (F2-265). NO VERDICTS here on purpose: three kinds live in this");
        out.add("# population and only one is a defect - see BUSINESS_REFERENCE_SETTER's javadoc.");
        out.add("sites=" + sorted.size());
        out.add("");
        out.addAll(sorted);
        Files.writeString(CENSUS, String.join(System.lineSeparator(), out),
                          StandardCharsets.UTF_8);

        assertThat(sorted.size())
            .as("%d create-path sites write a business reference from the request; the measured "
                + "ceiling is %d. This is a CENSUS, not a ban: the narrow ban above is a recorded "
                + "decision and banning the wider shape would fail on correct code (F2-214), so "
                + "what this leg exists for is that nobody was COUNTING it - six slices each filed "
                + "one instance by hand. If you closed one, lower the number in the same commit; "
                + "if you added one, say in the commit message which of the three kinds it is. "
                + "Full list: %s", sorted.size(), BASELINE_CALLER_NAMED_REFERENCES, CENSUS)
            .isLessThanOrEqualTo(BASELINE_CALLER_NAMED_REFERENCES);

        assertThat(sorted)
            .as("an HTTP status is not a business reference. ObService#recordApiCall writes "
                + "setStatusCode from the request, and a first draft of this scan counted it - "
                + "which is exactly how a '*Code' pattern over-reports. The (?!Status) in "
                + "BUSINESS_REFERENCE_SETTER is that exclusion; if this fires, it was removed.")
            .noneMatch(site -> site.endsWith(":: setStatusCode"));
    }

    @Test
    @DisplayName("F2-266: the FIU alert code is minted by the server, not named by the caller")
    void theAmlAlertCodeIsMintedRatherThanNamed() throws IOException {
        Path svc = javaSources().stream()
            .filter(p -> p.getFileName().toString().equals("AmlAlertService.java"))
            .findFirst().orElseThrow(() ->
                new AssertionError("AmlAlertService.java not found - this pin has lost its subject"));
        String src = stripComments(Files.readString(svc, StandardCharsets.UTF_8));

        assertThat(src)
            .as("the create path must MINT the code through the shared primitive. F2-250 probed "
                + "that a duplicate of a real AML-2025-0001 was accepted, and this is the "
                + "reference an FIU submission cites.")
            .containsPattern("ReferenceCodeMint\\s*\\.\\s*mint\\s*\\(");

        assertThat(src)
            .as("nothing may write alertCode from the request. The editable-field list used to "
                + "carry `if (s.getAlertCode() != null) t.setAlertCode(s.getAlertCode());`, which "
                + "is the whole defect.")
            .doesNotContainPattern("setAlertCode\\s*\\(\\s*s\\s*\\.")
            .doesNotContainPattern("setAlertCode\\s*\\(\\s*req\\s*\\.");
    }

    @Test
    @DisplayName("F2-266: there is ONE reference minter, not a second hand-rolled generator")
    void thereIsExactlyOneReferenceMinter() throws IOException {
        List<String> withAlphabet = new ArrayList<>();
        for (Path f : javaSources()) {
            String src = Files.readString(f, StandardCharsets.UTF_8);
            if (src.contains("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")) {
                withAlphabet.add(f.getFileName().toString());
            }
        }
        assertThat(withAlphabet)
            .as("the no-I/O/0/1 alphabet is the fingerprint of a reference generator. It must "
                + "appear in exactly one class: F2-190's rule, and this primitive was EXTRACTED "
                + "from IntakeService precisely so the second register could call it instead of "
                + "copying it. Found in: %s", withAlphabet)
            .containsExactly("ReferenceCodeMint.java");
    }

    // ── helpers for the F2-266 legs ──────────────────────────────────────────────────────────

    /** ONE left-to-right alternation: two sequential passes corrupt a file (F2-198/F2-211). */
    private static final Pattern COMMENTS =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String stripComments(String src) {
        return COMMENTS.matcher(src).replaceAll("");
    }

    private static List<Path> javaSources() throws IOException {
        try (var walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** The brace-balanced body starting at the first '{' at or after {@code from}. */
    private static String balancedBody(String src, int from) {
        int i = src.indexOf('{', from);
        if (i < 0) { return null; }
        int depth = 0;
        for (int j = i; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') { depth++; } else if (c == '}') {
                depth--;
                if (depth == 0) { return src.substring(i, j); }
            }
        }
        return null;
    }

}