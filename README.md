# Baguette Server Bot

Mod **Fabric** pour **Minecraft 26.2**, **CLIENT UNIQUEMENT**, qui relaye vers **Discord** ce que le joueur voit sur le serveur **`baguette.mine.fun`**.

> À partir de la v1.2.0, le mod est **uniquement un mod client**. Il s'installe dans le dossier `mods/` de VOTRE instance Minecraft (ex. MultiMC/Prism), **pas sur le serveur**. Il fonctionne sans aucune permission : même sans être OP, il voit tout ce que vous voyez, et envoie les événements à Discord.

## Fonctionnalités

| Fonctionnalité | Salon Discord | Description |
|---|---|---|
| Chat que VOUS envoyez | Salon chat | Chaque message que vous écrivez dans le chat est relayé |
| Chat des autres joueurs | Salon chat | Les messages des autres (à portée) sont relayés |
| Commandes que VOUS exécutez | Salon commandes | Chaque commande (ex. `/tp`, `/home`) est loguée |
| Discord → Minecraft | Salon chat | Un message du bot est envoyé dans le chat du serveur |
| Discord → Commande | Salon chat | Un message commençant par `/` exécute la commande |
| Connexions / Déconnexions | Salon joins | Joins/leaves de vous et des autres joueurs visibles |
| **Morts avec position** | Salon deaths | S'écrit quand UN joueur meurt, avec les **coordonnées X Y Z** + la dimension |
| Event de mort | — | Détecté via le packet d'événement d'entité (code 3) du client |
| Auto-update | — | Vérifie les **releases GitHub** au démarrage et se met à jour au redémarrage |

> **Position de mort** : le mod interprète côté client le packet `ClientboundEntityEventPacket` (event 3 = mort) et le packet
> `ClientboundDamageEventPacket` (source du coup), puis calcule `X Y Z` via `entity.blockPosition()`. Aucun joueur n'a
> besoin d'être **OP** : tout ce que le client voit est suffisant.
> ⚠️ Limite inhérente : seules les morts **à portée de rendu** du joueur qui a le mod sont détectées.

## Exemple de message de mort sur Discord

```
💀 TEAR36 was slain by ErLucAl
📍 Position de la mort : `-123, 64, 456` (Overworld)
```

## Prérequis

- Minecraft **26.2** en **client** avec **Fabric Loader 0.19.3+**
- **Fabric API 0.158.0+26.2**
- **Java 25** ou plus

## Installation

1. Depuis les **[Releases](https://github.com/tear360/mc-tracks-baguettesmp/releases)**, téléchargez
   `baguette-server-bot-<version>.jar`.
2. Placez-le dans le dossier `mods/` de VOTRE instance :
   - MultiMC / Prism : clic droit sur l'instance → *Dossier mods* (ou `…/<instance>/.minecraft/mods/`).
3. Démarrez le jeu **une fois** : le mod crée automatiquement son fichier de config.
4. Remplissez le fichier de config (voir ci-dessous).
5. Reconnectez-vous : le mod s'active automatiquement quand vous vous connectez à `baguette.mine.fun`.

## Configuration

Le mod crée automatiquement **au premier lancement** :

```
<dossier de l'instance>/.minecraft/config/baguette-server-bot/config.properties
```

⚠️ Le chemin exact est loggé au démarrage : cherchez `[BaguetteMod] Fichier de config : …` dans les logs.

```properties
# Token du bot Discord
discord_token=METTRE_TOKEN_ICI

# IDs des salons Discord (le bot doit y avoir accès)
channel_chat=ID_DU_SALON_CHAT
channel_joins=ID_DU_SALON_JOINS
channel_deaths=ID_DU_SALON_DEATHS
channel_commands=ID_DU_SALON_COMMANDS
channel_advancements=ID_DU_SALON_ADVANCEMENTS
```

> Mode développeur Discord requis pour copier les IDs des salons (Paramètres → Avancé → Mode développeur).

## Activation conditionnelle

Le mod ne s'active **que** si le nom du serveur auquel vous vous connectez contient `baguette.mine.fun`.
Sur une autre IP (serveur de test, local, etc.), le mod reste **inactif** : aucun bot connecté, aucun relais.

## Auto-update

Au lancement, le mod vérifie l'API GitHub
(`https://api.github.com/repos/tear360/mc-tracks-baguettesmp/releases/latest`).

- **Version à jour** → rien ne se passe.
- **Nouvelle version** → le jar est téléchargé dans `mods/baguette-server-bot-update.jar`.
  Au prochain arrêt de Minecraft, l'ancien jar est remplacé automatiquement (sinon un message indique le renommage manuel).

> Windows peut verrouiller le jar en cours d'utilisation : si le remplacement automatique échoue,
> supprimez l'ancien mod et renommez `baguette-server-bot-update.jar` en `baguette-server-bot-<version>.jar`.

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
- **JDA 5.1.0** (embarqué par shading, exclusions : slf4j-api, opus-java, guava)

### Structure

```
src/main/java/fr/baguettemod/
├── AutoUpdater.java                   # Vérification GitHub Releases + téléchargement
├── BaguetteMod.java                   # Entrypoint client, activation par IP, events self join/leave
├── Config.java                        # Lecture config.properties
├── DiscordBot.java                    # Client JDA + envoi par salon
├── DiscordListener.java               # Bridge Discord → Minecraft (sendChat/sendCommand)
└── mixin/
    └── ClientPacketListenerMixin.java # Chat, commandes, chat des autres, morts+position, joins/leaves
```

### Détection des événements (packets 26.2)

| Événement | Méthode mixinée | Packet |
|---|---|---|
| Votre chat | `ClientPacketListener.sendChat(String)` | — |
| Vos commandes | `ClientPacketListener.sendCommand(String)` | — |
| Chat des autres | `ClientPacketListener.handlePlayerChat` | `ClientboundPlayerChatPacket` |
| Mort (signal) | `ClientPacketListener.handleEntityEvent` | `ClientboundEntityEventPacket` (event 3) |
| Source de la mort | `ClientPacketListener.handleDamageEvent` | `ClientboundDamageEventPacket` |
| Join des autres | `ClientPacketListener.handlePlayerInfoUpdate` | `Action.ADD_PLAYER` |
| Leave des autres | `ClientPacketListener.handlePlayerInfoRemove` | `ClientboundPlayerInfoRemovePacket` |

### Publier une nouvelle version

```bash
# 1. Bump mod_version dans gradle.properties, puis :
gradlew.bat build

# 2. Tag + release :
git tag -a v1.2.0 -m "v1.2.0"
git push origin v1.2.0
gh release create v1.2.0 .\build\libs\baguette-server-bot-1.2.0.jar --repo tear360/mc-tracks-baguettesmp --title "v1.2.0"
```

Les clients se mettront à jour automatiquement au redémarrage suivant.

## Licence

Privé — tous droits réservés.