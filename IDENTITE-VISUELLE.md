# Identités visuelles du Studio

## Références communiquées et consultées

- CNEF : https://cnefcameroun.cm/
- DGI : https://www.impots.cm/
- SRC : https://src.cm/
- SIGIF : https://www.sigif2.cm/ (redirige vers la connexion du système de gestion des informations forestières).

Les contenus de ces pages ont été récupérés. Ils identifient les organismes,
mais ne constituent pas une charte graphique complète avec palettes et règles
d’usage certifiées. **Les quatre palettes fournies sont des propositions de
travail, pas des couleurs officielles vérifiées.** Les faire valider/remplacer
par chaque institution avant diffusion sous son identité. Les logos CNEF et DGI sont fournis localement (sources ci-dessous). Les images
SRC et SIGIF utilisent les URL externes fournies par l’utilisateur ; un échec
de chargement affiche le sigle, sans casser le sélecteur. Aucun logo artificiel
n’est utilisé. Pour un réseau fermé, déposer les images validées localement
et remplacer les URL dans branding.json.

Sources des images :
- CNEF : https://cnefcameroun.cm/wp-content/uploads/2023/06/logo1.png
- DGI : https://fr.wikipedia.org/wiki/Fichier:Logo_DGI_Cameroun.png (copie récupérée via recherche d’images ; à valider par la DGI).
- SRC : https://obivsolutions.com/wp-content/uploads/2024/08/Societe-de-Recouvrement-des-Creances-SRC.png (image choisie et fournie par l’utilisateur).
- SIGIF : https://tse4.mm.bing.net/th/id/OIP.0PBUR2GL3f5XOrLYptzMyQAAAA?r=0&rs=1&pid=ImgDetMain&o=7&rm=3 (image choisie et fournie par l’utilisateur ; son statut de logo officiel n’a pas été vérifié).

## Utilisation

1. Ouvrir **Organisations**, créer ou modifier l’organisation.
2. Choisir **Identité visuelle du Studio** : automatique, neutre, CNEF, DGI, SRC ou SIGIF.
   En automatique, les codes/noms contenant les sigles CNEF, DGI, SRC et SIGIF sont reconnus.
3. Enregistrer. Cette association est conservée en base dans
   `contextConfig.variables.studioTheme`, avec les autres paramètres de
   l’organisation. Elle ne contient aucun secret.
4. En haut du Studio, le **carrousel de logos** présente le thème actif au premier
   plan et les autres cartes en profondeur. Cliquer sur une carte derrière
   l’amène devant et applique son thème. Les flèches précédente/suivante bouclent
   sur toutes les plateformes, y compris le Studio neutre. Les touches clavier
   gauche/droite fonctionnent lorsque le focus est dans le carrousel ; Tab et
   Entrée permettent aussi de sélectionner chaque carte. Aucune rotation automatique.
   La liste « Organisation associée » et son bouton d’actualisation sont supprimés ;
   le shell ne charge plus les organisations pour ce sélecteur purement visuel.
   Les transitions sont désactivées si la réduction des animations est demandée.
5. Cliquer sur **API-GEN Studio** pour revenir à l’identité commune.

La sélection d’une organisation dans l’éditeur d’API applique également son thème.
Un ancien paramètre explicite `studioTheme: neutral` reste prioritaire sur la
détection : choisir « Automatique » dans Organisations ou cliquer directement
sur le logo pour appliquer CNEF. Le logo au premier plan indique le thème actif.

Le choix visuel n’est pas un mécanisme de sélection de tenant : il ne filtre
pas les listes et n’accorde aucun droit. Le bandeau le rappelle explicitement.
Le logo choisi explicitement est mémorisé dans le stockage local du navigateur,
par chemin de déploiement. Il est restauré au démarrage, y compris après
déconnexion. Il reste prioritaire sur le thème automatique des organisations. Les
organisations sans thème reconnu utilisent toujours le thème neutre.

## Ajuster les couleurs sans reconstruire le frontend

Modifier `branding.json`, déployé à côté de `index.html` (source :
`frontend/public/branding.json`), puis recharger le Studio. Servir ce fichier
avec un cache court ou désactivé. Chaque entrée comporte :

- `id` : identifiant technique, stable et unique ;
- `label` : libellé institutionnel ;
- `primary`, `strong`, `soft`, `sidebar`, `accent` : couleurs hexadécimales à six chiffres ;
- `source` : adresse HTTPS de référence ;
- `logo` : chemin local `branding/nom.png` (webp/jpg acceptés) ou URL HTTPS.

Ne pas y placer de secret. Les identifiants configurés dans les organisations
correspondent à ces `id`. La palette neutre reste intégrée comme repli si le
fichier est absent ou illisible. Les couleurs de statut (erreur, succès,
avertissement) ne sont pas remplacées par les couleurs institutionnelles.
Après ajustement, contrôler les contrastes, notamment blanc/primary, le texte
clair du menu/sidebar et le texte sur soft. La présence de styles de focus ne
constitue pas à elle seule une certification d’accessibilité.

## Améliorations ergonomiques communes

