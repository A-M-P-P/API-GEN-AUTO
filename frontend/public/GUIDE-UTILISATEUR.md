# API-GEN Studio — Guide pratique de l’utilisateur

Ce guide explique les tâches du Studio sans supposer de connaissances en informatique. Gardez-le ouvert pendant vos premières manipulations.

**Important :** certaines pages proposent encore du texte technique (requêtes SQL, JSON, règles d’authentification). Le Studio ne rend pas toutes ces tâches réalisables sans accompagnement. Le guide distingue ce que vous pouvez faire vous-même et ce qu’un responsable technique doit préparer. Ne copiez jamais une instruction inconnue dans une base réelle.

## 1. Comprendre les mots de l’écran

| Mot du Studio | Signification simple |
|---|---|
| Organisation | L’institution ou le service propriétaire des échanges. |
| API | Un dossier regroupant des services d’échange. |
| Endpoint | Une opération précise, par exemple « consulter la liste des dossiers ». |
| Connexion | Une fiche permettant au serveur de joindre une source de données. |
| Pipeline | La liste des actions exécutées par une opération, dans l’ordre. |
| Paramètre | Une information à fournir, par exemple un numéro de dossier. |
| Consommateur | Un logiciel externe autorisé à appeler les services. Ce n’est pas un utilisateur du Studio. |
| Profil du système | Les règles et les API autorisées pour un logiciel consommateur. |
| Token | Un laissez-passer temporaire utilisé par un logiciel externe. |
| Exécution | Une tentative de réalisation d’une opération. |
| Audit | L’historique des modifications et actions de configuration. |

## 2. Avant de commencer

Demandez à votre responsable :
- l’adresse du Studio et votre compte personnel ;
- le nom de votre organisation ;
- l’opération attendue, la source de données autorisée et un exemple du résultat souhaité ;
- un environnement d’essai, sans risque pour les données réelles ;
- les fiches de connexion préparées ou les renseignements pour les remplir.

**Ne partagez pas vos mots de passe dans un courriel, une capture ou ce guide.** Demandez les secrets par le moyen sécurisé de votre institution.

Les champs avec **\*** sont obligatoires. Un bouton grisé peut signaler un prérequis manquant, une opération en cours ou une action non autorisée. Attendez la confirmation avant de quitter une page : les modifications non enregistrées peuvent être perdues.

### Vos droits
- **Lecture seule** : consulter ; les changements sont réservés aux personnes habilitées.
- **Éditeur** : préparer et modifier les échanges selon les droits accordés.
- **Administrateur** : gérer aussi les utilisateurs et la configuration.

Si vous ne voyez pas une page ou si l’action est refusée, demandez le droit nécessaire. Ne créez pas un autre compte pour contourner le refus.

## 3. L’ordre à suivre

1. **Organisation** : créer la fiche de l’institution.
2. **Connexion** : préparer et vérifier la source si l’opération utilise une base, un service distant ou des fichiers.
3. **API** : créer le dossier dans l’organisation.
4. **Endpoint** : créer l’opération dans l’API enregistrée.
5. **Actions** : choisir ce que l’opération doit faire avec ses connexions.
6. **Essai interne** : enregistrer puis essayer avec des données de test.
7. **Accès consommateurs** : créer le profil du logiciel externe et autoriser ses API.
8. **Mise en service** : activer les éléments nécessaires après validation.
9. **Suivi** : consulter Exécutions et Journal d’audit.

Une connexion n’a pas besoin d’une API pour exister. Un utilisateur du Studio n’a pas besoin d’une connexion. Un profil consommateur peut être préparé désactivé ; il lui faut des API autorisées avant activation. Ne créez pas de faux éléments uniquement pour débloquer un bouton.

## 4. Se connecter, changer son mot de passe et quitter

1. Ouvrez l’adresse fournie dans votre navigateur.
2. Saisissez votre identifiant et votre mot de passe, puis utilisez le bouton de connexion.
3. Le tableau de bord s’affiche. Si la connexion est refusée, vérifiez la casse, puis contactez l’administrateur plutôt que de multiplier les essais.
4. Pour changer votre mot de passe, ouvrez votre profil dans le menu latéral. Saisissez le mot de passe actuel, le nouveau et sa confirmation ; validez.
5. Pour terminer, utilisez **Se déconnecter**, surtout sur un ordinateur partagé.

Le lien d’installation n’est pas nécessaire pour l’utilisation quotidienne. Ne reconfigurez pas une plateforme en fonctionnement.

## 5. Choisir l’apparence

