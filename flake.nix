{
  description = "waylandcraft dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
    }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        overlays = [ rust-overlay.overlays.default ];
      };
      rust = pkgs.rust-bin.stable.latest.default;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        nativeBuildInputs = with pkgs; [
          # rust
          rust
          pkg-config

          # java
          jdk25
          gradle

          # for resvg / text rendering
          cmake
        ];

        buildInputs = with pkgs; [
          # wayland / drm / input libs (smithay dependencies)
          wayland
          libxkbcommon
          libdrm
          libinput
          udev
          seatd
          mesa # egl/gbm

          # x11 libs (minecraft / glfw)
          libx11
          libxcursor
          libxrandr
          libxinerama
          libxi
          libxext
          libxxf86vm

          # opengl / rendering
          libGL
          vulkan-loader

          # audio (minecraft / openal)
          openal
          libpulseaudio
          alsa-lib
          flac

          # misc
          fontconfig
          freetype
        ];

        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
          pkgs.wayland
          pkgs.libxkbcommon
          pkgs.libdrm
          pkgs.libinput
          pkgs.udev
          pkgs.seatd
          pkgs.mesa
          pkgs.libGL
          pkgs.vulkan-loader
          pkgs.libx11
          pkgs.libxcursor
          pkgs.libxrandr
          pkgs.libxinerama
          pkgs.libxi
          pkgs.libxext
          pkgs.libxxf86vm
          pkgs.openal
          pkgs.libpulseaudio
          pkgs.alsa-lib
          pkgs.flac
          pkgs.fontconfig
          pkgs.freetype
        ];

        JAVA_HOME = "${pkgs.jdk25}";

        shellHook = ''
          echo "waylandcraft dev shell"
          echo "  rust: $(rustc --version)"
          echo "  java: $(java --version 2>&1 | head -1)"
          echo "  gradle: $(gradle --version 2>/dev/null | grep '^Gradle')"
        '';
      };
    };
}
