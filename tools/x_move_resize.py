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
