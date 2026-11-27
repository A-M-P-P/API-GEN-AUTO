# Installation assistée et fichier de configuration unique

## Ce qui change

Le projet possède maintenant **un seul `application.yml` interne** : il regroupe
les anciens réglages `dev`, `prod` et `local` dans des sections activées par profil.
Les trois fichiers séparés ne sont plus nécessaires. Le profil par défaut reste
`dev` ; en production, définir explicitement `APIGEN_PROFILE=prod`.

Le fichier externe à renseigner reste **`config/runtime.properties`**, ou celui
désigné par **`APIGEN_RUNTIME_CONFIG`**. L’assistant enregistre dans ce fichier la
connexion plateforme, le dialecte, les migrations et les réglages associés.
Les autres réglages déjà présents sont conservés (les commentaires sont réécrits
par le format Java Properties). Les organisations, API et connexions métier
restent en base, pas dans ce fichier.

Ce fichier est PRIVÉ : ne jamais le publier sur le serveur du frontend ou dans
Git. Il contient le mot de passe de base et les clés de sécurité. Le frontend
conserve son point d’entrée public (`config.json` ou adresse enregistrée dans le
navigateur) pour trouver le backend sur un serveur différent. Ce point d’entrée
ne contient aucun secret et ne peut pas disparaître par simple fusion des YAML.

## 1. Préparer une base dédiée

Faire créer la base et le compte applicatif par le DBA, sans fournir son compte
SYS/SYSTEM/root à l’assistant. Les droits doivent permettre les migrations dans
la seule base ou le seul schéma de la plateforme. Les comptes des bases métier
restent séparés.

| Choix | Minimum contrôlé par l’assistant | Cible configurée |
| --- | --- | --- |
| Oracle | 12c | Schéma du compte connecté ; 19c+ recommandé |
| PostgreSQL | 13 | Base dédiée, schéma `public` ; version maintenue recommandée |
| MySQL | 8 | Base dédiée ; 8.4 LTS recommandé |
| SQL Server | 2016 (majeure 13) | Base dédiée, schéma `dbo` |
| H2 | 2 | Fichier local fixe `./data/apigen`, développement uniquement |

Les adaptations de migrations et modules Flyway pour PostgreSQL, MySQL et SQL
Server sont fournies dans cette livraison, **sans validation d’exécution sur ces
serveurs dans le workspace**. Les essais réels restent obligatoires avant
production, y compris pour Oracle. « Version compatible » signifie ici que le
seuil minimal et le produit ont été contrôlés, pas qu’un scénario complet a été
certifié. Oracle 10g est refusé avant les migrations et reste non pris en charge
comme base plateforme.

Les migrations Oracle/H2 existantes V1 à V7 sont inchangées. Les autres moteurs
ont leurs propres V1 à V7 sous `db/platform/{postgresql,mysql,sqlserver}`.
**Ne jamais changer de moteur/dossier de migrations sur une base déjà initialisée.**
Cela ne migre pas les données et peut provoquer des erreurs de checksum.

Particularité SQL Server : pour éviter les chemins multiples de suppression en
cascade, l’historique des exécutions ne possède pas de FK directe sur `api_id` ;
la FK de l’endpoint conserve sa mise à NULL. Un ancien identifiant d’API peut
rester dans l’historique après suppression. Les sessions ont une FK client sans
cascade directe (la suppression de profil cascade sur ses sessions).

## 2. Démarrer en mode installation

Il s’agit d’une application de configuration séparée : **pas de connexion de
base automatique, pas de Flyway, pas de repositories, tâches planifiées ou API
métier**. Elle peut donc démarrer même si la base configurée est inaccessible.
Elle ne se déclenche PAS automatiquement après une panne de production.

Dans **IntelliJ → Run → Edit Configurations → Environment variables**, définir :

```text
APIGEN_SETUP_ENABLED=true
APIGEN_RUNTIME_CONFIG=C:/apigen/config/runtime.properties
APIGEN_CORS_ORIGINS=http://localhost:4200
```

Puis démarrer normalement `ApiGenApplication`. Le chemin absolu évite les
ambiguïtés entre la racine du dépôt et le dossier `backend`.

Pour un WAR dans Tomcat, définir ces variables dans l’environnement du service
ou `setenv.sh`/`setenv.bat`, puis redémarrer le déploiement. Le port et le contexte
restent ceux du conteneur (par exemple `apigen.war` → `/apigen`). Le point d’entrée
WAR et le point d’entrée Java choisissent tous deux le mode installation.
Pour le lancement Java autonome, `--apigen.setup.enabled=true` ou la propriété
JVM `-Dapigen.setup.enabled=true` sont aussi possibles. Ne définissez pas ce
commutateur dans le fichier runtime : il doit être connu AVANT le choix de
l’application à démarrer.

La console du serveur affiche un **code aléatoire temporaire de 30 minutes** et
le chemin du fichier cible. Protéger les journaux du serveur pendant cette
opération : le code donne accès à des opérations sensibles de configuration.
Les mots de passe et les résultats détaillés des erreurs JDBC ne sont pas affichés.

## 3. Ouvrir l’assistant depuis la page de connexion

