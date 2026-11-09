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

WebKit does not run on this host. The download works, but (1) Playwright's WebKit build needs about 50 system libraries
that are not installed (they can be unpacked without root with `apt-get download` + `dpkg -x`; done for all but the next
two), (2) the build links `libjxl.so.0.8`, which Ubuntu 24.04 does not package (only 0.7), and (3) headless WebKit (WPE)
needs an EGL display and there is no `/dev/dri` render node, so it aborts with "Could not create WPE EGL display".
Fix: run the WebKit step on a host or CI runner with the official Playwright image (`mcr.microsoft.com/playwright`, which
ships the WebKit dependencies and software GL) or install `libjxl0.8`, `mesa` and a render node on this host with root.
