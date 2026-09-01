package com.tss.grc.module.policy;

import com.tss.grc.module.policy.entity.PolicyDocument;

import java.util.Comparator;

/**
 * The policy module's one answer to <b>"which of these two policy versions is newer?"</b> — F2-419.
 *
 * <p>A policy version is a caller-supplied dotted string ({@code @Column(nullable = false,
 * length = 20)}), so ordering it <b>lexically is wrong</b> in the ordinary case: {@code "1.10"} is
 * newer than {@code "1.9"} and sorts <em>before</em> it as text, and {@code "10.0"} is newer than
 * {@code "9.0"} and sorts before it too. PM-H2 found that and swept the three latest-version picks
 * in {@code PolicyManagementService} — its comments say so in as many words, <em>"pick the latest
 * version per code by NUMERIC comparison, not the lexical DB max() (which ranked 9.0 above
 * 10.0)"</em> — and F2-401 then hardened the comparison into a proper total order.
 *
 * <p>⚠ Neither reached {@code PolicyExceptionPromotionAdapter}, added later in a sibling package,
 * which picked the newest version with {@code Comparator.comparing(p -> nullSafe(p.getVersion()))}
 * — a lexical sort key, behind a helper named for null-safety. So of the four sites in this module
 * that pick a latest policy version, three used the numeric comparator and one did not
 * (F2-205/F2-282's lone-outlier tell; F2-215's two-of-N, where the mechanism is visibly present
 * three times over).
 *
 * <p>This class exists so there is nowhere left to hand-roll one. {@code
 * PolicyManagementService.compareVersions} delegates here rather than keeping a copy — a second
 * copy is the thing that drifted in the first place (F2-177) — and {@link #LATEST} makes the call
 * site read as what it means, so {@code Comparator.comparing(…getVersion())} need never be written
 * again. {@code PolicyVersionOrderContractTest} enforces both.
 *
 * <p>It is a top-level class in the module root rather than a service method, following
 * {@code ChangeSoD}: a pure comparison carries no tenant, no transaction and no policy about which
 * documents are candidates, so it belongs where any package of the module can reach it. ⚠ The
 * <em>operation</em> is deliberately NOT shared: the adapter matches a submitter's typed code with
 * {@code equalsIgnoreCase(trim())} and {@code newVersion} matches with {@code equals}, so a shared
 * "latest of this code" method would have to pick one of those rules and would change behaviour at
 * one of the two sites (F2-190: share the primitive, not the operation).
 */
public final class PolicyVersions {

    private PolicyVersions() {}

    /**
     * Newest-last ordering of policy documents by version, for {@code Stream.max}.
     *
     * <p>A null version sorts lowest, so a document with no version never wins a {@code max}
     * against one that has a version — which is the right answer for "what is in force".
     */
    public static final Comparator<PolicyDocument> LATEST =
        (a, b) -> compare(a == null ? null : a.getVersion(), b == null ? null : b.getVersion());

    /**
     * Compare dotted version strings NUMERICALLY ({@code "9.0" < "10.0"}), not lexically.
     * A proper TOTAL order, segment by segment: two numeric segments compare by value, a numeric
     * segment sorts before a non-numeric one, and two non-numeric segments compare lexically;
     * missing trailing segments count as 0. (PM-H2, hardened into a total order by F2-401.)
     *
     * <p>The total order matters rather than merely being tidy: F2-401 measured that the previous
     * per-pair mix of numeric and lexical comparison was not transitive — {@code "2" < "10"}
     * numerically and {@code "10" < "1x"} lexically yet {@code "2" > "1x"} lexically — and an
     * intransitive comparator makes {@code Stream.max} return a <em>non-maximal</em> element
     * silently, with no exception to notice.
     */
    public static int compare(String a, String b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
        String[] pa = a.split("\\."), pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i].trim() : "0";
            String sb = i < pb.length ? pb[i].trim() : "0";
            Integer ia = tryParseInt(sa), ib = tryParseInt(sb);
            int c;
            if (ia != null && ib != null) c = Integer.compare(ia, ib);
            else if (ia != null)          c = -1;   // numeric segment sorts before non-numeric
            else if (ib != null)          c = 1;
            else                          c = sa.compareTo(sb);
            if (c != 0) return c;
        }
        return 0;
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
