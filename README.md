# Baguette Server Bot

Mod **Fabric** pour **Minecraft 26.2** qui relie votre serveur à **Discord**, uniquement sur le serveur `baguette.mine.fun`.

## Fonctionnalités

| Fonctionnalité | Salon Discord | Description |
|---|---|---|
| Chat Discord → Minecraft | Salon chat | Les messages du bot sont transmis au serveur Minecraft |
| Chat Minecraft → Discord | Salon chat | Chaque message du chat est relayé |
| Commandes (ex. `/ban`, `/tp`) | Salon commandes | Chaque commande exécutée par un joueur est loguée |
| Connexion / Déconnexion | Salon joins | S'écrit quand un joueur rejoint ou quitte |
| **Mort avec position** | Salon deaths | S'écrit quand un joueur meurt, avec les **coordonnées exactes** (X, Y, Z) + la dimension |
| Advancement | Salon advancements | S'écrit quand un joueur obtient une progression |
| Auto-update | — | Vérifie les **releases GitHub** au démarrage et met à jour le mod automatiquement |

> **Position de mort** : le mod calcule les coordonnées `X Y Z` de la mort **côté serveur** (via le mixin sur `ServerPlayer.die`) et les envoie dans le message Discord. Aucun joueur n'a besoin d'être **OP** : tout admin qui a accès au serveur Discord voit la position de la mort de chaque joueur.

## Exemple de message de mort sur Discord

```
💀 <joueur> s'est tué en essayant de fuir <mob>
📍 Position de la mort : `-123, 64, 456` (Overworld)
```

## Prérequis

- Serveur **Fabric Loader 0.19.3+** pour **Minecraft 26.2**
- **Fabric API 0.158.0+26.2**
- **Java 25** ou plus

## Installation

1. Depuis les **[Releases](https://github.com/tear360/mc-tracks-baguettesmp/releases)**, téléchargez
   `baguette-server-bot-<version>.jar`.
2. Placez-le dans le dossier `mods/` du serveur.
3. Créez le dossier `config/baguette-server-bot/` et dedans un fichier `config.properties` :

> Le mod **crée automatiquement** ce dossier et ce fichier au premier démarrage.
> Son emplacement exact dépend de votre lanceur : dossier `server/` (serveur dédié)
> ou dossier `config/` à côté de `.minecraft` — **en production c'est la racine du dossier du serveur**,
> à côté de `mods/`. Le chemin exact est affiché dans les logs du serveur au démarrage
> (`Fichier de config : ...`).

```properties
# Token du bot Discord
discord_token=METTRE_TOKEN_ICI

# IDs des salons Discord (le bot doit y avoir accès)
channel_chat=000000000000000000
channel_joins=000000000000000000
channel_deaths=000000000000000000
channel_commands=000000000000000000
channel_advancements=000000000000000000
```

4. Remplissez le token et les IDs des salons (clic droit sur le salon → *Copier l'ID du salon*).
   > Le mode développeur de Discord doit être activé (Paramètres → Avancé → Mode développeur).
5. Démarrez le serveur sur l'IP `baguette.mine.fun`.

## Activation conditionnelle

Le mod ne s'active **que** si l'IP locale du serveur contient `baguette.mine.fun` :

- `config/server.properties` → `server-ip=baguette.mine.fun` ou l'IP résolue vers le serveur.
- Si l'IP ne correspond pas, le mod se charge mais reste **inactif** (bot non connecté, chat non relayé).

## Auto-update

Au démarrage, le serveur vérifie l'API GitHub
(`https://api.github.com/repos/tear360/mc-tracks-baguettesmp/releases/latest`).

- **Version à jour** → rien ne se passe.
- **Nouvelle version** → le jar est téléchargé, l'ancien est sauvegardé en `*.jar.bak`,
  un message demande de **redémarrer le serveur** pour appliquer la mise à jour.

## Développement

### Compiler

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

Le jar produit se trouve dans `build/libs/baguette-server-bot-<version>.jar`.

### Pile technique

- **Loom 1.17.20** (plugin `net.fabricmc.fabric-loom`, noms Mojang déobfusqués 26.2)
- **Gradle 9.5.1** (wrapper inclus)
- **Fabric API 0.158.0+26.2**
- **JDA 5.1.0** (embarqué par shading dans le jar, exclusions : slf4j-api, opus-java, guava)

### Structure

```
src/main/java/fr/baguettemod/
├── AutoUpdater.java                       # Vérification GitHub Releases + téléchargement
├── BaguetteMod.java                       # Entrypoint, activation par IP, events join/leave
├── Config.java                            # Lecture config.properties
├── DiscordBot.java                        # Client JDA + envoi par salon
├── DiscordListener.java                   # Bridge Discord → Minecraft
├── mixin/
│   ├── ServerGamePacketListenerImplMixin.java  # Chat + commandes (handleChat/handleChatCommand)
│   └── ServerPlayerMixin.java                  # Mort (ServerPlayer.die) + position X Y Z
└── server/
    └── MessageForwarder.java              # Forward Discord → serveur MC
```

### Publier une nouvelle version

1. Bump `mod_version` dans `gradle.properties`.
2. Build : `gradlew.bat build`.
3. Tag et release :

```bash
git tag -a v1.1.0 -m "v1.1.0"
git push origin v1.1.0
gh release create v1.1.0 .\build\libs\baguette-server-bot-1.1.0.jar --repo tear360/mc-tracks-baguettesmp --title "v1.1.0"
```

Les serveurs concernés se mettront à jour automatiquement au prochain démarrage.

## Licence

Privé — tous droits réservés.