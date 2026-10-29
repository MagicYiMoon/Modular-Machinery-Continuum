/* SPDX-License-Identifier: GPL-3.0-only
 * Source MMCE active-recipe observations adapted for Forge 1.20.1, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/** Immutable observation of one craft. A retained observation can never become the next craft. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.ActiveMachineRecipe")
public final class ActiveMachineRecipe {
    private final String registryName;
    private final int tick, totalTick, parallelism, maxParallelism;

    public ActiveMachineRecipe(String registryName, int tick, int totalTick, int parallelism, int maxParallelism) {
        this.registryName = registryName;
        this.tick = tick;
        this.totalTick = totalTick;
        this.parallelism = parallelism;
        this.maxParallelism = maxParallelism;
    }

    @ZenCodeType.Getter("registryName") public String getRegistryName() { return registryName; }
    @ZenCodeType.Getter("tick") public int getTick() { return tick; }
    @ZenCodeType.Getter("totalTick") public int getTotalTick() { return totalTick; }
    @ZenCodeType.Getter("parallelism") public int getParallelism() { return parallelism; }
    @ZenCodeType.Getter("maxParallelism") public int getMaxParallelism() { return maxParallelism; }
    @ZenCodeType.Getter("completed") public boolean isCompleted() { return tick >= totalTick; }
}
