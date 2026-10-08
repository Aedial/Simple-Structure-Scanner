#!/usr/bin/env python3

from __future__ import annotations

import argparse
import gzip
import io
import os
import struct

from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, BinaryIO


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

TAG_NAMES = {
    TAG_END: "TAG_End",
    TAG_BYTE: "TAG_Byte",
    TAG_SHORT: "TAG_Short",
    TAG_INT: "TAG_Int",
    TAG_LONG: "TAG_Long",
    TAG_FLOAT: "TAG_Float",
    TAG_DOUBLE: "TAG_Double",
    TAG_BYTE_ARRAY: "TAG_Byte_Array",
    TAG_STRING: "TAG_String",
    TAG_LIST: "TAG_List",
    TAG_COMPOUND: "TAG_Compound",
    TAG_INT_ARRAY: "TAG_Int_Array",
    TAG_LONG_ARRAY: "TAG_Long_Array",
}

COUNT_SUFFIXES = ("", "k", "m", "g", "t", "p", "e")
FILE_SIZE_SUFFIXES = ("B", "kB", "MB", "GB", "TB", "PB", "EB")


@dataclass
class Tag:
    tag_id: int
    value: Any


@dataclass
class ListPayload:
    element_type: int
    values: list[Tag]


@dataclass
class RewriteStats:
    input_blocks: int
    output_blocks: int
    input_palette: int
    output_palette: int
    air_removed: int
    air_added: int


class NbtFormatError(ValueError):
    pass


parser = argparse.ArgumentParser(
    description="Strip air blocks from a Minecraft structure NBT or restore the capture-compatible air mask."
)
subparsers = parser.add_subparsers(dest="command", required=True)

for command, help_text in (
    ("strip", "Remove all minecraft:air block entries and write sibling *.stripped.nbt files."),
    ("restore", "Rebuild the capture-compatible air mask from non-air block positions and write sibling *.restored.nbt files."),
):
    command_parser = subparsers.add_parser(command, help=help_text)
    command_parser.add_argument("inputs", nargs="+", type=Path, help="One or more .nbt files or directories to process")
    command_parser.add_argument("--dry", action="store_true", help="Compute and print the output without writing any files")
    command_parser.add_argument("-f", "--force", action="store_true", help="Overwrite existing output files")
    command_parser.add_argument("-i", "--inplace", action="store_true", help="Overwrite each input file instead of writing a sibling output file")


