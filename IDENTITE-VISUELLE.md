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
par chaque institution avant diffusion sous son identité. Les logos CNEF et DGI sont fournis localement (sources ci-dessous). Les logos
SRC et SIGIF utilisent leurs URL publiées sur les sites de référence ; un échec
de chargement affiche le sigle, sans casser le sélecteur. Aucun logo artificiel
n’est utilisé. Pour un réseau fermé, déposer les images validées localement
et remplacer les URL dans branding.json.

Sources des images :
- CNEF : https://cnefcameroun.cm/wp-content/uploads/2023/06/logo1.png
- DGI : https://fr.wikipedia.org/wiki/Fichier:Logo_DGI_Cameroun.png (copie récupérée via recherche d’images ; à valider par la DGI).
- SRC : https://src.cm/wp-content/uploads/2025/09/logo-src-cameroun.png (référence retrouvée dans la médiathèque publique du site).
- SIGIF : https://www.sigif2.cm/sigif/images/logo_sigif_grand_format.png (image référencée par la page de connexion).

## Utilisation

1. Ouvrir **Organisations**, créer ou modifier l’organisation.
2. Choisir **Identité visuelle du Studio** : automatique, neutre, CNEF, DGI, SRC ou SIGIF.
   En automatique, les codes/noms contenant les sigles CNEF, DGI, SRC et SIGIF sont reconnus.
3. Enregistrer. Cette association est conservée en base dans
   `contextConfig.variables.studioTheme`, avec les autres paramètres de
   l’organisation. Elle ne contient aucun secret.
4. En haut du Studio, cliquer sur un **logo de plateforme** pour appliquer directement son thème,
   même sans association enregistrée. Le choix d’organisation reste également disponible.
   La navigation, les boutons principaux et le bandeau de contexte suivent
   alors le thème associé. Actualiser la liste avec ↻ après une création.
5. Cliquer sur **API-GEN Studio** pour revenir à l’identité commune.

La sélection d’une organisation dans l’éditeur d’API applique également son thème.
Un ancien paramètre explicite `studioTheme: neutral` reste prioritaire sur la
détection : choisir « Automatique » dans Organisations ou cliquer directement
sur le logo pour appliquer CNEF. Le bandeau affiche toujours le thème actif.

Le choix visuel n’est pas un mécanisme de sélection de tenant : il ne filtre
pas les listes et n’accorde aucun droit. Le bandeau le rappelle explicitement.
La sélection n’est pas persistée dans le navigateur : la connexion et une
nouvelle session de navigation du shell repartent en contexte neutre. Les
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
