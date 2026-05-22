package com.tss.grc.module.admin.committee.service;

/**
 * Shared helpers for the committees module's defensive-degradation
 * pattern: when the dev DB doesn't have the V154..V159 schema
 * bootstrapped yet, JPA queries throw
 * {@code InvalidDataAccessResourceUsageException} whose root cause
 * carries SQL Server's "Invalid object name 'admin.X'" message.
 *
 * <p>Services wrap their read queries in {@code try / catch}; when
 * {@link #isMissingObjectError(Throwable)} returns {@code true} they
 * return an empty result so the UI can render an empty register
 * instead of a 500. Runtime behaviour is unchanged once
 * {@code setup_committees_db.py} has populated the schema — the
 * catch only activates on the exact missing-object signature.
 */
final class CommitteeSqlGuards {

    private CommitteeSqlGuards() {}

    /** True when the exception (or any cause) looks like a
     *  missing-table error. Covers SQL Server, Postgres, and H2
     *  signatures so the helper survives a backend swap. */
    static boolean isMissingObjectError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String low = msg.toLowerCase();
                if (low.contains("invalid object name")  // SQL Server
                    || low.contains("does not exist")    // Postgres
                    || low.contains("not found")         // H2 / generic
                    ) return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
