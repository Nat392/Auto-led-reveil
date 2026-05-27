# Alarm Watcher

[![Build](https://img.shields.io/badge/build-Gradle-informational)](build.gradle.kts)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](app/build.gradle.kts)

Alarm Watcher est une application Android qui surveille l’alarme système la plus proche et déclenche des automatisations lumineuses autour du réveil. Elle peut piloter des ampoules Zengge en BLE pour lancer une rampe de lever de soleil avant l’alarme, puis appliquer un mode soirée autour du coucher du soleil.

## Ce que fait le projet

- Détecte la prochaine alarme système Android et planifie un pré-avertissement 30 minutes avant l’heure cible.
- Lance une rampe sunrise sur une ou plusieurs ampoules Zengge configurées.
- Applique un mode sunset séparé pour les zones configurées, basé sur une heure de coucher de soleil récupérée en ligne.
- Redémarre et resynchronise automatiquement les programmations au boot, lors des changements d’heure et après les changements d’alarme.
- Envoie les erreurs vers Discord si un webhook est configuré.

## Pourquoi le projet est utile

- Il automatise l’éclairage de réveil sans dépendre d’un service externe complexe.
- Il garde un plan B simple: si le contrôle BLE n’est pas configuré ou échoue, une notification permet d’ouvrir l’application cible manuellement.
- Il centralise la logique de réveil, de coucher de soleil et de diagnostic dans une seule app légère.
- Il expose une configuration simple par fichier local, sans interface d’administration lourde.

## Démarrage rapide

### Prérequis

- Android 12 ou plus récent est requis par le code actuel (`minSdk = 31`).
- JDK 17 et Android Studio ou le wrapper Gradle du dépôt.
- Un téléphone avec Bluetooth LE si vous voulez piloter des ampoules Zengge.

### Configuration

Créez ou complétez `local.properties` à la racine du dépôt avec les valeurs souhaitées:

```properties
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
ZENGGE_BULB_MAC=00:21:4d:00:00:01
ZENGGE_BULB_MAC_CHAMBRE=00:21:4d:00:00:02
ZENGGE_BULB_MAC_BUREAU=00:21:4d:00:00:03
```

Ces clés sont facultatives, mais elles changent le comportement de l’app:

- `DISCORD_WEBHOOK_URL` active le reporting de crash et de logs de diagnostic.
- `ZENGGE_BULB_MAC` peut servir au contrôle BLE principal.
- `ZENGGE_BULB_MAC_CHAMBRE` et `ZENGGE_BULB_MAC_BUREAU` activent les zones chambre et bureau pour les scènes sunrise/sunset.

### Installation et build

Depuis la racine du projet:

```powershell
./gradlew.bat assembleDebug
```

L’APK de debug est généré dans `app/build/outputs/apk/`.

La CI exécute aussi JaCoCo sur les tests JVM du module `app`, publie un commentaire de couverture sur les PR, puis compare la couverture globale de la PR avec celle de `main`. Le job échoue uniquement si la PR fait baisser cette couverture. Le rapport HTML correspondant est généré dans `app/build/reports/jacoco/jacocoDebugUnitTestReport/html/`.

Pour installer sur un appareil connecté:

```powershell
./gradlew.bat installDebug
```

### Premier lancement

1. Ouvrez l’application une première fois.
2. Accordez les permissions demandées: Bluetooth, notifications et alarmes exactes selon la version Android.
3. Si nécessaire, autorisez l’app à fonctionner sans optimisation batterie pour fiabiliser les déclenchements en arrière-plan.
4. Créez ensuite une alarme système normale dans l’application Horloge du téléphone.

### Exemple d’utilisation

1. Configurez au moins une MAC Zengge dans `local.properties`.
2. Créez une alarme système à venir.
3. L’app détecte la prochaine alarme et programme une pré-alarme 30 minutes avant.
4. Au moment prévu, elle démarre la rampe sunrise sur les zones configurées.
5. Si le BLE n’est pas configuré ou échoue, la notification de secours reste disponible.

## Où trouver de l’aide

- Consultez [scripts/install-git-hooks.sh](scripts/install-git-hooks.sh) si vous souhaitez activer les hooks Git fournis.
- Regardez `app/src/main/AndroidManifest.xml` et `app/build.gradle.kts` pour les permissions, services et dépendances réellement utilisés.
- Pour diagnostiquer un problème runtime, lancez l’app puis consultez les logs Android Studio ou `adb logcat`.

## Qui maintient et contribue

Maintenue par Nat392.

Pour contribuer:

1. Créez une branche dédiée.
2. Faites des changements ciblés.
3. Ouvrez une pull request avec une description courte et claire.

## Structure du dépôt

- `app/` : code Android de l’application.
- `scripts/` : scripts utilitaires et hooks Git.

## Licence

Aucune licence n’est déclarée pour le moment dans ce dépôt. Ajoutez un fichier `LICENSE` si vous souhaitez publier une licence explicite.
