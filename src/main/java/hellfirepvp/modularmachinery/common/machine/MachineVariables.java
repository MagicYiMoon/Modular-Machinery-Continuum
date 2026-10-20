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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the named block lists that machine JSONs reference as variables.
 *
 * <p>Upstream's {@code default_variables/*.var.json} files map a variable name to a list of acceptable
 * blocks:
 *
 * <pre>
 * "casings_decorative": [ "modularmachinery:blockcasing" ],
 * "casings_energy":     [ "modularmachinery:blockenergyinputhatch",
 *                         "modularmachinery:blockenergyoutputhatch" ]
 * </pre>
 *
 * <p>A part whose {@code elements} names one of these means "any of these blocks satisfies this
 * position". This is how one machine definition accepts any energy hatch tier without enumerating
 * every tier — and it is also why a variable-driven position must be matched as a set rather than by
 * equality.
 *
 * <p>Upstream nested variable definitions (a variable could reference another); resolution here is
 * iterative rather than recursive so that a mis-authored cyclic definition cannot cause a stack
 * overflow at load time — a real hazard when the input is user-editable content.
 */
public final class MachineVariables {

    private final Map<String, List<String>> variables = new LinkedHashMap<>();

    public void put(String name, List<String> elements) {
        variables.put(name, List.copyOf(elements));
    }

    public boolean isVariable(String name) {
        return variables.containsKey(name);
    }

    /** @return the block identifiers bound to {@code name}, or {@code null} if it is not a variable */
    public List<String> get(String name) {
        return variables.get(name);
    }

    public Map<String, List<String>> asMap() {
        return variables;
    }

    public int size() {
        return variables.size();
    }

    /**
     * Expands a variable reference one level.
     *
     * @return the referenced element list, or a single-element list containing {@code name} itself when
     *         it is a plain block id. Never returns {@code null}, so callers can treat a pattern part
     *         uniformly whether or not it used a variable.
     */
    public List<String> resolve(String name) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }
        List<String> bound = variables.get(name);
        if (bound != null) {
            return bound;
        }
        // Inline alternatives are written comma-separated by the loader when a part's 'elements' was an
        // array; a plain name has no comma and comes back as a single-element list.
        if (name.indexOf(',') >= 0) {
            return List.of(name.split(","));
        }
        return List.of(name);
    }
}
