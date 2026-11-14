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

/**
 * Lets a handler change the probability of an output (upstream ResultChanceCreateEvent, 30 lines).
 *
 * <p><b>RULE A0 -- THE CARRIED TYPE IS A float HERE AND A ResultChance OBJECT UPSTREAM, AND THE DECISION WAS MADE
 * LONG BEFORE THIS CLASS (PLAN 517, D8a).</b> The three parts the rules require:</p>
 *
 * <ol>
 *   <li><b>upstream method</b> -- the event carries a {@code ResultChance}, a SEEDED DICE ROLLER (61 lines, with a
 *       {@code Random} and {@code canWork(float)}), and a handler may REPLACE it for that craft.</li>
 *   <li><b>why not here</b> -- not impossible: the class could be built. THIS PORT ALREADY CHOSE A DIFFERENT MODEL AT
 *       SECTION 315, AND RECORDED ITS REASONING IN THE CODE: the probability is A FIELD ON THE REQUIREMENT, parsed
 *       from JSON, defaulting to 1.0. RequirementFluid own javadoc even records replacing an earlier comment that said
 *       a fluid requirement had no probability at all -- "that was the port speaking about itself, not about the
 *       mod". SO THERE IS NO ROLLER OBJECT TO REPLACE, and introducing one would REOPEN A SETTLED DECISION rather
 *       than fill a gap.</li>
 *   <li><b>the alternative</b> -- the event carries the probability as a float, so a handler CHANGES THE NUMBER. WHAT
 *       A HANDLER LOSES: the ability to supply a different roller, which is the ability to decide HOW the chance is
 *       evaluated. What it keeps: the ability to decide WHAT the chance is. THE DIFFERENCE IS REAL AND IS STATED HERE
 *       RATHER THAN HIDDEN.</li>
 * </ol>
 *
 * <p>AND ONE SHAPE DETAIL IS COPIED RATHER THAN NORMALISED: upstream setResultChance RETURNS THE EVENT, so it chains
 * (U26-U29). A void setter would be the tidier-looking choice and would be a difference from upstream for no reason,
 * so the chained form is kept.</p>
 */
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
    /**
     * Makes every probabilistic output of this craft succeed, bypassing the rolls entirely.
     *
     * <p><b>THIS IS WHAT UPSTREAM OWN ResultChance.GUARANTEED DOES (PLAN 588, 590.2), READ IN FULL:</b></p>
     *
     * <pre>
     * upstream ResultChance.java U22-U31:
     *     public static final ResultChance GUARANTEED = new ResultChance(0) {
     *         public boolean canProduce(float chance) { return true; }
     *         public boolean canWork(float chance)    { return true; }
     *     };
     * AND U50-U59, THE ORDINARY ONE:
     *     public boolean canWork(float chance) {
     *         if (chance >= 1.0F) return true;
     *         if (chance <= 0.0F) return false;
     *         return chance > rand.nextFloat();
     *     }
     * </pre>
     *
     * <p>SO THE EVENTS REAL CAPABILITY IS <b>REPLACING THE ROLLER</b>, NOT CHANGING THE ODDS: a handler swaps in
     * GUARANTEED and the craft becomes certain. IT NEVER TOUCHED A REQUIREMENT OWN CHANCE VALUE, which is why an
     * earlier plan to make this event carry an OVERRIDING FLOAT WAS WRONG -- there was nothing to override, and the
     * number I would have invented has no upstream counterpart.</p>
     *
     * <p><b>AND THIS PORT HAS NO ROLLER TO REPLACE, SO THE FLAG IS THE EQUIVALENT.</b> A flag rather than a value
     * also means NOTHING IS SILENTLY OVERRIDDEN: leaving it false changes no behaviour at all, which is the property
     * a per-requirement chance model needs from an event it did not ask for.</p>
     */
    public ResultChanceCreateEvent forceGuaranteed() {
        this.guaranteed = true;
        return this;
    }

    /** @return true when a handler has asked every probabilistic output of this craft to succeed */
    public boolean isGuaranteed() {
        return guaranteed;
    }
}
