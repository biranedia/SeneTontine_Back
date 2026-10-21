#!/bin/sh
# Sauvegarde quotidienne de la base (à planifier avec cron, voir GUIDE-DEPLOIEMENT.md).
# Garde les 14 dernières sauvegardes compressées dans ~/sauvegardes.
set -e
cd "$(dirname "$0")"
mkdir -p "$HOME/sauvegardes"
FILE="$HOME/sauvegardes/senetontine-$(date +%Y-%m-%d).sql.gz"
docker compose exec -T db pg_dump -U senetontine senetontine | gzip > "$FILE"
ls -1t "$HOME"/sauvegardes/senetontine-*.sql.gz | tail -n +15 | xargs -r rm --
echo "Sauvegarde OK : $FILE"
