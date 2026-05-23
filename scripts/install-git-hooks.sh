#!/usr/bin/env bash
set -euo pipefail

# Configure Git to use the repository-local hooks directory
git config core.hooksPath .githooks

echo "Git hooks path set to .githooks"
echo "Rendez le hook exécutable si nécessaire: chmod +x .githooks/commit-msg"
