/* SPDX-License-Identifier: GPL-3.0-only
 * Start gate adapted from NECO RecipePrimerHyperNet.requireResearch, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import java.util.*;

/** Nonconsumable startup gate; database loss never retrospectively cancels an admitted recipe. */
public final class RequirementResearch implements Requirement {
    private final List<String> research;
    public RequirementResearch(List<String> ids) {
        research = List.copyOf(ids);
        if (research.isEmpty() || research.size() > 64 || new HashSet<>(research).size() != research.size()) throw new IllegalArgumentException("research must contain 1..64 distinct IDs");
        research.forEach(HyperNetResearch::checkedId);
    }
    public List<String> research() { return research; }
    @Override public String typeId() { return "modularmachinery:research"; }
    @Override public IOType getIOType() { return IOType.INPUT; }
    @Override public boolean canStart(MachineContext context) { return describeFailure(context) == null; }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public String describe() { return "Research: " + String.join(", ",research); }
    @Override public String describeFailure(MachineContext context) {
        for (var id : research) if (HyperNetResearchRegistry.find(id) == null) return "craftcheck.failure.research.unknown";
        var pos = context.getControllerPos(); var chunk = context.getLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var be = chunk == null ? null : chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        var node = be instanceof TileController controller && context.getMachine() != null
                && controller.getMachineName().equals(context.getMachine().getRegistryName()) ? controller.peekHyperNetNode() : null;
        return node == null ? "craftcheck.failure.research.network" : node.researchFailure(research);
    }
}
