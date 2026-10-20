# Converts the upstream 1.12.2 asset packs to 1.20.1 (post-flattening) conventions.
#
# 1.12.2 asset conventions that 1.20.1 rejects, all established empirically from ModelBakery /
# ModelManager warnings on this project rather than assumed:
#   1. textures/blocks -> textures/block ; textures/items -> textures/item
#      (1.13 flattened the plural asset directories to singular)
#   2. model references must gain a type segment:
#        "model":  "ns:blockcasing_plain"     -> "ns:block/blockcasing_plain"
#        "parent": "ns:blockmodel_overlay"    -> "ns:block/blockmodel_overlay"
#      because 1.20.1 resolves "ns:name" to models/<name>.json, not models/block/<name>.json
#   3. texture references follow the directory flattening:
#        "ns:blocks/..." -> "ns:block/..." ; "ns:items/..." -> "ns:item/..."
#
# Handles BOTH namespaces this mod ships. `modularmagic` (the inlined Thaumcraft/Astral provider
# blocks) is a separate asset namespace whose models cross-reference `modularmachinery:` for their
# parent and background texture, so rule 3 must be applied across every namespace in the list --
# a single-namespace pass would leave those cross-references pointing at the removed plural dirs.
#
# Idempotent and self-checking: safe to re-run, and it re-parses every JSON it touches.
#
# Usage:  pwsh -File tools/convert-assets-1.20.1.ps1 [-AssetRoot <path>] [-WhatIf]

