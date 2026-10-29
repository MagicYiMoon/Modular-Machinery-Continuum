"""Fetch the pinned official GTCEu compile/runtime fixture; never build or bundle it into MMC.

Run from any directory with Python 3. Downloads only when a verified local file is absent.
The four embedded files are extracted so ForgeGradle can remap them for a development run.
"""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "libs" / "optional"
NAME = "gtceu-1.20.1-7.5.3.jar"
URL = "https://cdn.modrinth.com/data/7tG215v7/versions/yLwvjuvy/" + NAME
HASH = "d6560d8b695828a763389d3f45e0210ef70c1892c7b1633ac458544905df1a0d"
SLIM_NAME = "gtceu-1.20.1-7.5.3-slim.jar"
SLIM_HASH = "87b4c1d1ea54112668e72af1b6a45875d5f3a08bef343881816c1cd741501dee"
EMBEDDED = {
    "ldlib-forge-1.20.1-1.0.40.b.jar": "0fdf4cb65cda16b9c7a21b96c77d6365a56812ecbd180d75ec72f9b40a67dc67",
    "Registrate-MC1.20-1.3.11.jar": "81c5041b9a0017c5ac61c46655e984f78d711a6981330567d6fddfbde0385449",
    "configuration-forge-1.20.1-2.2.0.jar": "359d0a93004cd94da7973aff7fd3b64eaa8603ee211acd856b75b1810f806d17",
    "mixinextras-forge-0.5.3.jar": "89d60f6bf1f29664319acfa80e777abc03fde674370af52e94a9a2e452b98833",
}


def verified(path, digest):
    return path.is_file() and sha256(path.read_bytes()).hexdigest() == digest


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    target = DEST / NAME
    if not verified(target, HASH):
        with urlopen(Request(URL, headers={"User-Agent": "MMC-GTCEu-development"}), timeout=120) as response:
            data = response.read()
        if sha256(data).hexdigest() != HASH:
            raise ValueError("Official GTCEu checksum mismatch; no files written")
    else:
        data = target.read_bytes()
    with ZipFile(BytesIO(data)) as archive:
        extracted = {name: archive.read("META-INF/jarjar/" + name) for name in EMBEDDED}
    for name, content in extracted.items():
        if sha256(content).hexdigest() != EMBEDDED[name]:
            raise ValueError("Embedded dependency checksum mismatch: " + name)
    slim = DEST / SLIM_NAME
    if not verified(slim, SLIM_HASH):
        with urlopen(Request(URL.replace(NAME, SLIM_NAME), headers={"User-Agent": "MMC-GTCEu-development"}), timeout=120) as response:
            slim_data = response.read()
        if sha256(slim_data).hexdigest() != SLIM_HASH:
            raise ValueError("Official GTCEu slim checksum mismatch; no files written")
    else:
        slim_data = slim.read_bytes()
    if not verified(target, HASH):
        target.write_bytes(data)
    for name, content in extracted.items():
        if not verified(DEST / name, EMBEDDED[name]):
            (DEST / name).write_bytes(content)
    if not verified(slim, SLIM_HASH):
        slim.write_bytes(slim_data)
    print("Verified official GTCEu 7.5.3 full/slim and four development dependencies in", DEST)


if __name__ == "__main__":
    main()
