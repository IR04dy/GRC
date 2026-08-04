package com.tss.grc.security;

import com.tss.grc.module.platform.licensing.SkuRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5 / CE-1 — the rule that makes the Change Enablement separation real (ADR-012, §6c).
 *
 * <p><b>{@code module/change_mgmt/**} may import only itself, {@code common.*},
 * {@code security.*}, {@code platform.*} and its own {@code spi} interfaces — never another
 * {@code module.<peer>}.</b>
 *
 * <p><b>Why this test rather than a paragraph.</b> §6c measured the module's coupling and found it
 * one of the cleanest in the platform — fan-out 3, fan-in 1, zero risk imports — and concluded the
 * separation was "an information-architecture job, not a backend extraction". True, and it made the
 * remaining coupling easy to miss: the module still imported {@code admin.committee.entity.CommitteeReferral},
 * two {@code admin.committee} services and three {@code module.incident} types. Seven import lines
 * across three files, and while they stood, "Change Enablement is a separate module" described the
 * directory layout rather than the dependency graph. A prose claim cannot fail; this can.
 *
 * <p><b>Direction of enforcement.</b> Inbound coupling is deliberately <em>not</em> banned — peers
 * may read the change module (today {@code reporting} does, for the board pack, plus one
 * {@code admin} and one {@code common.exception} reference). A module that nothing may depend on is
 * not modular, it is isolated. What matters is that the change module can be compiled, reasoned
 * about and eventually extracted without dragging a peer in behind it.
 *
 * <p><b>The adapters are the exception that proves the rule.</b> {@code module/change_adapters}
 * imports both sides and is therefore the only place in the platform that may. It lives outside
 * both modules so neither depends on the other, and it is asserted to exist — a boundary with no
 * adapter means the behaviour was deleted rather than relocated.
 *
 * <p><b>The peer list is a decision log, not a tally.</b> It was {@code (admin, incident)} until
 * S7d/CE-7 needed a third: a PIR follow-up has to become a tracked CAPA, and the Base universal
 * tracker is a peer module like any other. Widening it is the intended way through — but only in the
 * commit that adds the port and states why the dependency is worth having. Widening it to quiet a
 * red build turns the boundary into a record of what leaked past it, which is the failure mode the
 * whole file exists to prevent.
 */
class ChangeModuleBoundaryTest {

    private static final Path MODULE   = Path.of("src/main/java/com/tss/grc/module/change_mgmt");
    private static final Path ADAPTERS = Path.of("src/main/java/com/tss/grc/module/change_adapters");

    /** {@code import com.tss.grc.module.<peer>...} — the group is the peer package. */
    private static final Pattern MODULE_IMPORT =
        Pattern.compile("^\\s*import\\s+(?:static\\s+)?com\\.tss\\.grc\\.module\\.([a-z_0-9]+)\\.",
                        Pattern.MULTILINE);

    /**
     * The only {@code module.*} packages the change module may reach.
     *
     * <p>{@code platform} is the kernel (audit ledger, automation events, licensing, realtime,
     * calendar). {@code change_mgmt} is itself. Everything else must go through {@code spi}.
     */
    private static final Set<String> ALLOWED = Set.of("change_mgmt", "platform");

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Strip block and line comments — a javadoc naming a peer is documentation, not a dependency. */
    private static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }

    @Test
    void theChangeModuleImportsNoPeerModule() {
        assertThat(MODULE).as("the change module must exist for this test to mean anything").exists();
        List<Path> files = javaFiles(MODULE);
        assertThat(files)
            .as("anti-vacuity: an empty scan would make every assertion below pass. If the module "
              + "moved (S5's rename to module/change), update MODULE — do not let the ratchet go quiet")
            .hasSizeGreaterThan(20);

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            Matcher m = MODULE_IMPORT.matcher(code(read(f)));
            while (m.find()) {
                String peer = m.group(1);
                if (!ALLOWED.contains(peer))
                    violations.add(MODULE.relativize(f) + " → module." + peer);
            }
        }

        assertThat(violations)
            .as("""
                ADR-012: module/change_mgmt may import only itself, common.*, security.*, platform.* \
                and its own spi interfaces. These imports reach a peer module directly:

                %s

                Add a method to CommitteePort / IncidentPort (or a new port with a stated reason), \
                implement it in module/change_adapters, and inject the port. Do NOT widen ALLOWED — \
                that turns the boundary into a record of what leaked past it.""", violations)
            .isEmpty();
    }

    @Test
    void thePortsExistAndAreInterfacesOnly() {
        Path spi = MODULE.resolve("spi");
        assertThat(spi).as("module/change_mgmt/spi must hold the outbound ports").exists();

        List<Path> ports = javaFiles(spi);
        assertThat(ports).as("the spi package must not be empty — a boundary needs a seam").isNotEmpty();

        for (Path p : ports) {
            String src = code(read(p));
            assertThat(src)
                .as("%s must declare an interface. An spi package holding a class is a second "
                  + "implementation, and the module now has two answers", p.getFileName())
                .containsPattern("\\binterface\\s+\\w+");
            Matcher m = MODULE_IMPORT.matcher(src);
            while (m.find()) {
                assertThat(m.group(1))
                    .as("%s imports module.%s — a port that names a peer type is not a boundary, it "
                      + "is the same import one file sideways: the module would still compile against "
                      + "the peer and still break when it changes",
                        p.getFileName(), m.group(1))
                    .isIn(ALLOWED);
            }
        }
    }

    @Test
    void theAdaptersExistOutsideBothModulesAndAreTheOnlyPlaceThatKnowsBothSides() {
        assertThat(ADAPTERS)
            .as("module/change_adapters must exist — a port with no adapter means the behaviour was "
              + "deleted rather than relocated, and the F2-14 anti-pattern is a port with no consumer")
            .exists();

        Set<String> peersReached = new TreeSet<>();
        boolean reachesTheSpi = false;
        for (Path f : javaFiles(ADAPTERS)) {
            String src = code(read(f));
            Matcher m = MODULE_IMPORT.matcher(src);
            while (m.find()) {
                if ("change_mgmt".equals(m.group(1))) reachesTheSpi = true;
                else if (!"platform".equals(m.group(1))) peersReached.add(m.group(1));
            }
        }

        assertThat(reachesTheSpi)
            .as("the adapters must import the change module's spi — otherwise they implement nothing")
            .isTrue();
        assertThat(peersReached)
            .as("""
                the adapters exist to reach exactly the peers the change module gave up: \
                admin (the committee engine), capa (S7d/CE-7's PIR follow-up tracker), evidence \
                (S8a/CE-9's change evidence) and incident (CH-13's rollback hook). Reaching a fifth \
                arrived without a port decision — widen this list only together with a new \
                port and the reason it exists, never to make a red build green.""")
            .containsExactly("admin", "capa", "evidence", "incident");
    }

    @Test
    void theAdaptersCarryNoEndpointOfTheirOwn() {
        for (Path f : javaFiles(ADAPTERS)) {
            String src = code(read(f));
            assertThat(src)
                .as("%s declares a controller. The guarded surface belongs in module/change_mgmt "
                  + "where the @RequireModule SKU annotation already is; an endpoint here would be a "
                  + "second, unguarded door into the same behaviour (the F2-04 shape)", f.getFileName())
                .doesNotContain("@RestController")
                .doesNotContain("@RequestMapping");
        }
    }

    /**
     * ADR-018 R1 — creating a module package is an entitlement decision, not a directory.
     *
     * <p>{@code SkuRegistry.requiredSkuForPackage} <b>throws</b> for any {@code com.tss.grc.module.*}
     * package ADR-018 never homed, "because silently treating it as Base is exactly how seven
     * packages became unsold revenue". So {@code change_adapters} needed a decision the moment it
     * existed. It is homed to {@code CHANGE}: it serves only Change Enablement, and homing it to Base
     * would put change-specific glue in the mandatory platform.
     */
    @Test
    void theAdaptersPackageIsHomedToTheSameSkuAsTheModuleItServes() {
        assertThat(SkuRegistry.PACKAGE_TO_SKU.get("change_adapters"))
            .as("an unhomed module package makes requiredSkuForPackage throw for anything inside it")
            .isEqualTo("CHANGE");
        assertThat(SkuRegistry.PACKAGE_TO_SKU.get("change_adapters"))
            .as("the adapters must be sold with the module they serve, or the entitlement is split "
              + "across two SKUs and a tenant can hold one half")
            .isEqualTo(SkuRegistry.PACKAGE_TO_SKU.get("change_mgmt"));
        assertThat(SkuRegistry.BASE_PACKAGES)
            .as("change_adapters must not be Base — Base has no licence row and no code that belongs "
              + "to one commercial module (ADR-018 R4)")
            .doesNotContain("change_adapters");
    }

    /**
     * ⚠ The package is called {@code change_mgmt} <b>by decision</b>, not by inertia — do not
     * "tidy" it to {@code change}.
     *
     * <p>{@code UNIFIED_IMPLEMENTATION_PLAN} §6c proposed the tree as {@code module/change/**} on
     * <b>2026-07-25</b> (`ff087c7c`), and <b>S5b</b> is written as "the package move
     * {@code module/change_mgmt} → {@code module/change}". Five days later
     * <b>ADR-017 D-4</b> (`921f8a11`, 2026-07-30) settled the naming question in the opposite
     * direction:
     *
     * <blockquote>{@code Change Governance & CAB} is the shelf/SKU name; <b>{@code change_mgmt}
     * remains the package name</b> and "Change Enablement" the internal practice name, because ITIL
     * alignment is an asset in engineering documentation and a liability on a price list.
     * </blockquote>
     *
     * <p>So the rename would reverse a ratified decision taken <em>after</em> the plan proposed it,
     * and it is not a cosmetic 55-file sweep but a reversal. The same trap W1-5 hit from the other
     * side: its first draft constituted a committee of type {@code "CAB"}, and the obvious fix —
     * widening the vocabulary — would have reversed ADR-017 D-2. A decision that lives only in a
     * document gets undone by the next person doing an obvious tidy-up, so it is pinned here.
     *
     * <p>To genuinely rename the package, amend ADR-017 D-4 first and delete this test in the same
     * commit — that is the intended way through, and it forces the decision to be re-taken rather
     * than drifted past.
     */
    @Test
    void thePackageNameIsChangeMgmtByRatifiedDecisionAndNotByAccident() {
        assertThat(Files.isDirectory(MODULE))
            .as("ADR-017 D-4: `change_mgmt` remains the package name. If this fails because the "
              + "package was renamed, the rename reversed a ratified decision — amend the ADR or "
              + "revert the move")
            .isTrue();
        assertThat(MODULE.toString().replace('\\', '/'))
            .as("the module must still live at module/change_mgmt (ADR-017 D-4)")
            .endsWith("module/change_mgmt");
        assertThat(SkuRegistry.PACKAGE_TO_SKU)
            .as("the entitlement homing keys on the package NAME, so a rename silently unhomes the "
              + "module and requiredSkuForPackage throws for everything inside it (ADR-018 R1)")
            .containsKey("change_mgmt");
        assertThat(ALLOWED)
            .as("the boundary's own allow-list names the package; a rename that missed this would "
              + "make every one of the module's self-imports look like a peer violation")
            .contains("change_mgmt");
    }
}
