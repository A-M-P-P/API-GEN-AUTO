# Guide d'installation locale

Ce guide décrit comment installer et lancer **API-GEN-AUTO** sur votre propre machine, puis comment le brancher sur votre base Oracle.

---

## 1. Ce qu'il faut installer

Trois outils sont **obligatoires**. Oracle n'est nécessaire qu'à partir du profil `dev` : le
profil `local` utilise une base H2 embarquée, aucune installation de base n'est requise pour
découvrir l'application.

| Outil | Version | Indispensable pour | Vérification |
| --- | --- | --- | --- |
| **JDK Java** | 17 (LTS) | Compiler et lancer le backend | `java -version` |
| **Maven** | 3.9 ou plus | Construire le backend | `mvn -version` |
| **Node.js + npm** | Node 20 LTS, npm 10 | Compiler et lancer le studio Angular | `node -v` et `npm -v` |
| **Oracle Database** | **12c** (votre cible), 19c, 21c ou 23ai | Base de la plateforme en `dev`/`prod`, et source de données des pipelines | `sqlplus -V` |

> **Important** : Spring Boot 3.3 exige **Java 17 minimum**. Avec Java 11 vous obtiendrez une erreur `Unsupported class file major version` ou `invalid target release`. Angular 17 exige Node 18.13 ou 20+.

### Où les télécharger

- **Java 17** — Eclipse Temurin : https://adoptium.net/temurin/releases/?version=17 (choisir le *JDK*, pas le *JRE*, pour compiler)
- **Maven** — https://maven.apache.org/download.cgi (archive *Binary zip* ou *tar.gz*)
- **Node.js 20 LTS** — https://nodejs.org/ (l'installeur inclut npm)
- **Oracle Database Free 23ai** — https://www.oracle.com/database/free/get-started/ si vous n'avez pas encore d'instance

### Sous Windows

Après installation de Java et Maven, positionnez les variables d'environnement :

```powershell
# PowerShell, en adaptant les chemins
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot", "User")
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\apache-maven-3.9.9", "User")
[Environment]::SetEnvironmentVariable("Path", "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path", "User")
```

Fermez puis rouvrez le terminal pour que les variables soient prises en compte.

---

## 2. Démarrage rapide (5 minutes)

Ouvrez **deux terminaux** dans le dossier `API-GEN-AUTO`.

### Terminal 1 — le backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

> **Deux profils possibles.** `local` embarque une base H2 : rien à installer. `dev` (et `prod`)
> ciblent votre **Oracle 12c** (`SID=ORCL`, utilisateur `sigif`) — voir la section 4.3.

La première exécution télécharge les dépendances Maven (2 à 5 minutes selon votre connexion), puis démarre. Attendez la ligne :

```
Started ApiGenApplication in X.X seconds
```

Le backend écoute sur http://localhost:8080. Vérifiez :

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Terminal 2 — le studio

```bash
cd frontend
npm install
npm start
```

`npm install` ne sert que la première fois. Attendez :

```
➜  Local:   http://localhost:4200/
```

### Ouvrez le studio

**http://localhost:4200** — identifiants de démonstration :

| Utilisateur | Mot de passe |
| --- | --- |
| `admin` | `Admin2026!` |

Le formulaire de connexion a un bouton **« Remplir les champs »** qui saisit ces identifiants pour vous.

---

## 3. Vérifier que tout fonctionne

Une fois connecté, ces quatre vérifications confirment que l'ensemble de la chaîne tourne :

1. **Tableau de bord** — les compteurs affichent 1 API, 4 endpoints, 2 connexions et 1 organisation.
2. **APIs → API de démonstration** — sélectionnez l'endpoint « Dernier article » et cliquez **« Exécuter maintenant »**. L'appel vers JSONPlaceholder est tenté ; si la machine n'a pas accès à Internet, l'endpoint affiche tout de même un titre et un contenu de démonstration. Le test ne dépend donc pas du réseau.
3. **Connexions → JSONPlaceholder → Tester** — renvoie « Connexion HTTP établie » avec la latence mesurée si Internet est accessible ; sinon ce test seul échoue, sans empêcher la démo de fonctionner.
4. **Connexions → Fichiers d'échange → Tester** — renvoie le chemin du répertoire.

Si ces quatre points passent, l'application est opérationnelle.

---

## 4. Brancher votre base Oracle

Deux usages distincts — vous pouvez faire l'un, l'autre, ou les deux.