Dans l’en-tête, cliquez sur un logo situé derrière la carte centrale pour le placer devant. Les flèches parcourent les logos en boucle. **Défaut** rétablit l’apparence commune.

Ce choix change uniquement les couleurs et le logo. Il ne sélectionne pas une institution dans les formulaires et n’accorde aucun droit. Dans chaque API, choisissez toujours la bonne organisation.

Si un logo externe ne se charge pas, son nom reste disponible. Si les thèmes sont indisponibles, utilisez **Réessayer les thèmes** ; si le message persiste, prévenez l’administrateur.

## 6. Créer ou modifier une organisation

**À faire par une personne autorisée.**

1. Ouvrez **Organisations**.
2. Cliquez sur **Nouvelle organisation**.
3. Dans **Code**, saisissez un nom court, par exemple `cnef`. Les espaces et les accents sont adaptés automatiquement à la sortie du champ.
4. Dans **Nom**, écrivez le nom complet de l’institution.
5. Ajoutez une description si elle est utile.
6. Choisissez l’identité visuelle, ou laissez **Automatique** si le code identifie l’institution.
7. Laissez les réglages avancés inchangés si votre responsable ne vous a rien fourni.
8. Enregistrez et vérifiez que la fiche apparaît dans la liste.

**Résultat attendu :** l’organisation est proposée dans le formulaire d’API. Si elle n’apparaît pas sur une page déjà ouverte, rechargez cette page après avoir enregistré vos autres modifications.

Pour modifier une fiche, utilisez son action de modification, ajustez les informations et enregistrez. Le code existant n’est pas renommé par le formulaire. Un code déjà utilisé nécessite de choisir un autre code, pas de recréer l’organisation sous plusieurs noms.

## 7. Préparer une connexion

Ouvrez **Connexions**, puis le bouton de création. Choisissez un nom parlant, par exemple « Base dossiers — essais ».

### A. Base de données

Demandez à votre responsable une fiche indiquant le type de base, l’hôte, le port, le service ou la base, le compte et le mot de passe. **Ne les devinez pas.**

1. Choisissez le type fourni : Oracle, PostgreSQL, MySQL ou SQL Server.
2. Recopiez les renseignements dans les champs correspondants.
3. Pour Oracle, faites confirmer le choix Service/SID et sa valeur.
4. Laissez les réglages avancés à leurs valeurs prévues, sauf instruction du responsable.
5. Utilisez la fonction de test proposée et lisez le résultat.
6. Enregistrez la connexion, puis vérifiez sa présence dans la liste.

Un enregistrement ne garantit pas que la source est joignable : le test doit aussi réussir avant mise en service. Lors d’une modification, laisser le mot de passe vide conserve les identifiants existants lorsque l’écran l’indique.

### B. Service distant (REST)

1. Choisissez **REST**.
2. Saisissez l’adresse de base fournie par le propriétaire du service.
3. Choisissez l’authentification demandée et remplissez uniquement les secrets fournis.
4. Testez, puis enregistrez.

N’utilisez pas votre mot de passe Studio à la place du mot de passe du service distant.

### C. Fichiers

1. Choisissez le type de connexion de fichiers locaux.
2. Saisissez le dossier indiqué par l’exploitant.
3. Enregistrez et vérifiez les accès avec lui.

**Ce dossier se trouve sur le serveur, pas nécessairement sur votre ordinateur.** Copier un fichier sur votre bureau ne le rend pas accessible au serveur. Les actions de lecture et écriture décrites ici concernent les fichiers locaux ; ne choisissez pas FTP/SFTP en supposant un fonctionnement équivalent.

### En cas d’échec de test

Vérifiez la fiche avec son propriétaire. Notez le nom de la connexion et le message affiché, sans communiquer le mot de passe. N’augmentez pas les délais et ne désactivez pas la sécurité au hasard.

## 8. Créer une API

**Prérequis : une organisation existante.** Si aucune organisation n’est disponible, le Studio vous invite à en créer une avant de poursuivre.

1. Ouvrez **APIs**, puis **Nouvelle API**.
2. Donnez un nom compréhensible, par exemple « Consultation des dossiers ».
3. Saisissez un code court, par exemple `consultation-dossiers`.
4. Sélectionnez l’organisation propriétaire : ce choix est obligatoire.
5. Laissez la version initiale proposée si vous n’avez pas d’instruction différente.
6. Gardez le statut **Brouillon** pendant la préparation.
7. Cliquez sur **Enregistrer l’API**.

