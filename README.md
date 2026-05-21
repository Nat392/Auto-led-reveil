# Alarm Watcher

Cette application surveille l'alarme système et lance une automatisation 30 minutes avant l'alarme.

Quand une MAC Zengge est configurée dans `local.properties` via `ZENGGE_BULB_MAC`, l'application pilote directement le bulbe en BLE au lieu d'ouvrir `com.zengge.blev2`.

Étapes d'installation / configuration à suivre sur l'appareil (Samsung Galaxy A17 / Android 16):

1. Installer l'APK.
2. Ouvrir l'application `MainActivity` pour demander `SCHEDULE_EXACT_ALARM` si nécessaire.
3. Autoriser `BLUETOOTH_CONNECT` si Android le demande.
4. Si vous voulez le contrôle direct BLE, ajouter la MAC du bulbe dans `local.properties` avec `ZENGGE_BULB_MAC=00:21:4d:00:00:01`.
5. Exempter l'application des optimisations batterie (One UI) pour fiabilité (paramètres Batterie -> Optimisations d'économie d'énergie).

Test rapide:

1. Créer une alarme réveil dans l'application Horloge, 31 minutes dans le futur.
2. Vérifier que l'application rescanne l'alarme système au lancement, au boot et lors des changements d'heure/alarme, puis que le `AlarmTriggerReceiver` reçoit le pré-avertissement 30 minutes avant l'alarme.
3. Si `ZENGGE_BULB_MAC` est défini, l'application envoie directement les commandes BLE pour allumer le bulbe et régler couleur / luminosité.
4. Si la MAC n'est pas définie, l'application affiche une notification de secours pour ouvrir l'app cible manuellement.

Notes techniques:
- Le contrôle direct BLE suit le protocole Zengge / Flux BLE du module Python `python-zengge`.
- Si la MAC n'est pas définie ou si la connexion BLE échoue, la notification de secours reste disponible.

Build APK:

1. Ouvrir un terminal à la racine du projet.
2. Lancer `./gradlew.bat assembleDebug` pour générer un APK de test.
3. Lancer `./gradlew.bat assembleRelease` pour générer un APK installable signé avec la clé debug.
4. Récupérer l'APK dans `app/build/outputs/apk/`.
