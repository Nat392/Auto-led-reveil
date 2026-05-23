# AGENTS — Instructions pour agents IA

But : fournir aux agents IA des règles concises et actionnables pour contribuer au dépôt.

Règle principale
- **Langue des commits :** Tous les messages de commit (sujet et description) doivent être rédigés en français.

Conseils pratiques
- Écrire le sujet du commit court, impératif et au présent (ex. « Corrige crash au démarrage »).
- Mettre la ligne de sujet en première ligne, suivie d'une ligne vide puis d'un corps de commit si nécessaire.
- Exemples :
  - Anglais → Français : "Add settings screen" → "Ajoute écran de configuration"
  - "Fix BLE reconnection" → "Corrige reconnexion BLE"

Ressources et liens
- Pour la procédure de build et tests, voir le README : [README.md](README.md)

Enforcement (suggestion)
- Il est recommandé d'ajouter un hook Git (pré-commit ou commit-msg) pour valider la langue des commits. Si vous voulez, je peux proposer un script `commit-msg` simple qui vérifie l'absence d'anglais basique ou impose une liste blanche de mots français.

Hook fourni
- Un hook `commit-msg` a été ajouté dans [/.githooks/commit-msg](.githooks/commit-msg) : il détecte des mots anglais courants et refuse le commit si le message semble en anglais. Pour forcer l'anglais (cas exceptionnel), préfixez la première ligne du message par `EN:`.

Installation des hooks
- Un script d'installation est fourni: [scripts/install-git-hooks.sh](scripts/install-git-hooks.sh). Pour activer les hooks :

```bash
./scripts/install-git-hooks.sh
chmod +x .githooks/commit-msg
```

Prochaines personnalisations recommandées
- Ajouter un fichier d'instruction spécifique aux hooks (`.github/hooks.md`) ou un skill pour gérer/installer hooks automatiquement.

Si vous voulez que j'ajoute une variante PowerShell pour Windows ou que j'installe automatiquement le hook, je peux le faire.