param(
    [string]$AssetRoot = (Join-Path $PSScriptRoot '..\src\main\resources\assets'),
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$AssetRoot = (Resolve-Path $AssetRoot).Path
Write-Host "Asset root: $AssetRoot"

$namespaces = @('modularmachinery', 'modularmagic')
$changed = 0

# --- JSON read/write helpers -------------------------------------------------
# Windows PowerShell 5.1's ConvertFrom-Json / ConvertTo-Json both reject an object whose property name
# is the empty string ("Cannot process argument because the value of argument \"name\" is invalid").
# The empty key is not a curiosity here -- it is *required*: a block that declares no blockstate
# properties must use "variants": {"": {...}} (see stage 6). PowerShell's cmdlets cannot express that,
# so all JSON work goes through .NET's JavaScriptSerializer instead.
Add-Type -AssemblyName System.Web.Extensions -ErrorAction SilentlyContinue
$script:jsonSerializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer

function Read-Json([string]$path) {
    return $script:jsonSerializer.DeserializeObject((Get-Content $path -Raw))
}

function Write-Json([string]$path, $obj) {
    # UTF8 without BOM: Minecraft's Gson tolerates a BOM but several tools in the pack-author chain do
    # not, and the upstream files have none.
    $text = $script:jsonSerializer.Serialize($obj)
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

# --- 1. Flatten the texture directories, per namespace ----------------------
foreach ($ns in $namespaces) {
    $nsRoot = Join-Path $AssetRoot $ns
    if (-not (Test-Path $nsRoot)) { continue }
    foreach ($pair in @(@('blocks', 'block'), @('items', 'item'))) {
        $from = Join-Path $nsRoot "textures\$($pair[0])"
        $to = Join-Path $nsRoot "textures\$($pair[1])"
        if (Test-Path $from) {
            if (Test-Path $to) {
                Get-ChildItem $from -File | ForEach-Object {
                    if (-not $WhatIf) { Move-Item $_.FullName $to -Force }
                }
                if (-not $WhatIf) { Remove-Item $from -Recurse -Force }
            } else {
                if (-not $WhatIf) { Move-Item $from $to -Force }
            }
            Write-Host "  $ns/textures/$($pair[0]) -> textures/$($pair[1])"
            $changed++
        }
    }
}

# --- 2/3. Rewrite JSON references ------------------------------------------
# NOTE: use the .NET Regex.Replace(string, MatchEvaluator) overload. PowerShell's `-replace`
# operator does NOT accept a scriptblock for the replacement -- it stringifies it, which silently
# writes the scriptblock's source text into the JSON. That produced a corrupted dry run during
# development of this script; the self-check below now guards against it.
$modelRefPatterns = @{}
foreach ($ns in $namespaces) {
    $modelRefPatterns[$ns] = New-Object System.Text.RegularExpressions.Regex(
        ('(?<=")({0}:)([A-Za-z0-9_]+)(?=")' -f [regex]::Escape($ns)))
}

function Edit-JsonFile([string]$path) {
    $text = Get-Content $path -Raw
    $orig = $text

    foreach ($ns in $namespaces) {
        # Texture namespace path: plural -> singular asset dir. Applied for every namespace, because
        # a model in one namespace can reference textures in another.
        $text = $text -replace "$ns`:blocks/", "$ns`:block/"
        $text = $text -replace "$ns`:items/", "$ns`:item/"

        # "model" / "parent" references must name a path, not a bare name. Only rewrites values that
        # carry no slash yet, so already-correct paths are left alone.
        $text = $modelRefPatterns[$ns].Replace($text, {
            param($m)
            return "$($m.Groups[1].Value)block/$($m.Groups[2].Value)"
        })
    }

    if ($text -ne $orig) {
        if (-not $WhatIf) { Set-Content -Path $path -Value $text -NoNewline -Encoding UTF8 }
        return $true
    }
    return $false
}

foreach ($ns in $namespaces) {
    foreach ($dir in @('blockstates', 'models\block', 'models\item')) {
        $full = Join-Path $AssetRoot "$ns\$dir"
        if (-not (Test-Path $full)) { continue }
        Get-ChildItem $full -Filter *.json -File | ForEach-Object {
            if (Edit-JsonFile $_.FullName) { $changed++ }
        }
    }
}

Write-Host "Files changed: $changed"
if ($WhatIf) { Write-Host '(dry run -- nothing written)' }

# --- 5. Remove forge_marker blockstates -------------------------------------
# `forge_marker: 1` is a 1.12.2 Forge model-loader feature that no longer exists. The affected files
# use its "defaults" block to set a shared model that the variants then inherit; on 1.20.1 each
# variant must name its model explicitly. The "inventory" pseudo-variant is also gone -- 1.20.1 gets
# the inventory form from models/item/<block>.json instead -- so it is dropped, and any remaining
# transform-only entry (which carries no "model") is dropped with it.
#
# This was missed by the first pass because it only had to handle the files that produce a *warning*;
# a forge_marker file whose variants happen to name their model still loads, but silently loses the
# inherited defaults. All of them are now normalised unconditionally.
function Convert-ForgeMarkerBlockstate([string]$path) {
    $json = Read-Json $path
    if (-not ($json.PSObject.Properties.Name -contains 'forge_marker')) { return $false }

    $defaultModel = $null
    if ($json.PSObject.Properties.Name -contains 'defaults') {
        $d = $json.defaults
        if ($d.PSObject.Properties.Name -contains 'model') { $defaultModel = $d.model }
    }

    $out = [ordered]@{ variants = [ordered]@{} }
    foreach ($variant in $json.variants.PSObject.Properties) {
        if ($variant.Name -eq 'inventory') { continue }
        $entries = @($variant.Value)
        $newEntries = @()
        foreach ($entry in $entries) {
            $hasModel = $entry -and ($entry.PSObject.Properties.Name -contains 'model')
            if (-not $hasModel) {
                # Inherit the defaults model only if one was declared; otherwise the entry is a pure
                # transform and has no meaning on 1.20.1.
                if ($defaultModel) {
                    $merged = [ordered]@{ model = $defaultModel }
                    if ($entry) {
                        foreach ($p in $entry.PSObject.Properties) {
                            if ($p.Name -ne 'transform' -and $p.Name -ne 'particle') {
                                $merged[$p.Name] = $p.Value
                            }
                        }
                    }
                    $newEntries += [pscustomobject]$merged
                }
                continue
            }
            $clean = [ordered]@{}
            foreach ($p in $entry.PSObject.Properties) {
                if ($p.Name -ne 'transform') { $clean[$p.Name] = $p.Value }
            }
            $newEntries += [pscustomobject]$clean
        }
        if ($newEntries.Count -gt 0) {
            $out.variants[$variant.Name] = $newEntries
        }
    }

    if (-not $WhatIf) {
        Set-Content -Path $path -Value ($out | ConvertTo-Json -Depth 10) -Encoding UTF8
    }
    return $true
}

$forged = 0
foreach ($ns in $namespaces) {
    $bsDir = Join-Path $AssetRoot "$ns\blockstates"
    if (-not (Test-Path $bsDir)) { continue }
    Get-ChildItem $bsDir -Filter *.json -File | ForEach-Object {
        if (Convert-ForgeMarkerBlockstate $_.FullName) {
            Write-Host "  forge_marker removed: $ns/blockstates/$($_.Name)"
            $forged++
        }
    }
}
Write-Host "forge_marker files converted: $forged"

# --- 6. Propertyless blocks use the empty variant key ------------------------
# A block that declares no blockstate properties cannot have a variant keyed "normal" -- 1.20.1
# rejects it with "Unknown blockstate property: 'normal'" and the model never resolves. The upstream
# provider blocks were metadata-driven (their "normal" came from block metadata), so on 1.20.1 they
# carry no properties and must use the single empty key "" instead.
#
# Only the bare "normal" key is rewritten. A key such as "facing=normal" is a real property/value pair
# and is left alone, which is why this matches the whole quoted string rather than a substring.
function Convert-PropertylessVariant([string]$path) {
    $raw = Get-Content $path -Raw
    if ($raw -notmatch '"normal"\s*:') { return $false }
    $json = Read-Json $path
    if (-not ($json.PSObject.Properties.Name -contains 'variants')) { return $false }
    if (-not ($json.variants.PSObject.Properties.Name -contains 'normal')) { return $false }

    $out = [ordered]@{ variants = [ordered]@{} }
    foreach ($variant in $json.variants.PSObject.Properties) {
        # "normal" becomes the empty key; anything else is a genuine property/value and is preserved.
        $key = if ($variant.Name -eq 'normal') { '' } else { $variant.Name }
        $out.variants[$key] = @($variant.Value)
    }
    if (-not $WhatIf) {
        Write-Json $path $out
    }
    return $true
}

$propertyless = 0
foreach ($ns in $namespaces) {
    $bsDir = Join-Path $AssetRoot "$ns\blockstates"
    if (-not (Test-Path $bsDir)) { continue }
    Get-ChildItem $bsDir -Filter *.json -File | ForEach-Object {
        if (Convert-PropertylessVariant $_.FullName) {
            Write-Host "  empty variant key: $ns/blockstates/$($_.Name)"
            $propertyless++
        }
    }
}
Write-Host "propertyless blockstates normalised: $propertyless"



# --- Self-check: every JSON must parse and carry no stray script text -------
# NOTE: do NOT use ConvertFrom-Json here. Windows PowerShell 5.1's implementation throws
# "Cannot process argument because the value of argument \"name\" is invalid" on any object whose
# property name is the empty string -- and "" is exactly the variant key that propertyless blocks
# require (see stage 6). That produced a false 44-file failure during development. The .NET
# JavaScriptSerializer accepts empty keys, so it is used for validation instead.
Add-Type -AssemblyName System.Web.Extensions -ErrorAction SilentlyContinue
$serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer

$bad = 0
foreach ($ns in $namespaces) {
    foreach ($dir in @('blockstates', 'models\block', 'models\item')) {
        $full = Join-Path $AssetRoot "$ns\$dir"
        if (-not (Test-Path $full)) { continue }
        Get-ChildItem $full -Filter *.json -File | ForEach-Object {
            $file = $_.FullName
            $label = "$ns/$dir/$($_.Name)"
            $c = Get-Content $file -Raw
            if ($c -match 'param\(\$m\)' -or $c -match 'Groups\[' -or $c -match '\$m\.') {
                Write-Warning "UNCONVERTED SCRIPT TEXT in $label"
                $bad++
                return
            }
            try { $null = $serializer.DeserializeObject($c) } catch {
                Write-Warning "INVALID JSON after conversion: $label -- $($_.Exception.Message)"
                $bad++
            }
        }
    }
}
if ($bad -eq 0) { Write-Host 'Self-check: all JSON in blockstates/models parses cleanly.' }
else { Write-Error "$bad file(s) failed the self-check." }
