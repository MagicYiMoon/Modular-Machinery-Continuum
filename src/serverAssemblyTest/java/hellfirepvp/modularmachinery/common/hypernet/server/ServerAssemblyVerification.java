/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet.server;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.Function;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyLayout.*;

/** Opt-in disposable Forge fixture; no player world, item registration or network producer. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class ServerAssemblyVerification {
    private static final Item[] ITEMS = {Items.REDSTONE, Items.PAPER, Items.IRON_INGOT, Items.COPPER_INGOT,
            Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.AMETHYST_SHARD, Items.QUARTZ,
            Items.COAL, Items.CHARCOAL, Items.REPEATER, Items.COMPARATOR};
    private static final Function<ItemStack, Kind> CLASSIFIER = stack -> {
        for (int i = 0; i < ITEMS.length; i++) if (stack.is(ITEMS[i])) return Kind.values()[i];
        return null;
    };
    private static ItemStack module(Kind kind) { return new ItemStack(ITEMS[kind.ordinal()]); }
    private static Address address(Bank bank, int index) { return new Address(bank, index); }
    private static ItemStack add(ServerAssemblyInventory assembly, Address address, ItemStack value, boolean simulate) {
        return assembly.inventory(address.bank()).insertItem(address.index(), value, simulate);
    }
    private static ItemStack remove(ServerAssemblyInventory assembly, Address address, boolean simulate) {
        return assembly.inventory(address.bank()).extractItem(address.index(), 1, simulate);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("modularmachinery.serverAssemblyTest")) return;
        try {
            run();
            ServerModuleVerification.run();
            Modularmachinery.LOG.info("[MMC][server-assembly] OVERALL: PASS (105 slots, dependencies, full physical stacks, bounded atomic persistence and conservation; backend only)");
        } catch (Throwable failure) {
            Modularmachinery.LOG.error("[MMC][server-assembly] OVERALL: FAIL", failure);
        } finally { event.getServer().halt(false); }
    }
    private static void run() throws Exception {
        check(slots().size() == 105, "total slots");
        for (var bank : Bank.values()) {
            check(slots().stream().filter(s -> s.address().bank() == bank).count() == bank.size(), "bank size");
            rejects(() -> address(bank, -1)); rejects(() -> address(bank, bank.size()));
        }
        check(slot(address(Bank.CPU, 2)).dependencies().equals(List.of(address(Bank.CPU, 20))), "CPU expansion mapping");
        check(slot(address(Bank.CPU, 12)).dependencies().equals(List.of(address(Bank.CPU, 20))), "RAM expansion mapping");
        check(slot(address(Bank.CPU, 40)).kind() == Kind.RAM_HEATSINK, "last RAM cooling slot");
        for (int i = 0; i < 8; i++) {
            check(slot(address(Bank.CALCULATE_CARD, i)).dependencies().equals(List.of(address(Bank.CALCULATE_CARD, 8 + i / 2))), "calculate line mapping");
            check(slot(address(Bank.CALCULATE_CARD, 12 + i)).dependencies().equals(List.of(address(Bank.CALCULATE_CARD, 8 + i / 2))), "calculate cooling mapping");
        }
        for (int i = 0; i < 16; i++)
            check(slot(address(Bank.EXTENSION, i)).dependencies().equals(List.of(address(Bank.EXTENSION, 16 + i / 4))), "extension line mapping");
        var server = new ItemStack(Items.CHEST); server.setHoverName(net.minecraft.network.chat.Component.literal("Server fixture"));
        server.getOrCreateTag().putInt("CustomModelData", 73);
        var assembly = new ServerAssemblyInventory(server, CLASSIFIER);
        check(server.getTag().contains("server") == false, "constructor mutated original item");
        rejects(() -> new ServerAssemblyInventory(new ItemStack(Items.CHEST, 2), CLASSIFIER));
        var base = address(Bank.CPU, 0); var expansion = address(Bank.CPU, 20); var extended = address(Bank.CPU, 2);
        var input = module(Kind.CPU); input.setCount(5); input.getOrCreateTag().putString("author", "完整组件NBT");
        input.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
        var untouched = input.copy(); var before = assembly.stack().save(new CompoundTag());
        check(!add(assembly, base, module(Kind.RAM), false).isEmpty(), "wrong type rejected");
        check(add(assembly, extended, input, false).getCount() == 5, "locked expansion rejected");
        check(add(assembly, base, input, true).getCount() == 4, "simulated insert remainder");
        check(assembly.revision() == 0 && assembly.stack().save(new CompoundTag()).equals(before), "simulation mutated assembly");
        check(ItemStack.matches(input, untouched), "input stack consumed in place");
        check(add(assembly, base, input, false).getCount() == 4 && assembly.get(base).getCount() == 1, "one-item slot");
        check(ItemStack.matches(input, untouched) && assembly.revision() == 1, "input copied and revision");
        check(!add(assembly, base, module(Kind.CPU), false).isEmpty(), "occupied replacement rejected");
        var copy = assembly.get(base); copy.getOrCreateTag().putString("author", "changed"); copy.setCount(44);
        check(assembly.get(base).getCount() == 1 && assembly.get(base).getTag().getString("author").equals("完整组件NBT"), "defensive slot copy");
        check(assembly.missingDependencies().equals(List.of(new ServerAssemblyInventory.Missing(base, address(Bank.CPU, 21), true))), "soft cooling diagnostics");
        check(assembly.activeModules().contains(base), "soft cooling blocks module");
        check(add(assembly, expansion, module(Kind.CPU_EXTENSION), false).isEmpty(), "expansion install");
        check(add(assembly, extended, module(Kind.CPU), false).isEmpty(), "expanded CPU install");
        check(remove(assembly, expansion, true).isEmpty() && remove(assembly, expansion, false).isEmpty(), "expansion extraction protection");
        remove(assembly, extended, false);
        var heatsink = address(Bank.CPU, 23);
        check(add(assembly, heatsink, module(Kind.CPU_HEATSINK), false).isEmpty(), "extended cooling install");
        check(remove(assembly, expansion, false).isEmpty(), "cooling also blocks expansion removal");
        remove(assembly, heatsink, false); check(!remove(assembly, expansion, false).isEmpty(), "expansion removal after dependents");
        var rev = assembly.revision(); var simulated = remove(assembly, base, true);
        check(simulated.getCount() == 1 && assembly.occupied(base) && assembly.revision() == rev, "simulated extraction");
        simulated.setCount(50); check(assembly.get(base).getCount() == 1, "extraction copy");

        // Fill expansion cards first, then every physical module and cooler.
        for (var definition : slots()) if (definition.dependencies().isEmpty() && !assembly.occupied(definition.address()))
            check(add(assembly, definition.address(), module(definition.kind()), false).isEmpty(), "root install");
        for (var definition : slots()) if (!assembly.occupied(definition.address()))
            check(add(assembly, definition.address(), module(definition.kind()), false).isEmpty(), "dependent install");
        check(assembly.activeModules().size() == 105 && assembly.missingDependencies().isEmpty(), "full assembly");
        var snapshot = assembly.stack();
        var data = snapshot.getOrCreateTag().getCompound("server");
        var prop = new CompoundTag(); prop.putFloat("unlockLimit", 2f); prop.putString("author", "opaque properties"); data.put("prop", prop);
        data.putString("future-extra", "keep"); data.getCompound("cpuInv").putString("bank-extra", "keep");
        var disk = java.nio.file.Path.of("server-assembly-fixture.nbt").toFile();
        NbtIo.writeCompressed(snapshot.save(new CompoundTag()), disk);
        var restored = new ServerAssemblyInventory(ItemStack.of(NbtIo.readCompressed(disk)), CLASSIFIER);
        check(restored.activeModules().size() == 105 && restored.missingDependencies().isEmpty(), "real compressed NBT reload");
        check(ItemStack.matches(restored.get(base), input.copyWithCount(1)), "custom name/enchantment/module data");
        var saved = restored.stack(); var resultData = saved.getTag().getCompound("server");
        check(saved.getHoverName().equals(server.getHoverName()) && saved.getTag().getInt("CustomModelData") == 73, "server item data");
        check(resultData.getCompound("prop").equals(prop) && resultData.getString("future-extra").equals("keep")
                && resultData.getCompound("cpuInv").getString("bank-extra").equals("keep"), "opaque server/bank data");
        saved.getTag().getCompound("server").putString("future-extra", "mutated");
        check(restored.stack().getTag().getCompound("server").getString("future-extra").equals("keep"), "snapshot isolation");

        corruption(snapshot); missingItems(snapshot); limits(); conservation();
        // Dismantle in dependency order and prove exactly one of each occupied module is returned.
        int count = 0;
        for (var definition : slots()) if (!definition.dependencies().isEmpty()) count += remove(restored, definition.address(), false).getCount();
        for (var definition : slots()) count += remove(restored, definition.address(), false).getCount();
        check(count == 105 && restored.activeModules().isEmpty(), "full dismantle conservation");
        Modularmachinery.LOG.info("[MMC][server-assembly] PASS: source layout, expansion and cooling dependencies, real ItemStack/NBT reload, malformed/future rejection, missing items preserved, simulated/real transfer conservation");
    }
    private static void corruption(ItemStack original) {
        var originalNbt = original.save(new CompoundTag());
        var future = original.copy(); future.getTag().getCompound("server").putInt("mmcAssemblyVersion", 9);
        var preserved = future.save(new CompoundTag()); rejects(() -> new ServerAssemblyInventory(future, CLASSIFIER));
        check(future.save(new CompoundTag()).equals(preserved), "future data overwritten");
        var old = original.copy(); old.getTag().getCompound("server").remove("mmcAssemblyVersion");
        rejects(() -> new ServerAssemblyInventory(old, CLASSIFIER));
        var wrong = original.copy(); wrong.getTag().getCompound("server").getCompound("cpuInv").putInt("Size", Integer.MAX_VALUE);
        rejects(() -> new ServerAssemblyInventory(wrong, CLASSIFIER));
        var duplicate = original.copy(); var items = duplicate.getTag().getCompound("server").getCompound("powerInv").getList("Items", Tag.TAG_COMPOUND);
        items.getCompound(1).putInt("Slot", 0); rejects(() -> new ServerAssemblyInventory(duplicate, CLASSIFIER));
        var wrongCountType = original.copy(); wrongCountType.getTag().getCompound("server").getCompound("powerInv").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putInt("Count", 1);
        rejects(() -> new ServerAssemblyInventory(wrongCountType, CLASSIFIER));
        var overstack = original.copy(); overstack.getTag().getCompound("server").getCompound("powerInv").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putByte("Count", (byte) 2);
        rejects(() -> new ServerAssemblyInventory(overstack, CLASSIFIER));
        var outside = original.copy(); outside.getTag().getCompound("server").getCompound("powerInv").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putInt("Slot", 8);
        rejects(() -> new ServerAssemblyInventory(outside, CLASSIFIER));
        var missing = original.copy(); missing.getTag().getCompound("server").remove("extInv");
        rejects(() -> new ServerAssemblyInventory(missing, CLASSIFIER));
        var wrongList = original.copy(); var list = new ListTag(); list.add(StringTag.valueOf("invalid"));
        wrongList.getTag().getCompound("server").getCompound("powerInv").put("Items", list);
        rejects(() -> new ServerAssemblyInventory(wrongList, CLASSIFIER));
        var wrongKind = original.copy();
        wrongKind.getTag().getCompound("server").getCompound("cpuInv").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putString("id", "minecraft:stone");
        var wrongAssembly = new ServerAssemblyInventory(wrongKind, CLASSIFIER);
        check(!wrongAssembly.installed(address(Bank.CPU, 0)) && !remove(wrongAssembly, address(Bank.CPU, 0), false).isEmpty(), "recover known wrong-kind item");
        check(original.save(new CompoundTag()).equals(originalNbt), "corruption checks mutated source");
    }
    private static void missingItems(ItemStack snapshot) {
        var unknown = snapshot.copy();
        var entries = unknown.getTag().getCompound("server").getCompound("cpuInv").getList("Items", Tag.TAG_COMPOUND);
        CompoundTag expansion = null;
        for (var entry : entries) if (((CompoundTag) entry).getInt("Slot") == 20) expansion = (CompoundTag) entry;
        check(expansion != null, "expansion fixture"); expansion.putString("id", "missing_mod:preserved_module");
        var caps = new CompoundTag(); caps.putString("test:capability", "keep"); expansion.put("ForgeCaps", caps);
        var knownCaps = new CompoundTag(); knownCaps.putString("test:existing", "preserved without a provider");
        entries.getCompound(0).put("ForgeCaps", knownCaps);
        var knownRaw = entries.getCompound(0).copy();
        var preserved = expansion.copy(); var inventory = new ServerAssemblyInventory(unknown, CLASSIFIER);
        var address = address(Bank.CPU, 20);
        check(inventory.occupied(address) && inventory.unresolved(address) && !inventory.installed(address), "unknown item state");
        check(!inventory.available(address(Bank.CPU, 2)) && !inventory.activeModules().contains(address(Bank.CPU, 2)), "unknown expansion grants nothing");
        check(!add(inventory, address, module(Kind.CPU_EXTENSION), false).isEmpty() && remove(inventory, address, false).isEmpty(), "unknown replacement/extraction");
        var saved = inventory.stack().getTag().getCompound("server").getCompound("cpuInv").getList("Items", Tag.TAG_COMPOUND);
        check(saved.stream().anyMatch(preserved::equals), "unknown item/capabilities lost");
        check(saved.stream().anyMatch(knownRaw::equals), "known item's opaque ForgeCaps lost");
        check(!remove(inventory, address(Bank.CPU, 2), false).isEmpty(), "recover module from unavailable slot");
        // Known item whose module definition has disappeared remains physical and removable.
        var disabled = new ServerAssemblyInventory(snapshot, ignored -> null);
        check(disabled.activeModules().isEmpty() && disabled.occupied(address(Bank.CPU, 0))
                && !remove(disabled, address(Bank.CPU, 0), false).isEmpty(), "missing definition recovery");
    }
    private static void limits() {
        var assembly = new ServerAssemblyInventory(new ItemStack(Items.CHEST), CLASSIFIER);
        var huge = module(Kind.CPU); huge.getOrCreateTag().putByteArray("oversize", new byte[ServerAssemblyInventory.MAX_NBT_BYTES]);
        var before = assembly.stack().save(new CompoundTag());
        check(!add(assembly, address(Bank.CPU, 0), huge, true).isEmpty()
                && !add(assembly, address(Bank.CPU, 0), huge, false).isEmpty(), "oversized insert rejected");
        check(assembly.revision() == 0 && assembly.stack().save(new CompoundTag()).equals(before), "oversized insert partially committed");
        var deep = module(Kind.CPU); var cursor = deep.getOrCreateTag();
        for (int i = 0; i < 4000; i++) { var child = new CompoundTag(); cursor.put("nested", child); cursor = child; }
        check(!add(assembly, address(Bank.CPU, 0), deep, false).isEmpty() && assembly.revision() == 0, "deep insert rejected");
        var oversized = new ItemStack(Items.CHEST); oversized.getOrCreateTag().putByteArray("oversize", new byte[ServerAssemblyInventory.MAX_NBT_BYTES]);
        rejects(() -> new ServerAssemblyInventory(oversized, CLASSIFIER));
        rejects(() -> new ServerAssemblyInventory(deep, CLASSIFIER));
    }
    private static void conservation() {
        var assembly = new ServerAssemblyInventory(new ItemStack(Items.CHEST), CLASSIFIER);
        var random = new Random(72113); int inserted = 0, extracted = 0;
        for (int i = 0; i < 1500; i++) {
            var definition = slots().stream().skip(random.nextInt(105)).findFirst().orElseThrow();
            boolean simulate = random.nextBoolean();
            if (random.nextBoolean()) {
                var stack = module(definition.kind()); stack.setCount(2);
                var remainder = add(assembly, definition.address(), stack, simulate);
                check(stack.getCount() == 2, "random insert input mutation");
                if (!simulate) inserted += 2 - remainder.getCount();
            } else if (!simulate) extracted += remove(assembly, definition.address(), false).getCount();
            else remove(assembly, definition.address(), true);
            long physical = slots().stream().filter(s -> assembly.occupied(s.address())).count();
            check(inserted - extracted == physical, "random transfer conservation");
            if (i % 50 == 0) {
                var reopened = new ServerAssemblyInventory(assembly.stack(), CLASSIFIER);
                check(reopened.stack().save(new CompoundTag()).equals(assembly.stack().save(new CompoundTag())), "random persistence round trip");
            }
        }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid assembly rejection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