1. Cliquer sur **Installer ou reconfigurer la plateforme**.
2. Si nécessaire, indiquer l’adresse publique complète du backend, contexte
   inclus, par exemple `https://serveur.example/apigen`.
3. Cliquer sur **Joindre le mode installation**.
4. Copier le code de la console dans le champ masqué.
5. Choisir le type de base et renseigner serveur DNS/IPv4, port, base/service,
   utilisateur et mot de passe. Pour Oracle, choisir explicitement SID ou SERVICE.
6. Cliquer sur **Tester la connexion et la version**.
7. Cliquer sur **Vérifier et enregistrer**. La connexion est revérifiée côté
   serveur : aucun succès fourni par le navigateur n’est considéré comme preuve.

Le test ne crée pas les tables et ne prouve pas que le compte a tous les droits
DDL. Le choix H2 peut créer son fichier local lors de la connexion. La base et
le compte doivent exister pour les moteurs externes.

Le fichier n’est remplacé qu’avec une opération atomique, dans le même dossier.
Si l’écriture échoue ou que le système de fichiers ne permet pas cette opération,
l’assistant signale l’échec sans utiliser de remplacement partiel. Un seul
enregistrement est autorisé par session d’installation. Le fichier reçoit les
droits propriétaire seulement sous POSIX ; sous Windows, l’exploitant doit
préparer un dossier dont les ACL limitent l’accès au compte du backend et aux
administrateurs. Protéger également les répertoires parents.

Des clés aléatoires sont générées si aucune clé explicite n’est configurée.
Une clé de chiffrement n’est PAS générée automatiquement si la base contient
déjà des tables : renseigner d’abord la vraie clé `apigen.crypto.key` de
l’installation source dans le fichier externe, sinon l’enregistrement est refusé.
Les clés explicites existantes sont conservées. **Ne jamais perdre ou remplacer
la clé d’une installation contenant des secrets chiffrés.**

## 4. Redémarrer normalement

Désactiver/enlever **APIGEN_SETUP_ENABLED**, et les éventuels commutateurs JVM
ou arguments équivalents. Garder le même chemin **APIGEN_RUNTIME_CONFIG**.
En production, définir **APIGEN_PROFILE=prod**. Redémarrer le backend.

Flyway prépare les tables avec le dossier de migrations du moteur choisi. Les
anciens réglages de connexion Flyway séparés sont retirés par l’assistant pour
qu’il utilise la même base et le même compte que la plateforme. Un schéma non
vide sans historique Flyway est refusé : ne pas contourner ce contrôle sans DBA.
Le garde-fou historique de création de tables Oracle/H2 ne s’exécute pas sur les
autres moteurs ; Flyway y est l’unique mécanisme de gestion du schéma.

Se connecter ensuite au Studio avec le compte interne initial configuré (par
défaut `admin` / `Admin2026!` pour une base neuve), puis changer son mot de passe.
La console H2 est désactivée et les données de démonstration ne sont pas créées
par la configuration générée par l’assistant.

## Sécurité et exploitation

- Utiliser HTTPS pour le frontend et le backend hors du poste local.
- Restreindre temporairement l’accès réseau de l’assistant aux installateurs.
  Le code donne aussi la possibilité de tester des connexions depuis le serveur.
- Le code expire après 30 minutes ; 20 requêtes protégées par minute maximum
  sont autorisées par instance. Redémarrer le mode installation pour obtenir
  un nouveau code. Aucun JWT Studio n’est envoyé par le formulaire.
- CORS se configure avant le démarrage via APIGEN_CORS_ORIGINS, ou le fichier
  externe existant s’il définit déjà `apigen.cors.allowed-origins` (prioritaire).
  Autoriser uniquement l’origine réelle du Studio, jamais toutes les origines.
- Ne jamais laisser le mode installation actif en production normale : les
  services métier y sont volontairement indisponibles.
- SQL Server utilise TLS avec validation du certificat. Préparer le certificat
  et la chaîne de confiance Java ; aucune option d’acceptation aveugle n’est ajoutée.
  Oracle TCPS, options TLS PostgreSQL/MySQL, IPv6, instances SQL Server nommées,
  schémas autres que les valeurs proposées et autres options JDBC avancées
  nécessitent une configuration externe manuelle par l’exploitant.
- Pour PostgreSQL/MySQL, configurer explicitement les exigences TLS de production
  dans le fichier externe selon la politique du serveur : le formulaire simple
  ne prétend pas imposer une configuration TLS universelle.
- Une réinstallation ou un changement de moteur n’effectue AUCUN transfert de
  données. Prévoir sauvegarde, migration et restauration avec le DBA.

## Validation à effectuer chez vous

Aucun build, test ou serveur n’a été lancé dans le workspace. Les tests ajoutés
vérifient les URL générées, le refus des injections d’options JDBC et la présence
des migrations. Ils ne constituent pas un test réel de chaque SGBD.

Vérifier localement : démarrage sans base en mode installation, refus sans code,
expiration du code, refus d’Oracle 10g, erreurs d’identifiants compréhensibles,
préservation des clés existantes, droits du fichier, redémarrage normal, migrations
V1 à V7 sur base vide, CRUD, authentification consommateurs, verrous de
renouvellement, journalisation, suppression et planifications sur chaque moteur.
