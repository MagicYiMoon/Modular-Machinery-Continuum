/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.world;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Selects the upstream multipart model without changing authoritative world block states. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EStorageDriveModel extends BakedModelWrapper<BakedModel> {
    private final Map<BlockState, BakedModel> variants;
    private EStorageDriveModel(BakedModel original, Map<BlockState, BakedModel> variants) { super(original); this.variants = variants; }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var variants = new IdentityHashMap<BlockState, BakedModel>();
        for (var state : EStorageRegistration.DRIVE.get().getStateDefinition().getPossibleStates())
            variants.put(state, event.getModels().get(BlockModelShaper.stateToModelLocation(state)));
        var originals = Collections.unmodifiableMap(variants);
        for (var state : variants.keySet()) {
            var model = variants.get(state);
            if (model != null) event.getModels().put(BlockModelShaper.stateToModelLocation(state), new EStorageDriveModel(model, originals));
        }
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        var appearance = data.get(EStorageDriveAppearance.DATA);
        if (state == null || appearance == null) return originalModel.getQuads(state, side, random, data, renderType);
        var virtual = appearance.renderState(state);
        return variants.getOrDefault(virtual, originalModel).getQuads(virtual, side, random, data, renderType);
    }
}
