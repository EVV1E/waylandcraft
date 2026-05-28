#!/usr/bin/env bash
set -euo pipefail

# Termux / Android host-side audio + X11 bootstrap
# - Starts or reuses PulseAudio
# - Loads AAudio sink
# - Exposes PulseAudio over TCP for the Ubuntu container
# - Starts termux-x11 only if :0 is not already running

X11_DISPLAY="${X11_DISPLAY:-:0}"
PULSE_PORT="${PULSE_PORT:-4713}"
PULSE_ACL="${PULSE_ACL:-127.0.0.1,192.168.2.0/24,10.0.0.0/8,172.16.0.0/12}"
AAUDIO_SINK_NAME="${AAUDIO_SINK_NAME:-AAudio_sink}"

required=(pulseaudio pactl pacmd termux-x11 grep awk sleep id pkill pgrep)
missing=()
for cmd in "${required[@]}"; do
  command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
done

if [ "${#missing[@]}" -ne 0 ]; then
  echo "❌ 缺少依赖："
  printf '   %s\n' "${missing[@]}"
  exit 1
fi

# Do not use $PREFIX/tmp directly if it was created by root. PulseAudio refuses
# XDG_RUNTIME_DIR owned by a different uid, which commonly happens after running
# Termux scripts via su/root once.
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$HOME/.termux-pulse-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
if [ ! -O "$XDG_RUNTIME_DIR" ]; then
  echo "⚠️ XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR 不是当前用户所有，改用用户目录 runtime。"
  export XDG_RUNTIME_DIR="$HOME/.termux-pulse-runtime"
  mkdir -p "$XDG_RUNTIME_DIR"
fi
chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true

# Avoid stale root-owned PulseAudio socket/cookie from previous runs.
if [ -e "$XDG_RUNTIME_DIR/pulse" ] && [ ! -O "$XDG_RUNTIME_DIR/pulse" ]; then
  echo "⚠️ $XDG_RUNTIME_DIR/pulse 不是当前用户所有；请在 Termux 普通用户下删除或换 runtime。"
  export XDG_RUNTIME_DIR="$HOME/.termux-pulse-runtime-$$"
  mkdir -p "$XDG_RUNTIME_DIR"
  chmod 700 "$XDG_RUNTIME_DIR" 2>/dev/null || true
fi

cleanup_pulseaudio_runtime() {
  # Termux PulseAudio can leave a stale pid/socket in $XDG_RUNTIME_DIR/pulse;
  # then new daemon startup fails with: pa_pid_file_create() failed / Daemon already running.
  rm -f "$XDG_RUNTIME_DIR/pulse/pid" \
        "$XDG_RUNTIME_DIR/pulse/native" \
        "$XDG_RUNTIME_DIR/pulse/cli" \
        "$XDG_RUNTIME_DIR/pulse/lock" 2>/dev/null || true
}

start_pulseaudio() {
  echo "🚀 正在启动 PulseAudio..."
  pulseaudio -k 2>/dev/null || true
  pkill pulseaudio 2>/dev/null || true
  sleep 0.8
  cleanup_pulseaudio_runtime
  pulseaudio --start --exit-idle-time=-1 --disallow-exit || true
  sleep 1
  if ! pactl info >/dev/null 2>&1; then
    echo "⚠️ --start 未连上，尝试前台 daemonize=no 后台启动..."
    cleanup_pulseaudio_runtime
    pulseaudio --daemonize=no --log-target=stderr --exit-idle-time=-1 --disallow-exit >/tmp/termux-pulseaudio.log 2>&1 &
    sleep 1.5
  fi
}

if pgrep -x pulseaudio >/dev/null 2>&1; then
  echo "ℹ️ 检测到 PulseAudio 进程，先检查 pactl 连接..."
else
  start_pulseaudio
fi

if ! pactl info >/dev/null 2>&1; then
  echo "⚠️ 现有 PulseAudio 进程不可用，尝试重启..."
  start_pulseaudio
fi

if ! pactl info >/dev/null 2>&1; then
  echo "❌ PulseAudio daemon 未就绪。下面是前台调试输出前 80 行："
  timeout 8 pulseaudio -vvv --exit-idle-time=-1 --disallow-exit 2>&1 | sed -n '1,80p' || true
  exit 1
