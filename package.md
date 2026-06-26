# Packaging BitXDisplayApp for macOS

This is the working procedure used to build, sign, notarize, staple, and install
the BitX display app into the Bitwig Studio Extensions folder.

## Prerequisites

- Java and Maven are installed.
- `jar2app` is installed:

```bash
brew install dante-biase/x2x/jar2app
```

- The Developer ID Application identity is available in the user keychain:

```bash
security find-identity -v -p codesigning
```

Expected identity:

```text
Developer ID Application: Ending Infinity (3L6LQ73H6F)
```

- The notarytool keychain profile exists:

```text
3L6LQ73H6F
```

## Build

From the repository root:

```bash
mvn -DskipTests clean package
```

The shaded JAR is created at:

```text
target/BitXDisplayApp-1.0-SNAPSHOT-shaded.jar
```

## Create The App Bundle

From `target/`:

```bash
jar2app "BitXDisplayApp-1.0-SNAPSHOT-shaded.jar" \
  -i "../icons/PerSonal_Logo.icns" \
  -n "BitXDisplayApp"
```

`jar2app` may create a truncated bundle named `BitXDisplayA.app`. If it does,
rename the bundle and embedded JAR:

```bash
mv BitXDisplayA.app BitXDisplayApp.app
mv BitXDisplayApp.app/Contents/MacOS/BitXDisplayA.app.jar \
  BitXDisplayApp.app/Contents/MacOS/BitXDisplayApp.jar
```

## Fix Launcher Scripts

`BitXDisplayApp.app/Contents/MacOS/launcher`:

```sh
#!/bin/sh

DIR=$(cd "$(dirname "$0")"; pwd)
exec "$DIR/runner" "$DIR"
```

`BitXDisplayApp.app/Contents/MacOS/runner`:

```sh
#!/bin/sh
set -e

DIR="$1"
APP_JAR="BitXDisplayApp.jar"
APP_NAME="BitX Display"

cd "$DIR"

if command -v java >/dev/null 2>&1; then
  JAVA="$(command -v java)"
elif [ -x "/usr/bin/java" ]; then
  JAVA="/usr/bin/java"
elif [ -x "/usr/libexec/java_home" ]; then
  JAVA="$(/usr/libexec/java_home)/bin/java"
else
  osascript -e 'display dialog "BitX Display requires Java to run." with title "Cannot launch BitX Display" buttons {"OK"} default button 1'
  exit 1
fi

exec "$JAVA" \
  -Dapple.laf.useScreenMenuBar=true \
  -Dcom.apple.macos.use-file-dialog-packages=true \
  -Xdock:name="$APP_NAME" \
  -Xdock:icon="$DIR/../Resources/application.icns" \
  -cp "$DIR:.:$DIR" \
  -jar "$DIR/$APP_JAR"
```

Normalize permissions before signing:

```bash
chmod -R u+rwX,go+rX BitXDisplayApp.app
chmod 755 BitXDisplayApp.app/Contents/MacOS/launcher \
  BitXDisplayApp.app/Contents/MacOS/runner
```

## Sign

From `target/`:

```bash
codesign --force --deep --strict --options runtime --timestamp \
  --sign "Developer ID Application: Ending Infinity (3L6LQ73H6F)" \
  BitXDisplayApp.app
```

Verify:

```bash
codesign --verify --deep --strict --verbose=4 BitXDisplayApp.app
codesign -dv --verbose=4 BitXDisplayApp.app
```

## Notarize And Staple

Create the notarization zip:

```bash
ditto -c -k --keepParent BitXDisplayApp.app BitXDisplayApp.zip
```

Submit to Apple:

```bash
xcrun notarytool submit BitXDisplayApp.zip \
  --keychain-profile "3L6LQ73H6F" \
  --wait
```

Staple the accepted ticket:

```bash
xcrun stapler staple BitXDisplayApp.app
xcrun stapler validate BitXDisplayApp.app
```

Run final Gatekeeper and signature checks:

```bash
spctl --assess --type execute --verbose=4 BitXDisplayApp.app
codesign --verify --deep --strict --verbose=2 BitXDisplayApp.app
```

Expected Gatekeeper result:

```text
BitXDisplayApp.app: accepted
source=Notarized Developer ID
```

Recreate the zip after stapling so the distributable archive contains the
stapled app:

```bash
ditto -c -k --keepParent BitXDisplayApp.app BitXDisplayApp.zip
```

## Install Into Bitwig Extensions

Destination:

```text
/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal
```

If an older app is already present, preserve it first:

```bash
mv "/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/BitXDisplayApp.app" \
  "/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/BitXDisplayApp.app.previous-YYYYMMDD"
```

Move the new app into place:

```bash
mv target/BitXDisplayApp.app \
  "/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/"
```

Keep `target/BitXDisplayApp.zip` as the signed, notarized, stapled distributable
archive.

## Last Successful Run

- Date: 2026-06-26
- Notarization submission: `3979c36c-1255-4c65-be3f-c6dafcc58776`
- Installed app destination:

```text
/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/BitXDisplayApp.app
```

- Previous installed app backup:

```text
/Users/wimvandenborre/Documents/Bitwig Studio/Extensions/PerSonal/BitXDisplayApp.app.previous-20260626-1712
```