### 4.1. Utiliser votre Oracle comme **source de données** dans les pipelines

C'est le cas le plus simple et le plus utile au début. **Aucune configuration Spring à toucher**, tout se passe dans l'interface.

**Préparez un utilisateur** (avec SQL*Plus ou SQL Developer, en `SYSDBA`) :

```sql
-- Si l'instance est multitenant (12c et plus), entrez dans le PDB
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER APIGEN IDENTIFIED BY "ChangeMe_2026" QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE, CREATE VIEW, CREATE TABLE TO APIGEN;
```

Contrôlez en ligne de commande — si ceci échoue, le studio échouera pareil :

```bash
sqlplus APIGEN/ChangeMe_2026@//localhost:1521/FREEPDB1
```

**Dans le studio** : *Connexions → Nouvelle connexion*

| Champ | Valeur |
| --- | --- |
| Type | `Oracle` |
| Hôte | `localhost` |
| Port | `1521` |
| Service | `FREEPDB1` (ou `XEPDB1`, `orcl`…) |
| Adressage | `Service name` — ou `SID` sur une instance ancienne |
| Utilisateur | `APIGEN` |
| Mot de passe | votre mot de passe |
| Pool (max) | `5` |

Cliquez **« Tester la connexion »** : le résultat s'affiche avec la latence. Puis **« Découvrir les tables »** : l'arbre des schémas et des tables apparaît, vous pouvez copier un nom ou générer une étape `DB_QUERY` sans écrire de SQL.

### 4.2. Faire tourner la **plateforme elle-même** sur Oracle 12c (dev et prod)

C'est le réglage par défaut des profils `dev` et `prod` : `SID=ORCL`, utilisateur `sigif`,
mot de passe `sigif2015`. L'URL utilise la **syntaxe SID** (deux-points, pas de `//`) :

```
jdbc:oracle:thin:@localhost:1521:ORCL
```

Rien à écrire dans le code. Pour changer la cible, surchargez les variables :

```bash
cd backend
export APIGEN_PROFILE=prod
export APIGEN_JWT_SECRET="$(openssl rand -hex 48)"
export APIGEN_CRYPTO_KEY="$(openssl rand -base64 32)"
# Facultatif : uniquement si l'hôte, le port ou le SID diffèrent des valeurs par défaut
export ORACLE_HOST=db-host
export ORACLE_PORT=1521
export ORACLE_SID=ORCL
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Sous Windows PowerShell :

```powershell
$env:APIGEN_PROFILE = "prod"
$env:ORACLE_HOST = "localhost"; $env:ORACLE_PORT = "1521"; $env:ORACLE_SID = "ORCL"
$env:APIGEN_JWT_SECRET = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | % {[char]$_})
$env:APIGEN_CRYPTO_KEY = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 40 | % {[char]$_})
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Ou directement l'URL complète, si vous préférez la syntaxe service-name (pluggable database) :

```bash
export APIGEN_DB_URL="jdbc:oracle:thin:@//db-host:1521/FREEPDB1"
export APIGEN_DB_USER="sigif"
export APIGEN_DB_PASSWORD="sigif2015"
```

Au premier démarrage, **Flyway crée les tables** (`V1__init.sql`, `V2__organizations.sql`) et
l'utilisateur `admin`. Si le schéma `SIGIF` contient déjà d'autres tables, Flyway pose un
référentiel (`baseline-on-migrate`) au lieu d'échouer sur « schéma non vide sans historique ».

#### Si une table manque, elle est créée automatiquement

Un garde-fou de schéma s'exécute à chaque démarrage, après Flyway : il vérifie les huit tables
(`organizations`, `api_definitions`, `endpoints`, `connections`, `schedules`, `exchange_runs`,
`app_users`, `audit_events`) et **recrée celles qui manquent**, avec leurs index et contraintes.

