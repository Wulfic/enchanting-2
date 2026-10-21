#!/usr/bin/env python3
"""Writes the GameTest mod's empty structure template for the NeoForge and Forge nodes (Phase 13C/13D).

Fabric's GameTest API ships fabric-gametest-api-v1:empty as .snbt and loads it with its own mixin; a NeoForge datapack
only loads binary .nbt structures, so the NeoForge / Forge GameTest mod carries the same template as
src/forgelike/gametest/resources/data/enchanting2_gametest/structure/empty.nbt: 8x8x8, every block minecraft:air,
DataVersion 2730 like Fabric's (vanilla's data fixer upgrades it on load).

    python scripts/make-empty-structure.py            # rewrites the file
    python scripts/make-empty-structure.py --check    # exits 1 if the committed file differs from what this writes

The output is byte-for-byte reproducible (gzip mtime 0), so --check can guard it.
"""
import gzip
import io
import struct
import sys
from pathlib import Path

TARGET = Path(__file__).resolve().parent.parent / 'src/forgelike/gametest/resources/data/enchanting2_gametest/structure/empty.nbt'
SIZE = 8
DATA_VERSION = 2730

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(text):
    raw = text.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def int_list(values):
    return struct.pack('>bi', TAG_INT, len(values)) + b''.join(struct.pack('>i', v) for v in values)


def compound(fields):
    """fields: list of (tag type, name, payload bytes); returns the payload of a compound tag."""
    return b''.join(struct.pack('>b', kind) + name(key) + payload for kind, key, payload in fields) + bytes([TAG_END])


def compound_list(payloads):
    return struct.pack('>bi', TAG_COMPOUND if payloads else TAG_END, len(payloads)) + b''.join(payloads)


def structure():
    blocks = [compound([(TAG_LIST, 'pos', int_list([x, y, z])), (TAG_INT, 'state', struct.pack('>i', 0))])
              for x in range(SIZE) for y in range(SIZE) for z in range(SIZE)]
    palette = [compound([(TAG_STRING, 'Name', name('minecraft:air'))])]
    root = compound([
        (TAG_INT, 'DataVersion', struct.pack('>i', DATA_VERSION)),
        (TAG_LIST, 'size', int_list([SIZE, SIZE, SIZE])),
        (TAG_LIST, 'palette', compound_list(palette)),
        (TAG_LIST, 'blocks', compound_list(blocks)),
        (TAG_LIST, 'entities', compound_list([])),
    ])
    raw = struct.pack('>b', TAG_COMPOUND) + name('') + root
    out = io.BytesIO()
    with gzip.GzipFile(fileobj=out, mode='wb', mtime=0) as gz:
        gz.write(raw)
    return out.getvalue()


def main():
    data = structure()
    if '--check' in sys.argv[1:]:
        if not TARGET.is_file() or TARGET.read_bytes() != data:
            print(f'{TARGET} differs from what scripts/make-empty-structure.py writes; rerun it', file=sys.stderr)
            return 1
        print(f'{TARGET} is up to date')
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f'wrote {TARGET} ({len(data)} bytes)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
