# BoneAI

An AI chat companion for your Minecraft SMP, powered by Claude (Anthropic API).
Players can ask it questions or just chat for fun, either by command or in normal chat.

## How it works

- **In chat**: type `bone, <message>` or `bone <message>` anywhere in normal chat
  (e.g. `bone, what's a good enchant for a diamond pickaxe?`).
- **Command**: `/bone <message>` works the same way.
- Each player gets a short rolling memory of their recent exchanges, so follow-up
  questions feel natural.
- A per-player cooldown (default 5s) keeps any one player from spamming requests.

Admins:
- `/boneai reload` - reload config.yml without restarting the server
- `/boneai clear <player>` - wipe a player's conversation memory

## Requirements

- Paper (or a Paper-fork) server for **Minecraft 26.2**
- **Java 25** on the server (26.2 requires it)
- An Anthropic API key: https://console.anthropic.com/
- JDK 25 on your build machine (to compile the plugin)

## Setup

1. Get an Anthropic API key from the console link above.
2. Build the plugin (see below), or download a prebuilt jar if you have one.
3. Drop `BoneAI.jar` into your server's `plugins/` folder and start the server once
   so it generates `plugins/BoneAI/config.yml`.
4. Stop the server (or use `/boneai reload`), open `config.yml`, and set:
   ```yaml
   api:
     key: "sk-ant-..."
   ```
5. Restart or reload. You're live.

## Building from source

This project uses Gradle and pulls the Paper API from PaperMC's Maven repo, so
building needs internet access (this sandbox doesn't have it, which is why this
project ships as source rather than a compiled jar). You have two options:

### Option A: Build with GitHub Actions (no local Java needed)

1. Create a new GitHub repo and push this whole `BoneAI` folder to it (including
   the `.github/workflows/build.yml` file already included).
2. Push to the `main` branch, or go to the repo's **Actions** tab and manually run
   the "Build BoneAI" workflow.
3. Once it finishes, open that workflow run and download the **BoneAI-jar**
   artifact from the bottom of the page - that's your compiled plugin jar.

This workflow installs JDK 25 and Gradle itself, so you don't need anything
installed locally - just a GitHub repo.

### Option B: Build locally

This project doesn't ship Gradle wrapper binaries (they're downloaded, and this
sandbox has no network access to fetch them), so install Gradle yourself first
(e.g. `brew install gradle`, or see https://gradle.org/install/), then:

```bash
cd BoneAI
gradle build
```

Either way, the compiled jar will be at `build/libs/BoneAI-1.0.0.jar`.

> Note on the Paper API version pin in `build.gradle.kts`
> (`io.papermc.paper:paper-api:26.2-R0.1-SNAPSHOT`): Paper's exact artifact
> naming for 26.x can shift during snapshot cycles. If the build fails to
> resolve that dependency, check https://repo.papermc.io/service/rest/repository/browse/maven-public/io/papermc/paper/paper-api/
> for the current 26.2 artifact string and swap it in.

## Configuration reference (`config.yml`)

| Key | Default | Description |
|---|---|---|
| `api.key` | `YOUR_ANTHROPIC_API_KEY` | Your Anthropic API key |
| `api.model` | `claude-haiku-4-5-20251001` | Model used for responses - fast/cheap, good fit for chat |
| `api.max-tokens` | `300` | Caps response length (and cost) per reply |
| `api.system-prompt` | (BoneAI personality) | Edit this to change BoneAI's tone/personality/rules |
| `chat.trigger-word` | `bone` | Word that triggers BoneAI in normal chat |
| `chat.hide-trigger-messages` | `false` | If true, hides the player's trigger message from other players |
| `chat.cooldown-seconds` | `5` | Per-player cooldown between requests |
| `chat.memory.enabled` | `true` | Whether BoneAI remembers recent exchanges per player |
| `chat.memory.max-messages` | `6` | How many messages (2 per exchange) to keep per player |

## Cost note

Every triggered message is one API call. With Haiku's pricing this is cheap even on an
active server, but you may want to keep `cooldown-seconds` reasonable and `max-tokens`
capped, especially if you have a lot of concurrent players.

## Extending it

- Swap models by changing `api.model` in config.
- Want it to answer in a channel/Discord bridge too? Hook into whatever event that
  plugin fires and call `plugin.getChatListener().handleQuestion(player, message)`.
- Want broadcast responses (visible to everyone) instead of private replies? Change
  `sendWrapped` in `ChatListener.java` to use `Bukkit.broadcastMessage(...)` instead of
  `player.sendMessage(...)`.
