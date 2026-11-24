# Item_List

## Ce projet fait partie de Toolkit

- Emplacement : `projets/item-list/`, à l'intérieur du dossier Toolkit
  (la racine de Toolkit est donc `../..` depuis ce dossier).
- Ce projet peut être modifié librement depuis n'importe quel éditeur (VS Code, Android
  Studio, Notepad++…), sans passer par Toolkit.
- Sa fiche `projet.json` décrit le projet pour Toolkit (nom, type, point d'entrée,
  actions…). **Garde-la à jour** si tu changes le point d'entrée, ajoutes ou modifies une
  action (bouton), etc. Le format est documenté dans `../../app/docs/projet-json.md`.
- Explique ce que tu fais en français, simplement.
- Git : fais des **commits locaux** réguliers avec des messages clairs en français.
  **Ne fais jamais de push** : l'utilisateur pousse lui-même depuis Toolkit.
  Ne versionne jamais de secrets (clés, mots de passe, keystores, `.env`, `local.properties`).

## Rôle du projet

Mod **Fabric 26.3**, **100 % client**, pour créer et suivre des listes d'items (objectifs de farm, projets de construction, recettes de plugins…), compatible avec les items custom de **Nexo**, **ItemsAdder**, **Oraxen** (et MythicMobs / MMOItems / plugins Bukkit génériques).

Documentation utilisateur : `README.md` (fonctionnalités, formats JSON/CSV, touches, fichiers de
config). Fonctionnement détaillé et dépannage : `docs/FONCTIONNEMENT.md`. Historique : `CHANGELOG.md`.
Ne recopie pas ces documents ici : mets-les à jour eux-mêmes quand le comportement change.

## Technologies

- Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.167.0+26.3**, Loom **1.19.4**,
  Gradle 9.8.2 (wrapper). Versions dans `gradle.properties`, minimums dans
  `src/main/resources/fabric.mod.json` (`fabricloader >=0.19.5`, `minecraft ~26.3`, `java >=25`).