Cas concret : la table `app_users` (utilisateurs de l'application) a été supprimée par erreur.

```
Table app_users absente : créée automatiquement.
Schéma auto-complété : 1 table(s) créée(s).
Compte admin créé : admin / Admin2026!
```

L'application redémarre normalement, le compte `admin` est rétabli. Aucun script SQL à rejouer.

> **Mot de passe en clair.** `sigif2015` figure dans les YAML des profils. En production,
> préférez `APIGEN_DB_PASSWORD` ou le fichier externe `config/runtime.properties` (hors WAR,
> non versionné).

> **Note** : `APIGEN_SEED_DEMO=false` en production. Si vous le laissez à `true`, l'API et les
> endpoints de démonstration sont recréés à chaque démarrage.

### 4.3. Organisations, contexte et rattachement des API

Une **organisation** possède zéro ou plusieurs API ; une API peut aussi rester « non classée ».
Chaque organisation dispose de **son propre contexte**, appliqué à toutes ses API.

1. Menu **Organisations** → **+ Nouvelle organisation** (code, nom, description, et éventuellement
   des paramètres avancés). Le code est le seul élément utilisé dans l'URL.
2. Menu **APIs → + Nouvelle API** → champ **Organisation** (ou **Aucune** pour une API non classée).
3. La liste des API est **groupée par organisation**, avec un filtre « Toutes les organisations »
   en haut de page.
4. Les appels à la passerelle utilisent toujours le code de l'organisation :
   `/{code-organisation}/api/gateway/{code-api}/...`. L'ancienne URL sans organisation n'est plus
   acceptée. Les API non classées sont accessibles sous `default`. Le préfixe d'organisation joue
   le rôle d'un contexte ; si un contexte global est réglé dans **Configuration**, il vient avant
   (ex. `/apigen/dgi/api/gateway/clients/...`).

Le **contexte d'organisation** ne doit pas être confondu avec le préfixe de l'URL : il ne modifie
pas la route. Il contient uniquement des valeurs propres aux API de l'organisation, utilisées dans
les pipelines. Le studio le génère depuis des champs guidés ; il n'y a rien à saisir en JSON.

En interne, ce contexte est un objet JSON :

```json
{
  "variables": { "region": "centre" },
  "secrets": { "apiToken": "valeur-secrète" },
  "overrides": {
    "rest": { "baseUrl": "https://erp.sigif.cm:8443/api" },
    "files": { "baseDir": "./data/exchanges/dgi" }
  }
}
```

- Le studio génère ce JSON depuis les champs guidés **Paramètres propres à cette organisation** :
  vous n'avez pas à saisir de JSON vous-même.
- `{{org.variables.region}}` et `{{org.secrets.apiToken}}` sont utilisables dans les étapes de pipeline.
- `org.overrides.rest.baseUrl` sert de base aux URL HTTP relatives sans connexion REST explicite.
- `org.overrides.files.baseDir` remplace la racine globale `APIGEN_FILES_BASE_DIR` pour les étapes
  fichier sans connexion explicite.
- Les secrets sont chiffrés en base et affichés masqués (`********`) dans le studio ; laisser le
  masque lors d'une modification conserve la valeur précédente.

Règles :

- le code d'une API est unique **dans son organisation** : deux organisations peuvent avoir une
  API `clients`, mais pas deux API `clients` dans la même organisation ;
- supprimer une organisation **ne supprime pas** ses API : elles deviennent « non classées » ;
- désactiver une organisation n'empêche pas ses API de fonctionner ;
- l'organisation par défaut est créée automatiquement au premier démarrage et recueille les API
  existantes lors d'une montée de version.

### 4.4. Formats acceptés et rendus par un endpoint

Dans l'éditeur d'API, rubrique **Réponse de la passerelle**, chaque endpoint choisit son format de
sortie : `JSON` (défaut), `TEXT`, `XML`, `FILE`, `BINARY` ou `REDIRECT`.

- `TEXT` renvoie le résultat en `text/plain` ; les identifiants d'exécution passent dans les
  en-têtes `X-Run-Id` et `X-Correlation-Id`.
- `XML` sérialise le résultat sous `<response>…</response>`, sauf si le pipeline produit déjà une
  chaîne XML.
- `FILE` télécharge le fichier écrit par une étape `FILE_WRITE` (le champ `path` du résultat sert
  de source) avec l'en-tête `Content-Disposition`.
- `BINARY` décode une valeur base64 issue des champs `base64`, `content` ou `data` du résultat.
- `REDIRECT` répond `302` vers `redirectLocation` ; `{runId}` et `{correlationId}` y sont remplacés.

En entrée, la passerelle détecte le format via le `Content-Type` : JSON, XML, texte, formulaire
urlencodé, `multipart/form-data` (fichiers exposés en base64 dans `request.files.<nom>.base64`)
et binaire (`request.body` en base64). Les requêtes sont limitées à 20 Mo.

Exemple avec un XML entrant :

```bash
curl -X POST http://localhost:8081/default/api/gateway/mon-api/orders \
  -H "Content-Type: application/xml" \
  -d '<order><reference>CM-123</reference></order>'
```

Le pipeline lit alors `{{request.body.order.reference}}`.

---

## 5. Modifier les adresses, ports et paramètres sans rebuild

Tout ce qui est réglé dans `application.yml` est figé dans le WAR/EAR une fois construit. Pour
changer une valeur **sans recompiler**, la plateforme lit un fichier de propriétés externe au
démarrage.

#### Deux bases bien distinctes

1. **La base de la plateforme** : elle contient les utilisateurs, organisations, API, endpoints,
   connexions, historique d'exécution et audit. C'est `spring.datasource.*` qui la désigne.
   La base H2 fichier (`./data/apigen`) n'est utilisée **que** avec le profil `local`. Le profil par
   défaut est `dev` (Oracle) et un déploiement doit utiliser `APIGEN_PROFILE=prod` (Oracle). Si la
   base visée n'est pas joignable, le backend **ne démarre pas** : il ne bascule pas silencieusement
   sur H2. Flyway crée ou met à jour les tables de cette base automatiquement.
2. **Les connexions** : ce sont les systèmes cibles des pipelines (ERP, API, fichiers). Elles sont
   enregistrées *dans* la base de la plateforme et peuvent être déclarées avant le premier login
   dans `config/connections.json` (voir ci-dessous), ou créées ensuite dans le studio.

#### Préparer un déploiement sans passer par l'interface

```bash
cp config/runtime.properties.example config/runtime.properties
cp config/connections.json.example config/connections.json
# Éditer les deux fichiers, puis lancer le backend :
set APIGEN_PROFILE=prod
java -jar apigen-platform.war
```

Points importants :

- **Le profil se choisit avant le démarrage** (`APIGEN_PROFILE=prod` ou
  `--spring.profiles.active=prod`) : il ne peut pas être changé dans `runtime.properties`.
- La configuration du studio (adresse du backend) se règle dans `config.json` côté frontend,
  comme décrit en 5.5 ; elle n'a rien à voir avec la base de la plateforme.
- Au premier démarrage, un administrateur est créé. Son identifiant et son mot de passe initiaux
  peuvent être fournis par `apigen.seed.admin-username` / `apigen.seed.admin-password`
  (`APIGEN_ADMIN_PASSWORD`). Changez-le ensuite depuis le studio.
- `config/connections.json` est lu au démarrage ; les connexions déjà présentes ne sont jamais
  écrasées. Les identifiants sont chiffrés avec `apigen.crypto.key`.
- Les mots de passe de `runtime.properties` sont en clair : protégez le fichier ou utilisez les
  variables d'environnement.

### 5.1. Le fichier `runtime.properties`

Emplacement par défaut :

```
config/runtime.properties     # relatif au répertoire depuis lequel vous lancez le serveur
```

Ordre de priorité appliqué au démarrage :

```
ligne de commande  >  config/runtime.properties  >  variables d'environnement  >  application.yml
```

Exemple de contenu :

```properties
server.port=9090
spring.datasource.url=jdbc:oracle:thin:@//db-host:1521/FREEPDB1
spring.datasource.username=apigen
apigen.scheduler.poll-interval-ms=30000
```

Le fichier n'a pas besoin d'exister au premier démarrage : il est créé à la première
enregistrement depuis l'interface. Pour le placer ailleurs (partage réseau, volume monté) :

```bash
# Variable d'environnement
set APIGEN_RUNTIME_CONFIG=D:\apigen\conf\runtime.properties

# Ou en argument de lancement
java -jar apigen-platform.war --apigen.runtime-config=/opt/apigen/conf/runtime.properties
```

### 5.2. Depuis le studio

Menu **Configuration** (rôle `ADMIN` requis). L'écran affiche :

- le profil actif, le port d'écoute, le chemin de contexte et le chemin du fichier externe ;
- les 14 propriétés éditables, groupées par thème (base de données, serveur, sécurité,
  fonctionnement) ;
- pour chacune, sa **source réelle** : `application.yml`, *Fichier externe*, variable
  d'environnement ou valeur par défaut.

Modifiez, puis **Enregistrer** : le fichier est réécrit, l'action est tracée dans le journal
d'audit (`CONFIG_UPDATE`), et les valeurs s'appliquent **au redémarrage suivant**.

Deux règles à connaître :

- un champ de secret affiche `••••••••` : laissez-le tel quel pour conserver la valeur actuelle ;
- une valeur vide supprime la ligne du fichier (retour à `application.yml`).

### 5.3. Depuis l'API

```bash
# Lire
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/config

# Écrire
curl -X PUT http://localhost:8080/api/config \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"values":{"server.port":"9090","apigen.audit.retention-days":"30"}}'
```

Réponse : `{ "changed": [...], "ignored": [...], "writtenTo": "...", "restartRequired": true }`.
Les clés inconnues sont listées dans `ignored`, jamais écrites.

### 5.4. Ce que ce mécanisme ne règle pas

- **Le port d'écoute** et l'adresse réseau appartiennent au serveur d'application. Dans un
  WebLogic, ils se définissent dans la console d'administration (ou dans `config.xml`), jamais
  dans le WAR : `server.port` n'a d'effet que si vous lancez le JAR exécutable embarqué.
- **Le redémarrage reste obligatoire** : la configuration est lue au démarrage, il n'y a pas de
  rechargement à chaud.
- **Les secrets sont en clair** dans le fichier. En production, préférez les variables
  d'environnement ou un coffre à secrets.

### 5.5. Backend et studio sur deux serveurs différents

Le studio Angular n'a pas besoin d'être hébergé avec le backend. Son adresse cible est lue dans un
petit fichier **`config.json`** placé à côté de l'application (`dist/apigen-studio/config.json`
après build, ou `frontend/public/config.json` dans les sources). Ce fichier est lu au chargement de
la page : **modifiable sans rebuild ni redéploiement**, il suffit de recharger le navigateur.

