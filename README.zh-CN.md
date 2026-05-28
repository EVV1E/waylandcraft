# WaylandCraft 中文说明

![waylandcraft banner](/assets/title_scaled.png)

WaylandCraft 是一个运行在 Minecraft 内部的 Wayland 合成器。它可以把 Linux 图形应用作为窗口显示在 Minecraft 世界里。

原项目视频：<https://youtu.be/cTkEM7b0IQw>

Modrinth 页面：<https://modrinth.com/mod/waylandcraft>

## 系统依赖

- Linux
- Minecraft 26.1.2
- Fabric Loader
- xkbcommon 1.11.0
- xkbcommon tools，例如 `xkbcli`
- Rust 开发环境
- Java 25 SDK

推荐：

- Prism Launcher
- Sodium
- xwayland-satellite

## 重要使用提示

1. 不要使用 Flatpak 打包的 Minecraft 启动器，否则应用窗口可能无法正常使用。
2. NVIDIA 用户建议设置环境变量：`__GL_THREADED_OPTIMIZATIONS=0`。
3. 如果 NVIDIA 上有奇怪的图形瑕疵，可以在视频设置里开启 “Improved Transparency”。
4. Zink OpenGL 驱动可能导致问题，建议使用原生 OpenGL。

## 默认按键

- `V`：打开应用启动器。
- `B`：打开窗口管理界面。
- `G`：普通键盘捕获，用于向窗口输入文字。
- `ALT+Q`：硬键盘捕获，可把 `ESC` 等按键转发给窗口。再次按 `ALT+Q` 退出。

## 运行 X11 应用

WaylandCraft 目前没有直接集成 Xwayland。需要在游戏内终端启动：

```sh
xwayland-satellite :2
```

之后启动 X11 应用时指定 `DISPLAY`：

```sh
DISPLAY=:2 xterm
DISPLAY=:2 steam
```

## 构建与开发运行

```sh
./build.sh
```

最终 jar 在 `build/libs`。开发环境可以运行：

```sh
./gradlew runClient
```

## Android / DroidSpaces / Termux-X11 辅助脚本

本分支增加了一组辅助脚本，方便在 Android DroidSpaces Ubuntu 容器 + Termux-X11 + Termux PulseAudio 环境里运行。

这些脚本主要解决两类问题：

1. 图形：从 Ubuntu 容器连接 Termux-X11。
2. 声音：从 Ubuntu 容器通过 TCP 连接 Termux 侧 PulseAudio，并输出到 Android `AAudio_sink`。

### 1. Termux 宿主侧启动音频和 X11

在 Termux 里运行：

```sh
cd /path/to/waylandcraft
X11_DISPLAY=:0 ./scripts/android/termux_audio_x11_setup.sh
```

脚本会尝试：

- 使用用户自己的 `$HOME/.termux-pulse-runtime`，避免 `$PREFIX/tmp` 被 root 拥有导致 PulseAudio 无法启动。
- 启动或重启 Termux PulseAudio。
- 加载 `module-aaudio-sink`。
- 把默认 sink 设置为 `AAudio_sink`。
- 加载 `module-native-protocol-tcp auth-anonymous=1`，监听 `4713` 端口。
- 启动或复用 `termux-x11 :0`。
- 尝试隐藏 Android 软键盘。

成功时应该能看到：

```text
Default Sink: AAudio_sink
module-aaudio-sink
module-native-protocol-tcp auth-anonymous=1
0.0.0.0:4713 LISTEN
```

如果 Android 软键盘仍然挡住窗口，请在 Termux-X11 的菜单里手动关闭 `Show keyboard` / `Soft keyboard`，并开启 `Fullscreen` / `Immersive mode` / `Hide system bars`。

### 2. Ubuntu 容器侧一键启动 WaylandCraft

在 DroidSpaces Ubuntu 容器里运行：

```sh
cd /home/user/waylandcraft
./scripts/android/run_waylandcraft.sh
```

默认环境：

```sh
DISPLAY=:0
PULSE_SERVER=tcp:127.0.0.1:4713
ALSOFT_DRIVERS=pulse
```

这会让 Minecraft/OpenAL 走 Termux PulseAudio，而不是容器里的 dummy PulseAudio。

如果要全屏启动：

```sh
WLC_FULLSCREEN=1 ./scripts/android/run_waylandcraft.sh
```

如果要强制指定横屏尺寸：

```sh
WLC_FULLSCREEN=1 WLC_WIDTH=2272 WLC_HEIGHT=1080 ./scripts/android/run_waylandcraft.sh
```

### 3. 单独隐藏软键盘

在 Termux 里运行：

```sh
./scripts/android/termux_hide_keyboard.sh
```

脚本会尝试调用 `termux-keyboard-hide`、Android `input keyevent BACK/ESC`，并在 root 可用时尝试切换到非软键盘输入法。

### 4. 强制调整 X11 窗口大小/位置

如果容器里没有 `xdotool` 或 `wmctrl`，可以使用 libX11 辅助脚本：

```sh
wid=$(DISPLAY=:0 xwininfo -root -tree | awk '/Minecraft\*/ {print $1; exit}')
python3 tools/x_move_resize.py :0 "$wid" 0 0 1080 2277
```

参数含义：

```text
DISPLAY 窗口ID X Y 宽 高
```

## Android 音频排错

容器里本地 PulseAudio 可能只有 `auto_null`，这只是 dummy 输出，不会有声音。应使用：

```sh
export PULSE_SERVER=tcp:127.0.0.1:4713
export ALSOFT_DRIVERS=pulse
```

测试：

```sh
PULSE_SERVER=tcp:127.0.0.1:4713 pactl info
PULSE_SERVER=tcp:127.0.0.1:4713 paplay /usr/share/sounds/freedesktop/stereo/audio-volume-change.oga
```

如果 `pactl info` 能看到 `Default Sink: AAudio_sink`，并且 `paplay` 有声音，说明容器到 Android 的声音链路已经打通。

## 免责声明

WaylandCraft 仍有许多限制和 bug。请自行承担使用风险。

贡献请遵守 GPLv3 许可证和原项目的贡献政策。
