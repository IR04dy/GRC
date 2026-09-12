package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W0-6 - governance-document integrity.
 *
 * <p>Three defect classes, all of which had already happened in this tree:
 *
 * <ol>
 *   <li><b>A second instruction file that drifts.</b> {@code AGENTS.md} was {@code CLAUDE.md}
 *       copied verbatim with a blind {@code Claude}->{@code Codex} substitution. The remote it
 *       invented was corrected in {@code 99a719da} - and that fixed one wrong fact while leaving
 *       the mechanism. Six weeks later the copy still shared <b>83.6%</b> of its substantive lines
 *       with {@code CLAUDE.md}, so it read as "basically the same file", while every figure that
 *       mattered had diverged: authorization ratchet <b>62</b> against an enforced <b>21</b>, next
 *       free migration <b>V296</b> against a head of <b>V358</b> (V296 taken since 2026-07-13 - a
 *       Flyway checksum failure on the deployed server), <b>94</b> permissions across <b>24</b>
 *       modules against <b>114</b> across <b>31</b>. Each had been true once, was found false, and
 *       was fixed in {@code CLAUDE.md} only. <b>A copy does not receive corrections.</b>
 *   <li><b>Point-in-time snapshots read as current state.</b> 217 documents describe a platform
 *       that has since changed. Without a banner a reader - or an agent - cites them as fact.
 *   <li><b>Anchors to things that no longer exist.</b> Three documents anchored themselves to
 *       branch {@code feat/pdpl-extensions}, deleted long ago, so nothing they claimed could be
 *       checked against anything.
 * </ol>
 *
 * <p><b>Why a test and not a one-time edit.</b> W0-6 is a documentation row, and the whole lesson
 * of this programme is that an unenforced correction rots. The banners are 217 edits; nothing
 * stopped the 218th file from arriving unstamped, or {@code AGENTS.md} from being "helpfully"
 * re-synced back into a copy. This is the enforcement.
 *
 * <p><b>The original acceptance criterion was unsatisfiable.</b> REMEDIATION_MASTER_PLAN W0-6 says:
 * <i>"{@code grep -rl "Codex-GRC-2" .} -> empty"</i>. But after the fix the only files naming that
 * remote are the four <b>findings documents that report the defect</b>; satisfying the grep would
 * mean deleting the evidence. The rule that actually protects a reader is not absence - it is that
 * <b>the fake remote is never named without its correction beside it</b>, which is what
 * {@link #aFakeRemoteIsNeverNamedWithoutItsCorrection()} asserts.
 */
class GovernanceDocumentIntegrityTest {

    /** Marker every stamped document carries in its head. Must match {@code w06_stamp.py}. */
    private static final String BANNER = "**HISTORIC";

    private static final Path ROOT = Path.of("..");
    private static final Path CLAUDE_MD = ROOT.resolve("CLAUDE.md");
    private static final Path AGENTS_MD = ROOT.resolve("AGENTS.md");

    private static final String REAL_REMOTE = "Claude-GRC-2";
    private static final String FAKE_REMOTE = "Codex-GRC-2";

    private static final String DEAD_BRANCH = "feat/pdpl-extensions";
    private static final String DEAD_BRANCH_SHA = "c6f116f";

    /** Directories whose every {@code .md} is a point-in-time artefact unless exempted below. */
    private static final List<String> SNAPSHOT_DIRS =
            List.of("docs", "sprints", "Saudi-GRC-Platform-Integration");

    /** Root-level snapshots. The register classes these {@code role=historic-snapshot}. */
    private static final List<String> ROOT_SNAPSHOTS =
            List.of("STATUS.md", "SESSION_SUMMARY.md", "SESSION_SUMMARY_2026-06-18.md",
                    "PROJECT_BUILD_HISTORY.md", "summary.md");

    /**
     * Documents inside {@link #SNAPSHOT_DIRS} that are <b>live</b> and must NOT be stamped.
     *
     * <p>The plan row said "the 158 {@code docs/} files" as though the directory were uniformly
     * V1-era. It is not: {@code DOCUMENT_REGISTER.csv} classes four of them current, and
     * {@code CAB_OPERATING_RUNBOOK.md} was written by <b>this programme</b> on 2026-08-04/05.
     * Stamping it HISTORIC would have been a false statement produced by following the row
     * literally.
     *
     * <p>This list is deliberately an allowlist and deliberately short. Adding to it is a claim
     * that the document is maintained - {@link #theLiveExemptionsAreReallyUnstamped()} checks the
     * claim is at least self-consistent.
     */
    private static final Set<String> LIVE_EXEMPT = Set.of(
            "docs/change-manager/CAB_OPERATING_RUNBOOK.md",
            "docs/PLATFORM_MAP.md",
            "docs/CYBERSECURITY_MODULE_MAP.md",
            "docs/SOC_OPERATIONS_MODULE_MAP.md");

    /**
     * Verbatim-line overlap above which {@code AGENTS.md} counts as a copy rather than a pointer.
     *
     * <p>Empirical, not guessed, and mutation-proof in both directions: the copy at
     * {@code 99a719da} measures <b>83.6%</b> against {@code CLAUDE.md} as it stands today; the
     * pointer that replaced it measures <b>0.0%</b>. Any threshold in between separates them, and
     * 25% leaves room for a pointer that legitimately quotes a line or two.
     */
    private static final int MAX_COPY_OVERLAP_PERCENT = 25;

    // ---------------------------------------------------------------------------------------

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    /** Head of the document - a banner deep inside a 3,000-line file is not a banner. */
    private static String head(Path p) {
        String s = read(p);
        return s.substring(0, Math.min(1500, s.length()));
    }

    private static List<Path> markdownUnder(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + dir.toAbsolutePath(), e);
        }
    }

    /** Repo-relative, forward-slashed - so keys match {@link #LIVE_EXEMPT} on Windows too. */
    private static String rel(Path p) {
        return ROOT.toAbsolutePath().normalize()
                .relativize(p.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /** Substantive lines only: short ones collide by accident and prove nothing about copying. */
    private static Set<String> substantiveLines(String text) {
        Set<String> out = new TreeSet<>();
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.length() >= 25) {
                out.add(t);
            }
        }
        return out;
    }

    /** Every tracked-ish {@code .md} in the repo, excluding build output and agent worktrees. */
    private static List<Path> allRepoMarkdown() {
        // F2-408: PRUNE the traversal at .claude / node_modules / target rather than only
        // filtering them out of the RESULT. Files.walk descends into every subtree first and
        // throws NoSuchFileException the moment it meets a dangling symlink inside a concurrent
        // agent worktree's node_modules (`.claude/worktrees/` holds full clones from concurrent
        // sessions — F2-134 measured 1,061 duplicate .md there; F2-206: scope filesystem sweeps
        // to the working tree). That crashed the whole build on garbage that is not this repo's
        // document. walkFileTree lets us SKIP_SUBTREE before descending, and visitFileFailed
        // tolerates a file removed mid-walk (e.g. a concurrent pnpm install).
        List<Path> out = new ArrayList<>();
        try {
            Files.walkFileTree(ROOT, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null) {
                        String n = name.toString();
                        if (n.equals(".claude") || n.equals("node_modules") || n.equals("target")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".md")) {
                        out.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + ROOT.toAbsolutePath(), e);
        }
        out.sort(null);
        return out;
    }

    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("AGENTS.md is a pointer to CLAUDE.md, not a second copy of it that can drift")
    void agentsMdIsAPointerNotACopy() {
        Assumptions.assumeTrue(Files.isRegularFile(AGENTS_MD) && Files.isRegularFile(CLAUDE_MD),
                "CLAUDE.md / AGENTS.md not reachable from " + ROOT.toAbsolutePath());

        String agents = read(AGENTS_MD);
        Set<String> agentLines = substantiveLines(agents);
        Set<String> claudeLines = substantiveLines(read(CLAUDE_MD));

        assertThat(agentLines)
                .as("AGENTS.md has no substantive content at all - it must at least point somewhere")
                .isNotEmpty();

        long shared = agentLines.stream().filter(claudeLines::contains).count();
        int percent = (int) Math.round(100.0 * shared / agentLines.size());

        assertThat(percent)
                .as("AGENTS.md shares %d%% of its substantive lines verbatim with CLAUDE.md (%d of "
                        + "%d). Above %d%% it is a COPY, and a copy does not receive corrections: "
                        + "the copy at 99a719da measured 83.6%% and had drifted to ratchet 62 "
                        + "(enforced 21) and 'next free V296' (taken since 2026-07-13 - a Flyway "
                        + "checksum failure on the deployed server). Do not re-sync this file; "
                        + "keep it a pointer.",
                        percent, shared, agentLines.size(), MAX_COPY_OVERLAP_PERCENT)
                .isLessThanOrEqualTo(MAX_COPY_OVERLAP_PERCENT);

        assertThat(agents)
                .as("AGENTS.md must send the reader to CLAUDE.md by name")
                .contains("CLAUDE.md");
    }

    @Test
    @DisplayName("a remote that does not exist is never named without its correction beside it")
    void aFakeRemoteIsNeverNamedWithoutItsCorrection() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT), "repo root not reachable");

        List<String> offenders = new ArrayList<>();
        for (Path p : allRepoMarkdown()) {
            String body = read(p);
            if (body.contains(FAKE_REMOTE) && !body.contains(REAL_REMOTE)) {
                offenders.add(rel(p));
            }
        }
        assertThat(offenders)
                .as("these documents name the non-existent remote '%s' without naming the real one "
                        + "('%s') anywhere in the same file, so a reader has no way to know it is "
                        + "wrong. Note the acceptance criterion in REMEDIATION_MASTER_PLAN W0-6 "
                        + "('grep -rl \"%s\" . -> empty') cannot be met without deleting the "
                        + "findings that report the defect; naming-with-correction is the rule "
                        + "that actually protects a reader.",
                        FAKE_REMOTE, REAL_REMOTE, FAKE_REMOTE)
                .isEmpty();

        assertThat(read(CLAUDE_MD))
                .as("CLAUDE.md must name the real remote")
                .contains(REAL_REMOTE);
    }

    @Test
    @DisplayName("every point-in-time document carries a HISTORIC banner")
    void everySnapshotCarriesItsBanner() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT.resolve("docs")),
                "docs/ not reachable from " + ROOT.toAbsolutePath());

        List<Path> corpus = new ArrayList<>();
        for (String dir : SNAPSHOT_DIRS) {
            Path d = ROOT.resolve(dir);
            if (Files.isDirectory(d)) {
                corpus.addAll(markdownUnder(d));
            }
        }
        for (String f : ROOT_SNAPSHOTS) {
            Path p = ROOT.resolve(f);
            if (Files.isRegularFile(p)) {
                corpus.add(p);
            }
        }

        // A collapsed scan must never read as a clean one - F2-69's lesson, and F2-19b's floor.
        assertThat(corpus.size())
                .as("the snapshot corpus scan found %d documents; it should find ~220. The scan "
                        + "broke - a vacuous pass here would silently retire this whole guard.",
                        corpus.size())
                .isGreaterThan(180);

        List<String> unstamped = new ArrayList<>();
        for (Path p : corpus) {
            String r = rel(p);
            if (LIVE_EXEMPT.contains(r)) {
                continue;
            }
            if (!head(p).contains(BANNER)) {
                unstamped.add(r);
            }
        }
        assertThat(unstamped)
                .as("these documents describe the platform at a past moment and say nothing about "
                        + "it, so a reader (or an agent) will cite them as current state. Either "
                        + "prepend the '%s' banner after the H1 - see any of the 217 already "
                        + "stamped - or, if the document really is maintained, add it to "
                        + "LIVE_EXEMPT with a reason.", BANNER)
                .isEmpty();
    }

    @Test
    @DisplayName("the live-document exemptions really are unstamped, so the allowlist stays honest")
    void theLiveExemptionsAreReallyUnstamped() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT.resolve("docs")), "docs/ not reachable");

        List<String> wrong = new ArrayList<>();
        for (String r : new TreeSet<>(LIVE_EXEMPT)) {
            Path p = ROOT.resolve(r);
            if (!Files.isRegularFile(p)) {
                wrong.add(r + " (exempted but does not exist - stale allowlist entry)");
            } else if (head(p).contains(BANNER)) {
                wrong.add(r + " (exempted as LIVE, yet carries a HISTORIC banner)");
            }
        }
        assertThat(wrong)
                .as("LIVE_EXEMPT must list maintained documents only. An entry that is stamped "
                        + "HISTORIC, or that no longer exists, means the allowlist has become a "
                        + "place to hide files from the previous test rather than a reviewed set.")
                .isEmpty();
    }

    @Test
    @DisplayName("the deleted branch is never cited without the commit SHA that outlived it")
    void theDeadBranchIsAlwaysAccompaniedByItsSha() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT), "repo root not reachable");

        List<String> danglers = new ArrayList<>();
        for (Path p : allRepoMarkdown()) {
            String body = read(p);
            if (body.contains(DEAD_BRANCH) && !body.contains(DEAD_BRANCH_SHA)) {
                danglers.add(rel(p));
            }
        }
        assertThat(danglers)
                .as("branch '%s' was deleted, so these documents anchor their claims to something "
                        + "that cannot be checked. Cite commit %s (2026-05-11) instead - a SHA "
                        + "outlives its branch, which is the whole point.",
                        DEAD_BRANCH, DEAD_BRANCH_SHA)
                .isEmpty();
    }

    /**
     * F2-77 / JRN-03 — a governance document whose diffs are unreadable is a governance document
     * nobody reviews.
     *
     * <p>🔑 A CR not followed by LF makes git REFUSE CRLF normalisation, so the blob is stored
     * verbatim and differs from its normalised predecessor on every line. A patch script of mine
     * wrote 16 of them into {@code UNIFIED_IMPLEMENTATION_PLAN.md} on 2026-09-12 and a four-anchor
     * edit arrived as <b>4,139 insertions / 4,123 deletions</b> — in the 1.9 MB document that
     * governs this whole programme. Nothing failed; the only tell was the diffstat.
     *
     * <p>⚠⚠ <b>This lives here rather than in {@link ScannableSourceContractTest} because that
     * class's ROOTS are the two SOURCE trees, and every governance document in this repo is outside
     * them</b> — so the gate that owns the byte-level question could not have caught the instance
     * that prompted it. Its detector is reused rather than copied (F2-190). A hard zero with no
     * exemption map is safe here in a way it is not there: a document is not an applied migration,
     * so correcting one is always available (F2-214).
     */
    @Test
    @DisplayName("no governance document contains a lone CR — git then refuses to normalise it")
    void noGovernanceDocumentContainsALoneCarriageReturn() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT), "repo root not reachable");

        List<Path> corpus = allRepoMarkdown();
        // A collapsed walk must never read as a clean one (F2-69). Measured 2026-09-12: 362.
        assertThat(corpus.size())
                .as("the markdown walk found %d documents; it should find ~360. A walk that stopped "
                        + "resolving reports a SMALLER population, which is indistinguishable from "
                        + "a corpus with nothing wrong in it.", corpus.size())
                .isGreaterThan(250);

        List<String> offenders = new ArrayList<>();
        for (Path p : corpus) {
            byte[] data;
            try {
                data = Files.readAllBytes(p);
            } catch (IOException e) {
                continue; // F2-408: tolerate a file removed mid-walk
            }
            List<Integer> hits = ScannableSourceContractTest.loneCarriageReturns(data);
            if (hits.isEmpty()) {
                continue;
            }
            int first = hits.get(0);
            int line = 1;
            for (int i = 0; i < first; i++) {
                if ((data[i] & 0xFF) == 0x0A) {
                    line++;
                }
            }
            offenders.add(String.format("%s:%d — %d lone CR(s)", rel(p), line, hits.size()));
        }

        assertThat(offenders)
                .as("these documents hold a CR that is not part of a CRLF, so git stores them "
                        + "verbatim and every later diff of them is a whole-file diff. Normalise "
                        + "CRLF->LF, then CR->LF, then LF->CRLF. ⚠ The post-condition most patch "
                        + "scripts here carry — no bare LF after stripping CRLF — reads CR-CR-LF as "
                        + "clean, so it cannot catch this.")
                .isEmpty();
    }
}
