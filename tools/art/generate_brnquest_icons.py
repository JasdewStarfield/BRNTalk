"""Export BRNTalk's BRNQuest type icons from editable, doubled 8x8 pixel masks."""

from pathlib import Path
import struct
import zlib


# Match the built-in type icons: a two-pixel grid and an opaque white silhouette.
# BRNQuest's EditorIcon renderer supplies the UI tint and one-pixel shadow.
MASKS = {
    "message_seen": (
        "........", ".######.", ".#....#.", ".##..##.",
        ".#.##.#.", ".#....#.", ".######.", "........",
    ),
    "conversation_complete": (
        ".#####..", ".#...#..", ".#...#..", ".####..#",
        "..#...##", "...#.##.", "....##..", "........",
    ),
    "start_conversation": (
        ".######.", ".#....#.", ".#.#..#.", ".#.##.#.",
        ".#.#..#.", ".######.", "..##....", "..#.....",
    ),
    "resume_conversation": (
        "..####..", ".#....#.", ".#....#.", "###...#.",
        ".#....#.", "..#..#..", "...##...", "........",
    ),
    "open_screen": (
        "########", "#......#", "#.####.#", "#.#..#.#",
        "#.####.#", "#..#...#", "#......#", "########",
    ),
}


def chunk(kind, data):
    """Write a PNG chunk with a CRC using only the Python standard library."""
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def export(mask, path):
    assert len(mask) == 8 and all(len(row) == 8 and set(row) <= {".", "#"} for row in mask)
    # Duplicate each logical pixel exactly; no interpolation or partial alpha.
    rows = b"".join(b"\0" + b"".join(
        bytes((255, 255, 255, 255)) if mask[y // 2][x // 2] == "#" else bytes(4)
        for x in range(16)) for y in range(16))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    output = root / "src/main/resources/assets/brntalk/textures/gui/sprites/brnquest/type"
    output.mkdir(parents=True, exist_ok=True)
    for name, mask in MASKS.items():
        export(mask, output / f"{name}.png")
    print(f"Exported {len(MASKS)} icons to {output}")
