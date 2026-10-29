/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.event.recipe;

import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.tiles.TileController;

























 
public class ResultChanceCreateEvent extends RecipeEvent {

    public final Phase phase;
    private float resultChance;
    /** Set by a handler to make every roll succeed; false means behave exactly as declared. */
    private boolean guaranteed = false;

    public ResultChanceCreateEvent(TileController controller, RecipeCraftingContext context, float currentChance,
                                   Phase phase) {
        super(controller, context);
        this.resultChance = currentChance;
        this.phase = phase;
    }

    /** @return the probability this craft will use, in [0,1] */
    public float getResultChance() {
        return resultChance;
    }

    /** Replaces the probability. Returns the event, as upstream does, so calls chain. */
    public ResultChanceCreateEvent setResultChance(float resultChance) {
        this.resultChance = resultChance;
        return this;
    }
    


























 
    public ResultChanceCreateEvent forceGuaranteed() {
        this.guaranteed = true;
        return this;
    }

    /** @return true when a handler has asked every probabilistic output of this craft to succeed */
    public boolean isGuaranteed() {
        return guaranteed;
    }
}
