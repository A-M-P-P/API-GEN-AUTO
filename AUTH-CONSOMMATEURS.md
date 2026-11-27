# Authentification des consommateurs par organisation et système

## Changement de sécurité à préparer avant déploiement

Cette version remplace le contrôle d’accès entrant par des tokens consommateurs
obligatoires. `NONE`, `API_KEY` et `JWT` restent des données historiques dans les
anciennes définitions, mais ne décident plus de l’accès à la passerelle.
**Ni une ancienne clé statique ni un JWT de connexion Studio ne donne accès aux API.**
Les essais manuels internes et les tâches planifiées ne sont pas des appels
consommateurs ; ils conservent leur mode de fonctionnement interne.

1. Sauvegarder la base plateforme et prévoir une fenêtre de migration avec les consommateurs.
2. Déployer le backend et appliquer Flyway **V6 et V7** ; déployer aussi le frontend.
3. Rattacher les API non classées à une organisation. Elles sont inaccessibles aux
   consommateurs tant que ce rattachement n’est pas effectué.
4. Ouvrir **Accès consommateurs**, choisir l’organisation puis le système.
5. Configurer sa vérification, cocher ses API autorisées, puis activer le profil.
6. Adapter les applications clientes : connexion au nouvel endpoint puis
   `Authorization: Bearer <access_token>` sur chaque appel métier.
7. Valider localement les scénarios décrits en fin de document avant ouverture.

Aucun accès public ni token universel n’est créé automatiquement pour faciliter
la transition. Chaque organisation possède un profil `default` créé désactivé au
démarrage, ou lors de sa création. Il faut le configurer explicitement.
Une installation par organisme fournisseur ou une plateforme centralisée sont
possibles ; les consommateurs n’installent aucun backend ou Studio pour appeler les API.

## Un profil par système

Une organisation CNEF peut avoir `default`, `systeme-a`, `systeme-b`, etc.
Un profil définit son pipeline et la liste maximale d’API auxquelles il peut donner
accès. Un consommateur d’un système n’obtient pas automatiquement tous les droits
de l’organisation. La portée de cette version est **l’API entière** (tous ses
endpoints entrants), pas une liste de routes à l’intérieur d’une API. Séparez les
API si leurs routes nécessitent des droits différents.

Les chemins ci-dessous sont relatifs au backend. Préfixez-les par son adresse
publique et son éventuel contexte global (`/apigen`, par exemple).

- `POST /cnef/api/auth/systeme-a/token`
- `POST /cnef/api/auth/systeme-a/refresh`
- Routes métier inchangées : `/cnef/api/gateway/code-api/chemin`

La configuration se fait dans **Accès consommateurs**, pas en créant une route
métier anonyme. L’endpoint par défaut est `/cnef/api/auth/default/token`.

## Mode local : applications sans authentification existante

1. Choisir « Identifiant et secret gérés ici ».
2. Sélectionner les API accessibles à ce système et enregistrer le profil actif.
3. Créer un consommateur. Copier immédiatement son secret dans un coffre-fort.
4. Envoyer au endpoint `/token` un corps JSON :

```json
{"client_id":"application-dgi","client_secret":"SECRET_A_REMPLACER"}
```

Les secrets aléatoires sont affichés une seule fois et stockés sous empreinte
BCrypt. Ils ne correspondent pas à des comptes Studio. Le mode local donne à
chaque consommateur les API cochées du profil. Pour des droits locaux différents,
créez des profils distincts. Pour remplacer un secret, désactivez son identifiant
puis créez un nouvel identifiant et son secret ; ne supprimez pas l’ancien avant
que les applications aient migré si une période de coexistence est nécessaire.

## Mode pipeline : respecter l’authentification du système existant

Le pipeline est une configuration **technique de confiance**, réservée à ADMIN
et EDITOR. Il ne doit jamais être fourni par le consommateur.
Il reçoit `request.body` avec les identifiants et `org` avec la configuration
habituelle de l’organisation. Les connexions déclarées restent disponibles.

Il doit vérifier les identifiants **et les droits réels**, puis produire :

```json
{"authenticated":true,"subject":"identifiant-stable-du-systeme","apiIds":[12,18]}
```

