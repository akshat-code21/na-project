# BHTTP — Binary HTTP (bserve / bcurl)

Java 17+ (tested 22), Maven, plain `java.net` sockets, JUnit 5.

## Build

```sh
mvn package
```

Produces `target/bhttp-1.0.jar`. Wrappers: `./bserve`, `./bcurl`.

## Run

```sh
./bserve ./www 9000
./bcurl -v localhost:9000/index.html > /tmp/out.html
./bcurl localhost:9000/missing; echo $exit # 1 on 404
```

- `bserve [-v] <root> <port>` — keep-alive server, 404/400 rules in SPEC.md.
- `bcurl [-v] [--head] [http://]host:port/path` — single connection, body->stdout, `-v` frames->stderr, exit 0 2xx/3xx, 1 4xx/5xx, 2 transport/usage.

## Test

```sh
mvn test
```

23 tests: framing goldens, header/request/response codecs, live-socket 200/404/400/HEAD/keep-alive-x2/unknown-skip(0x9F)/binary-safe-50KiB.

## Docs

- `IMPLEMENTATION_PLAN.md` — step-by-step build plan + architecture.
- `SPEC.md` — normative 2-page protocol spec (stranger-sufficient).
- `HEXDUMP.md` — annotated request+response, every byte accounted.

## Limits

Single sequential request per client run (no pipelining in client; server tolerates back-to-back). No TLS, no compression, static 10-header table, 16MiB max frame, 60s server idle close.