```json
{
  "backendUrl": "http://serveur-backend:8081",
  "contextPath": "/apigen"
}
```

- `backendUrl` vide (valeur par défaut) : le studio appelle son propre serveur. En développement,
  c'est le proxy Angular (`frontend/proxy.conf.js`) ; en production, c'est nginx.
- `backendUrl` renseigné : URL complète du backend, avec son port. Le chemin de contexte peut être
  mis ici directement (`"...:8081/apigen"`) ou dans `contextPath` — pas dans les deux.
- `contextPath` : préfixe global (`/apigen`) défini dans **Configuration** côté backend.

Le studio affiche l'adresse retenue dans **Configuration → Adresse du backend utilisée par le
studio**. Si vous n'avez pas accès au fichier déployé, l'écran de connexion propose
**Paramètres de connexion (avancé)** : saisissez l'adresse et le chemin de contexte, puis
**Appliquer**. La valeur est mémorisée dans ce navigateur ; **Réinitialiser** revient au
`config.json`.

Si le backend est sur une autre origine (autre host ou autre port), autorisez le studio
dans les CORS du backend :

```bash
set APIGEN_CORS_ORIGINS=http://studio.example.com,http://localhost:4200
```

Exemple en développement (le proxy Angular lit ces variables) :

```bash
set APIGEN_BACKEND=http://serveur-backend:8081
set APIGEN_CONTEXT_PATH=/apigen
npm start
```