`apiIds` désigne les identifiants des API de notre plateforme, pas les codes
internes de permission du système source : le pipeline doit effectuer la
correspondance. Les droits reçus sont intersectés avec les API cochées dans le
profil. Un résultat vide, incomplet, une erreur ou l’absence de droit refuse la
connexion. Ne recopiez jamais `authenticated`, `subject` ou `apiIds` du corps
client sans vérification par le système de confiance.

### Exemple HTTP (à adapter au contrat réel du système)

Le service interne doit réellement vérifier les identifiants et retourner le
contrat ci-dessus dans son corps JSON. Exemple de pipeline :

```json
[
  {
    "type":"HTTP", "name":"verification", "connectionId":123,
    "method":"POST", "url":"/authentification",
    "body":{"username":"{{request.body.username}}","password":"{{request.body.password}}"},
    "timeoutMs":10000
  },
  {
    "type":"MAP", "source":"output.body",
    "mappings":[
      {"source":"authenticated","target":"authenticated"},
      {"source":"subject","target":"subject"},
      {"source":"apiIds","target":"apiIds"}
    ]
  }
]
```

Remplacer `123` et `/authentification` par la connexion et la route internes
réelles. Ne pas utiliser une valeur de succès par défaut dans ce mapping.
Pour un annuaire, passer par le service d’authentification existant ou un service
interne adapté : aucun connecteur LDAP natif n’est ajouté par cette livraison.

### Oracle

Utiliser une connexion de lecture à privilèges minimaux et la fonction/vue
sécurisée de vérification du système existant. **Ne pas inventer une comparaison
de mot de passe en clair.** Si les empreintes ne sont pas vérifiables par la base,
appeler le service d’authentification existant plutôt que de changer leur stockage.

Une étape `DB_QUERY` peut être la dernière étape si elle retourne **une seule
ligne** avec les colonnes `authenticated` (texte exact `true` accepté), `subject`
et `apiids` (tableau JSON de nombres, éventuellement sous forme de texte).
Une absence de ligne ou plusieurs lignes refusent l’authentification.
Les colonnes JDBC sont normalisées en minuscules ; `apiids` est donc accepté
comme équivalent de `apiIds`. La requête doit sélectionner uniquement le résultat
de vérification et les droits utiles, jamais les empreintes ou mots de passe.

Toujours utiliser `?` et `params`, par exemple des valeurs liées
`["{{request.body.username}}", "{{request.body.password}}"]` dans l’ordre des
marqueurs de la requête validée par l’équipe du système source. L’insertion de
templates dans le texte SQL d’un pipeline d’authentification est refusée.
Les étapes LOG, fichiers, écritures SQL et `failOnError=false` sont interdites.

Les identifiants et résultats intermédiaires ne créent pas d’ExchangeRun : ils
restent en mémoire pour cette vérification. Les pipelines ne doivent pas placer
les mots de passe dans les URL, ni dans les paramètres d’adresse des appels HTTP.
Les secrets techniques doivent être dans les connexions ou les secrets
chiffrés de l’organisation, jamais en clair dans le JSON du profil.

## Tokens, renouvellement et révocation

Réponse d’une connexion réussie :

```json
{
  "access_token":"VALEUR_A_CONSERVER_EN_SECURITE",
  "token_type":"Bearer",
  "expires_in":86399,
  "refresh_token":"AUTRE_VALEUR_A_CONSERVER_EN_SECURITE",
  "refresh_expires_in":604799
}
```

Les durées retournées, en secondes, font foi. Le token d’accès dure au maximum
24 h. La famille de renouvellement dure au maximum 7 jours depuis la connexion
initiale. Les tokens ont 256 bits aléatoires ; seules leurs empreintes SHA-256
sont stockées. Les réponses d’authentification sont `no-store`.

Renouvellement local :

```json
{"refresh_token":"JETON_DE_RENOUVELLEMENT"}
```

Renouvellement pipeline :

```json
{
  "refresh_token":"JETON_DE_RENOUVELLEMENT",
  "credentials":{"username":"agent","password":"VALEUR_A_REMPLACER"}
}
```