**Résultat attendu :** la fiche enregistrée permet d’ajouter des endpoints. Les codes d’API ont 2 à 80 caractères ; lettres, chiffres, tirets et `_` sont possibles. Le signe `/` n’est pas admis. Les majuscules deviennent des minuscules et les espaces des tirets. Un code doit être unique dans son organisation.

Pour une ancienne API non classée, sélectionnez une organisation avant son prochain enregistrement. Les API existantes ne sont pas rattachées automatiquement par cette mise à jour.

## 9. Ajouter une opération (endpoint)

**Prérequis : une API enregistrée.**

1. Ouvrez la fiche de l’API, puis **Nouvel endpoint**.
2. Donnez un nom, par exemple « Liste des dossiers ».
3. Choisissez la méthode indiquée par le responsable. Pour une simple consultation, il s’agit souvent de **GET**, mais ne changez pas une méthode existante sans son accord.
4. Dans le chemin, saisissez une fin d’adresse courte comme `/dossiers`. Ce champ n’est pas le code de l’API.
5. Choisissez le sens de l’échange selon le besoin fourni. Demandez confirmation si vous hésitez.
6. Gardez le format de réponse attendu, généralement JSON pour un logiciel.
7. Préparez les actions comme expliqué ci-dessous.
8. Enregistrez.

Le nom d’une opération, son chemin et le code de l’API sont trois choses différentes. Ne collez pas l’adresse complète du serveur dans le champ de chemin.

## 10. Choisir les actions de l’opération

### Lire une base sans écrire de requête

1. Créez d’abord une connexion de base et vérifiez-la dans **Connexions**.
2. Dans l’endpoint, cliquez sur **Parcourir la base**.
3. Choisissez la connexion et la table autorisées.
4. Sélectionnez les colonnes utiles dans l’assistant et donnez aux colonnes de sortie les noms attendus.
5. Utilisez l’action d’ajout proposée par l’assistant, puis enregistrez l’endpoint.

**Avec plusieurs tables :** demandez au responsable quelles colonnes relient les tables. Une relation erronée peut produire un résultat faux même sans message d’erreur. Faites valider ces relations avant d’utiliser les résultats.

### Utiliser un modèle d’action

Dans **Insérer une action**, choisissez le modèle adapté : appel HTTP, lecture/écriture SQL, lecture/écriture de fichier, mise en forme ou message dans le journal.

Pour une action liée à une source, le Studio exige d’abord une connexion active du bon type. Il renseigne la première connexion compatible et annonce son nom : **vérifiez qu’il s’agit de la bonne source**. Les modèles restent des points de départ ; leurs chemins, tables, colonnes et valeurs d’exemple doivent être adaptés avant utilisation.

### Quand demander une aide technique

Si vous devez modifier du texte entre accolades, des instructions SQL ou un pipeline en JSON, faites préparer la partie concernée par une personne qualifiée. Une lecture SQL guidée simple est accessible par l’assistant ; une écriture de données ou une authentification personnalisée n’est pas une opération à improviser.

Ne testez jamais une écriture, un envoi externe ou une suppression sur des données réelles pour « voir ce que cela fait ».

## 11. Demander un numéro ou un filtre

1. Dans la partie paramètres reçus, ajoutez le type proposé par l’écran.
2. Pour une information qui identifie un dossier dans le chemin, choisissez un paramètre de chemin.
3. Pour une recherche facultative, choisissez un filtre de requête.
4. Donnez un nom simple et un libellé compréhensible, indiquez le type et un exemple.
5. Cochez obligatoire uniquement si l’information est indispensable ; les paramètres de chemin le sont.
6. Regardez l’aperçu du chemin et essayez avec une valeur de test autorisée.
7. Si l’information doit servir dans une action, utilisez le choix du paramètre et de l’étape prévu dans l’interface. Faites vérifier le branchement par le responsable.

Un paramètre déclaré mais non utilisé par une action ne filtrera pas les résultats par magie. Ne collez pas une valeur reçue directement dans une instruction SQL.

## 12. Faire un essai et lire son résultat

1. Enregistrez l’API, puis l’endpoint.
2. Renseignez les exemples de paramètres si l’opération en demande.
3. Si un corps d’essai est nécessaire et que l’écran attend du JSON, faites-vous fournir un exemple prêt à l’emploi ; ne mettez jamais de mot de passe réel dans un exemple partagé.
4. Utilisez le bouton d’essai de l’endpoint.
5. Lisez le résultat et vérifiez que les données sont bien celles attendues.
6. Ouvrez **Exécutions** pour retrouver la tentative et son détail.

