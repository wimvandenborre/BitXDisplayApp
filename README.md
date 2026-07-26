# BitXDisplayApp

BitXDisplayApp is a small JavaFX display app intended for a compact, fixed-size
overlay. It renders three horizontal sections:

- Clip text and live selected-track harmony (left)
- Remote knobs (center)
- VU meters (right)

The UI is drawn using JavaFX `Canvas` for low-latency rendering and updated by
simple text messages sent over a local TCP socket.

## If you just want to run the app:

download BitX (master extension for the displayapp) from
https://personalaudio.lemonsqueezy.com/

Create a 'PerSonal' folder inside the Bitwig Studio Extensions folder and put the files in it.

## Tech stack

- Java 21+
- JavaFX 23.0.2 (OpenJFX)
- Maven

Maven resolves the JavaFX native libraries for the operating system on which
the project is built.

## Build and run

Build a shaded JAR:

```bash
mvn -DskipTests package
```

Run with the JavaFX Maven plugin:

```bash
mvn javafx:run
```

Built artifacts are placed under `target/`.

## Package for Windows

On Windows, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

This creates a self-contained application image at:

```text
target\windows\BitXDisplayApp\BitXDisplayApp.exe
```

Copy the complete `BitXDisplayApp` directory, not only the executable, into:

```text
Documents\Bitwig Studio\Extensions\PerSonal\
```

The packaged app includes its Java runtime, so end users do not need Java or
Maven installed. See `package.md` for the complete Windows and macOS packaging
procedures.

## Network protocol

The app listens on loopback TCP port `127.0.0.1:9876` and accepts single-line
messages. Supported messages:

- `CLIP:<text>` - set clip name (use `|` for line breaks)
- `PLAYING_NOTES:<note names>` - set current selected-track MIDI notes
- `PLAYING_CHORD:<chord name>` - set current selected-track chord name
- `PAGE:<text>` - set remote controls page name
- `KNOB_NAME:<index>:<text>` - set a knob label (index 0-7)
- `KNOB_VALUE:<index>:<0..1>` - set knob value (0-1)
- `VU:<index>:<0..127>` - set VU meter value for track (index 0-7)
- `MASTER_VU:<0..127>` - set master VU meter value
- `COLOR:<index>:<r>:<g>:<b>` - set track color (0-255)
- `MASTER_COLOR:<r>:<g>:<b>` - set master track color (0-255)

## Assets

`icons/` contains app icon assets used by the launcher/bundling flow.
