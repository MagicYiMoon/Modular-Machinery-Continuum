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
 * original named above. Modifications made 2026-08 .. 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockSmartInterface;
import hellfirepvp.modularmachinery.common.block.prop.SmartInterfaceType;
import hellfirepvp.modularmachinery.common.event.machine.SmartInterfaceUpdateEvent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart interface — lets a machine's logic read a value that a pack author can drive externally.
 *
 * <p>Upstream: {@code TileSmartInterface.java}, 179 lines, read in full. This is the port of the CHANNEL
 * half (PLAN 5xx/B2): the data model, the provider a requirement reads through, the publish path that
 * fires {@link SmartInterfaceUpdateEvent}, and the prune tick. The consumer is
 * {@link hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput}.</p>
 *
 * <h2>RULE A0 — ONE SHAPE, AND IT IS THIS ONE. The port's {@code Data} wins, upstream's
 * {@code SmartInterfaceData} is deleted rather than kept beside it.</h2>
 *
 * <p>The two models were compared line by line at PLAN 551.4 and the choice between them was recorded as
 * a three-branch fork; branch (a) — "adapt the event to the port model" — is the one the user chose. What
 * the comparison actually says, field by field (<b>PLAN 674 CHANGED ONE ROW BACK: the machine's name is
 * carried again, see the paragraph below</b>):</p>
 *
 * <pre>
 * upstream SmartInterfaceData (99 lines)      this port's Data (T49-T83)
 *   BlockPos pos   -- the CONTROLLER the datum belongs to   (absent)
 *   ResourceLocation parent = the machine's name            String machineName  (PLAN 674; was absent)
 *   String type    -- the declared TYPE's name              String key        (same role)
 *   float value                                             double numberValue and String stringValue
 * </pre>
 *
 * <p><b>THE MISSING POSITION IS NOT MISSING INFORMATION, AND THAT IS THE EVIDENCE FOR BRANCH (a):</b>
 * the machine link is carried by the component mechanism instead — this tile IS a
 * {@link hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile}, and
 * {@code MachineFormation.bindComponents} (the port's walk over the pattern) calls
 * {@code setControllerPos} on it at every formation, which is the same fact {@code SmartInterfaceData.pos}
 * stored per datum. Upstream needed it per datum because its bound-data list lived on the interface and
 * was looked up BY CONTROLLER POSITION ({@code getMachineData(BlockPos)}, U123-U130, called with the
 * controller's own position at U906/U1115/U1125); with one controller per tile here, the tile's own
 * binding answers that question once instead of once per datum.</p>
 *
 * <p><b>AND THE MACHINE'S NAME CAME BACK, BECAUSE THE ROW ABOVE WAS ONLY HALF TRUE (PLAN 674).</b> "The
 * component mechanism carries the machine link" holds ON THE SERVER, where the tile can ask its controller.
 * It does NOT hold on a CLIENT: the screen that draws this datum's value also drew the machine's name and the
 * declared type's header/value/footer, and it obtained them by looking the definition up in
 * {@code MachineReloadListener}'s loader — a loader whose only producer is a SERVER-side event
 * ({@code AddReloadListenerEvent}, posted from {@code ForgeEventFactory.onResourceReload(
 * ReloadableServerResources, ...)}). On a client that lookup returned null and the screen returned BEFORE
 * DRAWING THE VALUE, which is the client-reported defect this round fixes. So the datum carries the machine's
 * registry name and display name again — upstream's own {@code parent}, for upstream's own reason
 * ({@code GuiContainerSmartInterface} U65 resolves the definition FROM THE DATUM}) — plus the declared type's
 * three display strings, and the screen reads all of them from the same tag the value travels in. See
 * {@link Data#setDisplay} for the producer and {@link Data} for every reader.</p>
 *
 * <p><b>AND THE VALUE SPLIT IS A SUPERSET RATHER THAN A DIVERGENCE.</b> Upstream's datum holds a single
 * {@code float}; its block enum ({@code SmartInterfaceTypeEnum}, one constant, NUMBER) had no string
 * storage at all, so the {@code type=string} block variant upstream shipped a model for could never hold
 * anything. This tile stores both and the variant decides which one is meaningful — see
 * {@link hellfirepvp.modularmachinery.common.block.prop.SmartInterfaceType}'s own javadoc for why the
 * enum has two constants on 1.20.1.</p>
 *
 * <h2>THE PROVIDER IS THE MECHANISM, AND IT IS WHY A REQUIREMENT CAN READ ANYTHING AT ALL</h2>
 *
 * <p>Upstream's {@code SmartInterfaceProvider} is a {@code MachineComponent} — a first-class object the
 * recipe machinery hands to a requirement — and its bound-data list is <b>the interface's own list, not a
 * copy</b> (upstream U109, {@code this.boundData = parent.boundData}). That is the whole channel: the
 * controller writes a datum into the provider during structure formation, and a requirement later asks
 * the same provider for it by type name and compares the value.</p>
 *
 * <p><b>RULE A0 — THE PROVIDER IS KEPT, ITS BASE CLASS IS NOT, AND THE REASON IS A TYPE THAT DOES NOT
 * EXIST HERE.</b> {@code MachineComponent} is absent from this port (PLAN 353.3 measured its absence
 * alongside {@code ActiveMachineRecipe}/{@code RecipeRegistry}/{@code MachineComponent}, and
 * {@code RequirementType}'s own javadoc records the same for {@code ComponentRequirement}), so
 * {@code extends MachineComponent<SmartInterfaceProvider>}, {@code getComponentType()} (upstream
 * U169-U172) and {@code getContainerProvider()} (upstream U175-U177) have nothing to bind to and are not
 * reproduced. What makes the provider REACHABLE here is the other half of upstream's shape: this tile
 * implements {@code MachineComponentTile}, so {@code MachineContext} resolves it during the machine's
 * component walk and a requirement receives the tile — and asks it for the provider
 * ({@code iface.provideComponent()}), which is upstream's own call site at {@code PktSmartInterfaceUpdate}
 * U50 and {@code GuiContainerSmartInterface} U49.</p>
 *
 * <p><b>{@code getMachineData(BlockPos)} IS DROPPED WITH ITS FACTS STATED:</b> its three upstream callers
 * looked up "the datum belonging to THIS controller" (U906 in the binding walk, U1115 and U1125 in two
 * CraftTweaker-facade getters). One controller per tile makes that "the datum this interface publishes",
 * so the port uses the by-index accessor upstream already provides ({@code getMachineData(int)}, U132-U135)
 * and no position-keyed overload exists to be called with the wrong argument. <b>Dropping it also drops
 * an upstream no-op</b>: U915 calls {@code removeMachineData(realPos)} with the COMPONENT's position while
 * every entry was added under the CONTROLLER's position (U922/U927), so the by-position removal can never
 * match and the stale datum upstream meant to drop stays put. The port's removal is keyed by the type
 * name, which is the same key the lookup uses, so the two cannot disagree.</p>
 */
public class TileSmartInterface extends TileMachineComponent {

    /**
     * One published key/value pair.
     *
     * <p>The key is the name of a {@link hellfirepvp.modularmachinery.common.util.SmartInterfaceType} the
     * machine DECLARES; the value class is decided by this block's variant. See the class javadoc for the
     * field-by-field comparison with upstream's {@code SmartInterfaceData}.</p>
     */
    public static final class Data {
        private final String key;
        private final SmartInterfaceType type;
        private String stringValue;
        private double numberValue;

        // ------------------------------------------------------------------
        // WHAT THE SCREEN DRAWS, CARRIED BY THE VALUE ITSELF (PLAN 674)
        // ------------------------------------------------------------------

        /**
         * The machine definition that DECLARED this datum's type — upstream's {@code SmartInterfaceData.parent}
         * (U21), which upstream's screen resolved the definition through ({@code GuiContainerSmartInterface}
         * U65: {@code MachineRegistry.getRegistry().getMachine(data.getParent())}).
         *
         * <p><b>WHY IT IS BACK AFTER §663.1(1) DROPPED IT, AND WHY THAT ARGUMENT WAS ONLY HALF RIGHT.</b>
         * Dropping it was justified by "the machine link is carried by the component mechanism instead" —
         * this tile knows its controller — and that is true ON THE SERVER. It is not true on a CLIENT: the
         * SCREEN needs the DECLARING MACHINE (for its name line) and the TYPE'S DISPLAY STRINGS, and the
         * port answered that with a CLIENT-SIDE definition lookup through the controller's machine name
         * ({@code ScreenSmartInterface.owningMachine}). <b>THAT LOOKUP HAS NO PRODUCER ON A CLIENT:</b> the
         * port's definitions are loaded ONLY by {@code MachineReloadListener.onAddReloadListeners}, and
         * Forge posts {@code AddReloadListenerEvent} from
         * {@code ForgeEventFactory.onResourceReload(ReloadableServerResources, RegistryAccess)} — a
         * SERVER-side path (verified against the mapped jar, PLAN 674). So a client connected to a server
         * has an EMPTY loader, {@code owningMachine} returned null, and {@code renderLabels} RETURNED BEFORE
         * DRAWING THE VALUE LINE — the number the player typed was stored, synced and correct, and never
         * drawn. See the class javadoc's "what the screen draws" paragraph for the reader list.</p>
         */
        private String machineName;

        /** The declaring machine's display name ({@code DynamicMachine.getLocalizedName()}), or null. */
        private String machineDisplayName;

        /** The declared type's header, a LANG KEY the client translates (upstream U83), or null. */
        private String headerInfo;

        /** The declared type's value FORMAT (upstream's {@code String.format}, U88), or null. */
        private String valueInfo;

        /** The declared type's footer, a LANG KEY the client translates (upstream U98), or null. */
        private String footerInfo;

        public Data(String key, SmartInterfaceType type) {
            this.key = key;
            this.type = type;
        }

        /**
         * @return the registry name of the machine that declared this datum's type, or {@code null} when this
         *         datum came from a save written before PLAN 674 — the screen falls back to
         *         {@link #getMachineDisplayName()} and then draws no machine line at all.
         */
        public String getMachineName() {
            return machineName;
        }

        /** @return the display name of the declaring machine, or {@code null} for a pre-674 save */
        public String getMachineDisplayName() {
            return machineDisplayName;
        }

        /** @return the declared type's header lang key, or {@code null} for a pre-674 save */
        public String getHeaderInfo() {
            return headerInfo;
        }

        /** @return the declared type's value format, or {@code null} for a pre-674 save */
        public String getValueInfo() {
            return valueInfo;
        }

        /** @return the declared type's footer lang key, or {@code null} for a pre-674 save */
        public String getFooterInfo() {
            return footerInfo;
        }

        /** Called only by the publication site, which is the one place that holds the parsed declaration. */
        public void setDisplay(String machineName, String machineDisplayName, String headerInfo,
                               String valueInfo, String footerInfo) {
            this.machineName = machineName;
            this.machineDisplayName = machineDisplayName;
            this.headerInfo = headerInfo;
            this.valueInfo = valueInfo;
            this.footerInfo = footerInfo;
        }

        /**
         * The machine line the screen draws, or {@code null} when this datum carries no machine.
         *
         * <p>Upstream drew {@code machine.getLocalizedName() + " (" + pos + ")"}
         * ({@code GuiContainerSmartInterface} U78-U80) after resolving the definition from the datum's own
         * {@code parent}. The name now comes FROM THE DATUM, so this method — and the screen that calls it —
         * needs no definition at all. <b>The position half is deliberately not taken here:</b> upstream's
         * datum carried the CONTROLLER's position, this port's datum carries the tile binding instead, and
         * the screen already has the bound controller's position on the client. One source for one fact.</p>
         *
         * @param controllerPos a short string for the bound controller, or {@code null}
         */
        public String machineLine(String controllerPos) {
            String name = machineDisplayName != null && !machineDisplayName.isEmpty()
                    ? machineDisplayName : machineName;
            if (name == null || name.isEmpty()) {
                return null;   // a pre-674 save: no name to draw, and inventing one would be a guess
            }
            return controllerPos == null ? name : name + " (" + controllerPos + ")";
        }

        /**
         * The value line the screen draws — upstream's own expression
         * ({@code GuiContainerSmartInterface} U87-U94), moved here so the rule can be asserted without a
         * client (the convention this channel's {@code apply} follows).
         *
         * <p>THE FORMAT IS A PACK'S TO DECLARE, AND A TYPO MUST NOT TAKE THE SCREEN DOWN: a format string
         * with the wrong conversion throws, and upstream logs it and falls back to the built-in line. The
         * fallback is the port's own {@code gui.smartinterface.value} key, which is in both lang files, so
         * THIS METHOD ALWAYS RETURNS A LINE CONTAINING THE NUMBER — which is what makes the value visible
         * on a client that has no definition to format it with.</p>
         *
         * @return the line, never {@code null} and never empty
         */
        public String valueLine() {
            String format = valueInfo;
            if (format != null && !format.isEmpty()) {
                try {
                    return String.format(format, numberValue);
                } catch (java.util.IllegalFormatException ex) {
                    Modularmachinery.LOG.warn("[MMCE] smart interface value format '{}' is invalid: {}",
                            format, ex.toString());
                }
            }
            return net.minecraft.network.chat.Component
                    .translatable("gui.smartinterface.value", numberValue).getString();
        }

        /** @return the declared type's name, which is also a recipe requirement's lookup key */
        public String getKey() {
            return key;
        }

        /** @return this block's variant, i.e. which of the two values below is meaningful */
        public SmartInterfaceType getType() {
            return type;
        }

        public double getNumber() {
            return numberValue;
        }

        public void setNumber(double value) {
            this.numberValue = value;
        }

        public String getString() {
            return stringValue;
        }

        public void setString(String value) {
            this.stringValue = value;
        }

        @Override
        public String toString() {
            return "SmartInterfaceData[" + key + "=" + numberValue + " (" + type + ")]";
        }
    }

    private final SmartInterfaceType type;
    private final List<Data> boundData = new ArrayList<>();

    /**
     * The object a requirement reads through. Upstream holds exactly this field (U27) and every reader
     * goes through it.
     */
    private final SmartInterfaceProvider provider = new SmartInterfaceProvider(this);

    /** How often the prune runs, in ticks. Upstream U48 checks {@code ticksExisted % 20}. */
    private static final int PRUNE_INTERVAL = 20;

    /**
     * This tile's own age, in ticks.
     *
     * <p>Upstream gets the same number from {@code TileEntity#ticksExisted} (U48), which vanilla no longer
     * keeps. Counting here rather than reading {@code Level#getGameTime} matters for more than taste: the game
     * time is global and the harness that checks this prune cannot advance it, so a game-time cadence would be
     * a behaviour no test could ever reach — while a tile-local one is exactly upstream's.</p>
     */
    private int ticksExisted;

    public TileSmartInterface(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.SMART_INTERFACE, pos, state);
        this.type = state.getValue(BlockSmartInterface.TYPE);
    }

    /**
     * @return the data kind this interface carries.
     *
     * <p>Named {@code getInterfaceType} rather than {@code getType} because
     * {@link net.minecraft.world.level.block.entity.BlockEntity#getType()} already exists and returns a
     * {@code BlockEntityType<?>}; an override with an incompatible return type will not compile, and
     * shadowing it would break the block entity contract.
     */
    public SmartInterfaceType getInterfaceType() {
        return type;
    }

    /** @return the provider a recipe requirement (and the screen) reads this interface's data through */
    public SmartInterfaceProvider provideComponent() {
        return provider;
    }

    public List<Data> getBoundData() {
        return boundData;
    }

    /**
     * Looks up (creating if absent) the slot bound to {@code key}.
     *
     * @return the data slot, whose value class is decided by this block's {@link SmartInterfaceType}
     */
    public Data getOrCreate(String key) {
        for (Data data : boundData) {
            if (data.getKey().equals(key)) {
                return data;
            }
        }
        Data created = new Data(key, type);
        boundData.add(created);
        return created;
    }

    /**
     * Fires when an interface publishes a value to a machine. Upstream U29-U35, read in full:
     *
     * <pre>
     * TileEntity te = owner.getWorld().getTileEntity(newData.getPos());
     * if (!(te instanceof final TileMultiblockMachineController ctrl)) return;
     * new SmartInterfaceUpdateEvent(ctrl, owner.getPos(), newData).postEvent();
     * </pre>
     *
     * <p><b>RULE A0 — ONE ARGUMENT'S SOURCE MOVED, AND THE MOVE IS FORCED BY THE DATA MODEL.</b> Upstream
     * finds the machine through {@code newData.getPos()}, because its datum carried the controller's
     * position. This port's datum does not (see the class javadoc), so the same question is answered from
     * the tile's own component binding: {@code owner.getControllerPos()}, which
     * {@code MachineFormation.bindComponents} sets at every formation. The {@code instanceof TileController}
     * test is upstream's guard verbatim — an interface whose controller was replaced by a casing publishes
     * to nobody rather than throwing — and {@code TileController} is the port's base for both controller
     * kinds, so a factory controller is accepted here exactly as upstream's base class accepted both.</p>
     *
     * @param owner    the interface that changed
     * @param newData  the datum that changed
     */
    public static void onDataUpdate(TileSmartInterface owner, Data newData) {
        net.minecraft.world.level.Level level = owner.getLevel();
        BlockPos controllerPos = owner.getControllerPos();
        if (level == null || controllerPos == null) {
            return;
        }
        if (level.getBlockEntity(controllerPos)
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            new SmartInterfaceUpdateEvent(controller, owner.getBlockPos(), newData).postEvent();
        }
    }

    /**
     * The prune, ported from upstream's {@code doRestrictedTick} (U43-U72).
     *
     * <p><b>What it is for:</b> a bound datum names a machine's declared type, and the machine can go away
     * — the controller is broken, or the structure is taken apart. Upstream drops datums whose controller
     * is no longer a controller and pushes the smaller list to its clients, and this does the same, on the
     * same one-second cadence.</p>
     *
     * <p>Server only, by construction: {@code BlockSmartInterface.getTicker} hands this to the server and
     * returns null on the client, which is upstream's own {@code if (getWorld().isRemote) return;} guard
     * (U45-U47) expressed in the 1.20.1 idiom (a block decides whether its block entity ticks).</p>
     */
    @Override
    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ticksExisted++;
        if (ticksExisted % PRUNE_INTERVAL != 0 || boundData.isEmpty()) {
            return;
        }
        int previousSize = boundData.size();
        for (int i = 0; i < boundData.size(); i++) {
            BlockPos controllerPos = getControllerPos();
            if (controllerPos == null || !level.isLoaded(controllerPos)) {
                continue;
            }
            if (!(level.getBlockEntity(controllerPos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.TileController)) {
                // No owner, so the datum cannot be read by anything: the requirement reads through the
                // machine. Dropping it is what makes "the value belongs to a machine" true.
                boundData.remove(i);
                i--;
            }
        }
        if (boundData.size() != previousSize) {
            markForUpdate();
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundData.clear();
        ListTag list = tag.getList("boundData", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Data data = new Data(entry.getString("key"), type);
            data.setNumber(entry.getDouble("number"));
            if (entry.contains("string")) {
                data.setString(entry.getString("string"));
            }
            // THE DISPLAY HALF TRAVELS WITH THE VALUE (PLAN 674). Absent in a save written before this
            // round, which is why every field is read defensively rather than assumed.
            data.setDisplay(
                    entry.contains("machine") ? entry.getString("machine") : null,
                    entry.contains("machineName") ? entry.getString("machineName") : null,
                    entry.contains("header") ? entry.getString("header") : null,
                    entry.contains("valueInfo") ? entry.getString("valueInfo") : null,
                    entry.contains("footer") ? entry.getString("footer") : null);
            boundData.add(data);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Data data : boundData) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", data.getKey());
            entry.putDouble("number", data.getNumber());
            if (data.getString() != null) {
                entry.putString("string", data.getString());
            }
            // Written only when present, so a datum that has no display half stays the size it was and the
            // client's load() above can tell "absent" from "empty" if it ever needs to.
            putIfPresent(entry, "machine", data.getMachineName());
            putIfPresent(entry, "machineName", data.getMachineDisplayName());
            putIfPresent(entry, "header", data.getHeaderInfo());
            putIfPresent(entry, "valueInfo", data.getValueInfo());
            putIfPresent(entry, "footer", data.getFooterInfo());
            list.add(entry);
        }
        tag.put("boundData", list);
    }

    private static void putIfPresent(CompoundTag entry, String key, String value) {
        if (value != null && !value.isEmpty()) {
            entry.putString(key, value);
        }
    }

    /**
     * The object the recipe machinery reads this interface through. Upstream's
     * {@code TileSmartInterface.SmartInterfaceProvider} (U102-U178), minus its base class — see the class
     * javadoc for that one A0 deviation and its evidence.
     *
     * <p>It holds THE TILE'S OWN LIST rather than a copy (upstream U109), which is what makes a value
     * published after the provider was handed out visible to the next reader. A copy would have been the
     * "data channel nobody reads" failure in the other direction: the writer would update the tile and the
     * reader would keep looking at a snapshot.</p>
     */
    public static final class SmartInterfaceProvider {

        private final TileSmartInterface parent;
        private final List<Data> boundData;

        public SmartInterfaceProvider(TileSmartInterface parent) {
            this.parent = parent;
            this.boundData = parent.boundData;
        }

        /** Upstream U112-U120: the datum published under {@code type}, or null when this interface has none. */
        public Data getMachineData(String type) {
            for (Data data : boundData) {
                if (data.getKey().equals(type)) {
                    return data;
                }
            }
            return null;
        }

        /** Upstream U132-U135. See the class javadoc for why the by-position overload does not exist here. */
        public Data getMachineData(int index) {
            return index >= 0 && boundData.size() > index ? boundData.get(index) : null;
        }

        /**
         * Publishes a value under {@code declared}'s type. Upstream U137-U152.
         *
         * <p>The override flag is upstream's own: with it, an existing datum for this type is replaced (and
         * therefore RESET to {@code defaultValue} — the removal and re-creation is what resets it); without
         * it, the existing datum is left exactly as the player set it. The binding walk passes true, which
         * is why re-forming a machine restores the declared default rather than keeping a stale value that
         * belonged to a structure that no longer exists.</p>
         *
         * <p><b>RULE A0 — THE PARAMETERS ARE UPSTREAM'S OWN FACTS, PASSED AS ONE OBJECT INSTEAD OF FOUR.</b>
         * Upstream's signature is
         * {@code addMachineData(BlockPos pos, ResourceLocation parent, String type, float defaultValue, boolean override)}
         * (U137), called from the binding walk as
         * {@code addMachineData(getPos(), foundMachine.getRegistryName(), type.getType(), type.getDefaultValue(), true)}
         * (U922/U927). The port's datum carries no position (see the class javadoc), and this port calls the
         * walk with the parsed descriptor in hand — so the declaring machine and the descriptor ARE the
         * remaining arguments, and {@code declared} carries the type name, the default value AND the three
         * display strings the screen draws. <b>The display strings are copied onto the datum HERE and nowhere
         * else</b>, which is what makes the client's screen self-contained: it reads the same tag the value
         * travels in, instead of looking a definition up in a loader a client never fills (PLAN 674).</p>
         *
         * @param machineName the registry name of the machine declaring this type — upstream's {@code parent}
         * @param displayName the declaring machine's display name, as the screen draws it
         * @param declared  the declared type: its name, its default value and its display strings
         * @param override  whether an existing datum for this type may be replaced
         */
        public void addMachineData(String machineName, String displayName,
                                   hellfirepvp.modularmachinery.common.util.SmartInterfaceType declared,
                                   boolean override) {
            String type = declared.getType();
            Data data = getMachineData(type);
            if (data != null) {
                if (override) {
                    removeMachineData(type);
                } else {
                    return;
                }
            }

            Data newData = parent.getOrCreate(type);
            newData.setNumber(declared.getDefaultValue());
            newData.setDisplay(machineName, displayName, declared.getHeaderInfo(), declared.getValueInfo(),
                    declared.getFooterInfo());
            // Upstream fires here (U149) and then marks the tile (U151). Both are needed and neither
            // substitutes for the other: the event tells the machine's handlers, the mark tells the CLIENTS
            // -- the screen reads its value from the synced tag, not from the event.
            onDataUpdate(parent, newData);
            parent.markForUpdate();
        }

        /** Upstream U154-U163, keyed by type name rather than by position (see the class javadoc). */
        public void removeMachineData(String type) {
            for (int i = 0; i < boundData.size(); i++) {
                if (boundData.get(i).getKey().equals(type)) {
                    boundData.remove(i);
                    parent.markForUpdate();
                    return;
                }
            }
        }

        /** Upstream U165-U167: how many datums this interface publishes. The screen shows it. */
        public int getBoundSize() {
            return boundData.size();
        }
    }
}
