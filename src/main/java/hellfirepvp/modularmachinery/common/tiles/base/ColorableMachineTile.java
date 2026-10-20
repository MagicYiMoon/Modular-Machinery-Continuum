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
 ******************************************************************************/package hellfirepvp.modularmachinery.common.tiles.base;

/**
 * A machine component that carries a player-chosen colour.
 *
 * <p>Port of upstream's interface of the same name (§103.1 listed it absent). Two methods; the value is
 * persisted by {@link TileMachineComponent}, so the interface itself carries no state — which is why adding
 * it is safe rather than the dead-code hazard §154 identified for the base LAYER as a whole.
 */
public interface ColorableMachineTile {

    /** @return the colour as an ARGB int, or 0 when the player has not set one */
    int getMachineColor();

    /** @param color the colour as an ARGB int */
    void setMachineColor(int color);
}