Avec Docker Compose, les mêmes réglages passent par l'environnement :

```bash
BACKEND_URL=http://serveur-backend:8081 CONTEXT_PATH=/apigen docker compose up -d
```

Le script `frontend/docker-entrypoint.sh` régénère alors `config.json` au démarrage du conteneur.

### 5.6. Passage en production (Tomcat + frontend statique)

Le backend est désormais packagé en **WAR** pour Tomcat **10.1+** ; le frontend est un dossier
statique destiné à WebLogic ou Apache/XAMPP/WAMP. La procédure complète est décrite dans
[`DEPLOY-PROD.md`](DEPLOY-PROD.md) : modèles `.env` / `runtime.properties` / `connections.json`,
`setenv.sh` Tomcat, adresse du backend dans `config.json`, CORS et checklist de mise en prod.
Le profil par défaut reste `dev` : définissez `APIGEN_PROFILE=prod` avant le démarrage.

### 5.7. Et le déploiement sur WebLogic 12c ?

**Point bloquant à connaître avant tout packaging.** Spring Boot 3.3 repose sur Jakarta EE 9+
(`jakarta.*`) et exige Java 17. WebLogic 12c (12.2.1.x) est certifié **Java EE 7 / JDK 8** :
il ne fournit que `javax.*`. Le WAR produit ne démarre donc pas sur 12c.

| Version WebLogic | Spécifications | Spring Boot 3.3 |
| --- | --- | --- |
| 12c (12.2.1.x) | Java EE 7, JDK 8 | incompatible |
| 14.1.2 | Jakarta EE 8 | incompatible |
| 15.1.1 | Jakarta EE 9.1, JDK 17/21 | **compatible** |

