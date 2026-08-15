package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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
}
