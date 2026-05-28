#!/usr/bin/env bash
set -euo pipefail

# Hide Android soft keyboard from Termux/Termux-X11.
# Run this on the Termux Android host, not inside the Ubuntu container.
# It tries non-root methods first, then root-only input-method commands if su works.

say() { printf '%s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

say "⌨️  正在尝试隐藏 Android 软键盘..."

# 1) Termux:API route. Works if termux-api app + package are installed.
if have termux-keyboard-hide; then
  termux-keyboard-hide >/dev/null 2>&1 || true
  say "✅ 已调用 termux-keyboard-hide"
fi

# 2) Generic Android key events. BACK usually hides IME. ESC helps some X11/focused views.
if have input; then
  input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.1
  input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
  say "✅ 已发送 Android BACK/ESC keyevent"
elif have su; then
  su -c 'input keyevent KEYCODE_BACK; sleep 0.1; input keyevent KEYCODE_ESCAPE' >/dev/null 2>&1 || true
  say "✅ 已通过 su 发送 Android BACK/ESC keyevent"
fi

# 3) Root route: temporarily switch to a non-soft-keyboard IME if available.
# This is conservative: only switches if a physical/null keyboard IME is already installed.
if have su && su -c 'id -u' 2>/dev/null | grep -q '^0$'; then
  CURRENT_IME=$(su -c 'settings get secure default_input_method' 2>/dev/null || true)
  ENABLED_IME=$(su -c 'settings get secure enabled_input_methods' 2>/dev/null || true)

  CANDIDATE_IME=$(printf '%s' "$ENABLED_IME" | tr ':' '\n' | grep -Ei 'NullKeyboard|AnySoftKeyboard.*physical|hardware|latin.*physical' | head -n1 || true)
  if [ -n "$CANDIDATE_IME" ] && [ "$CANDIDATE_IME" != "$CURRENT_IME" ]; then
    mkdir -p "$HOME/.termux-x11-state"
    printf '%s\n' "$CURRENT_IME" > "$HOME/.termux-x11-state/previous_ime"
    su -c "ime set '$CANDIDATE_IME'" >/dev/null 2>&1 || true
    say "✅ 已切换到候选非软键盘 IME: $CANDIDATE_IME"
    say "   原 IME 已保存到: $HOME/.termux-x11-state/previous_ime"
  else
    say "ℹ️ 未发现可自动切换的 Null/physical IME；保持当前输入法。"
  fi
fi

say "完成。若软键盘仍显示，请在 Termux-X11 浮动菜单里手动关闭 Show keyboard。"
