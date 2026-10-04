# NA Project — Binary HTTP (bserve / bcurl) — Implementation Plan (Java)
Workspace: `/Users/akshatsipany/SST/na-project` | Stack: Java 17+ (tested 22), Maven, plain `java.net` sockets, JUnit 5

## 1. Requirements distilled from slide

### Track 1 — server `./bserve ./www 9000`
- Accept TCP connection(s).
- Read ONE binary request frame, map path -> file under root.
- Reply: status + headers + bytes. 404 if missing, 400 if malformed.
- KEY: `keep the connection open` — loop serving sequential requests on same socket. Close only on client close / fatal framing desync / idle timeout (e.g. 60s).

### Track 2 — client `./bcurl -v localhost:9000/index.html`
- Build binary request frame. Read response, body -> stdout (binary-safe).
- `-v` hexdumps EVERY frame (header+payload) to stderr.
- Exit non-zero on 4xx/5xx.
- KEY: `never open a second connection` — exactly one Socket per invocation; read until END_STREAM.

### The middle bit — the actual project (your spec)
1. Fixed-size frame header — YOU pick fields+widths and defend them. HTTP/2 chose 24/8/8/31. Why?
2. Headers: number the 10 names you actually send, length-prefix rest (HPACK mechanisms 1+2, no Huffman/dynamic table).
3. MUST-skip line (cannot skip): `a receiver meeting a frame type it does not know MUST skip it cleanly.` -> framing must be Length-driven. This leaves room for v2.

### Hand-in
1. Spec (2 pages, enough for stranger). 2. Program. 3. Annotated hexdump of one complete request+response. Rule: if you cannot annotate own bytes, spec not finished.
Pair rule: only spec crosses between partners. Client working only vs own server = implementation, not protocol.

## 2. Architecture overview

```
 TCP (single persistent connection, big-endian / network order)
 [bcurl] --REQUEST(0x01)--> [bserve]
 [bcurl] <--RESPONSE(0x02)+DATA*(0x03)-- [bserve]
 client: stdout=body only, stderr(-v)=hexdump frames
 server: www/ root sandbox, thread-per-connection
```

Packages (Maven):
- com.bhttp: Main, BServe (bserve <root> <port>), BCurl (bcurl [-v] host:port/path)
- com.bhttp.proto: Frame, FrameType, Flags, FrameIO, HeaderTable, Request, Response, ProtocolException
- com.bhttp.server: ConnectionHandler, FileHandler, MimeTypes
- com.bhttp.client: ClientSession
- com.bhttp.util: Hexdump, IOUtil
- tests: FrameIOTest, HeaderTableTest, RequestCodecTest, FileHandlerTest, IntegrationTest, UnknownFrameSkipTest

IO model: ServerSocket.accept + virtual threads (21+) / cached pool (17).
DataInputStream.readFully(9) then readFully(Length). DataOutputStream+Buffered+flush per frame.
Streaming: RESPONSE END_STREAM=0 then DATA 16KiB chunks END_STREAM=1 last. Empty file: RESPONSE END_STREAM=1 only. HEAD: headers only.
Client: single Socket, REQUEST id=1, read till same id + END_STREAM=1. DATA payload -> System.out raw. Unknown -> skip by Length but -v dump.
## 3. Protocol design (what goes in 2-page spec + defense)

### 3.1 Frame header: 9 bytes = 24/8/8/31 (same widths as HTTP/2, defended)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 |                 Payload Length (24)           |
 +---------------+---------------+---------------+-----------------+
 |   Type (8)    |   Flags (8)   |R|     Request-ID (31)         |
 +---------------+---------------+-------------------------------+
 | Frame Payload (Length bytes) ...