En mode pipeline, la plateforme ne conserve pas le mot de passe. Le client doit
fournir à nouveau les informations nécessaires pour revérifier les droits dans
le système source. Le sujet doit rester identique et les droits ne peuvent pas
augmenter par renouvellement. Une nouvelle connexion est nécessaire pour obtenir
des droits supplémentaires.

Chaque renouvellement remplace les **deux** tokens. Les précédents ne sont plus
acceptés. Les renouvellements sont sérialisés par verrouillage en base ; une
application doit éviter plusieurs renouvellements simultanés et remplacer les
deux valeurs atomiquement. Après 7 jours, une nouvelle connexion est obligatoire.

Dans le Studio, il est possible de révoquer une session, toutes les sessions d’un
profil, ou de désactiver un consommateur local. Modifier un profil révoque ses
sessions. Désactiver une organisation bloque ses appels. Pour les systèmes
externes, une désactivation source est vue à la connexion ou au renouvellement,
pas à chaque appel métier : révoquer également la session dans le Studio si
l’arrêt doit être immédiat. Les accès consommateurs ne donnent aucun accès au
Studio. Le sujet vérifié est disponible dans `request.consumer.subject` dans les
pipelines métier et dans leur contexte d’exécution journalisé.

## Exploitation et limites à connaître

- HTTPS est obligatoire en production ; ne jamais transmettre des identifiants
  ou tokens en URL. Protéger également les liens internes vers les systèmes.
- Une limite locale de 20 tentatives par minute et couple organisation/profil/IP
  protège connexion et renouvellement. En cluster, ajouter une limitation
  partagée sur le reverse proxy/API manager ; chaque instance a son propre compteur.
- Configurer correctement l’IP de confiance du proxy : ne pas faire confiance à
  des en-têtes `X-Forwarded-For` arbitraires. Plusieurs clients derrière un même
  proxy peuvent partager le quota si le backend ne voit que l’IP de ce proxy.
- Les journaux d’audit tracent créations, configurations, connexions réussies ou
  refusées, renouvellements et révocations sans corps d’authentification ni token.
  Les refus d’accès métier renvoient 401 sans exécuter le pipeline.
- Les sessions dont le renouvellement est expiré depuis plus de 30 jours sont
  purgées quotidiennement à 03:30 UTC ; les événements d’audit suivent la rétention
  habituelle. Prévoir sauvegarde, supervision et rétention adaptées au volume.
- En déploiement multi-instance, coordonner les démarrages/initiaux des organisations.
- Le Studio conserve ses contrôles de rôles internes. Les profils ne doivent
  être modifiables que par des personnes habilitées à accorder ces accès.

## Paramètres guidés dans le pipeline

Dans l’éditeur d’endpoint, après avoir créé les identifiants/filtres et les étapes,
utiliser **Utiliser une information reçue** : choisir le paramètre, l’étape et
cliquer sur le bouton. Pour SQL, sa référence est ajoutée à `params` ; placer le
`?` correspondant dans la requête, en respectant l’ordre. Pour HTTP, un filtre
`queryParams` est ajouté ; pour SET, la valeur de l’étape est remplacée. Aucune
valeur consommateur n’est concaténée au SQL par cet outil.

## Validation locale à effectuer

Les tests ajoutés et adaptés ne sont pas exécutés dans le workspace, conformément
à la consigne. Avant production, vérifier sur votre environnement :

- Flyway V6/V7 sur une copie de la base Oracle et création des profils désactivés ;
- accès refusé sans token, avec clé statique ou JWT Studio ;
- accès accepté avec token de la bonne organisation et API autorisée seulement ;
- refus entre organisations et entre systèmes dont les API ne sont pas autorisées ;
- vérification Oracle/HTTP selon le vrai contrat, mauvais mot de passe, panne,
  réponse incomplète et absence de droit (aucun token ne doit être émis) ;
- expiration, renouvellement concurrent, rejet de l’ancien refresh/access token ;
- désactivation/révocation et prise en compte immédiate sur les appels suivants ;
- absence d’identifiants/tokens dans les journaux, traces proxy et ExchangeRun ;
- formulaires de paramètres, validation des types, essais et SQL avec valeurs liées.
