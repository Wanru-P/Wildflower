"""Local Android Emulator gRPC controls from the SDK's emulator_controller.proto.

Use only on the dedicated Phase6A AVD. Supply its ephemeral token via the process
environment WF_EMULATOR_TOKEN; nothing prints or persists that token.
"""
import argparse
import os
import struct
import time
import grpc


def varint(value):
    encoded = bytearray()
    while value > 127:
        encoded.append((value & 127) | 128)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def touch(x, y, identifier, pressure):
    body = b"".join(varint(field << 3) + varint(value) for field, value in
                    [(1, x), (2, y), (3, identifier), (4, pressure)])
    return b"\x0a" + varint(len(body)) + body


parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=["pose", "pinch"])
parser.add_argument("--width", type=int, default=1080)
parser.add_argument("--height", type=int, default=2400)
args = parser.parse_args()
channel = grpc.insecure_channel("127.0.0.1:8554")
metadata = [("authorization", "Bearer " + os.environ["WF_EMULATOR_TOKEN"])]


def call(method, request):
    return channel.unary_unary("/android.emulation.control.EmulatorController/" + method)(
        request, metadata=metadata, timeout=3)


if args.mode == "pose":
    call("setPhysicalModel", b"\x1a\x0e\x0a\x0c" + struct.pack("<fff", -.807, .32, 3.7))
    call("setPhysicalModel", b"\x08\x01\x1a\x0e\x0a\x0c" + struct.pack("<fff", 0, 180, 0))
    print("Applied virtual-scene pose toward the licensed ginkgo poster (not physical field photography).")
else:
    y = int(args.height * .40)
    x0, x1 = int(args.width * .4), int(args.width * .6)
    try:
        for step in range(11):
            delta = int(args.width * .01 * step)
            x0, x1 = int(args.width * .4) - delta, int(args.width * .6) + delta
            call("sendTouch", touch(x0, y, 1, 1) + touch(x1, y, 2, 1))
            time.sleep(.035)
    finally:
        call("sendTouch", touch(x0, y, 1, 0) + touch(x1, y, 2, 0))
    print("Delivered a real two-finger pinch to the emulator input device.")