- **Java 25** (compilation `release = 25`, Loom l'exige aussi pour lancer Gradle).
- modid **`itemlists`**, package racine **`com.eldrazy.itemlists`**, `"environment": "client"`.
  Version du mod : `version` dans `gradle.properties` (injectée dans `fabric.mod.json`).
- Aucune dépendance embarquée : config en Gson (fourni par le jeu), **pas** de Mod Menu ni Cloth
  Config (décision de l'utilisateur : coût client minimal, écran de réglages maison).
- **Pas de mixin** : tout passe par les événements Fabric API. Seul `itemlists.classtweaker`
  ouvre des champs de `AbstractContainerScreen` (position, taille, slot survolé) et le livre de
  recettes de `AbstractRecipeBookScreen`.
- Commentaires du code en français ; textes de l'interface dans `assets/itemlists/lang/`
  (`en_us.json` et `fr_fr.json` : ajouter chaque clé dans les deux).

## Architecture (`src/main/java/com/eldrazy/itemlists/`)

- `ItemListsClient` : point d'entrée client (chargement, tick, sauvegardes, enregistrements).
- `data/` : `ItemList`/`ListEntry` (modèle), `ListManager` (CRUD, liste active, sauvegarde
  différée et atomique), `ListIds` (slug du nom → id), `CustomItemCache` (items custom vus, par
  serveur), `ItemSource`/`ItemIds`.
- `identity/` : `IdentityRegistry` (chaîne ordonnée d'extracteurs), `CustomDataReaders`
  (lecteurs purs de `custom_data`), `VanillaVariants` (potions, flèches, livres enchantés).
- `inventory/` : `RecursiveCounter` (comptage pur, shulkers/bundles), `InventoryTracker`
  (empreinte par tick, recomptage seulement si elle change), `ContainerOrigin` (vrai stockage
  ou menu de plugin).
- `io/` : `ListJsonCodec`, `ListCsvCodec`, `ImportService`, `ExportService`.
- `config/` : `ModConfig`, `HudConfig`, `ConfigManager` (Gson), `ModPaths` (tous les chemins
  `config/itemlists/…`).
- `client/` : `ServerPrefix` + `IdConversion` (préfixe du serveur), `CatalogData`, `ItemDisplay`
  (id → stack affichable), `EntryRows`, `ListActions`, `FileDialogs` (sélecteur natif **SDL**),
  `ModKeyBindings`, `command/` (`/itemlists dump|open|hud`), `gui/` (écrans ; `Theme` centralise
  le style repris de chunklimitcounter), `hud/`, `inventory/` (`ContainerOverlay` : boutons et
  panneau latéral des conteneurs).
- `compat/rei`, `compat/jei` : intégrations optionnelles (voir plus bas).

La logique pure (codecs, slugs, `ListManager`, `RecursiveCounter`, `CustomDataReaders`, format de
`VanillaVariants`, purge du cache) ne dépend pas du jeu : garde-la ainsi, c'est ce qui la rend
testable en JUnit.

## Ids des items custom

Format validé par l'utilisateur, à ne pas changer (l'app Android La Citadelle s'aligne sur le mod) :

| Plugin | Lu dans `custom_data` | Id canonique |
|---|---|---|
| Nexo | `PublicBukkitValues."nexo:id"` | `nexo:<id>` |
| Oraxen | `PublicBukkitValues."oraxen:id"` | `oraxen:<id>` |
| ItemsAdder | `itemsadder.{namespace,id}` | `<namespace>:<id>` (namespace réel) |
| MythicMobs | `PublicBukkitValues."mythicmobs:type"` (ou ancien `MYTHIC_TYPE`) | `mythicmobs:<type>` |
| MMOItems | `MMOITEMS_ITEM_TYPE` + `MMOITEMS_ITEM_ID` | `mmoitems:<type>/<id>` |

- Variantes vanilla : `minecraft:potion[minecraft:fire_resistance]`,
  `minecraft:enchanted_book[minecraft:sharpness=5]` ; replis : `pack:modele` (item_model),
  `minecraft:paper#10001` (custom model data), `minecraft:paper#nom_slug` (nom non italique).
  Ordre complet des extracteurs : tableau du README.
- **Préfixe du serveur** (`useServerPrefix`, activé par défaut) : dans les listes et exports,
  `nexo:`, `oraxen:`, `mythicmobs:`, `mmoitems:` sont remplacés par le préfixe du serveur
  (`la_citadelle:ruby`, `solo:` en solo). ItemsAdder et `minecraft:` ne changent jamais. Le
  comptage accepte les deux formes (`ServerPrefix.canonical`).
- Ajouter un plugin : lecteur dans `CustomDataReaders`, `register(...)` dans le bloc `static` de
  `IdentityRegistry` (inséré avant les replis), cas dans `CustomDataReadersTest`
  (`docs/FONCTIONNEMENT.md` §11).
- Id d'une **liste** : slug ASCII du libellé, régénéré au renommage, le fichier suit l'id,
  suffixes `_2`, `_3` en cas de collision.

## Configuration

- `config/itemlists/config.json` (`ModConfig` + `HudConfig`, bornés par `clamp()`), listes dans
  `config/itemlists/lists/<id>.json`, cache `custom_items_cache.json`, dossiers `import/`,
  `export/`, `dumps/`. Arborescence et formats : README.
- Tout nouveau chemin passe par `ModPaths` ; tout nouveau réglage a une valeur par défaut sûre
  (les anciens `config.json` n'ont pas le champ).
- Le format JSON des listes est figé (ordre des clés, 2 espaces, LF, UTF-8 sans BOM) : les tests le
  comparent octet pour octet à `src/test/resources/reference/`.

## Intégrations REI / JEI (EMI désactivé)

- `compileOnly` dans `build.gradle`, jamais embarqués ; entrypoints `rei_client` et
  `jei_mod_plugin` dans `fabric.mod.json` (seuls REI/JEI chargent ces classes : aucune référence
  à `compat/` depuis le reste du code).
- Fonctions : zones d'exclusion, panneau placé à l'opposé de leur liste, touche `N` sur l'item
  survolé (liste et favoris), glisser-déposer vers le panneau latéral (favoris REI, liste JEI).
- Versions : `rei_version` / `jei_version` dans `gradle.properties`.
- **EMI** : pas de build 26.x, intégration désactivée. Ne pas la réactiver sans version 26.3.

## Lancer, compiler, tester

Java 25 obligatoire. Depuis Toolkit le bon JDK est fourni ; en ligne de commande, si `JAVA_HOME`
pointe vers le JDK 21 :

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot" ./gradlew build
```

| Commande | Rôle |
|---|---|
| `./gradlew build` | compile + tests unitaires ; jar dans `build/libs/itemlists-<version>.jar` |
| `./gradlew runClient` (`-Pviewer=rei` ou `-Pviewer=jei`) | client de dev, avec REI/JEI en option |
| `./gradlew runClientGameTest` (idem `-Pviewer=…`) | tests en jeu automatisés + captures |

- **Tests unitaires** (`src/test`, JUnit 5) : codecs JSON/CSV, slugs, `ListManager`, comptage
  récursif, lecteurs `custom_data`, variantes vanilla, préfixe serveur, purge du cache.
- **Tests en jeu** (`src/gametest`, modid `itemlists-gametest`, jamais dans le jar) :
  `ItemListsClientGameTest` crée un monde, donne de **faux items de plugins** par `/give` avec
  composants, puis vérifie identités, `/itemlists dump`, comptage, découverte (menu vs vrai
  coffre), variantes, panneau, préfixe serveur, et la touche `N` avec REI/JEI.
- **Captures** : `build/run/clientGameTest/screenshots/` (`itemlists_*` en anglais, `*_fr` en
  français). Les `showcase_*.png` (1920×1045, échelle GUI 3, items vanilla seulement) vont dans
  `docs/images/<nom>-vN.png` : **incrémenter `vN`** à chaque régénération (cache d'images GitHub).
  Lis les captures pour vérifier une interface sans demander à l'utilisateur de cliquer.
- Avant une version : `build` + `runClientGameTest` (puis avec REI et JEI), section en tête du
  CHANGELOG, `version` dans `gradle.properties`.

## Pièges

- **26.3 = SDL, plus GLFW** : utiliser `InputConstants` (touches, boutons de souris), jamais
  `org.lwjgl.glfw`. Le sélecteur de fichier passe par `org.lwjgl.sdl.SDLDialog` (tinyfd n'est
  plus livré) et doit être appelé depuis le thread du jeu ; son callback peut arriver sur un
  autre thread.
- Ne pas compter ni mémoriser les items des **menus de plugins** (boutons, icônes Nexo) :
  `ContainerOrigin` distingue un vrai stockage ouvert d'un clic droit d'un menu serveur. Seuls les
  items de plugins d'items sont mémorisés d'office dans le cache.
- Le comptage ne doit pas tourner à chaque tick ni le HUD recalculer à chaque image : passer par
  l'empreinte d'`InventoryTracker` et les caches de `HudLines` / du panneau.
- Un fichier de liste illisible n'est jamais écrasé ni supprimé ; une erreur d'import affiche un
  message, jamais de crash.
- Touches par défaut (`Y`, `H`, `N`) choisies pour éviter Minecraft, chunklimitcounter (K, J, L)
  et REI/JEI (R, U, A, Ctrl+O, Ctrl+F).
