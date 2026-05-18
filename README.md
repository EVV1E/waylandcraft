![waylandcraft banner](/assets/title_scaled.png)

Wayland Compositor in Minecraft

[Demo video](https://youtu.be/cTkEM7b0IQw)

## System dependencies
- OS: Linux
- Minecraft 26.1.2
- Fabric mod loader
- xkbcommon library 1.11.0
- xkbcommon tools (xkbcli)

## Frequently Asked Questions
### How do I use this thing?
Download the mod from the releases section, install Minecraft Fabric for 26.1.2 and drag the jar file in your mods folder.
Look at your keybind settings. By default `V` opens the app launcher, `G` enables keyboard capture allowing you to type in
the windows, `B` opens the window manager screen.

### How can I press Escape in the windows?
Instead of using `G` to capture the keyboard, use `ALT+Q` instead. The only way to turn it off is to press `ALT-Q` again,
so the `ESC` key is forwarded to the application.

### How to do the relative mouse movement thing for 3D games?
Move your mouse over the window, then activate the hard keyboard capture mode. (`ALT-Q`)
Exiting the hard keyboard capture mode releases the mouse.

### Will there be multiplayer support?
Multiplayer support would require video streaming, a bunch of networking code and a rewrite of input handling,
so it's not really planned right now.

### But can I use it on a server though?
You can, but because it's a client-side mod, other players won't see your windows or be able to interact with them.
Also you will not receive the windows as items. To spawn a window in the world, go into the wm screen (default bind `B`)
and then press and hold the "Grab" button.

### Does this work in VR?
Depending on your VR mod, you can probably get the windows to display fine but you probably won't be able to interact with
the windows using your controller. Soooo, kinda.

### Does this work with shaders?
The windows are rendered into the world by themselves (not like blocks or entities) so a lot of shaders will break the functionality.

## Building and Running
You need a Rust development environment and a Java 25 SDK.
```sh
./build.sh #all arguments are passed to cargo build
```

The final jar file will be in `build/libs`, or run `./gradlew runClient`
for a development environment


## Images
![screenshot](/assets/screenshot.png)

## Disclaimer
This compositor still has lots of issues and bugs. Use it at your own risk or whatever.

## Contribution Policy
All contributions have to be made an accordance with the GPLv3 license (see `LICENSE`).
Waylandcraft has a strong no generative AI policy for reasons of code and contribution quality as well as ethical and copyright concerns.
All contributions have to be made without major LLM assistance in the final submitted code. You sign this off every time you submit code
via a pull request or similar.
