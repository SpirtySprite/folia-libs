import struct
import sys
import zipfile
from pathlib import Path


def relocate(data, replacements):
    count = struct.unpack_from(">H", data, 8)[0]
    output = bytearray(data[:10])
    position = 10
    index = 1
    while index < count:
        tag = data[position]
        position += 1
        output.append(tag)
        if tag == 1:
            size = struct.unpack_from(">H", data, position)[0]
            position += 2
            value = data[position:position + size]
            position += size
            for old, new in replacements:
                value = value.replace(old.encode(), new.encode())
            output.extend(struct.pack(">H", len(value)))
            output.extend(value)
        else:
            sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                     12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
            size = sizes[tag]
            output.extend(data[position:position + size])
            position += size
            if tag in (5, 6):
                index += 1
        index += 1
    output.extend(data[position:])
    return output


def build(source, directory):
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source) as original:
        for letter in ("A", "B"):
            prefix = "fixture" + letter.lower()
            replacements = [("shaded/foliaboard", prefix + "/board"),
                            ("shaded/foliacommons", prefix + "/commons"),
                            ("io/github/spirtysprite/integration/BoardFixturePlugin", prefix + "/BoardFixturePlugin")]
            replacements += [(old.replace("/", "."), new.replace("/", ".")) for old, new in list(replacements)]
            with zipfile.ZipFile(directory / ("board-fixture-" + letter.lower() + ".jar"), "w", zipfile.ZIP_DEFLATED) as target:
                for name in original.namelist():
                    if not (name.startswith("shaded/foliaboard/") or name.startswith("shaded/foliacommons/")
                            or name.endswith("-version.properties")
                            or name.startswith("io/github/spirtysprite/integration/BoardFixturePlugin")):
                        continue
                    data = original.read(name)
                    if name.endswith(".class"):
                        data = relocate(data, replacements)
                    for old, new in replacements:
                        name = name.replace(old, new)
                    target.writestr(name, data)
                target.writestr("plugin.yml", f"name: BoardFixture{letter}\nversion: 1.0\nmain: {prefix}.BoardFixturePlugin\napi-version: '1.20'\nfolia-supported: true\ndepend: [FoliaIntegration]\n")


if __name__ == "__main__":
    build(Path(sys.argv[1]), Path(sys.argv[2]))
