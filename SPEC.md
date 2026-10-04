# BHTTP/1.0 — Binary HTTP Spec (2 pages)

## 1. Transport & framing
TCP, one persistent connection. All ints unsigned big-endian. Frame = 9-byte header + `Length` payload bytes. `readFully(9)` then `readFully(Length)`; partial reads are normal. `MAX_FRAME = 16,777,215` (2^24-1); larger = framing error -> close. `SO_TIMEOUT` server 60s idle, client connect 5s / read 10s.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 |                      Payload Length (24)                      |
 +---------------+---------------+-------------------------------+
 |   Type (8)    |   Flags (8)   |R|        Request-ID (31)        |
 +---------------+---------------+-------------------------------+
 |               Payload (Length bytes) ...
```

| Field | Width | Rule / defense |
|---|---|---|
| Length | 24 | Payload-only length. 16-bit fragments images; 32-bit invites 4GiB DoS. 24 bounds memory yet fits small files. Drives unknown-skip. |
| Type | 8 | `0x01 REQUEST, 0x02 RESPONSE, 0x03 DATA`. 256 values: 3 used, 253 for v2. **A receiver meeting a frame type it does not know MUST skip it cleanly**: read+discard exactly `Length` bytes, ignore flags/ID, continue, MUST NOT close. |
| Flags | 8 | Per-type. `END_STREAM=0x01, END_HEADERS=0x02`. Reserved bits MUST be 0 on send, ignored on receive. |
| R+ID | 1+31 | Top bit MUST be 0 (keeps Java int >= 0). ID 0 illegal in v1. Client uses odd `1,3,5...`, server echoes. Enables mismatch detect + future multiplex without header change. |

## 2. Frame payloads
**REQUEST (0x01):** `method:u8 + pathLen:u16 + path:UTF-8 + numHeaders:u16 + headerBlock`. `method`: `0x01 GET, 0x02 HEAD`, else 400. `path`: MUST start `/`, no NUL, <=2048B. Flags MUST be `END_STREAM|END_HEADERS (0x03)` in v1 (no request body).

**RESPONSE (0x02):** `status:u16 + numHeaders:u16 + headerBlock`. `status` 100..599. `END_STREAM=1` -> no body; `0` -> >=1 DATA follows.

**DATA (0x03):** opaque file bytes, `Length` bytes. `END_STREAM=1` on last chunk else `0`. Empty file / HEAD: RESPONSE with `END_STREAM=1`, zero DATA frames.

## 3. Headers (HPACK 1+2)
Static table: `0x01 :path 0x02 :status(reserved) 0x03 content-type 0x04 content-length 0x05 date 0x06 server 0x07 host 0x08 user-agent 0x09 accept 0x0A connection`. Per entry first byte = discriminator: `0x01-0x0A` -> `index:u8 + valueLen:u16 + value`; `0x00` -> `0x00 + nameLen:u16 + name + valueLen:u16 + value` (name lowercase `[a-z0-9-]`). Other -> 400. Limits: `<=64 headers, name <=256B, value <=8KiB, block <=64KiB`, else 400. No Huffman/dynamic table in v1.

## 4. Mapping & status
Strip `#frag` then `?query`, percent-decode (`%XX`, bad -> 400), `/` -> `/index.html`, trailing `/` -> `+index.html`, `normalize`, MUST stay under root else **400** (attack, not 404). Symlink escaping root -> 400. Regular file -> `200 + content-type + content-length + date(IMF) + server:bserve/1.0` then DATA 16KiB chunks. Missing/dir-without-index -> `404` with `Not Found` body. `HEAD` -> headers (`content-length`=size), no DATA. MIME: html/htm `text/html`, css `text/css`, js `text/javascript`, json `application/json`, txt `text/plain`, png/jpeg/gif/svg image/*, else `application/octet-stream`. I/O failure mid-response -> `500` then close.

## 5. Lifecycle
Server: `bserve <root> <port>`; accept loop, per-conn loop `read->dispatch->RESPONSE[+DATA]->flush->repeat`. Semantic errors (bad path/method/headers) -> `4xx RESPONSE` and **keep open**. Fatal framing (truncated header/payload, `Length>MAX`, `ID==0`, reserved-bit set) -> try `400` if boundary known else TCP close, no rescan. Client: `bcurl [-v] host:port/path`; exactly ONE Socket; `REQUEST id=1` (`host,user-agent:bcurl/1.0,accept:*/*,connection:keep-alive`); read until same-ID `END_STREAM=1`; `DATA` -> stdout raw; `-v` every frame -> stderr; exit `0` 2xx/3xx, `1` 4xx/5xx, `2` transport/usage. `DATA` before `RESPONSE` or ID mismatch -> transport error.
