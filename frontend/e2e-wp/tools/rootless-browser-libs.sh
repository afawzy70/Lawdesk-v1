#!/usr/bin/env bash
# NOTE: this script consolidates the commands that were actually run (in several steps, into two prefixes); the consolidated script itself was NOT executed end to end.
# Rootless libraries for Playwright's WebKit (WPE) and Firefox on a host where you are NOT root (LD-DOC r3; Ubuntu 24.04, Playwright 1.64.1).
# Nothing is installed: `apt-get download` only fetches .deb files, `dpkg -x` unpacks them into a private prefix, and the browsers are
# pointed at that prefix with LD_LIBRARY_PATH. No docker, no sudo, no change to the host, UAT or its OS. Usage:
#   tools/rootless-browser-libs.sh <prefix-dir>      then:   source <prefix-dir>/env-webkit.sh   (or env-firefox.sh)
# Verified on this repo's host: webkit 27.2 (build 2370) and firefox 157.0 ran the whole smoke matrix (docs/evidence/DOC-R3-TIME-BROWSERS).
# What it works around, exactly:
#   - Playwright's WebKit build links libjxl.so.0.8, which Ubuntu 24.04 does not package (it has 0.7): taken from the Debian snapshot archive.
#   - it links libbacktrace.so.0, which Ubuntu 24.04 does not package at all: a STUB (crash backtraces report nothing) is compiled with gcc.
#   - headless WebKit (WPE) needs an EGL display and there is no /dev/dri render node: Mesa's software "surfaceless" platform is used.
#   - the MiniBrowser wrapper script overwrites LD_LIBRARY_PATH: its copy (inside the downloaded browser directory) gets the prefix appended.
set -euo pipefail
prefix=${1:?usage: rootless-browser-libs.sh <prefix-dir>}
mkdir -p "$prefix/debs" "$prefix/root" "$prefix/stub"
cd "$prefix/debs"
packages="libabsl20220623t64 libasound2t64 libatk-bridge2.0-0t64 libatk1.0-0t64 libatspi2.0-0t64 libavif16 libbrotli1 libcairo-gobject2 libcairo2
 libcolord2 libcups2t64 libdatrie1 libdav1d7 libdrm-amdgpu1 libdrm-intel1 libdrm-nouveau2 libdrm-radeon1 libdrm2 libegl-mesa0 libegl1 libenchant-2-2
 libepoxy0 libevent-2.1-7t64 libexpat1 libflite1 libfribidi0 libgav1-1 libgbm1 libgdk-pixbuf-2.0-0 libgl1 libglapi-mesa libgles2 libglvnd0 libglx0
 libgraphite2-3 libgstreamer-gl1.0-0 libgstreamer-plugins-bad1.0-0 libgstreamer-plugins-base1.0-0 libgstreamer-plugins-good1.0-0 libgstreamer1.0-0
 libgtk-3-0t64 libharfbuzz-icu0 libharfbuzz0b libhwy1t64 libhyphen0 liblcms2-2 libllvm20 libmanette-0.2-0 libnghttp2-14 libopenjp2-7 libopus0
 liborc-0.4-0t64 libpango-1.0-0 libpangocairo-1.0-0 libpangoft2-1.0-0 libpciaccess0 libpixman-1-0 libpsl5t64 librav1e0 libsecret-1-0 libsensors5
 libsoup-3.0-0 libsqlite3-0 libsvtav1enc1d1 libthai0 libwayland-client0 libwayland-cursor0 libwayland-egl1 libwayland-server0 libwebp7
 libwebpdemux2 libwebpmux3 libwoff1 libx11-6 libx11-xcb1 libxau6 libxcb-dri2-0 libxcb-dri3-0 libxcb-glx0 libxcb-present0 libxcb-randr0 libxcb-render0
 libxcb-shm0 libxcb-sync1 libxcb-xfixes0 libxcb1 libxcomposite1 libxcursor1 libxdamage1 libxdmcp6 libxext6 libxfixes3 libxi6 libxinerama1 libxkbcommon0
 libxrandr2 libxrender1 libxshmfence1 libxslt1.1 libxtst6 libxxf86vm1 libyuv0 libzstd1 mesa-libgallium libgl1-mesa-dri"
for p in $packages; do apt-get download "$p" >/dev/null 2>&1 || echo "could not download $p (continuing)"; done
curl -fsSLO https://snapshot.debian.org/archive/debian/20231011T205917Z/pool/main/j/jpeg-xl/libjxl0.8_0.8.2-1_amd64.deb
for f in ./*.deb; do dpkg -x "$f" "$prefix/root"; done
cat > "$prefix/stub/backtrace_stub.c" <<'EOF'
typedef void (*err_cb)(void *, const char *, int);
void *backtrace_create_state(const char *f, int t, err_cb e, void *d) { (void)f; (void)t; (void)e; (void)d; static char s; return &s; }
int backtrace_pcinfo(void *s, unsigned long pc, void *cb, err_cb e, void *d) { (void)s; (void)pc; (void)cb; (void)e; (void)d; return 0; }
int backtrace_syminfo(void *s, unsigned long pc, void *cb, err_cb e, void *d) { (void)s; (void)pc; (void)cb; (void)e; (void)d; return 0; }
EOF
gcc -shared -fPIC -Wl,-soname,libbacktrace.so.0 -o "$prefix/root/usr/lib/x86_64-linux-gnu/libbacktrace.so.0" "$prefix/stub/backtrace_stub.c"
lib="$prefix/root/usr/lib/x86_64-linux-gnu"
cat > "$prefix/env-webkit.sh" <<EOF
export PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=1
export LD_LIBRARY_PATH=$lib
export LIBGL_DRIVERS_PATH=$lib/dri
export __EGL_VENDOR_LIBRARY_FILENAMES=$prefix/root/usr/share/glvnd/egl_vendor.d/50_mesa.json
export EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1
EOF
cat > "$prefix/env-firefox.sh" <<EOF
export PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=1
export LD_LIBRARY_PATH=$lib
EOF
echo "Next: PLAYWRIGHT_BROWSERS_PATH=<dir> npx playwright install webkit firefox chromium  (a download, no root), then append $lib to LD_LIBRARY_PATH in"
echo "  <dir>/webkit-*/minibrowser-wpe/MiniBrowser   (the line 'export LD_LIBRARY_PATH=...' of that wrapper), then source env-webkit.sh and run smoke.mjs."
