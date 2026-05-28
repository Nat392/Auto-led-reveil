# AGENT MEMORY

## 2026-05-28
- Commandes exécutées :
  - `Get-Command emulator, adb -ErrorAction SilentlyContinue`
  - `Get-ChildItem Env:ANDROID_SDK_ROOT,Env:ANDROID_HOME,Env:LOCALAPPDATA -ErrorAction SilentlyContinue`
  - `list_dir` sur `C:\Users\Utilisateur\AppData\Local\Android`, `C:\Users\Utilisateur\AppData\Local\Android\Sdk`, `C:\Users\Utilisateur\AppData\Local\Android\Sdk\emulator`, `C:\Users\Utilisateur\\.android`, `C:\Users\Utilisateur\\.android\avd`
  - `adb devices`
  - `adb wait-for-device`
- Statut : émulateur Pixel 7 démarré et visible en ligne via adb (`emulator-5554`)
- Fichiers modifiés : `AGENT_MEMORY.md`
- PR : non créée pour l’instant

## 2026-05-28 - CI locale complétée
- Commandes exécutées :
  - `git ls-files --stage gradlew`
  - `git update-index --chmod=+x gradlew`
  - `bash -lc './gradlew --build-cache assembleDebug lintDebug ktlintCheck detekt testDebugUnitTest jacocoDebugUnitTestReport'` (échec car WSL absent)
  - `& .\gradlew.bat --build-cache assembleDebug lintDebug ktlintCheck detekt testDebugUnitTest jacocoDebugUnitTestReport`
  - `& .\gradlew.bat --build-cache assembleRelease`
  - `adb -e shell pm grant com.example.alarmwatcher.debug android.permission.BLUETOOTH_CONNECT`
  - `adb -e shell pm grant com.example.alarmwatcher.debug android.permission.BLUETOOTH_SCAN`
  - `adb -e shell pm grant com.example.alarmwatcher.debug android.permission.POST_NOTIFICATIONS`
  - `& .\gradlew.bat --build-cache connectedDebugAndroidTest`
- Statut : assembleDebug, lintDebug, ktlintCheck, detekt, testDebugUnitTest, jacocoDebugUnitTestReport, assembleRelease et connectedDebugAndroidTest passés localement
- Fichiers modifiés : `AGENT_MEMORY.md`, `gradlew` (bit exécutable dans Git)
- PR : non créée pour l’instant