fi

# Ensure AAudio sink exists and becomes default.
if pactl list sinks short | grep -q "[[:space:]]${AAUDIO_SINK_NAME}[[:space:]]"; then
  echo "ℹ️ 已找到 AAudio sink: ${AAUDIO_SINK_NAME}"
else
  echo "🚀 正在加载 module-aaudio-sink..."
  pactl load-module module-aaudio-sink >/dev/null 2>&1 || true
  sleep 0.5
fi

AAUDIO_SINK=$(pactl list sinks short | awk -v n="$AAUDIO_SINK_NAME" '$2==n {print $2; exit}')
if [ -n "$AAUDIO_SINK" ]; then
  pactl set-default-sink "$AAUDIO_SINK"
  echo "✅ 默认音频设备设置为: $AAUDIO_SINK"
else
  echo "⚠️ 未找到 AAudio sink，当前 sinks："
  pactl list sinks short
fi

# Expose PulseAudio to the Ubuntu container over TCP.
load_tcp_module() {
  local args="$1"
  local out
  out=$(pactl load-module module-native-protocol-tcp $args 2>&1) && {
    echo "✅ native-protocol-tcp 已加载，module id: $out"
    return 0
  }
  echo "⚠️ 加载 TCP 参数失败: $args"
  echo "   pactl: $out"
  return 1
}

if pactl list modules short | grep -q native-protocol-tcp; then
  echo "ℹ️ native-protocol-tcp 已加载"
else
  echo "🚀 正在加载 module-native-protocol-tcp..."
  load_tcp_module "auth-ip-acl=$PULSE_ACL auth-anonymous=1" || \
  load_tcp_module "auth-anonymous=1" || {
    echo "❌ 无法加载 module-native-protocol-tcp。检查模块文件："
    ls "$PREFIX"/lib/pulse-*/modules/module-native-protocol-tcp.so 2>/dev/null || true
  }
fi

# Verify TCP listener if ss/netstat is available.
if command -v ss >/dev/null 2>&1; then
  echo "\n=== TCP listeners 4713 ==="
  ss -ltnp 2>/dev/null | grep ':4713' || echo "⚠️ 没看到 4713 监听"
elif command -v netstat >/dev/null 2>&1; then
  echo "\n=== TCP listeners 4713 ==="
  netstat -ltnp 2>/dev/null | grep ':4713' || echo "⚠️ 没看到 4713 监听"
fi

# Start termux-x11 only if not already running for this display.
if pgrep -f "termux-x11 ${X11_DISPLAY}" >/dev/null 2>&1; then
  echo "ℹ️ termux-x11 (${X11_DISPLAY}) 已经在运行，跳过启动。"
else
  if [ -n "${DISPLAY:-}" ] && [ "$DISPLAY" = "$X11_DISPLAY" ]; then
    echo "ℹ️ DISPLAY 已经是 ${X11_DISPLAY}，不重复启动 termux-x11。"
  else
    echo "🖥️ 正在启动 termux-x11 (${X11_DISPLAY})..."
    termux-x11 "$X11_DISPLAY" -dpi 315 >/dev/null 2>&1 &
    sleep 1
  fi
fi

# Hide Android soft keyboard after Termux-X11 is up. This prevents the IME
# overlay from covering the lower half of the fullscreen X11 window.
if command -v termux-keyboard-hide >/dev/null 2>&1; then
  termux-keyboard-hide >/dev/null 2>&1 || true
  echo "⌨️ 已调用 termux-keyboard-hide"
fi
if command -v input >/dev/null 2>&1; then
  input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.1
  input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
  echo "⌨️ 已发送 BACK/ESC 隐藏软键盘"
elif command -v su >/dev/null 2>&1; then
  su -c 'input keyevent KEYCODE_BACK; sleep 0.1; input keyevent KEYCODE_ESCAPE' >/dev/null 2>&1 || true
  echo "⌨️ 已尝试通过 su 隐藏软键盘"
fi

echo "\n=== PulseAudio 状态 ==="
pactl info | sed -n '1,20p'

echo "\n=== sinks ==="
pactl list sinks short

echo "\n=== modules tcp/aaudio ==="
pactl list modules short | grep -E 'aaudio|native-protocol-tcp' || true
