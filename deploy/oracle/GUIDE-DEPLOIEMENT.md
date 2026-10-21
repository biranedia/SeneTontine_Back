# Mettre SèneTontine en ligne

Architecture finale :

- **https://senetontine.com** : l'app web installable (PWA), hébergée gratuitement sur Cloudflare Pages.
- **https://api.senetontine.com** : le backend + la base PostgreSQL, sur un serveur Oracle Cloud gratuit, en HTTPS automatique (Caddy).
- Paiements des créateurs : NabooPay (Wave / Orange Money), confirmés par webhook signé.

Remplace partout `senetontine.com` par ton vrai domaine.

---

## 1. Le nom de domaine chez Namecheap, géré par Cloudflare

1. Sur **namecheap.com**, achète `senetontine.com` (≈ 10 $/an). Refuse les options payantes proposées (hébergement, e-mail…). La protection WHOIS gratuite reste activée.
2. Crée un compte gratuit sur **cloudflare.com** › *Add a site* › `senetontine.com` › offre **Free**.
3. Cloudflare t'affiche **deux serveurs de noms** (ex. `xxx.ns.cloudflare.com`).
4. Namecheap › *Domain List* › *Manage* › *Nameservers* › **Custom DNS** › colle les deux serveurs Cloudflare › ✓.
5. Attends l'e-mail « senetontine.com is now active on Cloudflare » (de quelques minutes à quelques heures).

Le domaine reste payé chez Namecheap ; Cloudflare gère seulement le DNS (gratuit, très rapide, protection anti-attaque).

## 2. Le code sur GitHub

Les dépôts existent déjà : `biranedia/SeneTontine_Back` et `biranedia/SeneTontine_Front`. Passe-les en **privé** (*Settings › Danger Zone › Change visibility*). Les secrets (`local.properties`, `.env`) ne sont jamais envoyés (`.gitignore`).

Pour que le serveur puisse cloner un dépôt privé : GitHub › *Settings › Developer settings › Fine-grained tokens* › nouveau jeton, accès **en lecture seule** au seul dépôt `SeneTontine_Back` (*Contents: Read*). Garde-le, il sert de mot de passe à l'étape 3.

## 3. Le serveur Oracle Cloud (gratuit à vie)

1. Inscris-toi sur **oracle.com/cloud/free**. Une carte bancaire est demandée pour vérifier ton identité ; rien n'est prélevé tant que tu restes sur les ressources « Always Free ». Région conseillée : **France Central (Paris)** ou **Spain Central (Madrid)** (la plus proche du Sénégal ; elle ne peut plus être changée ensuite).
2. *Compute › Instances › Create instance* :
   - Image : **Ubuntu 24.04**
   - Shape : **VM.Standard.A1.Flex**, 2 OCPU et 12 Go de mémoire (inclus dans le gratuit). Si Oracle répond « Out of capacity », réessaie plus tard ou choisis **VM.Standard.E2.1.Micro** (1 Go : mets alors `-Xmx512m` dans `JAVA_OPTS` de `docker-compose.yml`).
   - Télécharge la **clé SSH privée** proposée et garde-la précieusement.
3. Ouvre les ports web : instance › *Subnet* › *Security List* › *Add Ingress Rules* : source `0.0.0.0/0`, TCP, ports **80** et **443**.
4. Note l'**adresse IP publique** de l'instance.
5. **Important pour que le serveur ne soit jamais coupé :** Oracle peut récupérer une instance gratuite jugée « inactive » sur un compte de base. Passe ton compte en **Pay As You Go** (*Billing › Upgrade*) : tu restes gratuit tant que tu utilises les ressources « Always Free », mais l'instance n'est plus jamais récupérée. Ajoute une alerte budget à 1 $ (*Billing › Budgets*) pour être prévenu au moindre centime.

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
git clone https://github.com/biranedia/SeneTontine_Back.git senetontine-api
# identifiant : biranedia ; mot de passe : le jeton GitHub de l'étape 2
cd senetontine-api/deploy/oracle
cp .env.example .env
openssl rand -base64 32        # copie le résultat dans DB_PASSWORD
openssl rand -hex 10           # ex. a1b2...  → ADMIN_PATH=bo-a1b2...
nano .env                      # domaine, e-mail, DB_PASSWORD, ADMIN_PATH, ADMIN_PHONES (NabooPay plus tard)
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

- Version Node : variable `NODE_VERSION` = `20`

Une fois le site construit : *Custom domains* › ajoute **senetontine.com** puis **www.senetontine.com** (Cloudflare crée les entrées DNS tout seul). Ouvre-le sur ton téléphone Android : Chrome propose « Installer l'application ».

**Ton espace admin :** inscris-toi d'abord dans l'app avec le numéro mis dans `ADMIN_PHONES`, puis redémarre l'API (`docker compose restart api`) : ton compte devient admin. Ensuite ouvre `https://senetontine.com/<ADMIN_PATH>/overview`. Enregistre-le dans tes favoris, ne le partage pas.

**Pourquoi c'est rapide et ne s'endort jamais :** l'API tourne en permanence sur ta propre machine (pas de mise en veille), la base est à côté d'elle (aucune latence réseau), et l'app est servie depuis le réseau mondial de Cloudflare, dont un point à Dakar.

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
- **Être alerté si le site tombe** : crée un moniteur gratuit sur **uptimerobot.com** vers `https://api.senetontine.com/actuator/health` (toutes les 5 min, alerte par e-mail). Les conteneurs redémarrent seuls après une panne ou un redémarrage du serveur (`restart: unless-stopped`).
