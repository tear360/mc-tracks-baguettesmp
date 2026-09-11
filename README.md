# Baguette Server Bot

Mod **Fabric** pour **Minecraft 26.2**, **CLIENT UNIQUEMENT**, qui relaye vers **Discord** ce que le joueur voit sur le serveur **`baguette.mine.fun`**.

> À partir de la v1.2.0, le mod est **uniquement un mod client**. Il s'installe dans le dossier `mods/` de VOTRE instance Minecraft (ex. MultiMC/Prism), **pas sur le serveur**. Il fonctionne sans aucune permission : même sans être OP, il voit tout ce que vous voyez, et envoie les événements à Discord.

## Fonctionnalités

| Fonctionnalité | Salon Discord | Description |
|---|---|---|
| Chat de TOUS les joueurs | Salon chat | Chaque message envoyé est relayé (via `handleDisguisedChat` + `sendChat`) |
| Discord → Minecraft | Salon chat | Un message du bot est envoyé dans le chat du serveur |
| Discord → Commande | Salon chat | Un message commençant par `/` exécute la commande |
| Connexions mondiales | Salon joins | Joins de **tous** les joueurs (paquet PlayerInfo) |
| Déconnexions mondiales | Salon leaves | Leaves de **tous** les joueurs (paquet PlayerInfo) |
| **Morts mondiales** | Salon deaths | La mort de **tout joueur** est relayée |
| Morts avec position | Salon deaths | Si le joueur est dans le rayon de rendu : `X Y Z` + dimension |
| Advancements mondiaux | Salon advancements | Les progrès de **tous** les joueurs sont relayés |
| **Horaires de connexion** | Commande slash | `/horaires [joueur]` → présence par heure + plage la plus probable |
| Auto-update | — | Vérifie les **releases GitHub** au démarrage et se met à jour au redémarrage |

## Horaires de connexion (`/horaires`)

Le mod enregistre en **temps réel** l'heure de connexion/déconnexion de **chaque joueur**
(le tien inclus) et accumule les minutes de présence par heure de la journée (0h-23h).
Les données sont persistées dans `config/baguette-server-bot/schedules.dat`.

La commande slash `/horaires` est disponible dans **le serveur Discord du salon chat** :

- `/horaires` → classement des joueurs suivis par temps de présence (+ plage la plus probable de chacun).
- `/horaires joueur:<pseudo>` → détail d'un joueur : minutes en ligne, heatmap des 24h,
  plage de 2h la plus probable, top des heures de présence et des heures de connexion.

Plus le mod tourne longtemps et souvent, plus les plages deviennent fiables.

> **Position de mort** : le mod interprète côté client le packet `ClientboundEntityEventPacket` (event 3 = mort) et le packet
> `ClientboundDamageEventPacket` (source du coup), puis calcule `X Y Z` via `entity.blockPosition()`. Aucun joueur n'a
> besoin d'être **OP**.
>
> **Morts hors rayon de rendu** : chaque mort étant diffusée à tous les joueurs via `ClientboundSystemChatPacket`
> (clé `death.attack.*`), le mod la capte aussi — mais **sans position** (inconnaissable en client-only).
> Une déduplication de 5 s évite les doublons avec l'event d'entité (qui, lui, porte la position et arrive en premier).
>
> **Chat des autres joueurs** : le serveur envoie le chat des autres en *disguised chat*
> (`ClientboundDisguisedChatPacket`) — le mod les parse (`<nom> message`) et les relaye.
>
> ⚠️ Limites inhérentes au client-only : les **commandes des autres joueurs** ne sont pas visibles, et la
> **position de mort** n'existe que pour les joueurs dans le rayon de rendu.

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
channel_leaves=ID_DU_SALON_LEAVES
channel_deaths=ID_DU_SALON_DEATHS
channel_advancements=ID_DU_SALON_ADVANCEMENTS
```

> Si `channel_leaves` est vide, les déconnexions partent dans `channel_joins`.

> Mode développeur Discord requis pour copier les IDs des salons (Paramètres → Avancé → Mode développeur).

## Activation conditionnelle

Le mod ne s'active **que** si le nom du serveur auquel vous vous connectez contient `baguette.mine.fun`.
Sur une autre IP (serveur de test, local, etc.), le mod reste **inactif** : aucun bot connecté, aucun relais.

## Auto-update

Au lancement, le mod vérifie l'API GitHub
(`https://api.github.com/repos/tear360/mc-tracks-baguettesmp/releases/latest`).

- **Version à jour** → rien ne se passe.
- **Nouvelle version** → le jar est téléchargé dans `mods/baguette-server-bot-update.jar.part`.
  - Au **propre arrêt** de Minecraft, le jar est remplacé automatiquement.
  - Sur **Windows** (jar verrouillé en mémoire), un script `mods/baguette-swap.bat` est lancé :
    il attend la fermeture complète de Minecraft, supprime l'ancien jar et renomme la mise à jour.

> Si Minecraft est tué brutalement (crash, kill), vérifiez qu'il ne reste pas un
> `-update.jar.part` dans `mods/` : supprimez-le ou appliquez le renommage à la main.

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
    └── ClientPacketListenerMixin.java # Chat (sendChat + disguised), morts+position, morts mondiales, joins/leaves, advancements
```

### Détection des événements (packets 26.2)

| Événement | Méthode mixinée | Packet |
|---|---|---|
| Votre chat | `ClientPacketListener.sendChat(String)` | — |
| Chat des autres | `ClientPacketListener.handleDisguisedChat` | `ClientboundDisguisedChatPacket` |
| Mort (signal, à portée) | `ClientPacketListener.handleEntityEvent` | `ClientboundEntityEventPacket` (event 3) |
| Source de la mort | `ClientPacketListener.handleDamageEvent` | `ClientboundDamageEventPacket` |
| Mort mondiale (hors portée) | `ClientPacketListener.handleSystemChat` | `ClientboundSystemChatPacket` (clé `death.attack.*`) |
| Advancement mondial | `ClientPacketListener.handleSystemChat` | `ClientboundSystemChatPacket` (clé `chat.type.advancement.*`) |
| Join des autres | `ClientPacketListener.handlePlayerInfoUpdate` | `Action.ADD_PLAYER` |
| Leave des autres | `ClientPacketListener.handlePlayerInfoRemove` | `ClientboundPlayerInfoRemovePacket` |

### Publier une nouvelle version

```bash
# 1. Bump mod_version dans gradle.properties, puis :
gradlew.bat build

# 2. Tag + release :
git tag -a v1.3.0 -m "v1.3.0"
git push origin v1.3.0
gh release create v1.3.0 .\build\libs\baguette-server-bot-1.3.0.jar --repo tear360/mc-tracks-baguettesmp --title "v1.3.0"
```

Les clients se mettront à jour automatiquement au redémarrage suivant.

## Licence

Privé — tous droits réservés.