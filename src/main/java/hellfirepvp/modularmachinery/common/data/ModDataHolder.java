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
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.data;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Owns {@code config/modularmachinery/} — the directories a pack or a player puts machine and recipe
 * files into — and seeds them from the mod's own jar exactly once.
 *
 * <p><b>Ported from upstream {@code hellfirepvp.modularmachinery.common.data.ModDataHolder} (136 lines),
 * read in full.</b> The structures it reproduces are upstream's:
 * <ul>
 *   <li>{@link #setup} — upstream L37-L55: {@code mainDir}, {@code machineryDir} and {@code recipeDir}
 *       are created with {@code mkdirs()}, and {@code requiresDefaultMachinery} is set <b>only</b> when
 *       {@code mainDir} did not exist. That flag is upstream's actual never-overwrite mechanism.
 *   <li>{@link #copyDefaultMachinery} — upstream L71-L80: three copies into three targets, the third a
 *       {@code variables} subdirectory of the machinery directory.
 *   <li>{@link #copy} — upstream L82-L134: walk the shipped asset directory, take every {@code .json},
 *       and write it into the target <b>flattened</b> ({@code new File(directoryTo,
 *       filePath.getFileName().toString())}, upstream L124). Subdirectories are not recreated, which
 *       is why the port's {@code default_recipes/alloy_smelter/*.json} end up as {@code
 *       recipes/*.json}.
 * </ul>
 *
 * <h2>The two differences from upstream, both stated rather than implied</h2>
 *
 * <p><b>1. Where the mod's own files are read from.</b> Upstream obtained a
 * {@code java.io.File} of the mod's source and, for a jar, opened its own
 * {@code FileSystems.newFileSystem(modSource.toPath(), null)} (upstream L95-L107). 1.20.1 has no such
 * File: the mod file is a Forge {@code SecureJar} whose backing path may be a jar <i>or</i> — in a
 * development workspace — a union of the exploded {@code classes} and {@code resources} directories,
 * and Forge has already mounted it. The supported accessor for "a path inside my own mod file" is
 * {@code IModFile.findResource(String...)}, whose body was read from the 47.3.0 jar:
 * {@code getSecureJar().getPath("/" + String.join("/", path))}. Whether a second
 * {@code FileSystems.newFileSystem} on an already-mounted mod jar throws
 * {@code FileSystemAlreadyExistsException} was <b>not</b> tested, so no claim is made about it;
 * {@code findResource} removes the question instead of gambling on the answer. Everything downstream
 * of the root path — the walk, the {@code .json} filter, the flattening — is upstream's, unchanged.
 *
 * <p><b>2. A per-file existence check inside {@link #copy}.</b> Upstream's copy loop writes every
 * file unconditionally (upstream L124-L126), and is protected only by the one-shot
 * {@code requiresDefaultMachinery} gate. That gate is kept verbatim, and the per-file skip is added
 * on top of it: on a first run it can only change the outcome for two shipped files that share a
 * basename in different source subdirectories, where upstream silently keeps the last and this keeps
 * the first. It never restores a file the player deleted — that is the gate's job, and upstream's
 * answer there is "not restored" as well.
 *
 * <h2>Why the setup call lives in this class</h2>
 *
 * <p>Upstream called {@code setup} and the conditional {@code copyDefaultMachinery} from
 * {@code CommonProxy.preInit} (upstream CommonProxy L117-L119). This port has no proxy — it uses
 * {@code DistExecutor}/{@code @EventBusSubscriber} instead (see {@code Modularmachinery}'s javadoc) —
 * so the call site is an {@link FMLCommonSetupEvent} subscriber on the mod bus, which is the nearest
 * 1.20.1 equivalent of pre-initialisation and, like preInit, is guaranteed to run <b>before</b> the
 * server's resource reload, which is when the loaders read these directories.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModDataHolder {

    /**
     * The one holder.
     *
     * <p>Upstream kept its instance in {@code CommonProxy.dataHolder} (a static field). This port has no
     * CommonProxy to hang it on, and inventing a proxy class to hold one field would be a second
     * convention for "where shared mod state lives" — so the holder holds itself. The readers are the
     * two loaders and, from PLAN 641 step 4 onward, the in-game authoring tool.
     */
    public static final ModDataHolder INSTANCE = new ModDataHolder();

    private File mainDir;
    private File machineryDir;
    private File recipeDir;
    private boolean requiresDefaultMachinery;

    private ModDataHolder() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        INSTANCE.setup(FMLPaths.CONFIGDIR.get().toFile());
        if (INSTANCE.requiresDefaultMachinery()) {
            INSTANCE.copyDefaultMachinery();
        }
    }

    /** Creates {@code config/modularmachinery/{,machinery,recipes}}; upstream L37-L55. */
    public void setup(File configDir) {
        mainDir = new File(configDir, Modularmachinery.MODID);
        if (!mainDir.exists()) {
            requiresDefaultMachinery = true;
            mainDir.mkdirs();
        }

        machineryDir = new File(mainDir, "machinery");
        if (!machineryDir.exists()) {
            machineryDir.mkdirs();
        }

        recipeDir = new File(mainDir, "recipes");
        if (!recipeDir.exists()) {
            recipeDir.mkdirs();
        }

        // Upstream's last line here is Config.loadFrom(new File(mainDir, MODID + ".cfg")). This port's
        // Config is a ForgeConfigSpec (Config.java:53) registered in Modularmachinery's constructor
        // (Modularmachinery.java:71-73), so Forge reads and writes config/modularmachinery-common.toml
        // itself and there is no .cfg file to load. NOTHING IS MISSING: the values upstream loaded
        // there are read through Config.machineColor() here.
        Modularmachinery.LOG.info("[MMCE] config directories: {} and {}", machineryDir, recipeDir);
    }

    /**
     * Takes the flag, clearing it: upstream L57-L61, a one-shot consumer rather than a state query.
     *
     * <p>The one-shotness is the whole point. {@code true} means "the config directory was created by
     * this run", i.e. "there is nothing of the player's here to protect" — so the extraction may write.
     * A config directory that already existed answers {@code false} forever after, which is what makes
     * a pack's edit to {@code config/modularmachinery/machinery/foo.json} survive every future start.
     */
    public boolean requiresDefaultMachinery() {
        boolean old = requiresDefaultMachinery;
        requiresDefaultMachinery = false;
        return old;
    }

    /** @return {@code config/modularmachinery/machinery}, or null before {@link #setup} */
    public File getMachineryDirectory() {
        return machineryDir;
    }

    /** @return {@code config/modularmachinery/recipes}, or null before {@link #setup} */
    public File getRecipeDirectory() {
        return recipeDir;
    }

    /** The three copies, upstream L71-L80, in upstream's order and into upstream's targets. */
    public void copyDefaultMachinery() {
        copy("default_machinery", machineryDir);
        copy("default_recipes", recipeDir);

        File defaultVariableDir = new File(machineryDir, "variables");
        if (!defaultVariableDir.exists()) {
            defaultVariableDir.mkdirs();
        }
        copy("default_variables", defaultVariableDir);
    }

    /**
     * Copies every {@code .json} under {@code assets/modularmachinery/<assetDirFrom>} into
     * {@code directoryTo}, flat. Upstream L82-L134, with the source change documented on this class.
     *
     * <p><b>FLAT IS UPSTREAM'S SHAPE, NOT A SHORTCUT.</b> Upstream takes
     * {@code filePath.getFileName()} as the target name, so nested source files land side by side in
     * the config directory. The port ships {@code default_recipes/alloy_smelter/*.json} and
     * {@code default_recipes/centrifuge/*.json}, so this is observable: the loader then sees
     * {@code recipes/alloy_smelter_diamond.json}. Every basename in the shipped set is unique, so
     * nothing is lost by it.
     */
    private void copy(String assetDirFrom, File directoryTo) {
        Path root = assetRoot(assetDirFrom);
        if (root == null) {
            // Not fatal, and loud rather than silent: "nothing was copied" and "there was nothing to
            // copy" must not look the same in a log, because the first one means a fresh install has
            // no machines at all.
            Modularmachinery.LOG.warn("[MMCE] no shipped files found at assets/{}/{}, nothing extracted "
                    + "into {}", Modularmachinery.MODID, assetDirFrom, directoryTo);
            return;
        }

        int copied = 0;
        int kept = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            Iterator<Path> itr = walk.iterator();
            while (itr.hasNext()) {
                Path filePath = itr.next();
                // Upstream's filter, verbatim: only .json, and directories named *.json are treated the
                // same way upstream treats them (attempted, and failing loudly).
                if (!filePath.getFileName().toString().endsWith(".json")) {
                    continue;
                }

                File target = new File(directoryTo, filePath.getFileName().toString());
                if (target.exists()) {
                    // THE FILE THAT IS ALREADY THERE IS THE PLAYER'S. See the class javadoc, difference 2.
                    kept++;
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    Files.copy(filePath, fos);
                    copied++;
                } catch (Exception exc) {
                    Modularmachinery.LOG.error("[MMCE] couldn't copy file from {}", filePath, exc);
                }
            }
        } catch (IOException e) {
            Modularmachinery.LOG.error("[MMCE] error iterating through {} - skipping copying "
                    + "default setup!", assetDirFrom, e);
            return;
        }
        Modularmachinery.LOG.info("[MMCE] extracted {} shipped file(s) from {} into {} "
                + "({} already present and left untouched)", copied, assetDirFrom, directoryTo, kept);
    }

    /**
     * The root of one shipped asset directory inside the mod's own file.
     *
     * <p>Read through Forge rather than through the classloader: the classloader answers "give me the
     * bytes at this path", and this needs "enumerate what is under this path", which on 1.20.1 is only
     * well defined inside the mod file's own file system. See the class javadoc, difference 1.
     *
     * @return the directory, or null when it does not exist (or Forge has no mod file for this mod)
     */
    private Path assetRoot(String assetDirFrom) {
        IModFileInfo modFileInfo = ModList.get().getModFileById(Modularmachinery.MODID);
        if (modFileInfo == null) {
            return null;
        }
        Path root = modFileInfo.getFile()
                .findResource("assets", Modularmachinery.MODID, assetDirFrom);
        return (root != null && Files.exists(root)) ? root : null;
    }
}
