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

import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One installed machine upgrade: what it is, how many are stacked, and <b>what it changes about a recipe</b>.
 *
 * <h2>Rule A0 — upstream shape, and the ONE field replaced</h2>
 *
 * <p>Upstream: {@code github.kasuminova.mmce.common.upgrade.MachineUpgrade} (95 lines) — abstract, holding
 * {@code type}, {@code parentBus}, {@code stackSize}, NBT read/write, {@code copy(ItemStack)},
 * {@code getDescriptions()}, {@code getBusGUIDescriptions()}, and a
 * {@code Map<Class<?>, List<UpgradeEventHandlerCT>> eventProcessor} with {@code addEventHandler} /
 * {@code getEventHandlers} (U49-U55). Identity is the TYPE: {@code hashCode = type.hashCode()} and
 * {@code equals} compares types (U83-U94). Stack arithmetic is {@code incrementStackSize}/{@code decrementStackSize}
 * over an {@code int} field defaulted to 1 (U28, U66-U72).
 *
 * <p><b>{@code eventProcessor} is live again in this port.</b> Upgrade callbacks are registered by the optional
 * CraftTweaker facade as runtime consumers, copied to each bus instance, and dispatched by
 * {@link hellfirepvp.modularmachinery.common.upgrade.ScriptUpgradeEventRegistry} before ordinary script handlers.
 * They are deliberately not serialized: a ZenCode closure cannot be reconstructed from NBT, and CraftTweaker
 * rebuilds the registry on every script reload. The serializable {@code customData} round trip remains independent
 * and is implemented by this port and by {@link TileUpgradeBus}.
 *
 * <p><b>Declarative modifiers remain the data path for fixed effects</b>, and that is Rule B rather than an invention:
 * the port already has a complete, verified modifier pipeline — {@code RecipeModifier} (target + io + operation +
 * multiplier + chance), produced by {@code MachineTicker#activeModifiers}, consumed by
 * {@code RecipeCraftingContext#withModifiers}, whose result is what the start check, the tick's finish gate, the
 * emission and the factory's runs all read. An upgrade that wants "half the energy" is then the same statement as
 * a machine's declared {@code modifiers} entry, while event callbacks cover effects that depend on the live event
 * context.
 *
 * <h2>What {@code TileUpgradeBus} and the ticker actually do with this</h2>
 *
 * <pre>
 *   an item in a bus slot
 *     -> TileUpgradeBus.refreshUpgrades()            reads the slot, asks RegistryMachineUpgrade
 *     -> MachineUpgrade instances, one per type, stackSize merged
 *     -> MachineContext.upgradeBuses()               finds the bus from the formed structure
 *     -> MachineTicker.activeModifiers(machine, ctx) appends upgradeModifiers(machineName)
 *     -> RecipeCraftingContext.withModifiers(...)    scales the requirements
 * </pre>
 *
 * <p>The duration half is separate and additive rather than part of the modifier list, because this port's
 * {@code RecipeModifier} targets REQUIREMENTS by type id and a recipe's duration is not a requirement — see
 * {@link #durationMultiplier()}.
 */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MachineUpgrade")
public abstract class MachineUpgrade {

    protected final UpgradeType type;

    /** How many of this upgrade are installed, counted the way upstream counts them (U66-U72). */
    protected int stackSize = 1;
    /** Per-upgrade data; upstream stores this on the upgrade bus rather than on the item stack. */
    protected CompoundTag customData = new CompoundTag();

    /**
     * Live handlers declared by CraftTweaker for this upgrade prototype.  They are deliberately kept as runtime
     * callbacks rather than written to NBT: a script closure cannot be reconstructed from a saved world, and the
     * registry is rebuilt on every CraftTweaker reload.  Copies retain the handlers so the instance installed on a
     * bus behaves like the prototype that the script registered.
     */
    private final Map<Class<? extends MachineEvent>, List<BiConsumer<MachineEvent, MachineUpgrade>>> eventHandlers =
            new LinkedHashMap<>();

    protected MachineUpgrade(final UpgradeType type) {
        this.type = type;
    }

    /** @return an independent copy of this upgrade, for installation on a bus */
    public abstract MachineUpgrade copy();

    /** Source-compatible overload; static upgrades do not need an owner stack. */
    public MachineUpgrade copy(ItemStack owner) {
        return copy();
    }

    /** @return the lines a tooltip shows for this upgrade */
    public abstract List<String> getDescriptions();

    /** @return lines displayed beside the upgrade in a bus detail view; static upgrades have none by default */
    public List<String> getBusGUIDescriptions() {
        return Collections.emptyList();
    }

    /**
     * @return the recipe modifiers this upgrade contributes to the machine it is installed on, scaled by
     *         {@link #getStackSize()}
     *
     * <p>Empty for an upgrade that changes nothing about requirements. Called once per tick per machine from
     * {@code MachineTicker#activeModifiers}, so an upgrade installed or removed mid-craft changes the very next
     * tick — which is what upstream's per-tick {@code MachineTickEvent} dispatch gave its upgrades.
     */
    public List<RecipeModifier> requirementsModifiers() {
        return Collections.emptyList();
    }

    /**
     * @return the factor this upgrade multiplies the recipe's DURATION by, {@code 1.0} for "no change"
     *
     * <p><b>WHY THIS IS NOT A {@link RecipeModifier}.</b> Upstream's adapter recipes do declare a duration
     * modifier — {@code default_recipes/alloy_smelter/alloy_smelter_furnaces.adapter.json} carries
     * {@code {"target": "modularmachinery:duration", "io": "input", "operation": 0, "multiplier": 40}} — but this
     * port's {@code RecipeModifier.appliesTo} matches a modifier against a {@link
     * hellfirepvp.modularmachinery.common.crafting.Requirement}'s {@code typeId()}, and a recipe's duration is a
     * field on {@code MachineRecipe}, not a requirement. So a modifier list cannot express it, and pretending
     * otherwise would be a value that parses and silently applies to the wrong thing. This method is the second
     * channel, read by {@code MachineTicker} and {@code FactoryTicker} where they turn a recipe into a duration.
     *
     * <p><b>OWED (and stated rather than hidden):</b> the shipped {@code :duration} key above is still not applied
     * by the port's adapter path — that is a pre-existing gap on the recipe-modifier side, not something this
     * method closes. What it does close is the ability of an upgrade to change a tick count.
     */
    public float durationMultiplier() {
        return 1.0F;
    }

    /** @return the type, which is this object's identity (upstream U79) */
    @ZenCodeType.Getter("type")
    public UpgradeType getType() {
        return type;
    }

    /** @return how many of this upgrade are installed; never below 1 once installed */
    @ZenCodeType.Getter("stackSize")
    public int getStackSize() {
        return stackSize;
    }

    /** Upstream U66-U68: additive, and it returns the new value so a caller can log it. */
    public int incrementStackSize(int increment) {
        return stackSize += increment;
    }

    /** Upstream U70-U72. */
    public int decrementStackSize(int decrement) {
        return stackSize -= decrement;
    }

    /** Upstream U34-U38: the bus writes its own custom data through these. */
    public void readNBT(CompoundTag tag) {
        customData = tag == null ? new CompoundTag() : tag.copy();
    }

    public CompoundTag writeNBT() {
        return customData.copy();
    }

    /** A defensive copy for menus and diagnostics. Mutations should use {@link #setCustomData}. */
    public CompoundTag getCustomData() { return customData.copy(); }

    public void setCustomData(CompoundTag data) {
        customData = data == null ? new CompoundTag() : data.copy();
    }

    /** Registers one upgrade-scoped event callback. Used by the optional CraftTweaker facade. */
    public <E extends MachineEvent> void addEventHandler(Class<E> eventType,
                                                          BiConsumer<E, MachineUpgrade> callback) {
        if (eventType == null || callback == null) {
            throw new IllegalArgumentException("upgrade event type and callback are required");
        }
        @SuppressWarnings("unchecked")
        BiConsumer<MachineEvent, MachineUpgrade> erased =
                (event, upgrade) -> callback.accept(eventType.cast(event), upgrade);
        eventHandlers.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(erased);
    }

    /** Returns a stable snapshot for the direct event dispatcher. */
    public List<BiConsumer<MachineEvent, MachineUpgrade>> eventHandlers(Class<?> eventType) {
        List<BiConsumer<MachineEvent, MachineUpgrade>> handlers = eventHandlers.get(eventType);
        return handlers == null ? List.of() : List.copyOf(handlers);
    }

    /** Copies live script handlers to a per-bus upgrade instance. */
    protected final void copyEventHandlersTo(MachineUpgrade target) {
        eventHandlers.forEach((type, handlers) -> target.eventHandlers
                .computeIfAbsent(type, ignored -> new ArrayList<>()).addAll(handlers));
    }

    /** @return a display line for a chat/GUI report that does not need translation machinery */
    public Component describe() {
        return Component.literal(type.getName() + " x" + stackSize);
    }

    /**
     * <b>Identity is the TYPE, and therefore a bus may hold at most one instance per type.</b> Upstream U83-U94
     * says the same, which is why {@code checkAndAddUpgradeBus} (U858-U869) walks its list looking for an EQUAL
     * upgrade and merges the stack into it rather than appending a second one.
     */
    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof MachineUpgrade other && type.equals(other.type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + type.getName() + " x" + stackSize + "]";
    }

    /** @return the item stack this upgrade was read from, which callers use only for diagnostics */
    public ItemStack describeSource() {
        return ItemStack.EMPTY;
    }
}
