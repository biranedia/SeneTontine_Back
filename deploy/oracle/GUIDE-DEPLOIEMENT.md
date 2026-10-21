# Mettre SèneTontine en ligne

Architecture finale :

- **https://senetontine.com** : l'app web installable (PWA), hébergée gratuitement sur Cloudflare Pages.
- **https://api.senetontine.com** : le backend + la base PostgreSQL, sur un serveur Oracle Cloud gratuit, en HTTPS automatique (Caddy).
- Paiements des créateurs : NabooPay (Wave / Orange Money), confirmés par webhook signé.

Remplace partout `senetontine.com` par ton vrai domaine.

---

## 1. Le nom de domaine (≈ 10 €/an)

1. Crée un compte sur **cloudflare.com**.
2. *Domain Registration › Register Domains* : cherche ton nom (ex. `senetontine.com`) et achète-le. Cloudflare le vend à prix coûtant, sans surcoût au renouvellement.
3. Le domaine est alors géré dans Cloudflare (DNS gratuit).

## 2. Le code sur GitHub (privé)

Crée deux dépôts **privés** sur github.com : `senetontine-api` (dossier `senetontine-api\senetontine-api`) et `senetontine-app` (dossier `Senetontine`). Les fichiers secrets (`local.properties`, `.env`) sont déjà exclus par `.gitignore`.

## 3. Le serveur Oracle Cloud (gratuit à vie)

1. Inscris-toi sur **oracle.com/cloud/free**. Une carte bancaire est demandée pour vérifier ton identité ; rien n'est prélevé tant que tu restes sur les ressources « Always Free ». Région conseillée : **France Central (Paris)** ou **Spain Central (Madrid)** (la plus proche du Sénégal ; elle ne peut plus être changée ensuite).
2. *Compute › Instances › Create instance* :
   - Image : **Ubuntu 24.04**
   - Shape : **VM.Standard.A1.Flex**, 2 OCPU et 12 Go de mémoire (inclus dans le gratuit). Si Oracle répond « Out of capacity », réessaie plus tard ou choisis **VM.Standard.E2.1.Micro** (1 Go : mets alors `-Xmx512m` dans `JAVA_OPTS` de `docker-compose.yml`).
   - Télécharge la **clé SSH privée** proposée et garde-la précieusement.
3. Ouvre les ports web : instance › *Subnet* › *Security List* › *Add Ingress Rules* : source `0.0.0.0/0`, TCP, ports **80** et **443**.
4. Note l'**adresse IP publique** de l'instance.

Connecte-toi depuis PowerShell :

```
ssh -i C:\chemin\vers\cle.key ubuntu@IP_DU_SERVEUR
```

Puis, sur le serveur :

```
# Ouvrir les ports dans le pare-feu interne d'Ubuntu (fermés par défaut chez Oracle)
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Installer Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && exit
```

Reconnecte-toi (ssh), puis :

```
git clone https://github.com/TON-COMPTE/senetontine-api.git
cd senetontine-api/deploy/oracle
cp .env.example .env
openssl rand -base64 32        # copie le résultat dans DB_PASSWORD
nano .env                      # remplis domaine, e-mail, DB_PASSWORD (NabooPay plus tard)
```

## 4. Faire pointer api.senetontine.com vers le serveur

Cloudflare › ton domaine › *DNS* › *Add record* :

- Type **A**, nom **api**, contenu **IP_DU_SERVEUR**, proxy **désactivé (nuage gris, « DNS only »)**, pour que Caddy obtienne son certificat HTTPS.

Puis sur le serveur :

```
docker compose up -d --build
docker compose logs -f api      # attends « Started SenetontineApiApplication », puis Ctrl+C
```

Vérifie dans ton navigateur : **https://api.senetontine.com/actuator/health** doit afficher `{"status":"UP"}`.

## 5. L'app web sur Cloudflare Pages

Cloudflare › *Workers & Pages* › *Create* › *Pages* › *Connect to Git* › dépôt `senetontine-app` :

- Build command : `npx expo export --platform web`
- Output directory : `dist`
- Variable d'environnement : `EXPO_PUBLIC_API_URL` = `https://api.senetontine.com/api`

Une fois le site construit : *Custom domains* › ajoute **senetontine.com**. Ouvre-le sur ton téléphone Android : Chrome propose « Installer l'application ».

## 6. Activer les paiements NabooPay

1. Crée ton compte marchand sur **naboopay.com** et termine la vérification demandée.
2. *Paramètres › Intégration* : copie la **clé API** et crée un **webhook** vers
   `https://api.senetontine.com/api/billing/webhook/naboopay` ; copie son **secret**.
3. Sur le serveur : `nano .env` (NABOOPAY_API_KEY, NABOOPAY_WEBHOOK_SECRET), puis
   `docker compose up -d` pour appliquer.
4. Utilise le bouton de test du webhook dans NabooPay, puis fais un vrai paiement de 2 500 FCFA pour vérifier le parcours complet.

## 7. Entretien

- **Sauvegarde quotidienne** à 3h : `crontab -e` puis ajoute
  `0 3 * * * /home/ubuntu/senetontine-api/deploy/oracle/backup.sh >> /home/ubuntu/backup.log 2>&1`
  Télécharge régulièrement une copie hors du serveur (`scp`).
- **Mettre à jour le backend** après une modification poussée sur GitHub :
  `cd ~/senetontine-api && git pull && cd deploy/oracle && docker compose up -d --build`
- **L'app web** se met à jour toute seule à chaque `git push` sur `senetontine-app`.
- **Voir les erreurs** : `docker compose logs --tail 200 api`
