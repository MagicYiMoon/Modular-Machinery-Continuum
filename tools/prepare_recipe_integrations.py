"""Prepare pinned official optional recipe APIs. No foreign mod is bundled into MMC.
Run with Python 3 from any directory; all downloads and the named nested core are hash checked.
"""
from pathlib import Path
from hashlib import sha512, sha256
from urllib.request import Request, urlopen
from zipfile import ZipFile
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "libs" / "optional"
FILES = [{'name': 'cofh-core-11.0.2.jar',
  'url': 'https://cdn.modrinth.com/data/OWSRM4vD/versions/kglS53Hd/cofh_core-1.20.1-11.0.2.56.jar',
  'sha512': '5a62bc6dcaaf3cf77234f3e0559eb6d2ec7db05b39feeba56dbdd46f3378aec6d97bd2078e220fe33891384abc40e1a705affe9d77ae4e35cf60185e07c11a73',
  'version': 'kglS53Hd'},
 {'name': 'mantle-1.11.117.jar',
  'url': 'https://cdn.modrinth.com/data/Cg6Uc79H/versions/37ccXi7G/Mantle-1.20.1-1.11.117.jar',
  'sha512': '9851a78342f4bc0e44de2c9c4ab90ad16e5fb11ced94ff7620aeb8c3fde9ec68ef11f17e53d98d7cdb64ef9b6c6d4a8a7d065d60f5f7df062cf2836d40e70934',
  'version': '37ccXi7G'},
 {'name': 'nuclearcraft-1.2.36.jar',
  'url': 'https://cdn.modrinth.com/data/Qvd8pl5G/versions/kefJECnt/NuclearCraft-1.20.1-1.2.36.jar',
  'sha512': '0f1297562dedd70d531a6054466ac7b43d51a64bedc4730b2050187aff0e5437d3a8771abfbb1cc6d1a2e0d6fa749cdcd828a05443d4bf4ec13f5f2c7ccca241',
  'version': 'kefJECnt'},
 {'name': 'thermal-expansion-11.0.1.jar',
  'url': 'https://cdn.modrinth.com/data/hmD6rrUJ/versions/Ux2Z0ow1/thermal_expansion-1.20.1-11.0.1.29.jar',
  'sha512': 'cce9e05126064aa60d8c10955c8bcfef92b4080d042dfb663054565fefc9de8268c28b4cb025ed8e3bc9c3da1a9f1266e640c9ea9b799868b6ab4b758de6f3d0',
  'version': 'Ux2Z0ow1'},
 {'name': 'thermal-foundation-11.0.6.jar',
  'url': 'https://cdn.modrinth.com/data/Xvg6q5Wp/versions/44ilyZbi/thermal_foundation-1.20.1-11.0.6.70.jar',
  'sha512': '721908e776fa037c6b4e7a229f453b7e50efb62a935bfa67d4ee3ac68fbdf860e615b5cf54b0d2221504dd7c736e3d70659c3ea4a0abaa7e688ce5acdb98f87f',
  'version': '44ilyZbi'},
 {'name': 'tconstruct-3.12.1.231.jar',
  'url': 'https://cdn.modrinth.com/data/rxIIYO6c/versions/Fv0zTUuh/TConstruct-1.20.1-3.12.1.231.jar',
  'sha512': 'e8b78225f379249b0edf0ddcb2b84aed4bf8712e1fb1a5faae9a17c0ac6ff2c697a684c0dd9b5ea4af1e0e2bfb3eeb206e1c16f4900a38d270592ced6115adea',
  'version': 'Fv0zTUuh'}]
CORE_HASH = "c10f1ad221cb0719a6627c9ceb1d104f03631ba41bb401e10ec86fd21d7d96f7"

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    for entry in FILES:
        path = DEST / entry["name"]
        if path.exists() and sha512(path.read_bytes()).hexdigest() == entry["sha512"]:
            continue
        with urlopen(Request(entry["url"], headers={"User-Agent": "MMC-recipe-api-development"}), timeout=120) as response:
            data = response.read()
        if sha512(data).hexdigest() != entry["sha512"]:
            raise ValueError("Official checksum mismatch: " + entry["name"])
        path.write_bytes(data)
    with ZipFile(DEST / "thermal-foundation-11.0.6.jar") as archive:
        names = [n for n in archive.namelist() if n.startswith("META-INF/jarjar/") and "thermal_core" in n and n.endswith(".jar")]
        if len(names) != 1:
            raise ValueError("Expected one official nested Thermal core")
        core = archive.read(names[0])
    if sha256(core).hexdigest() != CORE_HASH:
        raise ValueError("Nested Thermal core checksum mismatch")
    (DEST / "thermal-core-11.0.6.jar").write_bytes(core)
    print("Verified six optional recipe APIs and compile-only Thermal core; nothing bundled into MMC")

if __name__ == "__main__":
    main()
