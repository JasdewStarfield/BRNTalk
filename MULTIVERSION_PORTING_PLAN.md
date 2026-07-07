# BRNTalk Multi-Version Porting Plan

## Branch Layout

- `master`: current default branch, kept as the shared coordination branch until the default branch policy changes.
- `mc/1.21.1-neoforge`: maintenance branch for Minecraft 1.21.1 + NeoForge.
- `mc/1.20.1-forge`: porting and maintenance branch for Minecraft 1.20.1 + Forge.

## Sync Rules

1. Put gameplay and dialogue-system changes on the newest stable maintenance branch first.
2. Cherry-pick portable commits into the other maintenance branch.
3. Keep loader-specific rewrites isolated in small commits, so cross-version cherry-picks stay easy to review.
4. Prefer platform wrapper methods over direct Forge/NeoForge calls in gameplay code.
5. Tag releases with both Minecraft version and loader, for example `mc1.21.1-neoforge/v1.0.3` and `mc1.20.1-forge/v1.0.3-forge.1`.

## First Refactor Goal

Before the Forge 1.20.1 port goes deep, move loader-specific calls behind a small platform layer. The goal is not to build a full multi-loader framework yet; it is to keep the shared dialogue/runtime code easy to cherry-pick.

Suggested package:

```text
yourscraft.jasdewstarfield.brntalk.platform
```

Suggested initial classes:

- `BrntalkPlatform`: central entry point for shared code that needs platform services.
- `TalkStateStorage`: reads, writes, clears, and persists `PlayerTalkState`.
- `TalkNetworking`: sends client/server packets and opens the talk screen.
- `PlatformEvents`: posts BRNTalk events and hides loader event-bus details.
- `PlatformClientHooks`: registers client-only UI, key, reload, and HUD hooks.

## Code To Encapsulate

### Player State Storage

Current direct NeoForge Attachment usage:

- `BrntalkRegistries.java`
- `BrntalkAPI.java`
- `SyncEventListener.java`
- `TalkNetwork.java`

Current problem:

- Shared gameplay code directly calls `player.getData(BrntalkRegistries.PLAYER_TALK_STATE)` and `player.setData(...)`.
- NeoForge uses `AttachmentType`; Forge 1.20.1 should use a Forge-compatible persistence path, most likely Capability or a SavedData-backed adapter.

Target wrapper shape:

```java
PlayerTalkState state = BrntalkPlatform.talkState(player);
BrntalkPlatform.setTalkState(player, new PlayerTalkState());
BrntalkPlatform.markTalkStateDirty(player);
```

Notes:

- Keep `PlayerTalkState` itself shared.
- Do not move dialogue history serialization into loader code.
- Keep the old `TalkWorldData` migration path visible until the Forge branch has its own migration decision.

### Network Packets

Current NeoForge 1.21.1 networking:

- `network/TalkNetwork.java`
- `network/PayloadSync.java`
- `client/ClientPayloadSender.java`
- `client/ClientPayloadHandler.java`

Current problem:

- `CustomPacketPayload`, `StreamCodec`, `ByteBufCodecs`, `RegisterPayloadHandlersEvent`, `IPayloadContext`, and `PacketDistributor` are not a direct Forge 1.20.1 drop-in.
- Shared logic is mixed with packet registration and transport details.

Target wrapper shape:

```java
TalkNetworking.requestOpenTalk();
TalkNetworking.sendOpenTalkScreen(player);
TalkNetworking.syncThreadsTo(player, threads);
TalkNetworking.sendAddThread(player, thread);
TalkNetworking.sendAppendMessages(player, threadId, messages);
TalkNetworking.sendUpdateState(player, threadId, lastReadTime);
```

Notes:

- Keep packet DTOs or payload snapshots shared only if they can be encoded by both branches.
- If Forge 1.20.1 requires `FriendlyByteBuf` serializers, keep those serializers branch-local.
- Keep server-side handlers as close as possible between branches: request open, select choice, mark read.

### Event Bus And Custom Events

Current direct NeoForge event usage:

- `Brntalk.java`
- `BrntalkAPI.java`
- `TalkNetwork.java`
- `SyncEventListener.java`
- `event/PlayerSeenMessageEvent.java`