```
All unsigned, big-endian. Header always 9 bytes.
- Length 24 (max 16,777,215): 16-bit forces fragmentation of images; 32-bit invites 4GiB DoS alloc. 24 fits large chunk/small file, bounds memory. Receiver enforces MAX_FRAME=16MiB, larger = framing error. Length covers payload ONLY so unknown types skipped by exactly Length bytes.
- Type 8 (256 values): v1 needs 3; 4 bits leaves no v2 room; 16 bits wastes byte/frame. 8 = 253 spare. Unknown MUST be skipped.
- Flags 8 per-type: enough for END_STREAM=0x01, END_HEADERS=0x02. Reserved bits MUST be 0 on send, ignored on receive.
- R+Request-ID 1+31: 31 bits ~2B requests/conn, never wraps. Top bit reserved MUST be 0 (keeps Java int non-negative, leaves future bit like HTTP/2). 0 illegal in v1 (connection-level in v2). Client uses odd 1,3,5.. server echoes. Even sequential keep-alive in v1, IDs catch mismatch + allow v2 multiplex without header change.
Rejected: 32/8/8/32 LE 10-byte header — LE breaks tcpdump/network-order convention, 32-bit len encourages OOM.

### 3.2 Frame types (v1 uses 3, rest MUST be skipped)
- 0x01 REQUEST: method:u8 + pathLen:u16 + path:bytes + numHeaders:u16 + headers... Flags END_HEADERS=0x02 always 1, END_STREAM=0x01 always 1 (no body in v1).
- 0x02 RESPONSE: status:u16 + numHeaders:u16 + headers... Flags END_STREAM=1 -> no body; 0 -> >=1 DATA follows.
- 0x03 DATA: opaque file bytes (Length bytes). Flags END_STREAM=1 last chunk else 0.
- others Unknown: opaque, MUST skip: read+discard Length bytes, continue, MUST NOT close, MUST ignore flags/ID except -v dump.
Methods: 0x01 GET, 0x02 HEAD. Other -> 400. Statuses v1: 200, 400 (malformed/traversal/bad method), 404, 500 (IO failure mid-response -> close after). Client treats any 4xx/5xx as error exit.

### 3.3 Header block (HPACK mechanisms 1+2 only)
Static table 0x01-0x0A (10 names actually sent):
 0x01 :path, 0x02 :status(reserved), 0x03 content-type, 0x04 content-length,
 0x05 date, 0x06 server, 0x07 host, 0x08 user-agent, 0x09 accept, 0x0A connection
Wire per entry, first byte discriminator:
- 0x01-0x0A indexed: index:u8 + valueLen:u16 + value:UTF-8
- 0x00 literal: 0x00 + nameLen:u16 + name:UTF-8 + valueLen:u16 + value:UTF-8. Name MUST be lowercase [a-z0-9-]; receiver MUST accept unknown literals.
- other discrim -> malformed -> 400.
Limits (DoS, in spec): <=64 headers, name <=256B, value <=8KiB, total block <=64KiB. Exceed -> 400.
No Huffman/dynamic table/varints in v1 — fixed u16 keeps evening scope, demos indexing + literal fallback.

### 3.4 Path->file + status rules
1. path MUST UTF-8, start with /, no NUL, <=2048B.
2. Strip ?query #frag; percent-decode %XX; bad % -> 400.
3. Trailing / -> append index.html. / -> /index.html.
4. Canonicalize /a/../b -> /b via Path.normalize + startsWith(root). Escape -> 400 (not 404).
5. Regular file -> 200 + content-type + content-length + date + server:bserve/1.0 -> DATA*. Dir w/o index -> 404. Missing -> 404 small text body Not Found. HEAD -> headers only, content-length=file size, no DATA.
6. MIME: .html text/html;charset=utf-8, .htm text/html, .css text/css, .js text/javascript, .json application/json, .txt text/plain;charset=utf-8, .png image/png, .jpg/.jpeg image/jpeg, .gif image/gif, .svg image/svg+xml, else application/octet-stream.

### 3.5 Connection lifecycle
Server: bind root+port, accept loop, per-conn loop read header->payload->dispatch->RESPONSE[+DATA]->repeat. SO_TIMEOUT 60s idle -> close. EOF -> close. Fatal framing (short read, Length>MAX, requestId==0, truncated header): try 400 RESPONSE if boundary known else TCP close; do NOT rescan (impossible on binary stream — document).
Client: connect timeout 5s -> send REQUEST id=1 -> read till END_STREAM id=1 -> exit. SO_TIMEOUT 10s. MUST NOT open second Socket. Single-Socket invariant asserted via server counter in tests.
Pipelining v1: NOT required (client sends one, waits). Server MUST handle back-to-back keep-alive, SHOULD handle pipelined in arrival order. Reused/out-of-order requestId -> 400.



## 4. Key components (Java responsibilities)
- Frame: record(type,flags,requestId,payload). Validate requestId top-bit 0, length==payload.length.
- FrameType/Flags: REQUEST=0x01 RESPONSE=0x02 DATA=0x03 END_STREAM=0x01 END_HEADERS=0x02, isUnknown(type).
- FrameIO: ONLY place touching wire. readFrame(DataInputStream) via readFully+MAX guard, writeFrame(DataOutputStream), skip=read+discard. Big-endian via readUnsignedByte/readInt. Handles partial TCP reads. Throws ProtocolException -> mapped to 400.
- HeaderTable: 10-entry map + encode/decode with limits.
- Request/Response: payload codecs (method:u8 pathLen:u16 status:u16 numHeaders:u16 + block). UTF-8 strict REPORT.
- BServe: main(<root> <port>), validate dir, ServerSocket, executor, shutdown hook. Exit 2 usage error.
- ConnectionHandler: while(!closed){read; UNKNOWN skip+continue; REQUEST->decode->resolve->RESPONSE(+DATA 16KiB)->flush->repeat}. ProtocolException->400 RESPONSE+continue; IOException->close. Isolate per-conn exceptions.
- FileHandler: resolve(root,rawPath) decode->strip->normalize->startsWith, index.html fallback, symlink NOFOLLOW+realpath check. serve()->Response+InputStream, mimeFor, errorBody.
- BCurl: main([-v] url), parse [http://]host:port/path, build REQUEST id=1 with host,user-agent:bcurl/1.0,accept:*/*,connection:keep-alive (uses 4 static slots). Loop read till END_STREAM. -v every frame to stderr via Hexdump (->/<-). DATA -> System.out raw. exit 0 2xx/3xx,1 4xx/5xx,2 transport/usage. Library code never System.exit (tests call ClientSession directly).
- Hexdump: -v + hand-in artifact. offset|hex 16B|ascii + per-field annotation.
- MimeTypes/IOUtil: ext map, percentDecode, readFully, IMF-fixdate GMT.

## 5. Step-by-step implementation (7 phases, each runnable/testable)
Phase 0 Scaffold (0.5d): pom.xml (Java17+, JUnit5, assembly fat jar, bserve/bcurl wrappers), skeleton, www/index.html hello.txt, Frame/FrameType/Flags/ProtocolException+MAX_FRAME. Milestone mvn compile.
Phase 1 Framing+Hexdump (1d, highest risk): FrameIO.write/read big-endian readFully, Hexdump.dump. Tests: round-trip all types, Length=0, MAX guard, truncated->EOF, unknown opaque, byte-order golden (e.g. 00 00 05 01 03 00 00 00 01). Milestone FrameIOTest green.
Phase 2 Headers+codecs (0.5-1d): HeaderTable encode/decode u8+u16 lowercase limits 64/256B/8KiB/64KiB. Request/Response codecs UTF-8 REPORT. Tests indexed/literal, unknown literal, bad discrim->ProtocolException, oversize->400-mapped, fuzz random->only ProtocolException/EOF. Milestone REQUEST /index.html pretty-print.
Phase 3 Server (1-1.5d): MimeTypes+FileHandler.resolve sandbox+index+symlink ->200/404/400. ConnectionHandler loop +400-continue +flush. BServe accept+pool+shutdown. Manual: BServe & + python raw socket probe. Milestone serves index.html, keeps open for 2nd request.
Phase 4 Client (1d): URL parse (require port in v1 doc, pick default 80 if omitted — document), REQUEST id=1, single-Socket loop, -v stderr ONLY (never stdout or binary corrupts), exit codes 0/1/2. Milestone bcurl>out matches file, -v shows frames, missing exits 1.
Phase 5 Robustness+interop (1d): keep-alive 50 mixed on 1 conn (counter=1), 0x9F inject skip test, malformed matrix (short header, Length lies, id 0, bad %, .., NUL, huge count -> 400 or close + next conn works), 20x10 concurrent, partner swap using ONLY SPEC.md then freeze spec. Milestone integration green + interop log.
Phase 6 Hand-in (0.5d): freeze SPEC.md 2pp PDF (diagram+tables+MUST-skip+limits+example), HEXDUMP.md from -v capture 100% bytes annotated, README build/run/test, final mvn package clean-checkout e2e.

## 6. Testing strategy
Unit JUnit5: frame round-trip/order goldens, header indexed/literal, codec, mime, sanitize, percent-decode.
Integration (ServerSocket(0)): 200 GET,404,400,HEAD no-body, keep-alive xN 1 socket, >64KiB multi-DATA, random-bytes cmp identical.
Robustness raw sockets: unknown skip, truncated->close, Length>MAX->close, ..->400, concurrent.
Interop manual: partner bcurl<->bserve SPEC-only, tcpdump/-v diff.
Hand-in shell: bserve & bcurl -v, echo $?, diff body, byte-count check.
Goal: FrameIO,HeaderTable,FileHandler.resolve 90%+, mutation flip-one-byte->400.

## 7. Edge cases (Java-specific)
1. Partial reads: never 1 read=1 frame. readFully(9)+readFully(Length). flush per frame.
2. stdout vs stderr: DATA->System.out FileOutputStream raw, logs/-v->System.err. No PrintWriter on stdout.
3. Charset UTF-8 strict StandardCharsets+REPORT, never platform default.
4. Signed bytes: &0xFF, readUnsignedByte/Short.
5. Sandbox: resolve.normalize.startsWith + toRealPath(NOFOLLOW), decode FIRST then normalize, reject NUL + %2e%2e.
6. Leaks: try-with-resources sockets/streams, close file stream, pool shutdown hook.
7. DoS: check MAX/64/256/8K BEFORE alloc; stream 16KiB, never readAllBytes.
8. Single-conn client: new Socket exactly once (grep test). No retry 2nd socket.
9. Exit: System.exit in main only.

## 8. Timeline (1-2 wks pair)
D1-2 Ph0+1 framing — agree header bytes, exchange hex. D3 Ph2 freeze 10-table. D4-5 Ph3 raw-socket demo. D6 Ph4 e2e. D7 Ph5 partner swap fix SPEC not hacks. D8 Ph6 spec+dump+README dry-run clean clone.

## 9. Hand-in checklist
- [ ] SPEC.md->2pp PDF: diagram+defense, types, headers+10-table, status/mapping, MUST-skip verbatim, limits, lifecycle, example.
- [ ] jar + bserve/bcurl executable; java version noted.
- [ ] HEXDUMP.md full req+resp every byte annotated, matches -v.
- [ ] www/ with index.html. [ ] mvn test green; interop log PASS.
- [ ] No 2nd conn (client audit), keep-open (2 reqs/1 socket).

Appendix wire ex: REQ GET /index.html id1 header 00 00 3A 01 03 00 00 00 01 (Len58 Type01 Flags03 ID1)+payload 01 00 0B 2F696E6465782E68746D6C... RESP 200 header 00 00 1E 02 00 00 00 00 01 (Type02 Flags00 ID1)+payload 00 C8 00 03... then DATA 00 10 00 03 01 00 00 00 01 +4096B. Replace with real -v in HEXDUMP.md.
Next: read SPEC.md (normative) then Phase1 FrameIO. Spec wins over plan.
