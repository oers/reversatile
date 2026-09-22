# Zebra-Engine aus `hoshir/zebra` aktualisieren (https://github.com/hoshir)

Diese Anleitung beschreibt den kontrollierten Update-Weg für den C-Kern. Die
JNI-Brücke und die Android-Anpassungen dürfen nicht durch einen unkontrollierten
Kopiervorgang überschrieben werden.

## 1. Vorbereiten

Im Hauptrepository zuerst einen sauberen Arbeitsstand für den Engine-Update
Commit herstellen. Eigene Änderungen müssen entweder committed oder separat
gesichert sein:

```powershell
Set-Location C:\Users\USER\Projects\reversatile
& "C:\Program Files\Git\cmd\git.exe" fetch origin --tags
& "C:\Program Files\Git\cmd\git.exe" status
```

Für ein Update einen festen Upstream-Tag oder Commit verwenden, nicht
`master`. Der bisher verwendete Referenzstand war `v1.2-hoshir`.

## 2. Isolierten Vergleich anlegen

Die Upstream-Dateien zunächst außerhalb des Projekts auschecken:

```powershell
$upstream = "C:\Users\USER\.copilot\session-state\hoshir-zebra"
& "C:\Program Files\Git\cmd\git.exe" clone https://github.com/hoshir/zebra.git $upstream
& "C:\Program Files\Git\cmd\git.exe" -C $upstream checkout v1.2-hoshir
```

Falls das Repository bereits existiert:

```powershell
& "C:\Program Files\Git\cmd\git.exe" -C $upstream fetch --tags
& "C:\Program Files\Git\cmd\git.exe" -C $upstream checkout v1.2-hoshir
```

Vor dem Kopieren die Änderungen zwischen dem aktuellen Projektstand und dem
neuen Tag prüfen:

```powershell
$local = "C:\Users\USER\Projects\reversatile\project\src\main\jni\zebra"
$src = "$upstream\src"
& "C:\Program Files\Git\cmd\git.exe" diff --no-index $local $src
```

## 3. Nur den Zebra-Kern synchronisieren

Die Dateien aus `$src` dürfen nach
`project\src\main\jni\zebra` kopiert werden. Diese Dateien gehören **nicht**
zum blinden Upstream-Austausch:

- `droidzebra-jni.c`
- `droidzebra-display.c`
- `droidzebra-msg.c`
- `droidzebra-json.c`
- `droidzebra.h`
- `Android.mk`

Danach müssen die Android-spezifischen Anpassungen gezielt wiederhergestellt
und geprüft werden:

- `android_files_dir` und der Pfad zu `coeffs2.bin`
- Android-Book-/Datenbankpfade und das Entpacken von `book.cmp.z`
- `clear_evaluated()` und `clear_endgame_performed()`
- `get_stored_move()`
- die synchronen JNI-Eval-/PV-/Status-Callbacks
- ARM64-sichere Bitmasken und Shifts

Neue Upstream-Dateien müssen in `Android.mk` aufgenommen werden. Bei
`v1.2-hoshir` betrifft das insbesondere `zebra/threads.c`. Header-Dateien
werden über die bestehenden Includes verwendet.

## 4. JNI-Threading prüfen

Die aktuelle JNI-Brücke verwendet threadgebundene `JNIEnv*`-Zeiger. Deshalb
muss die Engine zunächst single-threaded betrieben werden. Nach einem Upstream-
Update prüfen:

```powershell
Select-String -Path project\src\main\jni\droidzebra-jni.c `
  -Pattern "threads_init"
Select-String -Path project\src\main\jni\Android.mk `
  -Pattern "threads.c"
```

Der JNI-Initialisierungspfad muss `threads_init(1)` verwenden. Die parallele
Upstream-Endspielsuche darf erst aktiviert werden, wenn JNI-Callbacks aus
Worker-Threads sicher ausgeschlossen oder separat angebunden sind.

## 5. Bauen

Mit dem Android-Studio-JDK und dem installierten SDK bauen:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$env:ANDROID_HOME = "C:\Users\USER\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :project:externalNativeBuildDebug --no-daemon --console=plain
```

Mindestens `arm64-v8a` muss erfolgreich gebaut werden. Für die vollständige
ABI-Prüfung kann anschließend `:project:assembleDebug` verwendet werden.

## 6. Thor-Regressionstest

Der Test befindet sich in
`project\src\androidTest\java\de\earthlingz\oerszebra\WthorReplayTest.java`.
Das Asset `WTH_2025.wtb` wird über `project\build.gradle` als
Android-Test-Asset eingebunden.

Lokal laufen standardmäßig die ersten 200 Spiele:

```powershell
.\gradlew.bat :project:connectedDebugAndroidTest `
  --no-daemon --console=plain `
  "-Pandroid.testInstrumentationRunnerArguments.class=de.earthlingz.oerszebra.WthorReplayTest"
```

Alle Spiele können lokal explizit gestartet werden:

```powershell
.\gradlew.bat :project:connectedDebugAndroidTest `
  --no-daemon --console=plain `
  "-Pandroid.testInstrumentationRunnerArguments.class=de.earthlingz.oerszebra.WthorReplayTest" `
  "-Pandroid.testInstrumentationRunnerArguments.wthorGameLimit=all"
```

Der Test prüft dabei:

- jedes zweite Spiel Zug für Zug
- jedes zehnte Spiel zusätzlich vollständig mit Undo und Redo
- automatische Passzüge
- Game-Over-Dialoge
- WThor-Schwarz-/Weiß-Endstände

Der GitHub-Actions-Workflow übergibt `wthorGameLimit=all` automatisch. Nach
einem Engine-Update muss daher sowohl der lokale Lauf als auch der vollständige
CI-Lauf betrachtet werden.

## 7. Fehleranalyse und Commit

Bei einem Fehler zuerst das konkrete Spiel aus dem Testfehler und die
zugehörige Logcat-Datei auswerten. Nicht sofort lokale Workarounds in
`complete_pv()`, `make_move()` oder `unmake_move()` übernehmen. Zuerst den
Upstream-Code und den lokalen Diff vergleichen, weil dadurch die eigentliche
Ursache verdeckt werden kann.

Vor dem Commit kontrollieren:

```powershell
& "C:\Program Files\Git\cmd\git.exe" status
& "C:\Program Files\Git\cmd\git.exe" diff --stat
& "C:\Program Files\Git\cmd\git.exe" diff -- project\src\main\jni\droidzebra-jni.c project\src\main\jni\Android.mk
```

Engine-Update und Teständerungen möglichst in getrennten Commits halten. Der
Commit muss auf dem tatsächlich ausgecheckten Branch im Hauptrepository
liegen, nicht nur in einer isolierten Worktree-Kopie:

```powershell
& "C:\Program Files\Git\cmd\git.exe" branch --show-current
& "C:\Program Files\Git\cmd\git.exe" push -u origin <branch-name>
```
