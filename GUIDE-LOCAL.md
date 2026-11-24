# Guide d'installation locale

Ce guide décrit comment installer et lancer **API-GEN-AUTO** sur votre propre machine, puis comment le brancher sur votre base Oracle.

---

## 1. Ce qu'il faut installer

Trois outils sont **obligatoires**. Oracle est **optionnel** pour démarrer : le profil `dev` utilise une base H2 embarquée, aucune installation de base n'est nécessaire pour voir l'application tourner.

| Outil | Version | Indispensable pour | Vérification |
| --- | --- | --- | --- |
| **JDK Java** | 17 (LTS) | Compiler et lancer le backend | `java -version` |
| **Maven** | 3.9 ou plus | Construire le backend | `mvn -version` |
| **Node.js + npm** | Node 20 LTS, npm 10 | Compiler et lancer le studio Angular | `node -v` et `npm -v` |
| **Oracle Database** | 11g, 12c+, 19c, 21c ou 23ai | Uniquement si vous voulez brancher votre vraie base | `sqlplus -V` |

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
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

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
| `admin` | `ChangeMe123!` |

Le formulaire de connexion a un bouton **« Remplir les champs »** qui saisit ces identifiants pour vous.

---

## 3. Vérifier que tout fonctionne

Une fois connecté, ces quatre vérifications confirment que l'ensemble de la chaîne tourne :

1. **Tableau de bord** — les compteurs affichent 1 API, 4 endpoints, 2 connexions.
2. **APIs → API de démonstration** — sélectionnez l'endpoint « Dernier article » et cliquez **« Exécuter maintenant »**. Un appel REST réel vers JSONPlaceholder part, le résultat s'affiche en ~350 ms.
3. **Connexions → JSONPlaceholder → Tester** — renvoie « Connexion HTTP établie » avec la latence mesurée.
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

### 4.2. Faire tourner la **plateforme elle-même** sur Oracle

La base qui contient les APIs, endpoints, exécutions et l'audit devient votre Oracle au lieu de H2.

```bash
cd backend
export APIGEN_PROFILE=prod
export APIGEN_DB_URL="jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
export APIGEN_DB_USER="APIGEN"
export APIGEN_DB_PASSWORD="ChangeMe_2026"
export APIGEN_JWT_SECRET="$(openssl rand -hex 48)"
export APIGEN_CRYPTO_KEY="$(openssl rand -base64 32)"
export APIGEN_SEED_DEMO=false
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Sous Windows PowerShell, remplacez les `export` par :

```powershell
$env:APIGEN_PROFILE = "prod"
$env:APIGEN_DB_URL = "jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
$env:APIGEN_DB_USER = "APIGEN"
$env:APIGEN_DB_PASSWORD = "ChangeMe_2026"
$env:APIGEN_JWT_SECRET = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | % {[char]$_})
$env:APIGEN_CRYPTO_KEY = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 40 | % {[char]$_})
$env:APIGEN_SEED_DEMO = "false"
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Au démarrage, **Flyway crée automatiquement les 7 tables** (`api_definitions`, `endpoints`, `connections`, `schedules`, `exchange_runs`, `app_users`, `audit_events`) ainsi que l'utilisateur `admin`. Vous verrez :

```
Successfully applied 1 migration to schema "APIGEN", now at version v1
```

> **Note** : `APIGEN_SEED_DEMO=false` en production. Si vous le laissez à `true`, l'API et les endpoints de démonstration sont recréés à chaque démarrage.

---

## 5. Dépannage

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

```bash
SERVER_PORT=8081 mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Pensez à ajuster `target` dans `frontend/proxy.conf.json` (qui pointe sur `http://localhost:8080`).

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

## 6. Contenu de l'archive

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
│   └── src/
│       ├── main/java/com/apigen/
│       │   ├── config/         ← sécurité JWT, chiffrement, planification
│       │   ├── domain/         ← entités JPA
│       │   ├── service/        ← logique métier
│       │   │   └── connector/  ← REST, JDBC, fichiers, pool HikariCP
│       │   ├── execution/      ← moteur de pipelines, templates, étapes
│       │   └── web/            ← contrôleurs REST + passerelle dynamique
│       ├── main/resources/
│       │   ├── application*.yml
│       │   └── db/migration/V1__init.sql
│       └── test/               ← tests d'intégration
│
├── frontend/                   ← Angular 17, TypeScript, SCSS
│   ├── package.json
│   ├── angular.json
│   ├── proxy.conf.json
│   └── src/app/
│       ├── core/               ← services, guards, interceptors, modèles
│       ├── features/           ← login, dashboard, apis, connexions, runs…
│       ├── shared/             ← composants réutilisables (explorateur de schémas)
│       └── shell/
│
└── docs/screenshots/           ← captures de l'interface
```

---

## 7. Commandes utiles

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

Le jar exécutable produit par le build se trouve dans `backend/target/apigen-platform.jar` et se lance avec :

```bash
java -jar backend/target/apigen-platform.jar --spring.profiles.active=dev
```