Options :

1. **WebLogic 15.1.1** : aucune modification du code, déployez le WAR/EAR et réglez port et
   contexte dans la console.
2. **Rester sur 12c** : rétrograder toute la plateforme vers Spring Boot 2.7.18 (`javax.*`,
   JDK 8). C'est une refonte majeure (Hibernate 5, Spring Security 5, Jakarta → javax partout).
3. **Contourner** : lancer le JAR exécutable derrière un WebLogic frontal si la contrainte porte
   sur le domaine et non sur le conteneur.

---

## 6. Dépannage

### `java -version` renvoie 11 ou moins

Java 17 n'est pas installé, ou n'est pas celui utilisé. Vérifiez `JAVA_HOME` et que `%JAVA_HOME%\bin` précède les autres entrées dans `Path`.

### `mvn n'est pas reconnu`

Maven n'est pas dans le `Path`. Vérifiez `MAVEN_HOME` et rouvrez le terminal.

### `npm install` échoue ou est très lent

C'est le téléchargement normal des dépendances Angular (200 à 400 Mo). Si un proxy d'entreprise bloque :

```bash
npm config set proxy http://votre-proxy:port
npm config set https-proxy http://votre-proxy:port
```

### Le port 8080 est déjà occupé

C'est **l'erreur la plus fréquente au premier lancement**. Si le log se termine par :

```
Web server failed to start. Port 8080 was already in use.
```

**Tout le reste a fonctionné** — Flyway a validé la base, JPA s'est initialisé, la sécurité est en place. Seul le port est pris.

#### Identifier le coupable (Windows)

```powershell
netstat -ano | findstr :8080
```

La dernière colonne est le PID. Identifiez le processus :

```powershell
tasklist /FI "PID eq 12345"
```

Puis tuez-le :

```powershell
taskkill /PID 12345 /F
```

Dans la plupart des cas, le coupable est **une instance précédente de la même application** — un run IntelliJ qui n'a pas été arrêté, ou un terminal `mvn spring-boot:run` resté ouvert. Vérifiez :

- Dans IntelliJ : le bouton **Stop** (carré rouge) dans la barre d'outils, ou la fenêtre **Services**.
- Les terminaux où vous auriez lancé `mvn spring-boot:run`.
- Un conteneur Docker qui publie le port 8080 : `docker ps`.

Sous Linux ou macOS :

```bash
lsof -i :8080
kill -9 <PID>
```

#### Ou changer de port

Si vous ne pouvez pas libérer le 8080 (service système, autre projet), lancez le backend sur un autre port :

```powershell
$env:SERVER_PORT = "8081"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Dans IntelliJ : **Run → Edit Configurations → Environment variables** → ajoutez `SERVER_PORT=8081`.

Le studio Angular trouve le backend via `frontend/proxy.conf.js`. Ce proxy cible
`http://localhost:8081` (ou `APIGEN_BACKEND`) et **détecte automatiquement le chemin de contexte**
réellement appliqué par le backend (`/actuator/health` testé avec et sans préfixe). Il n'a donc pas
besoin de savoir depuis quel dossier le backend a été lancé. Après avoir changé le chemin dans
**Configuration** et redémarré le backend, rechargez la page du studio.

La ligne suivante apparaît dans le terminal où tourne `npm start` et indique le préfixe retenu :

```
[api-gen] proxy : backend http://localhost:8081, préfixe actif = /apigen
```

Si un 404 survient sur la connexion (backend redémarré entre-temps), le proxy relance la détection
et l'écran de connexion réessaie une fois automatiquement. En dernier recours, redémarrez
`npm start` (le proxy est lu au démarrage du serveur Angular).

Si le backend tourne sur un autre port, utilisez `APIGEN_BACKEND=http://localhost:8080 npm start`.
Le chemin peut aussi être forcé avec `APIGEN_CONTEXT_PATH=/apigen`.

> Les deux serveurs doivent tourner **simultanément** : le backend sur 8080 (ou 8081), le studio sur 4200. Le studio ne parle jamais directement à la base ; il passe par le backend via `/api/`.

### Le studio affiche une page blanche ou des erreurs d'appel API

Le backend n'est pas démarré, ou il est sur un autre port. Le proxy Angular renvoie `/api/` vers `http://localhost:8080` — vérifiez que le backend répond sur ce port.

### `npm start` plante avec `JavaScript heap out of memory`

