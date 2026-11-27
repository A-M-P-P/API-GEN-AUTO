# Schéma Oracle dédié à API-GEN

## Principe

Dans Oracle, un utilisateur propriétaire dispose de son schéma du même nom.
Le compte **APIGEN** est réservé aux tables de la plateforme : comptes Studio,
organisations, API, endpoints, pipelines, connexions, profils d’authentification,
sessions, planifications, exécutions, audit et historique Flyway.

Les tables métier restent dans SIGIF ou les autres schémas d’origine. Les
connexions configurées dans le Studio utilisent leurs propres comptes techniques
et droits minimaux. Ne donnez pas de droits métier au propriétaire APIGEN : ce
ne sont pas les identifiants de la base plateforme qui doivent lire ces tables.

Le frontend ne possède pas de schéma : il enregistre via le backend. H2 et les
tests ne sont pas modifiés par cette configuration Oracle.

## Première installation

1. Le DBA se connecte avec SQL*Plus/SQLcl à la **PDB/service cible**, pas au
   conteneur racine CDB$ROOT. Il adapte les tablespaces USERS/TEMP si nécessaire.
2. Il exécute `@database/oracle/create-apigen-schema.sql` depuis la racine du
   dépôt. Le mot de passe est demandé sans affichage ; ne pas activer de spool
   ni transmettre ce secret sur la ligne de commande. Pour ce script interactif,
   ne pas utiliser de guillemet double ni de `&` dans le mot de passe. Le DBA peut
   utiliser son outil de provisionnement sécurisé pour une autre politique.
3. Le script crée APIGEN avec un quota initial **1 Go**, CREATE SESSION,
   CREATE TABLE et CREATE SEQUENCE. Aucun rôle DBA, privilège ANY, accès aux
   schémas métier ni quota illimité n’est accordé. Le DBA ajuste le quota selon
   la rétention et la volumétrie. Il peut avoir à adapter le profil de mot de
   passe de son environnement. Le DDL Oracle est auto-commité : en cas d’échec
   partiel, le DBA doit contrôler les opérations réussies avant de reprendre.
4. Configurer **un seul emplacement** :

```properties
# config/runtime.properties (non versionné)
spring.datasource.url=jdbc:oracle:thin:@//SERVEUR_ORACLE:1521/SERVICE
spring.datasource.username=APIGEN
spring.datasource.password=A_REMPLACER
```

Ou, si ces valeurs ne sont pas définies dans le fichier prioritaire :

```bash
export APIGEN_DB_URL='jdbc:oracle:thin:@//SERVEUR_ORACLE:1521/SERVICE'
export APIGEN_DB_USER=APIGEN
export APIGEN_DB_PASSWORD='A_REMPLACER'
```

5. Démarrer le backend. Flyway crée toutes les tables dans le schéma du compte
   connecté. Il n’est pas nécessaire d’ajouter un `default_schema`, un
   `ALTER SESSION SET CURRENT_SCHEMA` ou des synonymes : le propriétaire et le
   compte de connexion sont ici le même utilisateur. Le garde-fou de schéma
   utilise ce même compte. Aucune migration versionnée existante n’est modifiée.

Pour vérifier la cible avec **les identifiants de connexion du backend** :

```sql
SELECT USER, SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS CURRENT_SCHEMA,
       SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTAINER_NAME FROM DUAL;
SELECT table_name FROM user_tables ORDER BY table_name;
```

USER et CURRENT_SCHEMA doivent désigner APIGEN. Les profils Oracle `dev` et
`prod` utilisent maintenant APIGEN par défaut, avec un mot de passe placeholder
à remplacer. Les fichiers runtime ou variables déjà remplis peuvent toujours
surcharger ces valeurs : vérifiez-les avant le démarrage.

## Installation existante dans SIGIF ou un autre schéma

**Changer le nom d’utilisateur ne déplace pas les données.** Un compte APIGEN
vide sera initialisé comme une nouvelle plateforme. Les comptes Studio, API,
connexions et journaux existants resteront dans l’ancien schéma.

Pour conserver cette installation, organiser avec le DBA :

1. Sauvegarde et inventaire des seules tables/contraintes/index de la plateforme,
   y compris l’historique Flyway. Ne pas exporter toutes les tables métier SIGIF.
2. Arrêt du backend et des planifications pour empêcher les écritures concurrentes.
3. Export/import sélectif, par exemple avec Data Pump et REMAP_SCHEMA, sous
   contrôle du DBA. Préparer la destination conformément à l’outil utilisé.
   Inclure les tables présentes dans V1 à V7, sans inventer les tables des
   migrations pas encore appliquées à l’installation source.
4. Contrôle des contraintes, index, colonnes identity et prochaines valeurs
   d’identifiants : aucun nouvel enregistrement ne doit réutiliser un ancien ID.
5. Conserver impérativement **la même clé `apigen.crypto.key`** pour pouvoir
   déchiffrer les secrets importés. Préserver aussi les autres secrets de
   déploiement selon la continuité souhaitée.
6. Configurer le compte APIGEN, démarrer, puis valider les comptes Studio,
   connexions, profils consommateurs, endpoints, exécutions et planifications.
7. Conserver l’ancien schéma intact jusqu’à validation. Pour un retour arrière,
   arrêter la nouvelle instance avant de revenir à l’ancienne configuration.
   Éviter deux instances actives qui exécuteraient les mêmes planifications.

Aucun déplacement ni effacement automatique n’est fourni : sans l’inventaire de
la base réelle, une copie aveugle risquerait d’altérer des données métier.

## Protection Flyway

Les profils Oracle désactivent désormais `baseline-on-migrate` par défaut :
un schéma non vide sans historique Flyway provoque un arrêt au lieu d’être
adopté implicitement. Une installation existante avec son historique continue
normalement. Si l’historique est manquant, faire analyser la situation par le
DBA ; ne pas activer aveuglément la base de référence pour contourner l’erreur.
Cette protection n’est pas une vérification du nom de compte : un override peut
toujours choisir un autre propriétaire. Le bon compte doit être contrôlé au déploiement.

Aucun script SQL, build ou test n’a été exécuté automatiquement pour provisionner
votre base réelle. Le DBA doit créer le compte et valider le script dans son environnement.
