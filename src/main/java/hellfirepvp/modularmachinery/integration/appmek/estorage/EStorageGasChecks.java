/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek.estorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ICellWorkbenchItem;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellInventory;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Actual chemical registry, key serialization and workbench filtering. Only called with AppMek. */
public final class EStorageGasChecks {
    private EStorageGasChecks() { }
    private static final IActionSource SOURCE = IActionSource.empty();
    private static List<AEKey> gases;
    public static AEKey key(int index) {
        if (gases == null) gases = MekanismAPI.gasRegistry().getValues().stream()
                .map(gas -> new GasStack(gas, 1)).filter(stack -> !stack.isEmpty()).map(MekanismKey::of)
                .sorted(Comparator.comparing(gas -> gas.getId().toString())).map(gas -> (AEKey) gas).toList();
        require(gases.size() > index, "gas registry lacks requested type " + index + "; available=" + gases.size() + "; use the disposable gas registry fixture for the 25-type boundary");
        return gases.get(index);
    }
    public static List<AEKey> otherForms() {
        var infusion = MekanismAPI.infuseTypeRegistry().getValues().stream().map(t -> new InfusionStack(t, 1)).filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        var pigment = MekanismAPI.pigmentRegistry().getValues().stream().map(t -> new PigmentStack(t, 1)).filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        var slurry = MekanismAPI.slurryRegistry().getValues().stream().map(t -> new SlurryStack(t, 1)).filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        return List.of(MekanismKey.of(infusion), MekanismKey.of(pigment), MekanismKey.of(slurry));
    }
    public static void run() {
        for (var tier : EStorageCellSpec.Tier.values()) {
            var spec = new EStorageCellSpec(EStorageCellSpec.Kind.GAS, tier);
            var root = new CompoundTag(); var data = new EStorageCellInventory(spec, root, null);
            long limit = new long[]{524_156_928_000L, 8_386_510_848_000L, 134_184_173_568_000L}[tier.ordinal()];
            require(spec.capacityForTypes(1, 8000) == limit, "gas capacity differs from source parameters");
            require(key(0).getAmountPerByte() == 8000 && data.insert(key(0), Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == limit
                    && root.isEmpty(), "gas capacity units or simulation");
            require(data.insert(key(0), Long.MAX_VALUE, Actionable.MODULATE, SOURCE) == limit
                    && limit > Integer.MAX_VALUE && data.freeBytes() == 0, "gas long capacity/full state");
            var saved = root.copy(); data = new EStorageCellInventory(spec, root.copy(), null);
            require(data.extract(key(0), limit - 1, Actionable.MODULATE, SOURCE) == limit - 1
                    && data.usedBytes() == spec.bytesPerType() + 1, "gas long reload/rounding");
            require(data.extract(key(0), 1, Actionable.MODULATE, SOURCE) == 1 && data.storedTypes() == 0, "gas final type reclaim");
            data = new EStorageCellInventory(spec, new CompoundTag(), null);
            for (int i = 0; i < 25; i++) require(data.insert(key(i), 1, Actionable.MODULATE, SOURCE) == 1, "gas type seed");
            require(data.insert(key(25), 1, Actionable.MODULATE, SOURCE) == 0
                    && data.insert(key(0), 7, Actionable.MODULATE, SOURCE) == 7, "gas 25-type limit");
            require(data.extract(key(0), 8, Actionable.MODULATE, SOURCE) == 8
                    && data.insert(key(25), 1, Actionable.MODULATE, SOURCE) == 1, "gas type slot reclaim");
            var stack = new ItemStack(EStorageRegistration.CELLS.get(spec).get());
            var editable = (ICellWorkbenchItem) stack.getItem();
            for (var wrong : otherForms()) {
                require(wrong.getType() == key(0).getType(), "chemical shared-type fixture");
                require(data.insert(wrong, 1, Actionable.MODULATE, SOURCE) == 0
                        && !data.isPreferredStorageFor(wrong, SOURCE), "gas matrix accepted non-gas chemical");
                editable.getConfigInventory(stack).addFilter(wrong);
                require(editable.getConfigInventory(stack).isEmpty(), "workbench accepted non-gas chemical");
                var corrupt = saved.copy(); corrupt.getCompound(EStorageCellInventory.DATA_TAG).getList("Entries", Tag.TAG_COMPOUND)
                        .getCompound(0).put("Key", wrong.toTagGeneric());
                refuses(spec, corrupt);
                var filter = new CompoundTag(); var list = new ListTag(); list.add(wrong.toTagGeneric()); filter.put("list", list);
                refuses(spec, filter);
            }
            editable.getConfigInventory(stack).addFilter(key(0));
            data = new EStorageCellInventory(spec, stack.getTag(), null);
            require(data.insert(key(1), 1, Actionable.MODULATE, SOURCE) == 0
                    && data.insert(key(0), 3_000_000_000L, Actionable.MODULATE, SOURCE) == 3_000_000_000L, "gas partition/long transfer");
            data.close();
            var loaded = ItemStack.of(stack.save(new CompoundTag()));
            data = new EStorageCellInventory(spec, loaded.getTag(), null);
            require(data.storedAmount() == 3_000_000_000L && data.extract(key(0), Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == 3_000_000_000L,
                    "gas matrix item serialization");
        }
        Modularmachinery.LOG.info("[MMC][es-gas] PASS: three real gas matrices, 8000 units/byte, long capacities/reload, 25 real gas types and reclamation, workbench partitions, no infusion/pigment/slurry on insert/filter/load; corrupt chemical NBT preserved");
    }
    private static void refuses(EStorageCellSpec spec, CompoundTag root) {
        var before = root.copy(); boolean rejected = false;
        try { new EStorageCellInventory(spec, root, null); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && before.equals(root), "wrong chemical NBT was accepted or modified");
    }
    private static void require(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