La compilation Angular est gourmande. Allouez plus de mémoire :

```bash
set NODE_OPTIONS=--max-old-space-size=4096
npm start
```

### Oracle : `ORA-12541: no listener`

Le listener n'est pas démarré :

```bash
lsnrctl start
```

### Oracle : `ORA-12505` ou `ORA-12514`

Le listener ne connaît pas le service ou le SID fourni. Vérifiez le nom exact :

```bash
lsnrctl status
```

Puis, dans le formulaire, choisissez l'adressage **`Service name`** (le plus courant) plutôt que `SID`, et reprenez le nom affiché par `lsnrctl`.

### Oracle : `ORA-01882: timezone region not found`

Erreur classique sur **Oracle 10g/11g** : le pilote JDBC envoie le nom de la région de la JVM
(`Africa/Douala`) et le fichier de fuseaux de la base ne la connaît pas.

**Correctif intégré** : toute connexion Oracle reçoit automatiquement
`oracle.jdbc.timezoneAsRegion=false`, ce qui force l'envoi d'un décalage numérique. Rien à
faire dans le studio ni dans le code — ni pour l'API, ni pour la base de la plateforme
(`dev`/`prod`).

Si une base exige l'ancien comportement, ouvrez la connexion → **Propriétés du pilote JDBC**
et saisissez :

```json
{ "oracle.jdbc.timezoneAsRegion": "true" }
```

### Oracle 10g : `ORA-00604` / `ORA-12705: impossible d'accéder aux fichiers de données NLS`

Le pilote Oracle exécute implicitement, à chaque connexion, une instruction du type :
`ALTER SESSION SET TIME_ZONE='...' NLS_LANGUAGE='...' NLS_TERRITORY='...'`. Les deux dernières
valeurs sont déduites du `Locale` de la JVM. Avec un poste configuré en français/Cameroun, la
10g peut recevoir un territoire qu'elle ne connaît pas et répondre `ORA-12705`, enveloppé dans
`ORA-00604` au niveau SQL récursif.

**Correctif intégré** : `JdbcConnectionProps` initialise aussi, au premier chargement :

```
oracle.jdbc.languageMap  = <lang>=AMERICAN;<LANG>=AMERICAN
oracle.jdbc.territoryMap = <pays>=AMERICA;<PAYS>=AMERICA
```

Le Locale de l'application n'est pas modifié ; seules les valeurs envoyées à Oracle sont
neutralisées. Vous pouvez forcer d'autres valeurs dans les VM options d'IntelliJ, par exemple :

```
-Doracle.jdbc.languageMap=fr=FRENCH;FR=FRENCH
-Doracle.jdbc.territoryMap=cm=FRANCE;CM=FRANCE
```

Le correctif n'est appliqué que si les propriétés ne sont pas déjà définies. Si l'erreur
persiste, vérifiez aussi la variable d'environnement `NLS_LANG` de Windows (registre
`HKEY_LOCAL_MACHINE\SOFTWARE\ORACLE`) : une valeur invalide produit la même erreur.

> **Limite** : le livrable utilise `ojdbc11` 23.5 (Java 17). Selon la matrice de compatibilité
> Oracle, ce pilote est certifié pour les bases 19c/21c/23ai, **pas pour la 10g**. Les
> correctifs ci-dessus permettent souvent de se connecter, mais pour un support officiel de la
> 10g il faut un pilote ancien (ojdbc6 11.2) et donc une JVM 8 — incompatible avec Spring Boot 3.3.
> La migration de la base vers 11g/12c reste la voie recommandée.

### Oracle : `ORA-18716: ... n'est dans aucun fuseau horaire` au démarrage

Au lancement, le contexte Spring échoue dans `DataInitializer` avec
`Could not extract column ... from JDBC ResultSet` et, dans la cause,
`oracle.jdbc.driver.TimestampAccessor.getOffsetDateTime`.

Hibernate 6.5 mappe par défaut `java.time.Instant` sur `TIMESTAMP_UTC` : la lecture passe par
`ResultSet.getObject(OffsetDateTime.class)`. Sur Oracle 12c, une colonne `DATE` (y compris dans
un schéma préexistant) ne porte aucun fuseau et le pilote renvoie `ORA-18716`.

