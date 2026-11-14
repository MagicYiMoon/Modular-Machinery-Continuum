/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO MachineCoolants. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Registry IDs replace the 1.12 global fluid names. Never guess between multiple namespaces. */
public final class EFabricatorCoolants {
    private EFabricatorCoolants() { }
    public record Rule(Fluid input, int inputAmount, Fluid output, int outputAmount, int units) { }
    private static List<? extends String> previous;
    private static Map<Fluid, Rule> rules = Map.of();
    public static Rule find(Fluid fluid) {
        var configured = Config.efCoolants();
        if (!configured.equals(previous)) {
            var resolved = new LinkedHashMap<Fluid, Rule>();
            for (String entry : configured) try {
                var fields = entry.split("\\|", -1);
                if (fields.length != 5) throw new IllegalArgumentException("expected five fields");
                Fluid input = resolve(fields[0]), output = fields[2].equals("-") ? null : resolve(fields[2]);
                int in = Integer.parseInt(fields[1]), out = Integer.parseInt(fields[3]), units = Integer.parseInt(fields[4]);
                if (in <= 0 || out < 0 || units <= 0 || units > EFabricatorSpecs.COOLANT_CACHE
                        || (fields[2].equals("-") ? out != 0 : out == 0)) throw new IllegalArgumentException("invalid amounts/units");
                if (input == null || !fields[2].equals("-") && output == null) continue;
                if (resolved.putIfAbsent(input, new Rule(input, in, output, out, units)) != null)
                    Modularmachinery.LOG.warn("[MMC][ef-cooling] Duplicate input ignored: {}", entry);
            } catch (IllegalArgumentException invalid) {
                Modularmachinery.LOG.warn("[MMC][ef-cooling] Invalid conversion ignored: {} ({})", entry, invalid.getMessage());
            }
            previous = List.copyOf(configured); rules = Collections.unmodifiableMap(resolved);
        }
        return rules.get(fluid);
    }
    private static Fluid resolve(String name) {
        if (name.contains(":")) {
            var id = ResourceLocation.tryParse(name);
            return id != null && ForgeRegistries.FLUIDS.containsKey(id) ? usable(ForgeRegistries.FLUIDS.getValue(id)) : null;
        }
        Fluid result = null;
        for (var entry : ForgeRegistries.FLUIDS.getEntries()) if (entry.getKey().location().getPath().equals(name)) {
            if (result != null) return null;
            result = usable(entry.getValue());
        }
        return result;
    }
    private static Fluid usable(Fluid fluid) { return fluid == null || fluid == Fluids.EMPTY ? null : fluid; }
}
