# Optional community integrations

These external mods are not embedded in Modular Machinery: Continuum. Only GeckoLib is embedded.

## Embers Rekindled compile API / optional runtime

- Target: Forge 1.20.1 **1.20.1-1.4.7**, upstream beta, official Modrinth version [QNWjEwxw](https://modrinth.com/mod/embers/version/QNWjEwxw).
- Source: [RCXcrafter/EmbersRekindled](https://github.com/RCXcrafter/EmbersRekindled/tree/c900e391c21d21ec35f17f17fba92b96a6fee37e), fixed commit `c900e391c21d21ec35f17f17fba92b96a6fee37e`.
- Official file SHA-512 verified: `6b94e06390c9929496e8676f7b9f158345e942369cbf6efa4550f9387c09abf613223e7cbd9a63799913d3d167cd9304bf27db317997a2b6fb6e8bbe1934f9af`.
- `compileOnly fg.deobf("maven.modrinth:embers:QNWjEwxw")`; development runtime only with `-PwithEmbersIntegration`. Optional dependency range `[1.20.1-1.4.7,1.20.1-1.5)`.
- Uses native `com.rekindled.embers` capability/storage and receiver transfer; no external source, textures or JAR embedded. Eight input tiers and one-shot fractional recipes only; no output or per-tick requirement.
- Install the official mod separately on both sides. Instructions: [Embers guide](../../docs/EMBERS_ZH.md). Available since MMC 1.3.0.

## PneumaticCraft compile API / optional runtime

- Target: Forge 1.20.1 **PneumaticCraft: Repressurized 6.0.23**, official Modrinth version `ohRZqkHb`: https://modrinth.com/mod/pneumaticcraft-repressurized/version/ohRZqkHb
- Official source tag `v6.0.23`, commit `2639a983eb9e3352517aed6dc03994a50e84883f`: https://github.com/TeamPneumatic/pnc-repressurized/tree/v6.0.23
- Official file SHA-512 verified against version metadata: `9f014a367d2e76b84207d7b74507eb1c60b246cbaee51198142c02fec95c7e034482df0af51eddf835bab97ea2bb0ff1539da3d8988f890886e154c9860960cb`.
- `compileOnly` plus `-PwithPneumaticIntegration` optional runtime; Patchouli is required and loaded by the same flag. The main optional dependency range is `[6.0.23,6.1)`.
- The air handler factory, machine capability, pressure balancing, leakage and overpressure behavior remain native. No PNC code, textures or mod JAR are embedded in MMC.
- Scope and instructions: [PneumaticCraft guide](../../docs/PNEUMATICCRAFT_ZH.md). Four upgrade slots, a pressure GUI and native volume/safety upgrades are implemented in source.

## Botania compile API / optional runtime

- Forge 1.20.1 target: **1.20.1-456-FORGE**, Modrinth version `npY4sRWF`, already pinned in `gradle.properties`.
- Official file: https://modrinth.com/mod/botania/version/npY4sRWF
- Spark API reference: https://github.com/VazkiiMods/Botania/tree/release-1.20.1-456 (commit `361ddf3e2142047ff57e3c5c2dc6de617d7a0b55`).
- Compile API remains optional at runtime; `-PwithMagicIntegration` loads Botania, Patchouli and Curios in development. Players must install the official mods and their required dependencies separately.
- Existing mana providers remain supported; spark hatches use the native capability and transfer implementation. Instructions: [Spark mana guide](../../docs/BOTANIA_SPARK_ZH.md).
- No Botania source, textures or mod JAR are embedded in MMC.

## GTCEu Modern compile API / optional runtime

- Audited/test target: Forge 1.20.1 **7.5.3** (upstream labels this release beta); MMC's optional dependency range is `[7.5.3,7.6)`.
- Official project: https://modrinth.com/mod/gregtechceu-modern/version/mc1.20.1-7.5.3
- Source: https://github.com/GregTechCEu/GregTech-Modern/tree/v7.5.3-1.20.1 (commit `91a79b8a7a2b62ec6277423e6c0ded4af89a831e`).
- Run `python tools/prepare_gtceu.py` from the project root before compilation. The script downloads and SHA-256 verifies the official full/slim jars, then extracts only the four named dependencies for ForgeGradle's development remapping. It does not modify/rebuild GTCEu or embed it into MMC.
- Gradle uses official `gtceu-1.20.1-7.5.3-slim.jar` plus LDLib 1.0.40.b, Registrate MC1.20-1.3.11, Configuration 2.2.0 and MixinExtras 0.5.3. A full GT jar plus separate Registrate jars causes duplicate Java modules in a development run; the official slim artifact avoids that.
- Full JAR SHA-256: `d6560d8b695828a763389d3f45e0210ef70c1892c7b1633ac458544905df1a0d`.
- Slim JAR SHA-256: `87b4c1d1ea54112668e72af1b6a45875d5f3a08bef343881816c1cd741501dee`.
- Source JAR SHA-256: `586cac7f2a81bb28f6302d716fa7e8fd6dfead8e79273608c170732baed8e557`.
- Dependency hashes are pinned in the preparation script. Files remain external optional development dependencies, always compileOnly and runtime only with `-PwithGTCEuIntegration`. GTCEu declares LGPL-3.0; each dependency retains its own license and notices.
- Players use the official full GTCEu jar, which already contains these dependencies. Do not copy both full and slim jars into a modpack.
- Integration scope and pack-author instructions: [GTCEu guide](../../docs/GTCEU_ZH.md). Available since MMC 1.3.0.

## Flux Networks compile API / optional runtime

- Forge 1.20.1 version: `7.2.1.15`, file `FluxNetworks-1.20.1-7.2.1.15.jar`.
- Official file: https://www.curseforge.com/minecraft/mc-mods/flux-networks/files/5234697
- Source: https://github.com/SonarSonic/Flux-Networks/tree/1.20
- Compile dependency: `curse.maven:flux-networks-248020:5234697` (downloaded by Gradle).
- SHA256: `8d24f39b6e1b2d83426a28a62ae0b65294b7e6e925e39a26b62f8ae077f54a89`.
- Runtime only with `-PwithFluxIntegration`; not embedded and not a required dependency.
- Setup and limits: [Flux Networks guide](../../docs/FLUX_NETWORKS_ZH.md).

## Astral Sorcery compile API / optional runtime

- File: `astralsorcery-1.20.1-1.0.0.0.jar`
- Source: https://github.com/MrWizard94-Compile/Astral-Sorcery-1.20.1-Port
- Fixed commit: `3cb52433420fbe951089dba1ccf63bd4598eceba`
- Source archive: https://codeload.github.com/MrWizard94-Compile/Astral-Sorcery-1.20.1-Port/zip/3cb52433420fbe951089dba1ccf63bd4598eceba
- Reproduction: inspect upstream build files, build that checkout with Java 17 / Gradle 8.8 using `jar reobfJar`, then copy `build/libs/astralsorcery-1.20.1-1.0.0.0.jar` here. No upstream Java/resource changes were made for this integration.
- Local artifact SHA256: `066d68b12344fa0a880b394bad563b4f46f9916ed661d5d395ef3ca014a4cb84`. Rebuilds may differ because the upstream manifest includes the build timestamp.
- Always compileOnly in the main project; runtime only with `-PwithAstralIntegration`.

## Extra Utilities Rebirth optional runtime

- Local file: `extrautilitiesrebirth-1.7.3.jar`
- Download: https://github.com/AuramMods/ExtraUtilities/releases/download/1.0.0/ExtraUtilities.jar
- Release tag is `1.0.0`; internal mod version is `1.7.3`.
- SHA256: `079023656906e40cf5f8b260e960cfd2efa3aab2a6e5935b9fb21900c1fa2fde`.
- No compile dependency is needed: this adapter checks the exact mod version, registered generator block and its persistent running flag.
- Runtime only with `-PwithRainbowIntegration`.
- The published mod uses the modern Forge loading-context constructor. Forge 47.3.0 fails to load it; use the verified 47.4.23 test configuration.

Both are experimental compatibility targets for this integration. Their original ownership/licenses remain with their authors. They are not part of MM's GPL source or its Jar-in-Jar payload. The client-side appearance of these external mods has not been verified here.


## Optional external recipe adapters (2026-10-28)

Run `python tools/prepare_recipe_integrations.py` before a clean compile. It verifies official Modrinth file SHA-512 hashes for TConstruct 3.12.1.231 (Fv0zTUuh), Mantle 1.11.117 (37ccXi7G), Thermal Expansion 11.0.1.29 (Ux2Z0ow1), Thermal Foundation 11.0.6.70 (44ilyZbi), CoFH Core 11.0.2.56 (kglS53Hd), NuclearCraft Neoteric 1.2.36 (kefJECnt). It also extracts and SHA-256 verifies the exact nested Thermal core for compile-time remapping; runtime uses Foundation's embedded copy.

All are compileOnly; never embedded in MMC. `-PwithRecipeIntegrations` enables full development runtime, or use `-PwithTinkersIntegration`, `-PwithThermalIntegration`, `-PwithNuclearIntegration` separately. TConstruct and Mantle target releases are upstream beta. See [recipe guide](../../docs/EXTERNAL_RECIPE_ADAPTERS_ZH.md) for supported static families and exclusions. Keep each dependency's own license and notices; no foreign source/assets are copied into MMC.
