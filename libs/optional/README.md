# Optional community integrations

These external mods are not embedded in Modular Machinery: Continuum. Only GeckoLib is embedded.

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