Une exécution réussie ne prouve pas, à elle seule, que les données sont correctes : comparez quelques dossiers connus avec la source. Un essai interne réussi ne prouve pas non plus que le logiciel externe possède ses droits.

## 13. Autoriser un logiciel externe

**Prérequis : une organisation et les API à autoriser.** Cette tâche nécessite l’accord du responsable de la sécurité.

1. Ouvrez **Accès consommateurs**.
2. Choisissez l’organisation.
3. Cliquez sur **Ajouter un système**, puis renseignez un code et un nom parlant.
4. Pour des identifiants gérés par la plateforme, choisissez le mode local proposé.
5. Cochez uniquement les API nécessaires au logiciel.
6. Enregistrez le profil. Vous pouvez le préparer désactivé ; il faut au moins une API autorisée pour l’activer.
7. Dans la partie consommateurs du profil enregistré, renseignez l’identifiant du logiciel et utilisez **Créer et afficher son secret une seule fois**.
8. Copiez immédiatement le secret dans le coffre-fort approuvé. Il ne sera plus affiché.
9. Transmettez les éléments par le canal sécurisé convenu à l’équipe du logiciel, pas dans une capture du Studio.
10. Activez le profil lorsque sa configuration est prête et validée.

Le mode **règles du système existant / pipeline** doit être préparé par l’équipe technique. Ne remplacez pas ces vérifications par une règle qui accepte tout.

Les adresses de connexion et de renouvellement sont affichées dans la fiche ; l’équipe du logiciel doit y ajouter l’adresse et l’éventuel contexte global du backend. Les consommateurs utilisent leurs propres tokens, jamais le compte ou le token d’un utilisateur Studio.

**Révoquer :** utilisez la révocation du profil ou d’une session uniquement si vous voulez couper l’accès correspondant. Enregistrer les changements du profil révoque les anciennes sessions : prévenez l’équipe du logiciel et prévoyez sa reconnexion.

## 14. Mettre en service ou suspendre

Avant mise en service, vérifiez avec le responsable :
- la bonne organisation et les bonnes connexions ;
- les essais et les résultats métier validés ;
- les droits du profil consommateur et la transmission sécurisée des secrets ;
- les états actifs requis de l’API, de l’endpoint, des connexions et du profil.

Dans l’API, choisissez **Active** puis enregistrez lorsque la mise en service est approuvée. Le logiciel externe doit encore faire son propre essai avec ses droits.

Pour interrompre un échange, utilisez le statut **En pause** de l’API ou la désactivation appropriée, puis enregistrez. Prévenez les utilisateurs concernés. Ne supprimez pas une fiche simplement pour arrêter momentanément un traitement.

## 15. Programmer une exécution automatique

1. Ouvrez un endpoint enregistré et testé.
2. Dans la partie planification, choisissez une fréquence prédéfinie.
3. Renseignez les éventuelles données de départ avec l’exemple validé par le responsable.
4. Activez la planification lorsque vous êtes prêt et enregistrez l’endpoint.
5. Vérifiez l’exécution suivante dans **Exécutions**.

Le mode avancé « cron » s’adresse à un technicien. Faites confirmer l’heure et le fuseau du serveur ; l’heure de votre ordinateur ne garantit pas celle du traitement. Une tâche automatique peut effectuer des écritures à répétition : faites valider sa fréquence et son effet.

Pour arrêter l’automatisme, désactivez sa planification et enregistrez. L’enregistrement de l’endpoint et celui de sa planification sont deux opérations : si le Studio signale un échec de planification, ne considérez pas cette dernière comme enregistrée.

## 16. Suivre les échanges et les modifications

### Exécutions
1. Ouvrez **Exécutions**.
2. Filtrez par API ou par statut si nécessaire.
3. Actualisez la liste.
4. Ouvrez le détail de la tentative pour voir le résultat ou l’étape en erreur.
5. Notez la date, l’API, l’endpoint et le message utile pour signaler un problème.

Évitez de relancer plusieurs fois une opération d’écriture : une première tentative peut avoir déjà modifié certaines données. Faites vérifier la situation avant de recommencer.

### Journal d’audit
Ouvrez **Journal d’audit** pour voir qui a changé une configuration et à quel moment. Il ne remplace pas la liste des échanges : cherchez les résultats de traitement dans **Exécutions**.

### Tableau de bord
Utilisez-le pour une vue d’ensemble des API, des connexions et des exécutions récentes. Les compteurs ne remplacent pas la vérification du détail d’un échange.

## 17. Gérer les utilisateurs — administrateur

