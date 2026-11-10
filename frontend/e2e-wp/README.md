# e2e-wp: cross-engine smoke, layout and accessibility checks (LD-DOC r2 UX quality)

Runs against a **local throwaway instance only** (the scripts refuse any non-local URL). Never point it at UAT or
Production: the journeys create data and the seed script rotates a bootstrap password.

What it covers: 39 screens added or changed by WP1-WP10 (dashboard, case register and intake, case page with all its
sections, library, search, directives/templates, deadlines, tasks, calendar, hearings, documents, experts,
notifications, archive, enforcement, reports and indicators, org/access/roles/users/lookups/audit admin, courts,
assistant status, profile) at 390px phone and 1366px desktop, in Arabic (RTL) and English.

- `smoke.mjs`: per screen: page errors, console errors, horizontal overflow, elements wider than the viewport outside a
  scroll region, clipped text without a title, small targets, and an axe-core run (wcag2a, wcag2aa, wcag21a/aa, wcag22aa,
  best-practice). Writes `results.json`.
- `journeys.mjs`: interaction steps (sign-in, wrong password, skip link, register filter and open case, intake
  validation, search, assistant status, calendar grid structure). Writes `journeys.json`; exit code 1 on any failed step.
- `tools/EmbeddedPg.java`: starts a throwaway embedded PostgreSQL (real binaries, no Docker).
- `tools/seed.mjs`: creates a SYNTH-DOC manager account (random password written to a mode-600 file outside the repo)
  and 24 synthetic cases.

## Run

```bash
# 1. backend jar and frontend build (JDK 21)
export JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1 PATH=$JAVA_HOME/bin:$PATH
(cd backend && ./mvnw -o -q package -DskipTests && ./mvnw -o -q dependency:build-classpath -Dmdep.outputFile=/tmp/ux-cp.txt -Dmdep.includeScope=test)
(cd frontend && npm ci && npx vite build)

# 2. throwaway PostgreSQL, then the app (cookie not Secure because the local origin is http)
mkdir -p /tmp/ux-run && javac -cp "$(cat /tmp/ux-cp.txt)" -d /tmp/ux-run frontend/e2e-wp/tools/EmbeddedPg.java
java -cp "/tmp/ux-run:$(cat /tmp/ux-cp.txt)" EmbeddedPg /tmp/ux-run/port &      # wait for /tmp/ux-run/port
printf 'Choose-A-Local-Password-1!' > /tmp/ux-run/adminpw
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$(cat /tmp/ux-run/port)/lawdesk_ux SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=postgres SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
LAWDESK_BOOTSTRAP_ADMIN_USERNAME=ux.admin LAWDESK_BOOTSTRAP_ADMIN_EMAIL=ux.admin@example.test \
LAWDESK_BOOTSTRAP_ADMIN_PASSWORD_FILE=/tmp/ux-run/adminpw java -jar backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar &
(cd frontend && npx vite preview --port 4173 --host 127.0.0.1 &)               # proxies /api to localhost:8080

# 3. seed (once per database), then run
cd frontend/e2e-wp && npm i
UX_ADMIN_PW_FILE=/tmp/ux-run/adminpw UX_CREDS_FILE=/tmp/ux-run/creds.json UX_CASES_FILE=/tmp/ux-run/cases.json node tools/seed.mjs
export PLAYWRIGHT_BROWSERS_PATH=/tmp/ux-pw/browsers      # where the engines are installed (below)
export LD_LIBRARY_PATH=/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu   # only if system libs are missing
UX_CREDS_FILE=/tmp/ux-run/creds.json UX_CASES_FILE=/tmp/ux-run/cases.json UX_OUT=/tmp/ux-run/out \
UX_ENGINES=chromium,firefox UX_LOCALES=ar,en UX_SHOTS=dashboard,cases,case node smoke.mjs
UX_CREDS_FILE=/tmp/ux-run/creds.json UX_OUT=/tmp/ux-run/out node journeys.mjs
```

`UX_AXE_ALL=1` also runs axe in Firefox (by default only in Chromium; axe checks the DOM, which is engine-independent).

## Engines

`PLAYWRIGHT_BROWSERS_PATH=/tmp/ux-pw/browsers npx playwright install chromium firefox` works without root. Firefox needs no
extra system libraries here. Chromium headless shell needs the libraries from `/tmp/lawdesk-qa-browser-libs`.

Widths (r3): `phone` 390, `tablet` 768x1024, `tablet-l` 1024x768, `desktop` 1366 (`UX_WIDTHS=tablet,tablet-l` selects); axe runs on every engine (`UX_AXE_ALL=0`: Chromium only);
extra checks: clipped actions and words broken mid-word (from 768px up).

WebKit runs on this host WITHOUT root and without touching UAT or the OS: `tools/rootless-browser-libs.sh <prefix>` downloads the libraries with `apt-get download`, unpacks them with
`dpkg -x`, takes `libjxl 0.8` from the Debian snapshot archive, compiles a stub `libbacktrace.so.0`, and prepares `env-webkit.sh` (Mesa software EGL, `EGL_PLATFORM=surfaceless`, no render node
needed). Append the prefix to `LD_LIBRARY_PATH` inside the downloaded browser's `minibrowser-wpe/MiniBrowser` wrapper, source `env-webkit.sh`, run `UX_ENGINES=webkit node smoke.mjs`.
Evidence, caveats and the official-image command for a docker-capable operator: `docs/evidence/DOC-R3-TIME-BROWSERS/README.md`.

## Firefox without root

If Playwright reports missing GTK libraries: run `npx playwright install firefox` from this folder (the engine revision must
match this package's Playwright, not another checkout's), unpack `libgtk-3-0t64`, `libpango-1.0-0`, `libpangocairo-1.0-0`,
`libcairo2`, `libcairo-gobject2`, `libgdk-pixbuf-2.0-0`, `libxcursor1` and their neighbours with `apt-get download` and
`dpkg -x` into a directory, and run with `PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=1` and that directory (plus the Chromium
library directory) on `LD_LIBRARY_PATH`. Evidence of a full run: `docs/evidence/DOC-UX/SUMMARY.md`.
