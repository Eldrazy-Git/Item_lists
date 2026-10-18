# Changelog

Toutes les évolutions notables d'Item Lists sont listées ici.

## [Non publié]

### Ajouté
- **Potions, flèches à effet et livres enchantés distingués par leurs composants**, au même format que l'index du catalogue La Citadelle :
  - potions, potions jetables / persistantes et flèches à effet : `minecraft:potion[minecraft:fire_resistance]`, `minecraft:tipped_arrow[minecraft:long_swiftness]` (composant `potion_contents`) ;
  - livres enchantés : `minecraft:enchanted_book[minecraft:sharpness=5]`, plusieurs enchantements triés et séparés par des virgules (composant `stored_enchantments`).
  
  Chaque variante est comptée séparément, a sa propre icône et son nom (« Livre enchanté (Tranchant V) »), et le catalogue propose toutes les variantes des onglets créatifs.

### À faire après la mise à jour
- Une entrée `minecraft:potion`, `minecraft:tipped_arrow` ou `minecraft:enchanted_book` sans variante ne compte plus que les items sans composant : remplacez-la par la variante voulue depuis le catalogue.

## [1.0.1] - 2026-10-19

Corrections issues de tests sur un vrai serveur (Nexo + MythicMobs).

### Corrigé
- **Armes, armures et outils MythicMobs / Crucible** : ils sont désormais reconnus par leur vrai identifiant (`mythicmobs:elytreum_sword`…). Avant, le mod se rabattait sur le *custom model data* (`minecraft:netherite_sword#10000`), et deux items différents pouvaient être confondus. Ces items rangent leur type dans `PublicBukkitValues."mythicmobs:type"` ; l'ancien emplacement `MYTHIC_TYPE` reste reconnu.
- **Catalogue pollué par les menus des plugins** : les boutons de menus (rangs, catégories, « Retour », « Fermer », récompenses quotidiennes…) ne sont plus mémorisés comme items, même quand ce sont des items Nexo.
- **Compter le conteneur ouvert** : seuls les vrais coffres, barils et shulkers sont comptés, jamais un menu de boutique.

### Modifié
- **Découverte des items custom** : un item n'est retenu que s'il passe dans l'inventaire ou dans un vrai stockage (coffre, baril, shulker, ender chest, minecart ou bateau à coffre, animal de bât) ouvert d'un clic droit. Seuls les items de plugins d'items (Nexo, Oraxen, ItemsAdder, MythicMobs, MMOItems) sont mémorisés d'office ; les autres le sont quand vous les ajoutez vous-même.
- **Nettoyage automatique du cache au premier lancement** : les entrées des versions précédentes, d'origine inconnue, sont retirées, sauf celles utilisées dans une liste. Vos vrais items reviennent dans le catalogue dès qu'ils repassent dans votre inventaire.

### Ajouté
- Réglage **« Découvrir aussi dans les menus »** (désactivé par défaut), pour remplir le catalogue depuis `/nexo inventory`.
- Touche **`Suppr`** dans le catalogue : oublie l'item custom survolé.

### À faire après la mise à jour
- Les entrées de liste ajoutées avec un ancien identifiant MythicMobs (`minecraft:…#10000`) ne sont plus comptées : supprimez-les et ajoutez à nouveau ces items.

## [1.0.0] - 2026-10-18

Première version publique.

**Compatibilité :** Minecraft 26.2, Fabric Loader ≥ 0.19.5, Fabric API, Java 25. Mod **100 % client** : aucune installation sur le serveur.

### Listes d'items
- Créer, renommer, dupliquer et supprimer des listes, et choisir une **liste active** suivie par le HUD et le panneau des inventaires.
- Sauvegarde automatique dans `config/itemlists/lists/` (un fichier JSON par liste).
- Menu des listes (touche `Y` ou `/itemlists open`) :
  - recherche (nom, id, `@namespace`) et tri (ordre, nom, progression, manquants, quantité) ;
  - quantités modifiables par clic (saisie) ou à la molette (±1, Maj ±64, Ctrl ±10).
- Catalogue d'items : tous les items vanilla et les items custom découverts sur le serveur, avec recherche et filtres par source et par onglet créatif.

### Import / export
- **JSON** au format de référence `{ id, label, entries: [{ item, label, target }] }`. La lecture est tolérante (apostrophes simples, commentaires, virgules finales).
- **CSV** pour Excel FR : colonnes `id;nom;quantite_cible;source`, UTF-8 avec BOM. L'import détecte automatiquement le séparateur et l'encodage.
- Import via le sélecteur de fichier du système, ou depuis le dossier `config/itemlists/import/`. Un fichier invalide affiche un message clair, sans jamais faire planter le jeu ni écraser une liste existante.

### Items custom (Nexo, ItemsAdder, Oraxen…)
- Reconnaissance de l'identifiant réel des items Nexo, Oraxen, ItemsAdder, MythicMobs, MMOItems et des autres plugins Bukkit. Pour les items non reconnus, le mod se replie sur le modèle, le custom model data ou le nom.
- Les items custom vus en jeu (inventaire, coffres, menus de plugins) sont mémorisés par serveur, pour le catalogue et l'affichage de leur vrai modèle.
- Commande `/itemlists dump` (et `dump inventory`) pour vérifier l'identité reconnue et les composants d'un item.

### HUD
- Affichage de la liste active dans le style de chunklimitcounter : couleur selon l'état (manquant, partiel, complet) et barre de progression.
- Comptage en temps réel de l'inventaire et du contenu des shulker boxes et bundles, de façon récursive. En option, le conteneur ouvert est aussi compté.
- Éditeur (`/itemlists hud`) :
  - placement par glisser-déposer, avec ancrage automatique ;
  - réglage de l'échelle, de l'opacité du fond et du texte, du nombre de lignes ;
  - mode compact, masquage des items complétés, barres de progression activables.
- Touche `H` pour afficher ou masquer le HUD.

### Inventaires et conteneurs
- Trois boutons dans l'inventaire et dans tous les conteneurs (coffres, shulkers, barils, menus de plugins) :
  - menu des listes ;
  - afficher ou masquer le panneau de la liste active ;
  - position du panneau (auto, gauche, droite, dessous).
- Quantités modifiables directement dans le panneau : clic puis saisie au clavier, ou molette.
- Touche `N` pour ajouter l'item survolé à la liste active. En jeu, sans menu ouvert, elle ajoute l'item tenu en main.

### Intégrations
- **REI** et **JEI** (optionnels) :
  - leur panneau d'items s'écarte de nos boutons et du panneau, qui se place du côté opposé à leur liste ;
  - la touche `N` fonctionne sur les items de leur liste et de leurs favoris ;
  - on peut glisser un item jusqu'au panneau (depuis les favoris REI ou la liste JEI).
- EMI n'est pas pris en charge : il n'existe pas encore de version EMI pour Minecraft 26.x.

### Limites connues
- Pour un item vanilla, l'identité est l'item lui-même : toutes les potions comptent comme `minecraft:potion` *(corrigé depuis : voir « Non publié »)*.
- REI ne permet pas de faire glisser les items de sa liste principale. Utilisez la touche `N`, ou passez par les favoris REI.
- Les clés utilisées pour Nexo, Oraxen et ItemsAdder correspondent aux versions publiques de ces plugins. Vérifiez-les sur votre serveur avec `/itemlists dump`.

[1.0.1]: https://github.com/Eldrazy-Git/Item_lists/releases/tag/v1.0.1
[1.0.0]: https://github.com/Eldrazy-Git/Item_lists/releases/tag/v1.0.0
