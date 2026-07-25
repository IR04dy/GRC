# DF-3 mutation testing: break each new guard in turn and prove a test catches it.
# A guard whose removal breaks nothing is not a control.
import subprocess, shutil, os, re, sys

SVC = r"D:\AYMAN\GRC\tss-grc-backend\src\main\java\com\tss\grc\module\admin\committee\service\CommitteeResolutionService.java"
BAK = SVC + ".mutbak"
CWD = r"D:\AYMAN\GRC\tss-grc-backend"
JAVA_HOME = r"C:\Program Files\Java\jdk-17"

# (label, find, replace, test that MUST fail)
MUTATIONS = [
    ("on-behalf gate removed (any member may vote as anyone)",
     "            if (!(actorSeat != null && sameName(actorSeat.getMemberName(), seat.getMemberName()))\n"
     "                    && !actor.mayRecordOnBehalf())",
     "            if (false)",
     "CommitteeVoteProvenanceTest#a_member_cannot_cast_a_vote_in_another_members_name"),

    ("roster membership check removed (any name accepted)",
     '            seat = roster.stream().filter(t -> sameName(t.getMemberName(), want)).findFirst()\n'
     '                .orElseThrow(() -> new IllegalArgumentException(',
     '            seat = roster.stream().filter(t -> sameName(t.getMemberName(), want)).findFirst()\n'
     '                .orElse(roster.isEmpty() ? null : roster.get(0));\n'
     '            if (false) throw new IllegalArgumentException((',
     "CommitteeVoteProvenanceTest#a_name_that_is_not_on_the_active_roster_is_refused"),

    ("canonical name replaced by the raw request string (recusal evasion returns)",
     "        String member = norm(seat.getMemberName());",
     "        String member = norm(requestedMember).isEmpty() ? norm(seat.getMemberName()) : norm(requestedMember);",
     "CommitteeVoteProvenanceTest#coi_recusal_cannot_be_evaded_by_a_case_or_padding_variant_of_the_name"),

    ("identified-actor check removed",
     "        if (actor == null || (actor.userId() == null && norm(actor.username()).isEmpty()))",
     "        if (false)",
     "CommitteeVoteProvenanceTest#an_unidentified_caller_cannot_record_a_vote_at_all"),

    ("non-voting-member check removed",
     "        if (seat.getIsVotingMember() != null && seat.getIsVotingMember() == 0)",
     "        if (false)",
     "CommitteeVoteProvenanceTest#a_non_voting_member_cannot_cast_a_counted_vote"),
]

env = dict(os.environ, JAVA_HOME=JAVA_HOME)
shutil.copy2(SVC, BAK)
results = []
try:
    for label, find, repl, test in MUTATIONS:
        src = open(BAK, encoding="utf-8").read()
        if find not in src:
            results.append((label, test, "SKIP — mutation anchor not found")); print("!! anchor miss:", label); continue
        open(SVC, "w", encoding="utf-8", newline="\n").write(src.replace(find, repl, 1))
        p = subprocess.run(["mvn.cmd", "-o", "test", "-Dtest=" + test.replace("#", "#")],
                           cwd=CWD, env=env, capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        out = p.stdout + p.stderr
        compiled = "COMPILATION ERROR" not in out
        failed = ("BUILD FAILURE" in out) and compiled
        verdict = ("CAUGHT — test failed as required" if failed
                   else ("INCONCLUSIVE — mutation did not compile" if not compiled
                         else "*** NOT CAUGHT — the guard is unproven ***"))
        results.append((label, test, verdict))
        print(f"  {verdict:52} {label}")
finally:
    shutil.copy2(BAK, SVC)
    os.remove(BAK)

print("\n" + "=" * 96)
print(f"{'verdict':54} guard")
for label, test, verdict in results:
    print(f"{verdict:54} {label}")
bad = [r for r in results if not r[2].startswith("CAUGHT")]
print("\nRESULT:", "all guards proven" if not bad else f"{len(bad)} guard(s) NOT proven")
sys.exit(1 if bad else 0)