**Correctif intégré** : `application.yml` force
`spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`. Les horodatages
sont alors lus et écrits comme des `TIMESTAMP` ordinaires, compatibles avec les colonnes
`DATE`/`TIMESTAMP` d'Oracle et de H2. Aucune modification du schéma n'est nécessaire. Après une
mise à jour du code, reconstruisez le backend pour que `application.yml` soit recopié dans
`target/classes` (dans IntelliJ : *Build → Rebuild Project*, puis relancez l'application).

Autres causes possibles d'un échec sur une base ancienne :

- `ORA-28040: No matching authentication protocol` — le serveur refuse les anciens protocoles.
  Côté base 12c+ : `SQLNET.ALLOWED_LOGON_VERSION=8` dans `sqlnet.ora`, puis redémarrage.
- `ORA-12505` / `ORA-12514` — SID ou service-name inconnu : voir les sections correspondantes.
- `IAIK` / `Unsupported version` — pilote trop récent pour la base : ajouter un pilote plus
  ancien dans le classpath (ojdbc8 pour 11g/12c).

### Oracle : `ORA-01017: invalid username/password`

Identifiants refusés. Recréez l'utilisateur, ou déverrouillez-le :

```sql
ALTER USER APIGEN ACCOUNT UNLOCK;
```

### Oracle : l'arbre « Découvrir les tables » reste vide alors que la connexion est validée

L'utilisateur n'a pas les droits pour voir les objets. Deux solutions :

```sql
-- Voir tous les objets accessibles
GRANT SELECT ANY DICTIONARY TO APIGEN;

-- Ou plus large
GRANT SELECT_CATALOG_ROLE TO APIGEN;
```

### La compilation Angular échoue avec des erreurs `NG2` ou `NG9`

Vous avez probablement modifié un template. Deux pièges connus dans ce projet :
- **Aucune accolade littérale dans le texte d'un template** (provoque `Invalid ICU message`). Utilisez `{{ }}` uniquement pour l'interpolation.
- `@else if (...)` **ne supporte pas** la syntaxe `as alias` en Angular 17 — utilisez l'assertion `result()!` à la place.

---

## 7. Contenu de l'archive

```
API-GEN-AUTO/
├── GUIDE-LOCAL.md              ← ce guide
├── README.md                   ← documentation complète (architecture, API, pipelines)
├── Makefile                    ← raccourcis : make run-backend, make run-frontend…
├── .env.example                ← modèle des variables d'environnement
├── docker-compose.yml          ← Oracle 23ai + backend + frontend
│
├── backend/                    ← Spring Boot 3.3, Java 17
│   ├── pom.xml
│   ├── Dockerfile
│   ├── config/
│   │   ├── runtime.properties.example   ← modèle de configuration externe
│   │   └── runtime.properties            ← généré par le studio (à ne pas versionner)
│   └── src/
│       ├── main/java/com/apigen/
│       │   ├── config/         ← sécurité JWT, chiffrement, planification, garde-fou de schéma
│       │   ├── domain/         ← entités JPA
│       │   ├── service/        ← logique métier
│       │   │   └── connector/  ← REST, JDBC, fichiers, pool HikariCP
│       │   ├── execution/      ← moteur de pipelines, templates, étapes
│       │   └── web/            ← contrôleurs REST + passerelle dynamique
│       ├── main/resources/
│       │   ├── application*.yml     ← local (H2), dev et prod (Oracle 12c)
│       │   └── db/migration/         ← V1__init.sql, V2__organizations.sql
│       └── test/               ← tests d'intégration
│
├── frontend/                   ← Angular 17, TypeScript, SCSS
│   ├── package.json
│   ├── angular.json
│   ├── proxy.conf.js
│   └── src/app/
│       ├── core/               ← services, guards, interceptors, modèles
│       ├── features/           ← login, dashboard, organisations, apis, connexions, runs…
│       ├── shared/             ← composants réutilisables (explorateur de schémas)
│       └── shell/
│
└── docs/screenshots/           ← captures de l'interface
```

---

## 8. Commandes utiles

```bash
make install         # build backend + install frontend
make run-backend     # démarrer le backend (profil dev)
make run-frontend    # démarrer le studio
make test            # tests backend
make build           # build de production backend + frontend
make docker          # tout en Docker avec Oracle 23ai
```

Sans `make`, les équivalents directs :

```bash
cd backend && mvn -q -DskipTests package          # build du jar
cd backend && mvn test                            # tests
cd frontend && npm run build                      # build de production
```

Le WAR exécutable produit par le build se trouve dans `backend/target/apigen-platform.war` et se lance avec :

```bash
java -jar backend/target/apigen-platform.war --spring.profiles.active=dev
```
