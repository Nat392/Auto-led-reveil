# Alarm Watcher

Cette application surveille l'alarme système et lance une automatisation 30 minutes avant l'alarme.

Étapes d'installation / configuration à suivre sur l'appareil (Samsung Galaxy A17 / Android 16):

1. Installer l'APK.
2. Ouvrir l'application `MainActivity` pour demander `SCHEDULE_EXACT_ALARM` si nécessaire.
3. Activer le service d'accessibilité `Alarm Watcher` dans les Paramètres -> Accessibilité -> Services installés.
4. Exempter l'application des optimisations batterie (One UI) pour fiabilité (paramètres Batterie -> Optimisations d'économie d'énergie).

Test rapide:

1. Créer une alarme réveil dans l'application Horloge, 31 minutes dans le futur.
2. Vérifier que l'application rescanne l'alarme système au lancement, au boot et lors des changements d'heure/alarme, puis que le `AlarmTriggerReceiver` reçoit le pré-avertissement 30 minutes avant l'alarme.
3. Si l'accessibilité est activée, l'application tentera d'ouvrir `com.zengge.blev2` et d'effectuer les actions: retour, sélectionner la pièce, choisir la couleur, puis augmenter progressivement la luminosité.

Notes techniques:
- L'automatisation repose sur `AccessibilityService` et des gestes simulés; le comportement est dépendant de l'UI de `com.zengge.blev2` et nécessite ajustements sur l'app réelle.
- Si l'automatisation échoue, une notification de secours est affichée pour lancer manuellement l'app cible.

Build APK:

1. Ouvrir un terminal à la racine du projet.
2. Lancer `./gradlew.bat assembleDebug` pour générer un APK de test.
3. Lancer `./gradlew.bat assembleRelease` pour générer un APK installable signé avec la clé debug.
4. Récupérer l'APK dans `app/build/outputs/apk/`.