- Contexte visuel explicite et retour au thème neutre.
- Navigation active avec bordure et texte renforcé, pas uniquement une couleur.
- Champs de saisie plus hauts, libellés renforcés et textes secondaires plus lisibles.
- Focus clavier visible et lien d’évitement « Aller au contenu principal ».
- Mise en page adaptée aux petits écrans et défilement des conteneurs de tableaux.
- Respect du réglage système de réduction des animations.
- Champs obligatoires du changement de mot de passe signalés.

## Validation

Aucun build, test ou serveur n’a été exécuté dans le workspace. Vérifier
localement l’association de chaque thème, la réouverture du formulaire, le
changement depuis l’en-tête, le retour neutre à la déconnexion, le repli sans
branding.json, la navigation clavier, les contrastes et les petits écrans.
La conformité aux chartes nécessite la validation des organismes concernés.

## Palettes dérivées des logos

Les thèmes CNEF et DGI utilisent les familles de couleurs visibles dans leurs
logos locaux : vert dominant, avec une touche dorée pour CNEF et rouge pour
DGI. Les nuances des boutons sont assombries pour la lisibilité ; il ne s’agit
pas d’un relevé colorimétrique certifié. Les couleurs de fond, bordures, titres,
cartes et focus sont désormais déclinées pour chaque thème, pas uniquement CNEF.
Les statuts fonctionnels conservent leur sens (erreur rouge, avertissement, succès).

Les images externes SRC et SIGIF fournies par l’utilisateur n’ont pas pu être
ouvertes par les outils de consultation lors de cette mise à jour. Leurs palettes
précédentes restent donc provisoires : ne pas les présenter comme extraites de
ces images. Fournir les fichiers image pour pouvoir les ajuster fidèlement.

## Déploiement des ressources de thème

La configuration `frontend/angular.json` inclut explicitement `branding.json`
et tout le dossier `public/branding` dans les assets Angular. Auparavant seul
`config.json` était copié : les thèmes et les logos locaux manquaient donc à
l’exécution. Cette correction de configuration nécessite de reconstruire puis
de redéployer le frontend une première fois. Ensuite, les palettes de
`branding.json` restent modifiables sans reconstruction.

Contrôle manuel après déploiement : ouvrir `branding.json` à côté de l’URL
de `index.html` (le résultat doit être du JSON, pas la page HTML de connexion),
puis `branding/cnef.png` et `branding/dgi.png`. Cliquer ensuite sur CNEF et DGI
dans le Studio et vérifier que le logo sélectionné passe au premier plan et que les couleurs changent. Un échec
de chargement du fichier affiche désormais un message et un bouton Réessayer.

## Interface compacte

Le carrousel conserve la navigation en boucle, avec des cartes de 68 × 60 px
et des logos de 48 × 32 px. La légende du thème et le bandeau de contexte
permanent sont retirés ; les erreurs de chargement restent visibles. Les
espacements partagés (pages, cartes, tableaux, formulaires) sont réduits, avec
champs et boutons d’au moins 44 px sur les appareils tactiles. Les blocs
promotionnels de connexion et le guide répétitif du tableau de bord sont retirés.
Les aides de sécurité, erreurs et indications obligatoires sont conservées.

Pour les codes des organisations, API et systèmes consommateurs, la saisie
accepte les majuscules, espaces et accents : en création, ces derniers sont
adaptés à la sortie du champ ou à l’enregistrement (ex. « Mon API » devient
`mon-api`). Les codes existants ne sont pas renommés. Les limites du backend
restent en place : unicité, longueurs et interdiction du slash dans les codes
d’URL ; le code consommateur doit commencer par une lettre. Aucun symbole
non autorisé n’est supprimé silencieusement.

Validation manuelle à effectuer : saisie naturelle des codes, enregistrement
avec Entrée, validation des erreurs, codes existants inchangés, carrousel sur
petit écran et parcours des formulaires au clavier. Aucun build ni test exécuté.

### Ajustement du carrousel

Les cartes mesurent désormais 78 × 70 px et les logos 58 × 42 px. Le titre
visible « Choisir une plateforme » est retiré ; le carrousel conserve un nom
accessible. Le thème neutre possède un logo original généré, stocké localement
dans `branding/default.png`, et sa carte porte le libellé « Défaut ». Le même
logo est repris dans la barre latérale lorsque le thème neutre est actif.

## Préférence persistante du logo

Le chargement des thèmes démarre à la racine de l’application, même avant la
connexion. Le choix explicite « Défaut » est mémorisé comme les autres logos.
Seul l’identifiant du thème est stocké : aucun compte, secret ou droit. Un thème
supprimé de branding.json est remplacé par le neutre après un chargement réussi.
Un échec réseau conserve la préférence pour une nouvelle tentative.

La préférence concerne ce navigateur et cette adresse de déploiement ; elle
n’est pas synchronisée entre appareils. Si le stockage est bloqué ou effacé,
la persistance ne peut pas être garantie ; la sélection courante fonctionne
toujours sans bloquer l’accès au Studio. Vérifications manuelles : sélectionner
CNEF, recharger, fermer/rouvrir, se déconnecter/reconnecter, ouvrir une API
d’une autre organisation, puis sélectionner Défaut et recharger.
