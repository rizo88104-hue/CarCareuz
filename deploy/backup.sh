#!/usr/bin/env bash
# CarCare bazasining zaxira nusxasi. Har kuni cron orqali ishga tushiring:
#   crontab -e  →  30 3 * * * /opt/carcare/deploy/backup.sh >> /var/log/carcare-backup.log 2>&1
# Tiklash:  gunzip -c FAYL.sql.gz | docker compose exec -T db psql -U carcare -d carcare
set -euo pipefail
cd "$(dirname "$0")/.."
DIR=/var/backups/carcare
KEEP_DAYS=14
mkdir -p "$DIR"
FILE="$DIR/carcare-$(date +%F_%H%M).sql.gz"
docker compose exec -T db pg_dump -U carcare -d carcare | gzip > "$FILE"
find "$DIR" -name 'carcare-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') zaxira tayyor: $FILE ($(du -h "$FILE" | cut -f1))"