1. Ouvrez **Utilisateurs** et le bouton de création.
2. Renseignez l’identifiant, le nom si demandé et un mot de passe initial conforme aux règles affichées.
3. Choisissez le rôle minimum nécessaire : lecture seule, éditeur ou administrateur.
4. Enregistrez et transmettez les accès par le canal sécurisé de l’institution.
5. Demandez à la personne de changer son mot de passe dès sa première utilisation.

Pour un départ, désactivez le compte selon les procédures de l’institution. Ne désactivez pas le seul administrateur disponible. Un consommateur externe se gère dans **Accès consommateurs**, pas ici.

## 18. Configuration et installation — à confier à l’exploitant

La page **Configuration** agit sur la plateforme, pas sur une seule API. N’y changez pas une adresse, un port, un profil ou un chemin pour corriger un simple problème de saisie.

Après une modification, suivez le message de redémarrage si l’écran le demande. Demandez à l’exploitant de faire ce redémarrage. Les réglages du navigateur et ceux du serveur sont distincts.

Pour installer : utiliser le document **INSTALLATION-ASSISTEE.md** avec l’exploitant. Le mode installation exige un code temporaire provenant du serveur. Ce guide utilisateur ne demande pas de manipuler la base de données ni de saisir des commandes.

## 19. Supprimer ou désactiver sans risque

Avant de supprimer un élément, demandez qui l’utilise. Préférez une pause ou une désactivation pour un arrêt temporaire. Ne supprimez pas une connexion utilisée par une opération ni une organisation contenant des API sans plan validé.

Les contrôles de création ne constituent pas une garantie générale contre toutes les suppressions ou modifications faites ailleurs. Si un prérequis a été supprimé entre-temps, le Studio peut demander de le recréer ou de choisir son remplaçant.

## 20. Résoudre les problèmes courants

| Situation | Que faire |
|---|---|
| Créer une API est bloqué | Créer une organisation, revenir dans l’API et la sélectionner. |
| Organisation introuvable dans une liste | Enregistrer la fiche, puis recharger la page concernée. Si cela persiste, faire vérifier vos droits et la disponibilité du serveur. |
| Impossible d’ajouter une action de source | Créer une connexion active du bon type avant de revenir à l’endpoint. |
| Une connexion est refusée à l’enregistrement | Vérifier qu’elle existe encore, qu’elle est active et qu’elle correspond au type d’action. |
| Le code est refusé | Utiliser un nom court sans `/`, respecter la longueur indiquée et choisir un code non déjà utilisé. |
| Le profil consommateur ne s’active pas | Créer/rattacher une API à l’organisation et cocher ses droits. |
| Je ne peux pas créer de secret | Enregistrer d’abord le profil du système. |
| L’essai échoue | Lire le message et le détail dans Exécutions ; ne pas relancer une écriture à l’aveugle. |
| L’essai réussit mais le logiciel externe échoue | Faire vérifier le profil consommateur, ses API autorisées, ses tokens et l’adresse du backend. |
| Aucun résultat | Vérifier les exemples, filtres et données attendues avec le propriétaire de la source. |
| Une modification n’apparaît pas | Vérifier la confirmation d’enregistrement, puis recharger ; ne pas quitter un autre formulaire non enregistré. |
| Un menu ou un bouton manque | Demander à l’administrateur de vérifier votre rôle. |

### Les informations à transmettre pour obtenir de l’aide

Envoyez le nom de la page, l’action tentée, la date/heure, le nom de l’API ou de la connexion et le message exact. Une capture peut aider après masquage des mots de passe, secrets et données personnelles.

## 21. Première réussite : votre liste de contrôle

- [ ] Je dispose d’un compte autorisé et d’un environnement d’essai.
- [ ] Mon organisation existe.
- [ ] La connexion nécessaire existe, est active et a été testée.
- [ ] Mon API est enregistrée dans cette organisation.
- [ ] Mon endpoint contient les actions validées, dans le bon ordre.
- [ ] J’ai enregistré puis testé avec des exemples sans risque.
- [ ] J’ai comparé le résultat avec les données attendues.
- [ ] Le profil du logiciel externe n’autorise que les API nécessaires.
- [ ] Les secrets ont été conservés et transmis de façon sécurisée.
- [ ] La mise en service a été approuvée et les premières exécutions sont suivies.

---
Guide rédigé à partir des écrans et du code du dépôt. Il ne constitue pas un procès-verbal de recette : aucun build, test ni serveur n’a été exécuté pendant cette modification. Faites confirmer les réglages propres à votre institution par son responsable.
