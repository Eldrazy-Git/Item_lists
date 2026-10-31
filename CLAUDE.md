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

Mod **Fabric 26.2**, **100 % client**, pour créer et suivre des listes d'items (objectifs de farm, projets de construction, recettes de plugins…), compatible avec les items custom de **Nexo**, **Items

## Technologies

Projet Java (Gradle).

## Lancer ou compiler

Compiler avec le wrapper Gradle du projet : `gradlew build` ; le .jar est produit dans `build/libs/`.
