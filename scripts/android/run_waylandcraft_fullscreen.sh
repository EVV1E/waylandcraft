#!/usr/bin/env bash
set -euo pipefail

# One-key WaylandCraft fullscreen launcher for DroidSpaces/Termux-X11.
# Run inside the Ubuntu container from the repository root:
#   ./scripts/android/run_waylandcraft_fullscreen.sh
#
# Optional env:
#   DISPLAY=:0
#   WLC_PULSE_SERVER=/run/user/1000/pulse/native
#   WLC_TERMINATE_EXISTING=1
#   WLC_FULLSCREEN_MODE=root    # root | portrait | custom
#   WLC_WIDTH=2272 WLC_HEIGHT=1080

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

DISPLAY="${DISPLAY:-:0}"
PULSE_SERVER="${WLC_PULSE_SERVER:-${PULSE_SERVER:-/run/user/1000/pulse/native}}"
TERMINATE_EXISTING="${WLC_TERMINATE_EXISTING:-1}"
FULLSCREEN_MODE="${WLC_FULLSCREEN_MODE:-root}"

log() { printf '%s\n' "$*"; }

get_root_geometry() {
  DISPLAY="$DISPLAY" xwininfo -root 2>/dev/null | awk '
    /Width:/ {w=$2}
    /Height:/ {h=$2}
    END { if (w && h) print w " " h; else exit 1 }
  '
}

if [ "$FULLSCREEN_MODE" = "custom" ]; then
  WIDTH="${WLC_WIDTH:?WLC_FULLSCREEN_MODE=custom 时必须设置 WLC_WIDTH}"
  HEIGHT="${WLC_HEIGHT:?WLC_FULLSCREEN_MODE=custom 时必须设置 WLC_HEIGHT}"
elif [ "$FULLSCREEN_MODE" = "portrait" ]; then
  # Some Termux-X11 sessions expose a portrait-sized X11 root even while Android is rotated.
  # Use this only if root mode looks wrong on your device.
  WIDTH="${WLC_WIDTH:-1080}"
  HEIGHT="${WLC_HEIGHT:-2277}"
else
  read -r WIDTH HEIGHT < <(get_root_geometry)
fi

log "🖥️ DISPLAY=$DISPLAY"
log "🖥️ target fullscreen size=${WIDTH}x${HEIGHT} mode=$FULLSCREEN_MODE"
log "🔊 PULSE_SERVER=$PULSE_SERVER"

# Set Minecraft options before launching so LWJGL/Minecraft creates the right framebuffer.
if [ -f run/options.txt ]; then
  python3 - <<PY
from pathlib import Path
p = Path('run/options.txt')
text = p.read_text()
repls = {
    'fullscreen:': 'fullscreen:true',
    'exclusiveFullscreen:': 'exclusiveFullscreen:false',
    'overrideWidth:': 'overrideWidth:$WIDTH',
    'overrideHeight:': 'overrideHeight:$HEIGHT',
}
lines = []
seen = set()
for line in text.splitlines():
    replaced = False
    for prefix, value in repls.items():
        if line.startswith(prefix):
            lines.append(value)
            seen.add(prefix)
            replaced = True
            break
    if not replaced:
        lines.append(line)
for prefix, value in repls.items():
    if prefix not in seen:
        lines.append(value)
p.write_text('\n'.join(lines) + '\n')
PY
else
  mkdir -p run
  {
    echo 'fullscreen:true'
    echo 'exclusiveFullscreen:false'
    echo "overrideWidth:$WIDTH"
    echo "overrideHeight:$HEIGHT"
  } > run/options.txt
fi

if [ "$TERMINATE_EXISTING" = "1" ]; then
  log "🧹 stopping existing runClient/Minecraft if any..."
  pkill -f 'net.fabricmc.devlaunchinjector.Main|gradle-wrapper.jar runClient' 2>/dev/null || true
  sleep 2
fi

# Helper to force the X11 window to the exact root size after launch.
mkdir -p tools
cat > tools/x_move_resize.py <<'PY'
#!/usr/bin/env python3
import ctypes, sys
lib = ctypes.CDLL('libX11.so.6')
lib.XOpenDisplay.argtypes = [ctypes.c_char_p]
lib.XOpenDisplay.restype = ctypes.c_void_p
lib.XMoveResizeWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint]
lib.XMapRaised.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
lib.XRaiseWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
lib.XFlush.argtypes = [ctypes.c_void_p]
lib.XCloseDisplay.argtypes = [ctypes.c_void_p]
if len(sys.argv) != 7:
    raise SystemExit(f"usage: {sys.argv[0]} DISPLAY WINDOW_HEX X Y WIDTH HEIGHT")
dpy = lib.XOpenDisplay(sys.argv[1].encode())
if not dpy:
    raise SystemExit('XOpenDisplay failed')
win = int(sys.argv[2], 0)
x, y, w, h = map(int, sys.argv[3:7])
lib.XMoveResizeWindow(dpy, win, x, y, w, h)
lib.XMapRaised(dpy, win)
lib.XRaiseWindow(dpy, win)
lib.XFlush(dpy)
lib.XCloseDisplay(dpy)
print(f'moved/resized 0x{win:x} to {w}x{h}+{x}+{y}')
PY
chmod +x tools/x_move_resize.py

log "🚀 starting WaylandCraft..."
(
  source ~/.cargo/env 2>/dev/null || true
  export DISPLAY="$DISPLAY"
  export LIBRARY_PATH=/home/user/.local/lib
  export LD_LIBRARY_PATH="$ROOT_DIR/native/target/debug:/home/user/.local/lib:${LD_LIBRARY_PATH:-}"
  export PULSE_SERVER="$PULSE_SERVER"
  export ALSOFT_DRIVERS=pulse
  exec ./gradlew runClient --console=plain
) &
PID=$!
log "📌 launcher pid=$PID"

# Wait for the Minecraft X11 window, then force exact geometry a few times.
log "⏳ waiting for Minecraft window..."
WIN=""
for i in $(seq 1 90); do
  WIN=$(DISPLAY="$DISPLAY" xwininfo -root -tree 2>/dev/null | awk '/Minecraft\*/ {print $1; exit}') || true
  if [ -n "$WIN" ]; then
    break
  fi
  sleep 1
done

if [ -z "$WIN" ]; then
  log "⚠️ 90 秒内没有找到 Minecraft 窗口。runClient 仍在后台运行，pid=$PID"
  wait "$PID"
  exit $?
fi

log "✅ found Minecraft window: $WIN"
for i in $(seq 1 8); do
  python3 tools/x_move_resize.py "$DISPLAY" "$WIN" 0 0 "$WIDTH" "$HEIGHT" >/dev/null 2>&1 || true
  sleep 1
done

log "✅ fullscreen applied"
DISPLAY="$DISPLAY" xwininfo -id "$WIN" -stats 2>/dev/null | grep -E 'Width:|Height:|-geometry' || true
log ""
log "提示：如果 Android 软键盘/导航栏仍遮挡，请在 Termux-X11 内手动关闭 Show keyboard，并打开 Immersive/Fullscreen/Hide system bars。"
log "按 Ctrl+C 会停止等待，但 Minecraft/Gradle 可能继续运行；如需结束可运行：pkill -f 'net.fabricmc.devlaunchinjector.Main|gradle-wrapper.jar runClient'"

wait "$PID"
