#!/bin/bash

# ExpiryGuard Database Backup Script
# Run this daily via cron: 0 2 * * * /path/to/backup.sh

BACKUP_DIR="/backups/expiryguard"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/expiryguard_backup_$DATE.sql"

# Create backup directory if it doesn't exist
mkdir -p $BACKUP_DIR

# Create backup
docker exec expiryguard-postgres-1 pg_dump -U expiryguard expiryguard > $BACKUP_FILE

# Compress backup
gzip $BACKUP_FILE

# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: ${BACKUP_FILE}.gz"