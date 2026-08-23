# Generator V6.1 Auto Journey host benchmark

This is a bounded host-JVM diagnostic, not a device thermal, ANR, or UI benchmark.

- Easy (4x4): CERTIFIED, wall 133 ms, thread CPU 130 ms, heap delta 8273480 bytes.
- Medium (5x5): CERTIFIED, wall 4111 ms, thread CPU 4063 ms, heap delta 173296488 bytes.
- Hard (6x6): CERTIFIED, wall 84 ms, thread CPU 82 ms, heap delta 4139232 bytes.
- Super Hard (7x7): CERTIFIED, wall 565 ms, thread CPU 553 ms, heap delta 116814464 bytes.
- Expert (8x8): CERTIFIED, wall 2314 ms, thread CPU 2259 ms, heap delta 96742136 bytes.

Runtime orchestration uses `Dispatchers.Default`; the app test asserts benchmark work is not on the main thread.
Thermal impact and rendered UI responsiveness require a supported physical/emulated device and were not measured here.
