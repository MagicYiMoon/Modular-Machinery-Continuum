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
package hellfirepvp.modularmachinery.common.upgrade;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * One kind of machine upgrade: its identity, its display name, its level and how far it stacks.
 *
 * <h2>Rule A0 — upstream shape, and the one field that could not be copied</h2>
 *
 * <p>Upstream: {@code github.kasuminova.mmce.common.upgrade.UpgradeType}, fields
 * {@code compatibleMachines}/{@code incompatibleMachines}/{@code name}/{@code localizedName}/{@code level}/
 * {@code maxStackSize}, and its own {@code isCompatible(DynamicMachine)} at U49-U57. This class keeps every one of
 * those and the compatibility RULE verbatim:
 *
 * <pre>
 *   if (!compatibleMachines.isEmpty())   return compatibleMachines.contains(machine);      // allow-list WINS
 *   if (!incompatibleMachines.isEmpty()) return !incompatibleMachines.contains(machine);
 *   return true;                                                                            // neither list: all
 * </pre>
 *
 * <p><b>The one thing that changed is WHAT the machine sets contain, and it is a real difference rather than a
 * tidy-up.</b> Upstream holds {@code Set<DynamicMachine>} and fills it from CraftTweaker's
 * {@code MachineUpgradeBuilder.addCompatibleMachines}, which runs its lookups at script-execution time — after
 * machine JSON is loaded — and queues them through {@code MachineModifier.WAIT_FOR_MODIFY} when it is not. This
 * port has no script API ({@code CraftTweakerIntegration} is a stub), so the registrations happen at <b>mod
 * construction</b>, which is BEFORE {@code MachineReloadListener} has read a single machine file. Holding
 * {@code DynamicMachine} references there would mean holding objects that do not exist yet, and the loader
 * REPLACES its machine objects on every reload — so a reference captured at construction would go stale and
 * silently stop matching.
 *
 * <p>So this class holds the machines' <b>registry names</b> ({@code DynamicMachine#getRegistryName()}, upstream
 * U223 uses the same string for the same purpose) and takes a name in {@code isCompatible}. The rule, the
 * precedence and the outcome are upstream's; only the handle is a string, for the reason above.
 *
 * <p>Sets are {@link LinkedHashSet} rather than {@link java.util.HashSet}: the order is {@code DynamicMachine}'s
 * {@code equals}/{@code hashCode} upstream, and this port has no such identity to rely on, so preserving insertion
 * order at least makes a report readable and deterministic instead of depending on hash order.
 */
public class UpgradeType {

    private final Set<String> compatibleMachines = new LinkedHashSet<>();
    private final Set<String> incompatibleMachines = new LinkedHashSet<>();
    private final String name;
    private final String localizedName;
    private final float level;
    private final int maxStackSize;

    /**
     * @param name          this upgrade's unique id, which is also the key
     *                      {@link RegistryMachineUpgrade} registers it under and the key
     *                      {@link hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus}'s custom-data map uses
     * @param localizedName a translation key or a literal; see {@link #getLocalizedName()}
     * @param level         upstream's {@code level} field, which its own builder javadoc marks "暂无作用" (no effect
     *                      yet). Kept because a plugin author reads it and because removing it would change the
     *                      constructor a ported registration uses
     * @param maxStackSize  how many items' worth of this upgrade one type may accumulate to; see
     *                      {@code TileUpgradeBus}, which enforces it the way upstream's
     *                      {@code checkAndAddUpgradeBus} does (U861)
     */
    public UpgradeType(final String name, final String localizedName, final float level, final int maxStackSize) {
        this.name = Objects.requireNonNull(name, "an upgrade type needs a name");
        this.localizedName = localizedName == null ? name : localizedName;
        this.level = level;
        this.maxStackSize = maxStackSize;
    }

    /** @return a convenience type with upstream's own level/stack defaults for a fixed effect */
    public static UpgradeType of(String name, String localizedName, int maxStackSize) {
        return new UpgradeType(name, localizedName, 1.0F, maxStackSize);
    }

    public Set<String> getCompatibleMachines() {
        return Collections.unmodifiableSet(compatibleMachines);
    }

