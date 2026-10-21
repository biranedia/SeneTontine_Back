#!/bin/bash
# =====================================================================================
# SèneTontine — installation automatique du serveur Oracle (aucune connexion SSH requise).
# À coller dans Oracle : Create instance › Show advanced options › Management ›
# « Paste cloud-init script ». Remplis seulement les 5 lignes ci-dessous avant de coller.
# =====================================================================================
GITHUB_TOKEN="COLLE_ICI_TON_JETON_GITHUB"     # jeton lecture seule du dépôt SeneTontine_Back
ACME_EMAIL="ton-email@exemple.com"            # e-mail pour le certificat HTTPS
ADMIN_PHONES="77XXXXXXX"                      # ton numéro, tel que saisi à l'inscription
ADMIN_PATH="bo-a9564d5a90fde3b6"              # adresse secrète de ton espace admin
APP_DOMAIN="senetontine.com"
# -------------------------------------------------------------------------------------
set -e
exec > /var/log/senetontine-install.log 2>&1

# Ports web (Ubuntu chez Oracle les ferme par défaut)
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
netfilter-persistent save || true

# Docker
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
systemctl enable --now docker

# Code
sudo -u ubuntu git clone "https://biranedia:${GITHUB_TOKEN}@github.com/biranedia/SeneTontine_Back.git" /home/ubuntu/senetontine-api
cd /home/ubuntu/senetontine-api/deploy/oracle

# Secrets (le mot de passe de la base est généré ici et ne quitte jamais le serveur)
cat > .env <<ENV
APP_DOMAIN=${APP_DOMAIN}
API_DOMAIN=api.${APP_DOMAIN}
ACME_EMAIL=${ACME_EMAIL}
DB_PASSWORD=$(openssl rand -hex 24)
NABOOPAY_API_KEY=
NABOOPAY_WEBHOOK_SECRET=
ADMIN_PHONES=${ADMIN_PHONES}
ADMIN_PATH=${ADMIN_PATH}
ENV
chmod 600 .env
chown -R ubuntu:ubuntu /home/ubuntu/senetontine-api

# Lancement (base + API + HTTPS) — redémarre tout seul après une panne
docker compose up -d --build

# Sauvegarde quotidienne de la base à 3 h
chmod +x backup.sh
echo "0 3 * * * ubuntu /home/ubuntu/senetontine-api/deploy/oracle/backup.sh >> /home/ubuntu/backup.log 2>&1" > /etc/cron.d/senetontine-backup

echo "INSTALLATION TERMINEE"
