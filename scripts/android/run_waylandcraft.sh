#!/usr/bin/env bash
set -euo pipefail

# WaylandCraft one-key launcher for DroidSpaces Ubuntu container + Termux-X11 + Termux PulseAudio.
# Run inside the Ubuntu container:
#   cd /home/user/waylandcraft
#   ./scripts/android/run_waylandcraft.sh
#
# Before running this, start Termux host audio with:
#   X11_DISPLAY=:0 ./scripts/android/termux_audio_x11_setup.sh
#
# Optional env:
#   DISPLAY=:0
#   WLC_PULSE_SERVER=tcp:127.0.0.1:4713
#   WLC_FULLSCREEN=0|1
#   WLC_WIDTH=2272 WLC_HEIGHT=1080    # only used when WLC_FULLSCREEN=1 and set explicitly
#   WLC_KILL_EXISTING=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

DISPLAY="${DISPLAY:-:0}"
PULSE_SERVER="${WLC_PULSE_SERVER:-tcp:127.0.0.1:4713}"
KILL_EXISTING="${WLC_KILL_EXISTING:-1}"
FULLSCREEN="${WLC_FULLSCREEN:-0}"

log() { printf '%s\n' "$*"; }

get_root_size() {
  DISPLAY="$DISPLAY" xwininfo -root 2>/dev/null | awk '
    /Width:/ {w=$2}
    /Height:/ {h=$2}
    END { if (w && h) print w " " h; else exit 1 }
  '
}

set_option() {
  local key="$1" value="$2"
  python3 - "$key" "$value" <<'PY'
from pathlib import Path
import sys
key, value = sys.argv[1], sys.argv[2]
p = Path('run/options.txt')
p.parent.mkdir(exist_ok=True)
text = p.read_text() if p.exists() else ''
lines = text.splitlines()
out = []
seen = False
for line in lines:
    if line.startswith(key + ':'):
        out.append(f'{key}:{value}')
        seen = True
    else:
        out.append(line)
if not seen:
    out.append(f'{key}:{value}')
p.write_text('\n'.join(out) + '\n')
PY
}

log "🖥️ DISPLAY=$DISPLAY"
log "🔊 PULSE_SERVER=$PULSE_SERVER"
log "🔊 ALSOFT_DRIVERS=pulse"

# Quick audio server check. Do not fail hard: Minecraft may still start, but this warns early.
if command -v pactl >/dev/null 2>&1; then
  if PULSE_SERVER="$PULSE_SERVER" timeout 5 pactl info >/tmp/wlc-pactl-info.txt 2>&1; then
    log "✅ PulseAudio reachable: $(grep -m1 '^Server Name:' /tmp/wlc-pactl-info.txt | cut -d: -f2- | xargs)"
    log "✅ Default sink: $(grep -m1 '^Default Sink:' /tmp/wlc-pactl-info.txt | cut -d: -f2- | xargs)"
  else
    log "⚠️ PulseAudio 暂时不可达：$PULSE_SERVER"
    sed -n '1,10p' /tmp/wlc-pactl-info.txt || true
    log "   请先在 Termux 运行: X11_DISPLAY=:0 ./termux_audio_x11_setup.sh"
  fi
fi

if [ "$FULLSCREEN" = "1" ]; then
  if [ -n "${WLC_WIDTH:-}" ] && [ -n "${WLC_HEIGHT:-}" ]; then
    WIDTH="$WLC_WIDTH"
    HEIGHT="$WLC_HEIGHT"
  else
    read -r WIDTH HEIGHT < <(get_root_size)
  fi
  log "🖼️ 设置 Minecraft 全屏: ${WIDTH}x${HEIGHT}"
  set_option fullscreen true
  set_option exclusiveFullscreen false
  set_option overrideWidth "$WIDTH"
  set_option overrideHeight "$HEIGHT"
else
  log "🪟 使用 Minecraft 默认窗口大小"
  set_option fullscreen false
  set_option exclusiveFullscreen false
  set_option overrideWidth 0
  set_option overrideHeight 0
fi

if [ "$KILL_EXISTING" = "1" ]; then
  log "🧹 stopping existing runClient/Minecraft if any..."
  pkill -f 'net.fabricmc.devlaunchinjector.Main|gradle-wrapper.jar runClient' 2>/dev/null || true
  sleep 2
fi

source ~/.cargo/env 2>/dev/null || true
export DISPLAY
export LIBRARY_PATH=/home/user/.local/lib
export LD_LIBRARY_PATH="$ROOT_DIR/native/target/debug:/home/user/.local/lib:${LD_LIBRARY_PATH:-}"
export PULSE_SERVER
export ALSOFT_DRIVERS=pulse

log "🚀 starting WaylandCraft..."
log "   cwd=$ROOT_DIR"
exec ./gradlew runClient --console=plain
