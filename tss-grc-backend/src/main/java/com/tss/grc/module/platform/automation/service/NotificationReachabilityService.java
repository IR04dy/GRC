package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * F2-94 — answers "if these rules fired right now, who would actually be told?"
 *
 * <h3>Why this exists rather than another log line</h3>
 * The engine had been reporting its own inertness for as long as it had been running:
 * {@code platform.audit_log} held 143 rows in dev, and 164 in production, whose message was
 * {@code "has no role configured"}. Nobody read them. That is the third time in this
 * programme a control announced its own failure into a channel with no reader — after
 * F2-87's boot WARN and F2-92's javadoc. A better-worded WARN would have been the fourth.
 *
 * <p>So the state is exposed at {@code GET /api/v1/automation/notification-reachability}, with the
 * two failures separated because their remedies are different and only one of them is a grant:
 * <ul>
 *   <li>{@link #NO_ACTIVE_HOLDER} — the role exists here, nobody active holds it. Grant it.</li>
 *   <li>{@link #NOT_A_ROLE} — the name is not a role in this tenant. No grant can fix that;
 *       the rule is wrong, or the role catalogue is missing an entry. Measured 2026-08-03:
 *       <b>69 of the 84 role names addressed by automation rules are not rows in
 *       {@code iam.roles}</b> — the same ghost-role shape as F2-52, in a third subsystem.</li>
 * </ul>
 *
 * <p>Deliberately <em>not</em> done here: creating the 69 missing roles. Automation seeds are
 * not an access model, and inventing roles to make a report go green would fabricate one.
 *
 * <h3>⚠⚠ F2-437 — this sentence used to read "a report an operator can OPEN", and it was the
 * fourth instance of the very pattern counted above</h3>
 *
 * There is <b>no screen</b>. Measured 2026-09-02: the frontend contains <b>zero</b> references to
 * this endpoint — no client in {@code lib/`}, no page, no link — and {@code GET
 * /api/v1/automation/rules} has none either, so all 214 automation rules are administered only by
 * SQL. An operator cannot open this report, and the paragraph above claimed they could.
 *
 * <p>🔑 <b>The count in that paragraph was right and it stopped one short of itself.</b> F2-87's
 * boot WARN, F2-92's javadoc, this engine's 143-and-164 audit rows — and then a JSON endpoint
 * nothing fetches. An unread report is a channel with no reader exactly as an unread WARN is; what
 * changed was the format, not the readership. Building the console is product work and is filed;
 * what is fixed here is the <em>claim</em>, because a comment asserting a reader is worse than no
 * comment — it answers the question the next reader was about to ask (F2-184).
 *
 * <p>{@code SelfReportingControlReaderContractTest} holds the population: this endpoint, {@code
 * /api/v1/changes/sod-coverage} and {@code /api/v1/changes/governance-health} are the three
 * report-shaped surfaces with no frontend reader, asserted exactly and in both directions, so
 * wiring one must delete its entry and a fourth cannot join a count silently.
 */
@Service
@RequiredArgsConstructor
public class NotificationReachabilityService {

    public static final String DELIVERABLE = "DELIVERABLE";
    public static final String NO_ACTIVE_HOLDER = "NO_ACTIVE_HOLDER";
    public static final String NOT_A_ROLE = "NOT_A_ROLE";

    private static final ObjectMapper M = new ObjectMapper();

    private final AutomationRuleRepository rules;
    private final RoleRepository roles;

    /** One addressed role name and whether a broadcast to it would land. */
    public record RoleReachability(String role, String status, int activeHolders) {}

    /** One NOTIFY/ESCALATE rule and the audience it would actually reach. */
    public record RuleReachability(String ruleCode, String nameEn, String triggerEvent,
                                   String actionType, boolean enabled, long fireCount,
                                   List<RoleReachability> audience, boolean reachesNobody) {}

    public record ReachabilityReport(Long organisationId,
                                     int notifyRules,
                                     int reachingNobody,
                                     int audienceless,
                                     List<String> notARole,
                                     List<String> noActiveHolder,
                                     List<RuleReachability> rules,
                                     String remedy) {}

    @Transactional(readOnly = true)
    public ReachabilityReport report(Long organisationId) {
        Map<String, Integer> holders = holdersByRole(organisationId);

        List<RuleReachability> out = new ArrayList<>();
        var notARole = new TreeSet<String>();
        var unheld = new TreeSet<String>();
        int reachingNobody = 0;
        int audienceless = 0;

        for (AutomationRule rule : rules.findByOrganisationIdOrderByPriorityAscRuleCodeAsc(organisationId)) {
            JsonNode action;
            try {
                action = M.readTree(rule.getActionJson() == null ? "{}" : rule.getActionJson());
            } catch (Exception unparseable) {
                continue;
            }
            String type = action.path("type").asText("");
            if (!"NOTIFY".equals(type) && !"ESCALATE".equals(type)) continue;

            List<RoleReachability> audience = new ArrayList<>();
            boolean anyDeliverable = false;
            for (String role : AutomationActionExecutor.recipientRoles(action)) {
                Integer count = holders.get(role.toUpperCase(Locale.ROOT));
                if (count == null) {
                    audience.add(new RoleReachability(role, NOT_A_ROLE, 0));
                    notARole.add(role);
                } else if (count == 0) {
                    audience.add(new RoleReachability(role, NO_ACTIVE_HOLDER, 0));
                    unheld.add(role);
                } else {
                    audience.add(new RoleReachability(role, DELIVERABLE, count));
                    anyDeliverable = true;
                }
            }
            if (audience.isEmpty()) audienceless++;
            if (!anyDeliverable) reachingNobody++;

            out.add(new RuleReachability(rule.getRuleCode(), rule.getNameEn(),
                rule.getTriggerEvent(), type, Boolean.TRUE.equals(rule.getEnabled()),
                rule.getFireCount() == null ? 0L : rule.getFireCount(),
                audience, !anyDeliverable));
        }

        out.sort(Comparator.comparing(RuleReachability::reachesNobody).reversed()
            .thenComparing(RuleReachability::ruleCode));

        return new ReachabilityReport(organisationId, out.size(), reachingNobody, audienceless,
            List.copyOf(notARole), List.copyOf(unheld), out, remedy(reachingNobody, notARole.size()));
    }

    private Map<String, Integer> holdersByRole(Long organisationId) {
        Map<String, Integer> holders = new LinkedHashMap<>();
        for (Object[] row : roles.countActiveHoldersByRole(organisationId)) {
            if (row.length < 2 || row[0] == null) continue;
            holders.put(String.valueOf(row[0]).toUpperCase(Locale.ROOT),
                row[1] == null ? 0 : ((Number) row[1]).intValue());
        }
        return holders;
    }

    /**
     * The remedy names the screen, and names which fault it does <em>not</em> address — the
     * F2-96 lesson: a message that sends an operator to the wrong screen is worse than none,
     * because granting a role cannot resolve a name that is not a role.
     */
    private static String remedy(int reachingNobody, int notARoleCount) {
        if (reachingNobody == 0) {
            return "Every NOTIFY/ESCALATE rule in this tenant would reach at least one person.";
        }
        StringBuilder b = new StringBuilder();
        b.append(reachingNobody).append(" rule(s) would fire and reach nobody. ");
        if (notARoleCount > 0) {
            b.append("For roles marked ").append(NOT_A_ROLE).append(" (").append(notARoleCount)
             .append(" name(s)), granting the role is NOT the fix — the name is not a role in "
                   + "this tenant. Either correct the rule's audience under Automation → Rules, "
                   + "or add the role in Access Governance → Roles first. ");
        }
        b.append("For roles marked ").append(NO_ACTIVE_HOLDER)
         .append(", assign the role to an active user in Access Governance → Users.");
        return b.toString();
    }
}