    public Set<String> getIncompatibleMachines() {
        return Collections.unmodifiableSet(incompatibleMachines);
    }

    /**
     * Adds a machine this upgrade may be installed on, by registry name.
     *
     * <p>The two lists are mutually exclusive and upstream THROWS when they overlap (U31-U43) — including when the
     * same machine is added twice to the two different lists. The check is kept, and it is worth keeping: the two
     * lists have OPPOSITE precedence, so an upgrade present in both would mean "only this machine" and "every
     * machine except this one" at the same time with no way to tell which the author meant.
     */
    public void addCompatibleMachine(final String machineName) {
        if (incompatibleMachines.contains(machineName)) {
            throw new IllegalArgumentException("Machine '" + machineName + "' is already in the incompatible list of"
                    + " upgrade '" + name + "'; an upgrade cannot allow and deny the same machine");
        }
        compatibleMachines.add(machineName);
    }

    /** Adds a machine this upgrade may NOT be installed on, by registry name. See {@link #addCompatibleMachine}. */
    public void addIncompatibleMachine(final String machineName) {
        if (compatibleMachines.contains(machineName)) {
            throw new IllegalArgumentException("Machine '" + machineName + "' is already in the compatible list of"
                    + " upgrade '" + name + "'; an upgrade cannot deny and allow the same machine");
        }
        incompatibleMachines.add(machineName);
    }

    /**
     * @param machineName the registry name of the machine asking, or {@code null} for "no machine known"
     * @return whether an upgrade of this type may act on that machine
     *
     * <p>Upstream's rule, in upstream's order (U49-U57). {@code null} — a bus whose controller is gone, or the
     * emission path of a machine being torn down — is treated as compatible, which is the same answer upstream
     * gives for a {@code null} machine in {@code UpgradeBusProvider.getUpgrades} (U258: {@code foundMachine == null
     * || type.isCompatible(foundMachine)}); the alternative would make a torn-down machine's upgrades flap in and
     * out while the machine is being removed.
     */
    public boolean isCompatible(final String machineName) {
        if (machineName == null) {
            return true;
        }
        if (!compatibleMachines.isEmpty()) {
            return compatibleMachines.contains(machineName);
        }
        if (!incompatibleMachines.isEmpty()) {
            return !incompatibleMachines.contains(machineName);
        }
        return true;
    }

    public String getName() {
        return name;
    }

    /**
     * @return the translated display name for this upgrade, as a component the caller can render
     *
     * <p>Upstream returns a {@code String} from {@code I18n.hasKey(localizedName) ? I18n.format(localizedName) :
     * localizedName} (U65). <b>The port returns a {@link Component} instead, and the reason is that upstream's
     * {@code I18n} does not exist on a dedicated server</b>: {@code net.minecraft.client.resources.language.I18n}
     * is a CLIENT class, so the guard upstream used to decide between "translate" and "use the literal" cannot be
     * called from this class at all — this class is loaded during mod construction on every side, including a
     * dedicated server, where reaching for it would be a {@code NoClassDefFoundError}.
     *
     * <p>So the translation is resolved where it legitimately can be: {@code Component#translatable} carries the key
     * through to whoever renders it, and 1.20.1 renders a missing key as the key itself, which is exactly upstream's
     * {@code localizedName} fallback for the case that fallback existed for (a registration that passed a literal
     * string rather than a key). The difference that remains is stated rather than glossed: <b>a registration that
     * passes a KEY whose lang entry is absent shows the key on screen</b>, where upstream would have shown the key
     * too — upstream's own expression is {@code I18n.format(localizedName)} whenever {@code hasKey} is true, and an
     * absent entry makes it false, which returns the same string. Upstream never showed a literal for the key case
     * either.
     */
    public Component getLocalizedName() {
        return Component.translatable(localizedName);
    }

    public float getLevel() {
        return level;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** {@inheritDoc} Upstream's identity is the name (U77-U79). */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof UpgradeType other && name.equals(other.name);
    }

    @Override
    public String toString() {
        return "UpgradeType[" + name + "]";
    }

    /** @return the lowercase id, which is what a language key's last segment is built from */
    public String getSerializedName() {
        return name.toLowerCase(Locale.ROOT);
    }
}