Current problem:

- Gameplay code posts `PlayerSeenMessageEvent` through `NeoForge.EVENT_BUS`.
- Event class extends NeoForge-side player event types.

Target wrapper shape:

```java
BrntalkPlatform.postPlayerSeenMessage(player, scriptId, messageId);
```

Notes:

- Keep the public event concept, but let each branch own the concrete event base class.
- Shared dialogue progression should not know whether the backing event bus is Forge or NeoForge.

### Mod Bootstrap And Registration

Current loader-specific bootstrap:

- `Brntalk.java`
- `BrntalkRegistries.java`
- `config/BrntalkConfig.java`
- `src/main/templates/META-INF/neoforge.mods.toml`
- `build.gradle`
- `gradle.properties`

Current problem:

- Constructor injection uses NeoForge `ModContainer` and `IEventBus`.
- Config registration, metadata generation, dependencies, Java version, and Gradle plugin are all loader/version-specific.

Target:

- Keep branch-local bootstrap files.
- Move only stable startup intent into shared helper methods, such as command registration, reload listener construction, and common setup logging.

Notes:

- Do not force these files through a shared abstraction too early.
- For Forge 1.20.1, expect Java 17 and ForgeGradle-oriented build files.

### Client Hooks

Current client registration points:

- `client/ClientInit.java`
- `client/ClientKeyRegistry.java`
- `client/ClientPayloadHandler.java`
- `client/ui/TalkHud.java`
- `config/ClothConfigIntegration.java`
- `BrntalkJeiPlugin.java`

Current problem:

- `RegisterGuiLayersEvent`, `VanillaGuiLayers`, `RegisterKeyMappingsEvent`, `RegisterClientReloadListenersEvent`, `RegisterClientCommandsEvent`, and config screen extension points may need Forge 1.20.1 equivalents.
- Client packet handling currently depends on `IPayloadContext`.

Target wrapper shape:

```java
PlatformClientHooks.registerClient(modEventBus);
PlatformClientHooks.enqueueClientWork(runnable);
PlatformClientHooks.openTalkScreen();
```

Notes:

- Keep visual rendering classes mostly shared.
- Encapsulate event registration, not the UI math.
- JEI and Cloth Config should stay optional in both branches, with version-specific dependency coordinates.

### Java 21 Convenience API

Current Java 21 APIs to replace for Forge 1.20.1 / Java 17:

- `List.getFirst()`
- `List.getLast()`

Affected files:

- `BrntalkAPI.java`
- `data/TalkConversation.java`
- `client/ClientPayloadHandler.java`
- `client/ui/TalkScreen.java`
- `runtime/TalkThread.java`
- `client/ui/TalkToast.java`
- `client/ui/TalkHud.java`

Target:

- Replace with Java 17-compatible helpers or direct index access.
- Prefer tiny local helper methods where the intent matters, for example `firstMessage(messages)` or `lastMessage(messages)`.

## Suggested Commit Order

1. Create platform wrapper package on `mc/1.21.1-neoforge` without changing behavior.
2. Replace direct `player.getData()/setData()` calls with `TalkStateStorage`.
3. Replace direct event posting with `PlatformEvents`.
4. Move client packet send calls behind `TalkNetworking`.
5. Move client hook registration behind `PlatformClientHooks`.
6. Cherry-pick the wrapper commits to `mc/1.20.1-forge`.
7. On `mc/1.20.1-forge`, replace wrapper internals with Forge 1.20.1 implementations.
8. Replace Java 21 collection APIs with Java 17-compatible code.
9. Only after compile passes, adjust optional integrations: Cloth Config, JEI, FTB Library.

## Validation Checklist

- `mc/1.21.1-neoforge` still builds after wrapper extraction.
- `mc/1.20.1-forge` compiles on Java 17.
- `/brntalk start` creates and syncs a thread.
- Choice selection appends messages and persists history.
- `/reload` rebuilds runtime threads from saved player state.
- Rejoin restores dialogue history and unread state.
- HUD, Toast, and full talk screen all receive network updates.
- Optional open button behavior stays compatible with JEI and FTB Library.
