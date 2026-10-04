# Annotated hexdump — GET /hello.txt -> 200 (one request + response)

Captured with `bcurl -v` (stderr). Body (18B `hello from bserve\n`) went to stdout. Date bytes vary per run; structure does not.

## Frame 1: REQUEST (client -> server, 9 + 64 = 73 bytes)

```
-> Frame{type=0x01 flags=0x03 id=1 len=64} REQUEST
0000  00 00 40 01 03 00 00 00  01 01 00 0a 2f 68 65 6c  ..@........./hel
0010  6c 6f 2e 74 78 74 00 04  07 00 0f 31 32 37 2e 30  lo.txt.....127.0
0020  2e 30 2e 31 3a 31 39 30  30 31 08 00 09 62 63 75  .0.1:19001...bcu
0030  72 6c 2f 31 2e 30 09 00  03 2a 2f 2a 0a 00 0a 6b  rl/1.0...*/*...k
0040  65 65 70 2d 61 6c 69 76  65                       eep-alive
  Length=64 Type=0x01 REQUEST Flags=0x03 ID=1
```

| Bytes | Value | Meaning |
|---|---|---|
| `00 00 40` | 64 | Length: payload = 64 bytes (24-bit BE) |
| `01` | 0x01 | Type: REQUEST |
| `03` | END_STREAM\|END_HEADERS | Flags: request complete, no body |
| `00 00 00 01` | 1 | Request-ID 1 (top bit 0, odd client ID) |
| `01` | GET | method 0x01 |
| `00 0a` | 10 | pathLen |
| `2f 68 65 6c 6c 6f 2e 74 78 74` | `/hello.txt` | UTF-8 path |
| `00 04` | 4 | numHeaders |
| `07 00 0f ...` | host=`127.0.0.1:19001` | indexed 0x07 + vlen 15 + value |
| `08 00 09 ...` | user-agent=`bcurl/1.0` | indexed 0x08 + vlen 9 |
| `09 00 03 2a 2f 2a` | accept=`*/*` | indexed 0x09 + vlen 3 |
| `0a 00 0a 6b...65` | connection=`keep-alive` | indexed 0x0A + vlen 10 |

Total: 9 + (1+2+10+2) + (1+2+15)+(1+2+9)+(1+2+3)+(1+2+10) = 9+64 = 73 bytes. 73/73 annotated.

## Frame 2: RESPONSE (server -> client, 9 + 81 = 90 bytes)

```
<- Frame{type=0x02 flags=0x00 id=1 len=81} RESPONSE
0000  00 00 51 02 00 00 00 00  01 00 c8 00 04 03 00 19  ..Q.............
0010  74 65 78 74 2f 70 6c 61  69 6e 3b 20 63 68 61 72  text/plain; char
0020  73 65 74 3d 75 74 66 2d  38 04 00 02 31 38 05 00  set=utf-8...18..
0030  1c 53 75 6e 2c 20 34 20  4f 63 74 20 32 30 32 36  .Sun, 4 Oct 2026
0040  20 31 35 3a 30 33 3a 31  35 20 47 4d 54 06 00 0a   15:03:15 GMT...
0050  62 73 65 72 76 65 2f 31  2e 30                    bserve/1.0
  Length=81 Type=0x02 RESPONSE Flags=0x00 ID=1
```

| Bytes | Value | Meaning |
|---|---|---|
| `00 00 51` | 81 | Length 81 |
| `02` | 0x02 | Type: RESPONSE |
| `00` | 0x00 | Flags: END_STREAM=0 -> DATA follows |
| `00 00 00 01` | 1 | echo Request-ID 1 |
| `00 c8` | 200 | status |
| `00 04` | 4 | numHeaders |
| `03 00 19 ...` | content-type=`text/plain; charset=utf-8` (25B) | indexed 0x03 |
| `04 00 02 31 38` | content-length=`18` | indexed 0x04 |
| `05 00 1c ...` | date=`Sun, 4 Oct 2026 ... GMT` (28B, varies) | indexed 0x05 |
| `06 00 0a 6273657276652f312e30` | server=`bserve/1.0` | indexed 0x06 |

9 + (2+2) + (1+2+25)+(1+2+2)+(1+2+28)+(1+2+10) = 9+81 = 90 bytes. 90/90 annotated.

## Frame 3: DATA (server -> client, 9 + 18 = 27 bytes)

```
<- Frame{type=0x03 flags=0x01 id=1 len=18} DATA
0000  00 00 12 03 01 00 00 00  01 68 65 6c 6c 6f 20 66  .........hello f
0010  72 6f 6d 20 62 73 65 72  76 65 0a                 rom bserve.
  Length=18 Type=0x03 DATA Flags=0x01 ID=1
```

| Bytes | Value | Meaning |
|---|---|---|
| `00 00 12` | 18 | Length = file size 18 |
| `03` | 0x03 | Type: DATA |
| `01` | END_STREAM | last (only) chunk |
| `00 00 00 01` | 1 | Request-ID 1 |
| `68 65 6c ... 0a` | `hello from bserve\n` | 18 opaque file bytes -> stdout |

Exchange total: 73 + 90 + 27 = 190 bytes. Every byte annotated. Unknown-type rule exercised separately in UnknownFrameSkipTest (0x9F skipped by Length, connection stays open).
