package com.tss.grc.common.util;

/**
 * F2-449 — the one place a caller-supplied term is made safe to bind into a {@code LIKE} pattern.
 *
 * <p>SQL Server treats <b>{@code %}</b>, <b>{@code _}</b> and a <b>closed {@code [..]}</b> as
 * pattern metacharacters, and {@code LIKE} has <b>no default escape character</b> — so a query
 * has to declare one. Every search query in this platform built its pattern as
 * {@code CONCAT('%', :q, '%')} with no {@code ESCAPE} clause, which makes the caller's own string
 * the pattern. Probed live pre-fix: {@code ?search=%}, {@code ?search=_} and {@code ?search=[ac]}
 * each returned the <b>whole register</b> (7 of 7 users, 35 of 35 compliance controls) where a
 * nonsense term returned 0.
 *
 * <p>🔑 This is house rule 5's own trap — <i>{@code [REVIEW-TEST]} in a {@code LIKE} is a
 * character class, so use {@code CHARINDEX} for a literal</i> — asked of <b>user input</b> rather
 * than of test data. The platform already knew the shape; nobody had put the question to the
 * search boxes.
 *
 * <p><b>Both halves are required and neither works alone.</b> Escaping without an
 * {@code ESCAPE} clause stores a literal backslash in the pattern and matches nothing;
 * an {@code ESCAPE} clause without escaping changes nothing at all. Every query that binds a
 * caller term therefore declares {@link #ESCAPE_CLAUSE} and every binding site calls
 * {@link #escape(String)} — and {@code LikePatternEscapingContractTest} holds that pairing at a
 * hard zero by reflection over the {@code @Query} values, because a source scan keyed on the text
 * {@code "@Query("} silently missed the two methods that write the annotation fully qualified.
 */
public final class LikePatterns {

    private LikePatterns() { }

    /**
     * The escape character these queries declare. Backslash is conventional and, unlike a
     * printable stand-in such as {@code '!'}, is not a character a user is likely to type — but
     * that only matters for readability: {@link #escape(String)} escapes the escape character
     * itself, so any choice would be correct.
     */
    public static final char ESCAPE_CHAR = '\\';

    /**
     * The clause a query must carry, written as it appears in JPQL/SQL: {@code ESCAPE '\'}.
     *
     * <p>⚠ This constant is documentation, not something to interpolate: a {@code @Query} value is
     * a compile-time constant, so the clause is written out at each of the 25 {@code LIKE}
     * occurrences and the contract test is what stops one being forgotten.
     */
    public static final String ESCAPE_CLAUSE = "ESCAPE '\\'";

    /**
     * Escape a caller-supplied term so that it matches literally inside a {@code LIKE} pattern.
     *
     * <p>The caller keeps the surrounding wildcards — most of these queries wrap the term in SQL
     * as {@code CONCAT('%', :q, '%')} — so this escapes only what is inside the term.
     *
     * <p>⚠ <b>{@code ]} is deliberately NOT escaped.</b> Outside a character class SQL Server
     * treats {@code ]} as an ordinary character, and escaping {@code [} means a class can never
     * open — so a lone {@code ]} has nothing to close and stays literal. Escaping it as well would
     * be harmless but would state a rule that is not the one doing the work.
     *
     * @param term a caller-supplied search term; {@code null} is returned unchanged, because every
     *             caller here treats {@code null} as "no filter" and turning it into an empty
     *             pattern would change which rows a filterless request returns
     * @return the term with every {@code LIKE} metacharacter prefixed by {@link #ESCAPE_CHAR}
     */
    public static String escape(String term) {
        if (term == null || term.isEmpty()) {
            return term;
        }
        StringBuilder out = new StringBuilder(term.length() + 8);
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_' || c == '[') {
                out.append(ESCAPE_CHAR);
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * A contains-match pattern for a caller-supplied term: the term escaped, wrapped in wildcards.
     *
     * <p>For the few queries that take a whole pattern rather than a term — a repository parameter
     * actually named {@code likePattern} — so that the wrapping and the escaping cannot come apart
     * at the call site.
     */
    public static String contains(String term) {
        return term == null ? null : "%" + escape(term) + "%";
    }
}
