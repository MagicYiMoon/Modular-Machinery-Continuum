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
package hellfirepvp.modularmachinery.common.item;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The machine projector: right-click a controller to preview that controller's structure.
 *
 * <p>Ported from {@code youyihj.mmce.common.item.MachineProjector} (4,305 bytes), whose {@code onItemUse} reads
 * the clicked block, checks it is a {@code BlockController} or {@code BlockFactoryController}, takes the
 * controller's {@link DynamicMachine} and calls
 * {@code StructurePreviewHelper.renderMachinePreview(machine, pos)}. Its tooltip is
 * {@code tooltip.modularmachinery.machine_projector}, and it registers as {@code modularmachinery:
 * machine_projector}.
 *
 * <p><b>The jar ships two of these and the other one is empty.</b> {@code hellfirepvp...ItemMachineProjector}
 * is 687 bytes with a no-arg constructor and no methods at all -- a class held open for an id, which is what
 * §60's stubs look like. The behaviour is entirely in the {@code youyihj} one, so that is the one ported.
 *
 * <p><b>Dist safety.</b> The preview is a rendering concern, so the server half does nothing and the client
 * half is reached through {@link DistExecutor}. A dedicated server must be able to load this class -- this port
 * has already had one crash from a client-only type being touched on the server, and the item is registered
 * unconditionally.
 */
public class ItemMachineProjector extends Item {

    public ItemMachineProjector() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockController)) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileController controller)) {
            return InteractionResult.PASS;
        }
        // The controller stores the machine NAME; the loader resolves it. Same call ControllerInteraction
        // makes at its line 81, rather than a second lookup path that could disagree with it.
        DynamicMachine machine = hellfirepvp.modularmachinery.common.machine.MachineReloadListener
                .getLoader().findByName(controller.getMachineName());
        if (machine == null) {
            return InteractionResult.PASS;   // no structure matched; there is nothing to preview
        }
        // The controller's own orientation, so the ghost is drawn on the same axis the matcher transforms
        // every part through. §188 hard-coded NORTH; this is the fix for that limitation (§189).
        net.minecraft.core.Direction facing = state.getValue(BlockController.FACING);
        if (level.isClientSide) {
            // The dedicated projector item anchors directly to the clicked controller.
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper
                            .renderMachinePreview(machine, pos, facing));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modularmachinery.machine_projector")
                .withStyle(ChatFormatting.GRAY));
    }
}
