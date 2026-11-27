# Mise en production — backend Tomcat, frontend WebLogic / Apache

Ce guide décrit le déploiement retenu : **backend en WAR dans Tomcat**, **frontend statique dans
WebLogic ou XAMPP/WAMP (Apache)**. Les deux applications ne sont pas sur le même serveur :
l'adresse du backend est déclarée côté frontend dans `config.json`.

## 1. Pré-requis

| Composant | Version requise | Pourquoi |
| --- | --- | --- |
| Java | 17 | Spring Boot 3.3 |
| Tomcat | **10.1+** | Jakarta EE 10 (`jakarta.*`). Tomcat 9 (javax) est incompatible. |
| Oracle | 12c+ recommandé | Base de la plateforme. H2 reste réservé au profil `local`. |
| Frontend | Serveur statique | WebLogic (application explodée) ou Apache/XAMPP/WAMP. |

> WebLogic 12c/14 ne peut pas exécuter ce WAR (voir README, tableau de compatibilité). Il peut en
> revanche héberger le frontend statique sans problème.

## 2. Fichiers à préparer

À partir du dépôt :

```bash
cp .env.example .env                                   # variables du backend
cp config/runtime.properties.example /opt/apigen/config/runtime.properties
cp config/connections.json.example /opt/apigen/config/connections.json
```

Tomcat ne lit pas automatiquement le fichier `.env` : il sert de modèle. Reportez ses valeurs dans
`setenv.sh` (variables d'environnement) ou dans `runtime.properties`. Remplacez **toutes** les
valeurs `A_REMPLACER` et `SERVEUR_ORACLE` :

| Fichier | Contenu |
| --- | --- |
| `.env` | profil `prod`, secrets JWT/chiffrement, CORS, mot de passe admin initial, emplacement des fichiers externes |
| `config/runtime.properties` | URL/utilisateur de la base plateforme, secrets, réglages de fonctionnement, liste des connexions |
| `config/connections.json` | connexions des pipelines (ERP, API, fichiers) créées au premier démarrage |

N'utilisez qu'un seul emplacement par secret (fichier **ou** environnement). Le fichier externe
est prioritaire sur les variables d'environnement.

## 3. Backend dans Tomcat

```bash
cd backend
mvn -B -DskipTests package
# target/apigen-platform.war
cp target/apigen-platform.war $CATALINA_HOME/webapps/apigen.war
```

**Le contexte est le nom du WAR** : `apigen.war` donne `/apigen`. Les propriétés `server.port` et
`server.servlet.context-path` du fichier externe ne s'appliquent pas ici.

Créez `$CATALINA_HOME/bin/setenv.sh` :

```sh
export APIGEN_PROFILE=prod
export APIGEN_RUNTIME_CONFIG=/opt/apigen/config/runtime.properties
export CATALINA_OPTS="$CATALINA_OPTS -Dapigen.runtime-config=/opt/apigen/config/runtime.properties"
```

Le profil doit être fixé **avant** le démarrage : il ne peut pas être lu depuis
`runtime.properties`. Les réglages NLS Oracle sont appliqués automatiquement par
`ServletInitializer` avant l'initialisation de Spring (aucun flag JVM à ajouter).

Au démarrage, vérifiez dans les logs :

```
Configuration externe chargée : /opt/apigen/config/runtime.properties (… propriétés)
Connexions initiales : 3 créée(s), 0 ignorée(s) depuis /opt/apigen/config/connections.json
Compte administrateur créé : admin — …
```

Flyway crée ou met à jour les tables dans la base configurée : aucune création manuelle.

## 4. Frontend statique

```bash
cd frontend
npm ci
npm run build
# copier le contenu de dist/apigen-studio dans le docroot Apache/WebLogic
```

Éditez `config.json` **dans le dossier déployé** (à côté de `index.html`) :

```json
{ "backendUrl": "http://tomcat.example.com:8080/apigen", "contextPath": "" }
```

- Ne mettez pas `/apigen` dans les deux champs.
- Laissez `backendUrl` vide uniquement si le frontend et le backend sont servis sous la même origine
  (reverse proxy).
- Aucun rebuild après modification de `config.json` : rechargez le navigateur.
- L'adresse effective est visible dans **Configuration → Adresse du backend utilisée par le studio**.

## 5. CORS et première connexion

Dans `runtime.properties` (ou `.env`) :

```properties
apigen.cors.allowed-origins=http://studio.example.com
```

L'origine doit être exacte : protocole, hôte et port, sans slash final.

Connectez-vous avec l'administrateur créé au démarrage, puis **changez immédiatement son mot de
passe** depuis le studio. Une fois connecté, le menu **Configuration** permet d'ajuster les
paramètres non critiques ; redémarrez Tomcat pour les appliquer.

Les URL publiques des API suivent le format :

```
http://tomcat.example.com:8080/apigen/{code-organisation}/api/gateway/{code-api}/...
```

## 6. Checklist rapide

- [ ] Tomcat 10.1+ / Java 17
- [ ] `apigen.war` déployé (le nom fixe `/apigen`)
- [ ] `APIGEN_PROFILE=prod` dans `setenv.sh`
- [ ] `APIGEN_RUNTIME_CONFIG` pointe sur le fichier externe
- [ ] Base plateforme renseignée et joignable
- [ ] Secrets JWT/chiffrement remplacés
- [ ] `apigen.cors.allowed-origins` = origine exacte du studio
- [ ] `config/connections.json` renseigné
- [ ] `config.json` du frontend pointe sur `http://tomcat:8080/apigen`
- [ ] Mot de passe admin changé après la première connexion

## Schéma Oracle dédié (à préparer avant démarrage)

La plateforme utilise désormais le propriétaire **APIGEN** par défaut, et non
un compte métier. Faire exécuter le script DBA
`database/oracle/create-apigen-schema.sql` dans la bonne PDB, puis configurer le
compte et son mot de passe dans le fichier runtime ou l’environnement.
Voir **[SCHEMA-ORACLE.md](SCHEMA-ORACLE.md)** pour les droits, les contrôles et la
reprise d’une installation existante. Changer seulement le compte JDBC ne migre
pas les données. Les profils Oracle refusent un schéma non vide sans historique
Flyway ; ne contournez pas ce contrôle sans analyse du DBA.

## Assistant de configuration avant connexion

Le backend peut désormais démarrer sans base en **mode installation explicite**
(`APIGEN_SETUP_ENABLED=true`). Depuis la connexion Studio, cliquer sur
**Installer ou reconfigurer la plateforme**, puis utiliser le code temporaire
affiché sur la console du backend. L’assistant teste Oracle, PostgreSQL, MySQL,
SQL Server ou H2 local et enregistre le fichier externe unique
`APIGEN_RUNTIME_CONFIG` (par défaut `./config/runtime.properties`).

Les quatre YAML historiques sont regroupés dans un seul `application.yml`,
avec sections par profil. Le profil par défaut reste `dev` et `prod` doit être
activé explicitement en production. Le moteur choisi par l’assistant surcharge
les réglages de base du profil via le fichier externe.

**Après enregistrement, désactiver le mode installation et redémarrer.**
Les bases/comptes doivent être créés par le DBA. Les migrations des nouveaux
moteurs sont fournies mais leur validation réelle reste à effectuer chez vous.
Aucun transfert de données n’est automatique.
Consulter [INSTALLATION-ASSISTEE.md](INSTALLATION-ASSISTEE.md) avant utilisation,
notamment pour préserver les clés d’une installation existante et configurer TLS.
