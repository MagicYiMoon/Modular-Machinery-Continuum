/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import java.util.HashMap;
import java.util.Map;

/**
 * What happens to a recipe's progress when a tick of it FAILS.
 *
 * <h2>This class exists because an audit against the recovered upstream source found it missing</h2>
 *
 * <p>Upstream has this enum, reads it from the machine JSON's {@code failure-action} key, and applies it in
 * {@code ActiveMachineRecipe.doFailureAction}. The port had NONE of the three: no enum, no field, no parse — and the
 * key is declared by the port's OWN shipped content, {@code assembly_line.json:4}, which asks for
 * {@code "decrease"} and silently got {@code "still"}.
 *
 * <p>That is the shape a whole-audit slice kept finding: <b>the data files are byte-identical to upstream's and the
 * parsers read a fraction of what they contain.</b> A key nothing reads is not an error the loader can report,
 * because the loader never looked.
 *
 * <h2>The three actions, from upstream's {@code doFailureAction}</h2>
 *
 * <pre>
 *     case RESET    -&gt; this.tick = 0;                      // lose all progress
 *     case DECREASE -&gt; { if (this.tick &gt; 0) this.tick--; }  // lose one tick of progress
 *     // STILL: nothing happens                              // keep progress
 * </pre>
 *
 * <p><b>STILL is the default</b>, which upstream also makes configurable through
 * {@code general/default-failure-actions} (default {@code "still"}). The port has no config for it yet; the constant
 * here is upstream's own default value, and the field is written so that adding the config later changes one line.
 *
 * <h2>What was NOT copied</h2>
 *
 * <p>Upstream's enum carries {@code @ZenRegister}/{@code @ZenClass} CraftTweaker annotations, a
 * {@code REGISTRY_FAILURE_ACTIONS} map keyed by {@code ResourceLocation}, and a {@code LAST_READ_CONFIG} static for
 * its config reload. None of those are behaviour a player observes, and the port's CraftTweaker integration does not
 * expose this type yet, so they are omitted rather than stubbed — a stub would be a claim that a scripting API exists.
 */
public enum RecipeFailureActions {

    /** Progress is discarded entirely: the recipe starts over. */
    RESET("reset"),

    /** Progress is kept where it is: the recipe waits for the inputs to return. Upstream's default. */
    STILL("still"),

    /** Progress retreats one tick for each failing tick. */
    DECREASE("decrease");

    /** Upstream's default, and the value upstream's config defaults to. */
    private static final Map<String, RecipeFailureActions> NAME_MAP = new HashMap<>();

    static {
        for (RecipeFailureActions value : values()) {
            NAME_MAP.put(value.name, value);
        }
    }

    private final String name;

    RecipeFailureActions(String name) {
        this.name = name;
    }

    /** The name as it appears in the machine JSON. */
    public String getName() {
        return name;
    }

    /**
     * Resolves a JSON value.
     *
     * <p><b>An unknown name falls back to the default rather than throwing</b>, which is upstream's behaviour: its
     * {@code getFailureAction} returns {@code defaultAction} when {@code NAME_MAP} has no entry. That is deliberately
     * more forgiving than the sibling {@code requires-blueprint} key, which upstream DOES reject — the difference is
     * upstream's, and matching it is the whole point of this class existing.
     *
     * @return the matching action, or {@link #DEFAULT_ACTION} when the name is unknown
     */
    public static RecipeFailureActions byName(String name) {
        RecipeFailureActions action = name == null ? null : NAME_MAP.get(name);
        return action == null ? getDefaultAction() : action;
    }

    /** Internal parser used by Config to avoid a Config -> enum -> Config initialization loop. */
    public static RecipeFailureActions byNameWithoutConfig(String name) {
        RecipeFailureActions action = name == null ? null : NAME_MAP.get(name);
        return action == null ? STILL : action;
    }

    /** @return the action used when a machine does not declare one */
    public static RecipeFailureActions getDefaultAction() {
        return hellfirepvp.modularmachinery.common.data.Config.defaultFailureAction();
    }
}