def read_exact(stream: BinaryIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise NbtFormatError(f"Unexpected end of file while reading {size} bytes")

    return data


def read_struct(stream: BinaryIO, format_string: str) -> Any:
    size = struct.calcsize(format_string)
    return struct.unpack(format_string, read_exact(stream, size))[0]


def write_struct(stream: BinaryIO, format_string: str, value: Any) -> None:
    stream.write(struct.pack(format_string, value))


def read_unsigned_byte(stream: BinaryIO) -> int:
    return read_struct(stream, ">B")


def read_int(stream: BinaryIO) -> int:
    return read_struct(stream, ">i")


def read_string(stream: BinaryIO) -> str:
    length = read_struct(stream, ">H")
    return read_exact(stream, length).decode("utf-8")


def write_unsigned_byte(stream: BinaryIO, value: int) -> None:
    write_struct(stream, ">B", value)


def write_int(stream: BinaryIO, value: int) -> None:
    write_struct(stream, ">i", value)


def write_string(stream: BinaryIO, value: str) -> None:
    encoded_value = value.encode("utf-8")
    if len(encoded_value) > 65535:
        raise NbtFormatError(f"String is too long for NBT encoding: {value!r}")

    write_struct(stream, ">H", len(encoded_value))
    stream.write(encoded_value)


def read_tag_payload(stream: BinaryIO, tag_id: int) -> Any:
    if tag_id == TAG_BYTE:
        return read_struct(stream, ">b")

    if tag_id == TAG_SHORT:
        return read_struct(stream, ">h")

    if tag_id == TAG_INT:
        return read_struct(stream, ">i")

    if tag_id == TAG_LONG:
        return read_struct(stream, ">q")

    if tag_id == TAG_FLOAT:
        return read_struct(stream, ">f")

    if tag_id == TAG_DOUBLE:
        return read_struct(stream, ">d")

    if tag_id == TAG_BYTE_ARRAY:
        length = read_int(stream)
        if length < 0:
            raise NbtFormatError(f"Negative TAG_Byte_Array length: {length}")

        return read_exact(stream, length)

    if tag_id == TAG_STRING:
        return read_string(stream)

    if tag_id == TAG_LIST:
        element_type = read_unsigned_byte(stream)
        length = read_int(stream)
        if length < 0:
            raise NbtFormatError(f"Negative TAG_List length: {length}")

        values = []
        for _ in range(length):
            values.append(Tag(element_type, read_tag_payload(stream, element_type)))

        return ListPayload(element_type, values)

    if tag_id == TAG_COMPOUND:
        compound_value: OrderedDict[str, Tag] = OrderedDict()

        while True:
            child_tag_id = read_unsigned_byte(stream)
            if child_tag_id == TAG_END:
                break

            child_name = read_string(stream)
            compound_value[child_name] = Tag(child_tag_id, read_tag_payload(stream, child_tag_id))

        return compound_value

    if tag_id == TAG_INT_ARRAY:
        length = read_int(stream)
        if length < 0:
            raise NbtFormatError(f"Negative TAG_Int_Array length: {length}")

        return [read_struct(stream, ">i") for _ in range(length)]

    if tag_id == TAG_LONG_ARRAY:
        length = read_int(stream)
        if length < 0:
            raise NbtFormatError(f"Negative TAG_Long_Array length: {length}")

        return [read_struct(stream, ">q") for _ in range(length)]

    raise NbtFormatError(f"Unsupported NBT tag id: {tag_id}")


def write_tag_payload(stream: BinaryIO, tag: Tag) -> None:
    if tag.tag_id == TAG_BYTE:
        write_struct(stream, ">b", tag.value)
        return

    if tag.tag_id == TAG_SHORT:
        write_struct(stream, ">h", tag.value)
        return

    if tag.tag_id == TAG_INT:
        write_struct(stream, ">i", tag.value)
        return

    if tag.tag_id == TAG_LONG:
        write_struct(stream, ">q", tag.value)
        return

    if tag.tag_id == TAG_FLOAT:
        write_struct(stream, ">f", tag.value)
        return

    if tag.tag_id == TAG_DOUBLE:
        write_struct(stream, ">d", tag.value)
        return

    if tag.tag_id == TAG_BYTE_ARRAY:
        byte_value = tag.value if isinstance(tag.value, bytes) else bytes((item & 0xFF for item in tag.value))
        write_int(stream, len(byte_value))
        stream.write(byte_value)
        return

    if tag.tag_id == TAG_STRING:
        write_string(stream, tag.value)
        return

    if tag.tag_id == TAG_LIST:
        payload = expect_list_payload(tag, None, TAG_NAMES[TAG_LIST])
        write_unsigned_byte(stream, payload.element_type)
        write_int(stream, len(payload.values))
        for child_tag in payload.values:
            if child_tag.tag_id != payload.element_type:
                raise NbtFormatError(
                    f"Mismatched list entry type: expected {tag_name(payload.element_type)}, got {tag_name(child_tag.tag_id)}"
                )

            write_tag_payload(stream, child_tag)
        return

    if tag.tag_id == TAG_COMPOUND:
        compound_value = expect_compound(tag, TAG_NAMES[TAG_COMPOUND])
        for child_name, child_tag in compound_value.items():
            write_unsigned_byte(stream, child_tag.tag_id)
            write_string(stream, child_name)
            write_tag_payload(stream, child_tag)

        write_unsigned_byte(stream, TAG_END)
        return

    if tag.tag_id == TAG_INT_ARRAY:
        write_int(stream, len(tag.value))
        for value in tag.value:
            write_struct(stream, ">i", value)
        return

    if tag.tag_id == TAG_LONG_ARRAY:
        write_int(stream, len(tag.value))
        for value in tag.value:
            write_struct(stream, ">q", value)
        return

    raise NbtFormatError(f"Unsupported NBT tag id: {tag.tag_id}")


def tag_name(tag_id: int) -> str:
    return TAG_NAMES.get(tag_id, f"TAG_{tag_id}")


def read_nbt_file(path: Path) -> tuple[str, Tag]:
    with path.open("rb") as raw_stream:
        with gzip.GzipFile(fileobj=raw_stream, mode="rb") as gzip_stream:
            root_tag_id = read_unsigned_byte(gzip_stream)
            root_name = read_string(gzip_stream)
            root_tag = Tag(root_tag_id, read_tag_payload(gzip_stream, root_tag_id))

    if root_tag.tag_id != TAG_COMPOUND:
        raise NbtFormatError(
            f"Structure root must be {tag_name(TAG_COMPOUND)}, got {tag_name(root_tag.tag_id)}"
        )

    return root_name, root_tag


def build_nbt_file_bytes(root_name: str, root_tag: Tag) -> bytes:
    buffer = io.BytesIO()
    with gzip.GzipFile(filename="", fileobj=buffer, mode="wb", mtime=0) as gzip_stream:
        write_unsigned_byte(gzip_stream, root_tag.tag_id)
        write_string(gzip_stream, root_name)
        write_tag_payload(gzip_stream, root_tag)

    return buffer.getvalue()


def write_file_bytes(path: Path, file_bytes: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as raw_stream:
        raw_stream.write(file_bytes)


def write_nbt_file_atomically(path: Path, file_bytes: bytes) -> None:
    temporary_path = path.with_name(path.name + ".tmp")
    write_file_bytes(temporary_path, file_bytes)
    temporary_path.replace(path)


def expect_compound(tag: Tag, context: str) -> OrderedDict[str, Tag]:
    if tag.tag_id != TAG_COMPOUND:
        raise NbtFormatError(f"{context} must be {tag_name(TAG_COMPOUND)}, got {tag_name(tag.tag_id)}")

    return tag.value


def expect_list_payload(tag: Tag, expected_element_type: int | None, context: str) -> ListPayload:
    if tag.tag_id != TAG_LIST:
        raise NbtFormatError(f"{context} must be {tag_name(TAG_LIST)}, got {tag_name(tag.tag_id)}")

    payload = tag.value
    if expected_element_type is not None and payload.element_type != expected_element_type:
        raise NbtFormatError(
            f"{context} must contain {tag_name(expected_element_type)}, got {tag_name(payload.element_type)}"
        )

    return payload


def expect_int(tag: Tag, context: str) -> int:
    if tag.tag_id != TAG_INT:
        raise NbtFormatError(f"{context} must be {tag_name(TAG_INT)}, got {tag_name(tag.tag_id)}")

    return tag.value


def expect_string(tag: Tag, context: str) -> str:
    if tag.tag_id != TAG_STRING:
        raise NbtFormatError(f"{context} must be {tag_name(TAG_STRING)}, got {tag_name(tag.tag_id)}")

    return tag.value


def get_required_tag(compound_value: OrderedDict[str, Tag], key: str, context: str) -> Tag:
    if key not in compound_value:
        raise NbtFormatError(f"Missing {key!r} in {context}")

    return compound_value[key]


def make_string_tag(value: str) -> Tag:
    return Tag(TAG_STRING, value)


def make_int_tag(value: int) -> Tag:
    return Tag(TAG_INT, value)


def make_list_tag(element_type: int, values: list[Tag]) -> Tag:
    return Tag(TAG_LIST, ListPayload(element_type, values))


def make_compound_tag(entries: OrderedDict[str, Tag]) -> Tag:
    return Tag(TAG_COMPOUND, entries)


def make_int_list_tag(values: tuple[int, int, int]) -> Tag:
    return make_list_tag(TAG_INT, [make_int_tag(value) for value in values])


def default_air_palette_entry() -> Tag:
    return make_compound_tag(OrderedDict([("Name", make_string_tag("minecraft:air"))]))


def serialize_tag_payload(tag: Tag) -> bytes:
    buffer = io.BytesIO()
    write_tag_payload(buffer, tag)
    return buffer.getvalue()


def get_structure_parts(root_tag: Tag) -> tuple[OrderedDict[str, Tag], tuple[int, int, int], list[Tag], list[Tag]]:
    root_value = expect_compound(root_tag, "root")

    if "palette" not in root_value and "palettes" in root_value:
        raise NbtFormatError("This tool only supports structure NBT files with a single 'palette' tag")

    size_payload = expect_list_payload(get_required_tag(root_value, "size", "root"), TAG_INT, "root.size")
    if len(size_payload.values) != 3:
        raise NbtFormatError(f"root.size must contain exactly 3 integers, got {len(size_payload.values)}")

    size = tuple(expect_int(tag, f"root.size[{index}]") for index, tag in enumerate(size_payload.values))
    palette_payload = expect_list_payload(get_required_tag(root_value, "palette", "root"), TAG_COMPOUND, "root.palette")
    blocks_payload = expect_list_payload(get_required_tag(root_value, "blocks", "root"), TAG_COMPOUND, "root.blocks")
    return root_value, size, palette_payload.values, blocks_payload.values


def is_air_palette_entry(palette_entry: Tag) -> bool:
    palette_value = expect_compound(palette_entry, "palette entry")
    name_tag = get_required_tag(palette_value, "Name", "palette entry")
    return expect_string(name_tag, "palette entry Name") == "minecraft:air"


def get_block_state_index(block_tag: Tag, palette_size: int, context: str) -> int:
    block_value = expect_compound(block_tag, context)
    state_index = expect_int(get_required_tag(block_value, "state", context), f"{context}.state")
    if state_index < 0 or state_index >= palette_size:
        raise NbtFormatError(f"{context}.state index {state_index} is outside the palette range 0..{palette_size - 1}")

    return state_index


def set_block_state_index(block_tag: Tag, state_index: int) -> None:
    block_value = expect_compound(block_tag, "block")
    block_value["state"] = make_int_tag(state_index)


def get_block_position(block_tag: Tag, context: str) -> tuple[int, int, int]:
    block_value = expect_compound(block_tag, context)
    position_payload = expect_list_payload(get_required_tag(block_value, "pos", context), TAG_INT, f"{context}.pos")
    if len(position_payload.values) != 3:
        raise NbtFormatError(f"{context}.pos must contain exactly 3 integers, got {len(position_payload.values)}")

    return tuple(expect_int(tag, f"{context}.pos[{index}]") for index, tag in enumerate(position_payload.values))


def build_air_block_tag(position: tuple[int, int, int], state_index: int) -> Tag:
    return make_compound_tag(
        OrderedDict(
            [
                ("pos", make_int_list_tag(position)),
                ("state", make_int_tag(state_index)),
            ]
        )
    )


def rebuild_palette(original_palette: list[Tag], used_state_ids: set[int], appended_entries: list[Tag]) -> tuple[list[Tag], dict[int, int]]:
    new_palette: list[Tag] = []
    remapped_state_ids: dict[int, int] = {}

    for state_index, palette_entry in enumerate(original_palette):
        if state_index not in used_state_ids:
            continue

        remapped_state_ids[state_index] = len(new_palette)
        new_palette.append(palette_entry)

    seen_serialized_entries = {serialize_tag_payload(entry) for entry in new_palette}
    for palette_entry in appended_entries:
        serialized_entry = serialize_tag_payload(palette_entry)
        if serialized_entry in seen_serialized_entries:
            continue

        seen_serialized_entries.add(serialized_entry)
        new_palette.append(palette_entry)

    return new_palette, remapped_state_ids


def ensure_position_within_bounds(position: tuple[int, int, int], size: tuple[int, int, int], context: str) -> None:
    if position[0] < 0 or position[0] >= size[0]:
        raise NbtFormatError(f"{context} x coordinate {position[0]} is outside 0..{size[0] - 1}")

    if position[1] < 0 or position[1] >= size[1]:
        raise NbtFormatError(f"{context} y coordinate {position[1]} is outside 0..{size[1] - 1}")

    if position[2] < 0 or position[2] >= size[2]:
        raise NbtFormatError(f"{context} z coordinate {position[2]} is outside 0..{size[2] - 1}")


def strip_air_blocks(root_tag: Tag) -> RewriteStats:
    root_value, size, palette_entries, block_entries = get_structure_parts(root_tag)
    air_state_ids = {index for index, palette_entry in enumerate(palette_entries) if is_air_palette_entry(palette_entry)}

    kept_blocks: list[Tag] = []
    used_state_ids: set[int] = set()
    removed_air_blocks = 0

    for block_index, block_tag in enumerate(block_entries):
        context = f"root.blocks[{block_index}]"
        position = get_block_position(block_tag, context)
        ensure_position_within_bounds(position, size, context)

        state_index = get_block_state_index(block_tag, len(palette_entries), context)
        if state_index in air_state_ids:
            removed_air_blocks += 1
            continue

        kept_blocks.append(block_tag)
        used_state_ids.add(state_index)

    new_palette, remapped_state_ids = rebuild_palette(palette_entries, used_state_ids, [])
    for block_index, block_tag in enumerate(kept_blocks):
        old_state_index = get_block_state_index(block_tag, len(palette_entries), f"kept blocks[{block_index}]")
        set_block_state_index(block_tag, remapped_state_ids[old_state_index])

    root_value["palette"] = make_list_tag(TAG_COMPOUND, new_palette)
    root_value["blocks"] = make_list_tag(TAG_COMPOUND, kept_blocks)

    return RewriteStats(
        input_blocks=len(block_entries),
        output_blocks=len(kept_blocks),
        input_palette=len(palette_entries),
        output_palette=len(new_palette),
        air_removed=removed_air_blocks,
        air_added=0,
    )


class AirRetentionMask:
    AXIS_X = 0
    AXIS_Z = 1

    def __init__(self, size: tuple[int, int, int]) -> None:
        self.width, self.height, self.depth = size
        self.halo_width = self.width + 2
        self.halo_height = self.height + 2
        self.halo_depth = self.depth + 2
        self.non_air = bytearray(self.width * self.height * self.depth)
        self.x_lines = [0] * (self.height * self.depth)
        self.y_lines = [0] * (self.width * self.depth)
        self.z_lines = [0] * (self.width * self.height)
        self.outside = bytearray(self.halo_width * self.halo_height * self.halo_depth)
        self.entrances: dict[int, bool] = {}
        self.plane_tags: dict[int, int] = {}
        self.next_plane_tag = 1

    def include(self, position: tuple[int, int, int]) -> None:
        x_position, y_position, z_position = position
        self.non_air[self.get_index(x_position, y_position, z_position)] = 1
        self.x_lines[y_position * self.depth + z_position] |= 1 << x_position
        self.y_lines[x_position * self.depth + z_position] |= 1 << y_position
        self.z_lines[x_position * self.height + y_position] |= 1 << z_position

    def should_keep(self, index: int) -> bool:
        x_position, y_position, z_position = self.get_position(index)
        return not self.is_outside(x_position, y_position, z_position)

    def classify_outside(self) -> None:
        queue = [self.get_halo_index(-1, -1, -1)]
        self.outside[queue[0]] = 1
        head = 0

        while head < len(queue):
            index = queue[head]
            head += 1
            x_position = index % self.halo_width - 1
            plane_index = index // self.halo_width
            z_position = plane_index % self.halo_depth - 1
            y_position = plane_index // self.halo_depth - 1

            self.enqueue_outside(queue, x_position, y_position, z_position, x_position - 1, y_position, z_position)
            self.enqueue_outside(queue, x_position, y_position, z_position, x_position + 1, y_position, z_position)
            self.enqueue_outside(queue, x_position, y_position, z_position, x_position, y_position - 1, z_position)
            self.enqueue_outside(queue, x_position, y_position, z_position, x_position, y_position + 1, z_position)
            self.enqueue_outside(queue, x_position, y_position, z_position, x_position, y_position, z_position - 1)
            self.enqueue_outside(queue, x_position, y_position, z_position, x_position, y_position, z_position + 1)

    def enqueue_outside(
        self,
        queue: list[int],
        x_position: int,
        y_position: int,
        z_position: int,
        neighbor_x: int,
        neighbor_y: int,
        neighbor_z: int,
    ) -> None:
        if not self.is_inside_halo(neighbor_x, neighbor_y, neighbor_z):
            return

        neighbor_index = self.get_halo_index(neighbor_x, neighbor_y, neighbor_z)
        if self.outside[neighbor_index] or not self.is_air(neighbor_x, neighbor_y, neighbor_z):
            return

        if not self.can_traverse(x_position, y_position, z_position, neighbor_x, neighbor_y, neighbor_z):
            return

        self.outside[neighbor_index] = 1
        queue.append(neighbor_index)

    def can_traverse(
        self,
        x_position: int,
        y_position: int,
        z_position: int,
        neighbor_x: int,
        neighbor_y: int,
        neighbor_z: int,
    ) -> bool:
        if x_position != neighbor_x:
            return self.can_traverse_entrance(
                x_position, y_position, z_position, neighbor_x, neighbor_y, neighbor_z, self.AXIS_X
            )

        if z_position != neighbor_z:
            return self.can_traverse_entrance(
                x_position, y_position, z_position, neighbor_x, neighbor_y, neighbor_z, self.AXIS_Z
            )

        return True

    def can_traverse_entrance(
        self,
        x_position: int,
        y_position: int,
        z_position: int,
        neighbor_x: int,
        neighbor_y: int,
        neighbor_z: int,
        axis: int,
    ) -> bool:
        direction = neighbor_x - x_position if axis == self.AXIS_X else neighbor_z - z_position
        if self.is_inside(neighbor_x, neighbor_y, neighbor_z) and \
           self.is_entrance(neighbor_x, neighbor_y, neighbor_z, axis):
            far_x = neighbor_x + direction if axis == self.AXIS_X else neighbor_x
            far_z = neighbor_z + direction if axis == self.AXIS_Z else neighbor_z
            if not self.is_outside(far_x, neighbor_y, far_z):
                return False

        if self.is_inside(x_position, y_position, z_position) and \
           self.is_entrance(x_position, y_position, z_position, axis):
            far_x = x_position - direction if axis == self.AXIS_X else x_position
            far_z = z_position - direction if axis == self.AXIS_Z else z_position
            if not self.is_outside(far_x, y_position, far_z):
                return False

        return True

    def is_entrance(self, x_position: int, y_position: int, z_position: int, axis: int) -> bool:
        key = self.get_plane_key(x_position, y_position, z_position, axis)
        entrance = self.entrances.get(key)
        if entrance is not None:
            return entrance

        if not self.has_air_on_both_sides(x_position, y_position, z_position, axis):
            self.entrances[key] = False
            return False

        if not self.has_connected_frame(x_position, y_position, z_position, axis):
            self.entrances[key] = False
            return False

        return self.tag_entrance(x_position, y_position, z_position, axis)

    def has_air_on_both_sides(self, x_position: int, y_position: int, z_position: int, axis: int) -> bool:
        if axis == self.AXIS_X:
            return self.is_air(x_position - 1, y_position, z_position) and \
                   self.is_air(x_position + 1, y_position, z_position)

        return self.is_air(x_position, y_position, z_position - 1) and \
               self.is_air(x_position, y_position, z_position + 1)

    def has_connected_frame(self, x_position: int, y_position: int, z_position: int, axis: int) -> bool:
        lower_y = self.find_previous_non_air_y(x_position, y_position, z_position)
        upper_y = self.find_next_non_air_y(x_position, y_position, z_position)
        if lower_y < 0 or upper_y < 0:
            return False

        if axis == self.AXIS_X:
            first = self.find_previous_non_air_z(x_position, y_position, z_position)
            second = self.find_next_non_air_z(x_position, y_position, z_position)
        else:
            first = self.find_previous_non_air_x(x_position, y_position, z_position)
            second = self.find_next_non_air_x(x_position, y_position, z_position)

        if first < 0 or second < 0:
            return False

        tag = self.get_plane_tag(x_position, lower_y, z_position, axis)
        if tag != self.get_plane_tag(x_position, upper_y, z_position, axis):
            return False

        first_x = x_position if axis == self.AXIS_X else first
        first_z = first if axis == self.AXIS_X else z_position
        if tag != self.get_plane_tag(first_x, y_position, first_z, axis):
            return False

        second_x = x_position if axis == self.AXIS_X else second
        second_z = second if axis == self.AXIS_X else z_position
        return tag == self.get_plane_tag(second_x, y_position, second_z, axis)

    def tag_entrance(self, x_position: int, y_position: int, z_position: int, axis: int) -> bool:
        queue = [self.get_index(x_position, y_position, z_position)]
        self.entrances[self.get_plane_key(x_position, y_position, z_position, axis)] = True
        reaches_boundary = False
        head = 0

        while head < len(queue):
            index = queue[head]
            head += 1
            current_x, current_y, current_z = self.get_position(index)
            reaches_boundary |= self.is_plane_boundary(current_x, current_y, current_z, axis)

            if axis == self.AXIS_X:
                self.add_entrance_position(queue, current_x, current_y - 1, current_z, axis)
                self.add_entrance_position(queue, current_x, current_y + 1, current_z, axis)
                self.add_entrance_position(queue, current_x, current_y, current_z - 1, axis)
                self.add_entrance_position(queue, current_x, current_y, current_z + 1, axis)
            else:
                self.add_entrance_position(queue, current_x - 1, current_y, current_z, axis)
                self.add_entrance_position(queue, current_x + 1, current_y, current_z, axis)
                self.add_entrance_position(queue, current_x, current_y - 1, current_z, axis)
                self.add_entrance_position(queue, current_x, current_y + 1, current_z, axis)

        if not reaches_boundary:
            return True

        for index in queue:
            self.entrances[self.get_plane_key_for_index(index, axis)] = False

        return False

    def add_entrance_position(
        self, queue: list[int], x_position: int, y_position: int, z_position: int, axis: int
    ) -> None:
        if not self.is_inside(x_position, y_position, z_position) or \
           self.is_non_air(x_position, y_position, z_position):
            return

        key = self.get_plane_key(x_position, y_position, z_position, axis)
        if self.entrances.get(key) is True:
            return

        self.entrances[key] = True
        queue.append(self.get_index(x_position, y_position, z_position))

    def is_plane_boundary(self, x_position: int, y_position: int, z_position: int, axis: int) -> bool:
        if axis == self.AXIS_X:
            return y_position in (0, self.height - 1) or z_position in (0, self.depth - 1)

        return x_position in (0, self.width - 1) or y_position in (0, self.height - 1)

    def get_plane_tag(self, x_position: int, y_position: int, z_position: int, axis: int) -> int:
        key = self.get_plane_key(x_position, y_position, z_position, axis)
        tag = self.plane_tags.get(key)
        if tag is not None:
            return tag

        tag = self.next_plane_tag
        self.next_plane_tag += 1
        self.plane_tags[key] = tag
        queue = [self.get_index(x_position, y_position, z_position)]
        head = 0

        while head < len(queue):
            index = queue[head]
            head += 1
            current_x, current_y, current_z = self.get_position(index)

            if axis == self.AXIS_X:
                self.add_plane_position(queue, current_x, current_y - 1, current_z, axis, tag)
                self.add_plane_position(queue, current_x, current_y + 1, current_z, axis, tag)
                self.add_plane_position(queue, current_x, current_y, current_z - 1, axis, tag)
                self.add_plane_position(queue, current_x, current_y, current_z + 1, axis, tag)
            else:
                self.add_plane_position(queue, current_x - 1, current_y, current_z, axis, tag)
                self.add_plane_position(queue, current_x + 1, current_y, current_z, axis, tag)
                self.add_plane_position(queue, current_x, current_y - 1, current_z, axis, tag)
                self.add_plane_position(queue, current_x, current_y + 1, current_z, axis, tag)

        return tag

    def add_plane_position(
        self, queue: list[int], x_position: int, y_position: int, z_position: int, axis: int, tag: int
    ) -> None:
        if not self.is_inside(x_position, y_position, z_position) or \
           not self.is_non_air(x_position, y_position, z_position):
            return

        key = self.get_plane_key(x_position, y_position, z_position, axis)
        if key in self.plane_tags:
            return

        self.plane_tags[key] = tag
        queue.append(self.get_index(x_position, y_position, z_position))

    def find_previous_non_air_x(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_previous_non_air(self.x_lines[y_position * self.depth + z_position], x_position)

    def find_next_non_air_x(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_next_non_air(self.x_lines[y_position * self.depth + z_position], x_position)

    def find_previous_non_air_y(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_previous_non_air(self.y_lines[x_position * self.depth + z_position], y_position)

    def find_next_non_air_y(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_next_non_air(self.y_lines[x_position * self.depth + z_position], y_position)

    def find_previous_non_air_z(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_previous_non_air(self.z_lines[x_position * self.height + y_position], z_position)

    def find_next_non_air_z(self, x_position: int, y_position: int, z_position: int) -> int:
        return self.find_next_non_air(self.z_lines[x_position * self.height + y_position], z_position)

    @staticmethod
    def find_previous_non_air(bits: int, position: int) -> int:
        before = bits & ((1 << position) - 1)
        return before.bit_length() - 1 if before else -1

    @staticmethod
    def find_next_non_air(bits: int, position: int) -> int:
        after = bits >> (position + 1)
        return position + (after & -after).bit_length() if after else -1

    def is_air(self, x_position: int, y_position: int, z_position: int) -> bool:
        return self.is_inside_halo(x_position, y_position, z_position) and (
            not self.is_inside(x_position, y_position, z_position)
            or not self.is_non_air(x_position, y_position, z_position)
        )

    def is_non_air(self, x_position: int, y_position: int, z_position: int) -> bool:
        return bool(self.non_air[self.get_index(x_position, y_position, z_position)])

    def is_outside(self, x_position: int, y_position: int, z_position: int) -> bool:
        return self.is_inside_halo(x_position, y_position, z_position) and \
            bool(self.outside[self.get_halo_index(x_position, y_position, z_position)])

    def is_inside(self, x_position: int, y_position: int, z_position: int) -> bool:
        return 0 <= x_position < self.width and 0 <= y_position < self.height and 0 <= z_position < self.depth

    def is_inside_halo(self, x_position: int, y_position: int, z_position: int) -> bool:
        return -1 <= x_position <= self.width and -1 <= y_position <= self.height and -1 <= z_position <= self.depth

    def get_index(self, x_position: int, y_position: int, z_position: int) -> int:
        return x_position + self.width * (z_position + self.depth * y_position)

    def get_position(self, index: int) -> tuple[int, int, int]:
        x_position = index % self.width
        plane_index = index // self.width
        z_position = plane_index % self.depth
        y_position = plane_index // self.depth

        return x_position, y_position, z_position

    def get_halo_index(self, x_position: int, y_position: int, z_position: int) -> int:
        return x_position + 1 + self.halo_width * (z_position + 1 + self.halo_depth * (y_position + 1))

    def get_plane_key(self, x_position: int, y_position: int, z_position: int, axis: int) -> int:
        return self.get_plane_key_for_index(self.get_index(x_position, y_position, z_position), axis)

    @staticmethod
    def get_plane_key_for_index(index: int, axis: int) -> int:
        return index * 2 + axis


def restore_air_blocks(root_tag: Tag) -> RewriteStats:
    root_value, size, palette_entries, block_entries = get_structure_parts(root_tag)

    non_air_blocks: list[Tag] = []
    air_retention_mask = AirRetentionMask(size)
    used_state_ids: set[int] = set()
    existing_air_palette_entry: Tag | None = None
    removed_air_blocks = 0

    for palette_entry in palette_entries:
        if is_air_palette_entry(palette_entry):
            existing_air_palette_entry = palette_entry
            break

    for block_index, block_tag in enumerate(block_entries):
        context = f"root.blocks[{block_index}]"
        position = get_block_position(block_tag, context)
        ensure_position_within_bounds(position, size, context)

        state_index = get_block_state_index(block_tag, len(palette_entries), context)
        if is_air_palette_entry(palette_entries[state_index]):
            removed_air_blocks += 1
            continue

        non_air_blocks.append(block_tag)
        air_retention_mask.include(position)
        used_state_ids.add(state_index)

    air_retention_mask.classify_outside()
    added_air_indices: list[int] = []

    # Air is restored with the same hidden-space rule as StructureCaptureService.
    for index, is_non_air in enumerate(air_retention_mask.non_air):
        if not is_non_air and air_retention_mask.should_keep(index):
            added_air_indices.append(index)

    air_palette_entry = existing_air_palette_entry if existing_air_palette_entry is not None else default_air_palette_entry()
    appended_entries = [air_palette_entry] if added_air_indices else []
    new_palette, remapped_state_ids = rebuild_palette(palette_entries, used_state_ids, appended_entries)

    for block_index, block_tag in enumerate(non_air_blocks):
        old_state_index = get_block_state_index(block_tag, len(palette_entries), f"non-air blocks[{block_index}]")
        set_block_state_index(block_tag, remapped_state_ids[old_state_index])

    new_blocks = list(non_air_blocks)
    if added_air_indices:
        air_state_index = len(new_palette) - 1
        for index in added_air_indices:
            new_blocks.append(build_air_block_tag(air_retention_mask.get_position(index), air_state_index))

    root_value["palette"] = make_list_tag(TAG_COMPOUND, new_palette)
    root_value["blocks"] = make_list_tag(TAG_COMPOUND, new_blocks)

    return RewriteStats(
        input_blocks=len(block_entries),
        output_blocks=len(new_blocks),
        input_palette=len(palette_entries),
        output_palette=len(new_palette),
        air_removed=removed_air_blocks,
        air_added=len(added_air_indices),
    )


def resolve_output_path(command: str, input_path: Path, inplace: bool) -> Path:
    if inplace:
        return input_path

    if command == "strip":
        return input_path.with_name(f"{input_path.stem}.stripped{input_path.suffix}")

    if command == "restore":
        return input_path.with_name(f"{input_path.stem}.restored{input_path.suffix}")

    raise NbtFormatError(f"Unsupported command: {command}")


def format_scaled_value(value: int, suffixes: tuple[str, ...]) -> str:
    scaled_value = float(value)
    suffix_index = 0

    while scaled_value >= 999.5 and suffix_index < len(suffixes) - 1:
        scaled_value /= 1000.0
        suffix_index += 1

    if suffix_index == 0:
        return f"{int(scaled_value)}{suffixes[suffix_index]}"

    if scaled_value >= 100:
        text = f"{scaled_value:.0f}"
    elif scaled_value >= 10:
        text = f"{scaled_value:.1f}".rstrip("0").rstrip(".")
    else:
        text = f"{scaled_value:.2f}".rstrip("0").rstrip(".")

    return f"{text}{suffixes[suffix_index]}"


def format_count(value: int) -> str:
    return format_scaled_value(value, COUNT_SUFFIXES)


def format_file_size(value: int) -> str:
    return format_scaled_value(value, FILE_SIZE_SUFFIXES)


def format_signed_count_delta(value: int) -> str:
    if value == 0:
        return "0"

    sign = "+" if value > 0 else "-"
    return f"{sign}{format_count(abs(value))}"


def format_signed_file_size_delta(value: int) -> str:
    if value == 0:
        return "0B"

    sign = "+" if value > 0 else "-"
    return f"{sign}{format_file_size(abs(value))}"


def print_processed_entry(
    input_path: Path,
    stats: RewriteStats,
    input_size: int,
    output_size: int,
    additional_log_line: str,
) -> None:
    size_delta = output_size / input_size if input_size > 0 else 0
    percentage_change = f"{str(round(size_delta * 100))}%" if size_delta > 0 else "N/A"

    print(f"Processed {input_path} :")
    print(
        f"- Total blocks: {format_count(stats.input_blocks)} -> {format_count(stats.output_blocks)} "
        f"({format_signed_count_delta(stats.output_blocks - stats.input_blocks)})"
    )
    print(
        f"- File size: {format_file_size(input_size)} -> {format_file_size(output_size)} "
        f"({format_signed_file_size_delta(output_size - input_size)}, {percentage_change})"
    )

    if additional_log_line:
        print(additional_log_line)

    print()


def print_error_entry(input_path: Path, error_message: str) -> None:
    print(f"Processed {input_path} :")
    print(f"Error: {error_message}")
    print()


def expand_input_path(input_path: Path) -> list[Path]:
    if not input_path.is_dir():
        return [input_path]

    collected_paths: list[Path] = []
    for candidate_path in input_path.rglob("*"):
        if not candidate_path.is_file():
            continue

        if candidate_path.suffix.lower() != ".nbt":
            continue

        collected_paths.append(candidate_path)

    collected_paths.sort(key=lambda path: str(path))
    return collected_paths


def write_output_file(input_path: Path, output_path: Path, output_bytes: bytes, dry_run: bool, force: bool) -> str:
    if dry_run:
        return f"File not written because of --dry: {output_path.name}"

    if output_path != input_path and output_path.exists() and not force:
        return f"File already exists (use -f/--force): {output_path.name}"

    write_nbt_file_atomically(output_path, output_bytes)
    return f"Wrote: {output_path.name}"


def process_file(command: str, input_path: Path, dry_run: bool, force: bool, inplace: bool) -> None:
    if not input_path.is_file():
        raise NbtFormatError(f"Input file does not exist: {input_path}")

    input_size = input_path.stat().st_size
    root_name, root_tag = read_nbt_file(input_path)
    stats = strip_air_blocks(root_tag) if command == "strip" else restore_air_blocks(root_tag)

    output_path = resolve_output_path(command, input_path, inplace)
    output_bytes = build_nbt_file_bytes(root_name, root_tag)
    additional_log_line = write_output_file(input_path, output_path, output_bytes, dry_run, force)
    print_processed_entry(input_path, stats, input_size, len(output_bytes), additional_log_line)


def run_command(command: str, inputs: list[Path], dry_run: bool, force: bool, inplace: bool) -> int:
    had_error = False
    processed_any_file = False

    for input_path in inputs:
        try:
            expanded_paths = expand_input_path(input_path)
        except OSError as e:
            had_error = True
            print_error_entry(input_path, str(e))
            continue

        for expanded_path in expanded_paths:
            processed_any_file = True
            try:
                process_file(command, expanded_path, dry_run, force, inplace)
            except (OSError, NbtFormatError) as e:
                had_error = True
                print_error_entry(expanded_path, str(e))

    if not processed_any_file:
        raise NbtFormatError("No .nbt files found in the provided inputs")

    return 1 if had_error else 0


def main() -> int:
    args = parser.parse_args()

    return run_command(args.command, args.inputs, args.dry, args.force, args.inplace)


if __name__ == "__main__":
    raise SystemExit(main())
