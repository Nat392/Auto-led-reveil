# Alarm Watcher

[![Build](https://img.shields.io/badge/build-Gradle-informational)](build.gradle.kts)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF)](app/build.gradle.kts)
[![Licence](https://img.shields.io/badge/licence-non%20d%C3%A9clar%C3%A9e-lightgrey)](#licence)

**Alarm Watcher** est une application Android qui surveille l'alarme système la plus proche et orchestre un réveil lumineux progressif sur des ampoules Zengge en BLE, du lever du soleil simulé jusqu'au fondu nocturne du soir.

## Sommaire

- [Aperçu](#aperçu)
- [Fonctionnalités](#fonctionnalités)
- [Architecture](#architecture)
- [Démarrage rapide](#démarrage-rapide)
  - [Prérequis](#prérequis)
  - [Configuration](#configuration)
  - [Build et installation](#build-et-installation)
  - [Contrôles qualité](#contrôles-qualité-formatage-et-analyse-statique)
  - [Tests et couverture](#tests-et-couverture)
  - [Premier lancement](#premier-lancement)
  - [Exemple d'utilisation](#exemple-dutilisation)
- [Intégration continue](#intégration-continue)
- [Où trouver de l'aide](#où-trouver-de-laide)
- [Qui maintient et contribue](#qui-maintient-et-contribue)
- [Structure du dépôt](#structure-du-dépôt)
- [Licence](#licence)

## Aperçu

- Détecte la prochaine alarme système Android et planifie un pré-avertissement 30 minutes avant l'heure cible.
- Lance une rampe sunrise progressive sur une ou plusieurs ampoules Zengge configurées.
- Applique une scène sunset chaude en fin de journée, basée sur une heure de coucher de soleil récupérée en ligne.
- Enchaîne un fondu nocturne idempotent vers un rouge quasi éteint pour préparer le coucher.
- Ajuste en continu l'éclairage artificiel selon la luminosité naturelle (Daylight Harvesting).
- Redémarre et resynchronise automatiquement les programmations au boot, lors des changements d'heure et après les changements d'alarme.
- Envoie les erreurs vers Discord si un webhook est configuré.

Pourquoi ce projet est utile :

- Il automatise l'éclairage de réveil et de soirée sans dépendre d'un service externe complexe.
- Il garde un plan B simple : si le contrôle BLE n'est pas configuré ou échoue, une notification permet d'ouvrir l'application cible manuellement.
- Il centralise la logique de réveil, de coucher de soleil, de fondu nocturne, de daylight harvesting et de diagnostic dans une seule app légère.
- Il expose une configuration simple par fichier local, sans interface d'administration lourde.

## Fonctionnalités

| Fonctionnalité | Fichiers clés | Description |
| --- | --- | --- |
| **Rampe Sunrise** | `SunriseRampRunner.kt`, `SunriseService.kt`, `AlarmScheduler.kt`, `AlarmTriggerReceiver.kt` | Transition de couleur progressive vers la teinte « jour » de chaque zone, déclenchée avant l'alarme système. |
| **Scène Sunset** | `SunsetSceneService.kt`, `SunsetAutomationScheduler.kt`, `SunsetTimesStore.kt`, `OpenMeteoClient.kt` | Applique une teinte chaude (orange/rouge) à l'heure du coucher du soleil, récupérée via l'API Open-Meteo et adaptée par zone. |
| **Fondu nocturne (Night Fade)** | `NightFadeRunner.kt`, `NightFadeService.kt`, `NightFadeScheduleStore.kt`, `NightFadeRunningStore.kt` | Extinction progressive et idempotente vers un rouge quasi éteint (~1 %), calculée sur l'horloge absolue afin de reprendre correctement même après un redémarrage ou un kill de l'app. Le vert et le bleu atteignent 0 avant le rouge pour éviter l'effet Purkinje. |
| **Daylight Harvesting** | `DaylightHarvestingEstimator.kt`, `DaylightHarvestingScheduler.kt`, `DaylightHarvestingWorker.kt`, `DaylightFadeRunner.kt`, `DaylightHarvestingStateStore.kt` | Ajuste en continu la lumière artificielle selon la radiation solaire (`shortwave_radiation`, W/m²) en suivant la loi psychophysique de Weber-Fechner : pleine intensité à 0 W/m², extinction complète à partir de 600 W/m². |
| **Multi-zones** | `SunriseZoneConfig.kt` | Trois zones (Bureau, Chambre, Cuisine), chacune avec ses propres couleurs sunrise/sunset et sa propre adresse MAC BLE, activables indépendamment. |
| **Surveillance d'alarme & resynchronisation** | `AlarmMonitor.kt`, `AlarmMonitorRunner.kt`, `AlarmScheduler.kt`, `BootReceiver.kt`, `AlarmChangeReceiver.kt` | Détecte la prochaine alarme système, programme un pré-avertissement 30 minutes avant, et resynchronise tout au démarrage, lors d'un changement d'heure ou de modification d'alarme. |
| **Diagnostic & erreurs** | `DiscordCrashReporter.kt` | Envoie les crashs et erreurs non fatales vers un webhook Discord si `DISCORD_WEBHOOK_URL` est configuré. |

## Architecture

Le code de l'application (`app/src/main/kotlin/com/example/alarmwatcher/`) suit un découpage simple par responsabilité :

- **Services** (premier plan) : `SunriseService`, `NightFadeService`, `SunsetSceneService` exécutent les automatisations lumineuses en tâche de fond pendant leur durée.
- **BroadcastReceivers** : `AlarmTriggerReceiver`, `AlarmChangeReceiver`, `SunsetAutomationReceiver`, `BootReceiver` réagissent aux événements système (alarme, boot, changement d'heure).
- **Workers (WorkManager)** : `DaylightHarvestingWorker`, `SunsetCatchUpWorker` exécutent des tâches périodiques ou de rattrapage tolérantes aux redémarrages.
- **Runners** : `SunriseRampRunner`, `NightFadeRunner`, `DaylightFadeRunner` contiennent la logique pure de calcul/interpolation des couleurs, testée unitairement sans dépendances Android.
- **Stores** : `NightFadeScheduleStore`, `NightFadeRunningStore`, `SunsetTimesStore`, `DaylightHarvestingStateStore` persistent l'état nécessaire pour reprendre une automatisation après un redémarrage.
- **Clients externes** : `OpenMeteoClient` (heures de coucher de soleil et radiation solaire), `DiscordCrashReporter` (reporting d'erreurs).
- **Configuration** : `SunriseZoneConfig` centralise les zones, couleurs et adresses BLE.

## Démarrage rapide

### Prérequis

- Android 12 ou plus récent est requis par le code actuel (`minSdk = 31`).
- JDK 17 et Android Studio ou le wrapper Gradle du dépôt.
- Un téléphone avec Bluetooth LE si vous voulez piloter des ampoules Zengge.

### Configuration

Créez ou complétez `local.properties` à la racine du dépôt (ce fichier est ignoré par Git) avec les valeurs souhaitées :

```properties
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
ZENGGE_BULB_MAC=00:21:4d:00:00:01
ZENGGE_BULB_MAC_CHAMBRE=00:21:4d:00:00:02
ZENGGE_BULB_MAC_BUREAU=00:21:4d:00:00:03
ZENGGE_BULB_MAC_CUISINE=00:21:4d:00:00:04
```

Toutes ces clés sont facultatives, mais elles changent le comportement de l'app :

| Clé | Effet |
| --- | --- |
| `DISCORD_WEBHOOK_URL` | Active le reporting de crash et de logs de diagnostic vers Discord. |
| `ZENGGE_BULB_MAC` | Adresse BLE utilisée comme contrôle principal. |
| `ZENGGE_BULB_MAC_BUREAU` | Active la zone Bureau pour les scènes sunrise/sunset/night fade. |
| `ZENGGE_BULB_MAC_CHAMBRE` | Active la zone Chambre pour les scènes sunrise/sunset/night fade. |
| `ZENGGE_BULB_MAC_CUISINE` | Active la zone Cuisine pour les scènes sunrise/sunset/night fade. |

### Build et installation

Depuis la racine du projet :

```powershell
./gradlew.bat assembleDebug
```

L'APK de debug est généré dans `app/build/outputs/apk/`.

Pour installer sur un appareil connecté :

```powershell
./gradlew.bat installDebug
```

### Contrôles qualité (formatage et analyse statique)

Le projet intègre `ktlint` et `detekt` pour faire respecter les standards Kotlin avant fusion.

Commandes utiles en local :

```powershell
./gradlew.bat ktlintCheck detekt
```

Pour corriger automatiquement le formatage Kotlin :

```powershell
./gradlew.bat ktlintFormat
```

Ces vérifications sont aussi lancées dans la CI sur les PR vers `main`.

### Tests et couverture

Les tests unitaires (JVM) utilisent **JUnit 5** et **Mockk**, et les tests instrumentés utilisent **Espresso** et **UiAutomator**.

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat jacocoDebugUnitTestReport
./gradlew.bat connectedDebugAndroidTest
```

La CI exécute aussi JaCoCo sur les tests JVM du module `app`, publie un commentaire de couverture sur les PR, puis compare la couverture globale de la PR avec celle de `main`. Le job échoue uniquement si la PR fait baisser cette couverture. Le rapport HTML correspondant est généré dans `app/build/reports/jacoco/jacocoDebugUnitTestReport/html/`.

### Premier lancement

1. Ouvrez l'application une première fois.
2. Accordez les permissions demandées : Bluetooth, notifications et alarmes exactes selon la version Android.
3. Si nécessaire, autorisez l'app à fonctionner sans optimisation batterie pour fiabiliser les déclenchements en arrière-plan.
4. Créez ensuite une alarme système normale dans l'application Horloge du téléphone.

### Exemple d'utilisation

1. Configurez au moins une MAC Zengge dans `local.properties`.
2. Créez une alarme système à venir.
3. L'app détecte la prochaine alarme et programme une pré-alarme 30 minutes avant.
4. Au moment prévu, elle démarre la rampe sunrise sur les zones configurées.
5. En soirée, la scène sunset puis le fondu nocturne s'enchaînent automatiquement selon l'heure du coucher du soleil.
6. Si le BLE n'est pas configuré ou échoue, la notification de secours reste disponible.

## Intégration continue

Deux workflows GitHub Actions sont définis dans `.github/workflows/` :

- **`android.yml`** : construit les APK debug et release, exécute `ktlintCheck`/`detekt`/lint, lance les tests unitaires JVM avec couverture JaCoCo (commentée sur la PR et comparée à `main`), puis exécute les tests instrumentés sur un émulateur Android (API 33, x86_64). Se déclenche sur les push vers `main`, les PR vers `main`, et manuellement.
- **`dependabot-automerge.yml`** : approuve et active l'automerge (squash) pour les PR Dependabot dont la CI passe. Se déclenche automatiquement et peut aussi être lancé manuellement, éventuellement pour une PR précise.

Les mises à jour de dépendances sont configurées dans `.github/dependabot.yml` (Gradle quotidien, groupé par écosystème ; GitHub Actions quotidien).

## Où trouver de l'aide

- Consultez [scripts/install-git-hooks.sh](scripts/install-git-hooks.sh) si vous souhaitez activer les hooks Git fournis.
- Regardez `app/src/main/AndroidManifest.xml` et `app/build.gradle.kts` pour les permissions, services et dépendances réellement utilisés.
- Pour diagnostiquer un problème runtime, lancez l'app puis consultez les logs Android Studio ou `adb logcat`.

## Qui maintient et contribue

Maintenue par Nat392.

Pour contribuer :

1. Créez une branche dédiée.
2. Faites des changements ciblés.
3. Rédigez vos messages de commit en français, conformément à [AGENTS.md](AGENTS.md).
4. Ouvrez une pull request avec une description courte et claire.
5. Si vous activez l'automerge GitHub pour les PR Dependabot, assurez-vous que le dépôt autorise l'automerge et les squash merges, puis laissez la CI GitHub Actions terminer avant la fusion.
6. Pour une PR déjà ouverte, lancez le workflow GitHub Actions `Dependabot Auto-merge` manuellement depuis l'onglet Actions, puis indiquez un numéro de PR si vous voulez en cibler une seule.

## Structure du dépôt

| Chemin | Contenu |
| --- | --- |
| `app/` | Code Android de l'application (Kotlin, ressources, tests unitaires et instrumentés). |
| `scripts/` | Scripts utilitaires (bootstrap Gradle, installation des hooks Git). |
| `.github/` | Workflows GitHub Actions et configuration Dependabot. |
| `.githooks/` | Hook `commit-msg` validant la langue française des commits. |
| `gradle/` | Wrapper Gradle. |
