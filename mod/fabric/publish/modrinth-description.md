# CraftPilot: AI Control

Let AI assistants like Claude see and control your Minecraft client via MCP. Fabric 1.21.1, client-side (NeoForge / Forge planned), MIT.

## What it does

- Screenshots of your game
- Read and press GUI buttons on any screen
- Keys and typing
- Commands with their output
- Movement, looking, using items, attacking
- Inventory and hotbar
- List, open, create and leave singleplayer worlds

## Use cases

- Test your own mods and modpacks automatically
- Reproduce bug reports
- Build with commands
- Make screenshots for mod pages

## Safety

- Listens only on `127.0.0.1`, protected by a secret token
- Every command can be toggled off in settings, with tooltips; plus a master switch
- The mod itself sends nothing to the internet
- For singleplayer and testing. Not for public servers. Do not use it to cheat.

## Setup

1. Install the mod and Fabric API.
2. Install Node.js.
3. Add to `claude_desktop_config.json`, then restart Claude Desktop:

```json
{"mcpServers":{"craftpilot":{"command":"npx","args":["-y","github:laur221/craftpilot"]}}}
```

You can also run `/craftpilot setup` in game or press the **Copy AI setup** button to copy it.

## Settings

Icon button at the top-left of the pause menu, Mod Menu config, or `/craftpilot config|setup|status|token reset`.

![Settings](images/config.png)

## Tools

| Tool | Description |
|---|---|
| `mc_status` | Client state: world, screen, player, window, FPS, version |
| `mc_screenshot` | Take a screenshot and return it as an image |
| `mc_screen` | List widgets on the current screen |
| `mc_click_widget` | Click a widget by label or id |
| `mc_click` | Click at GUI coordinates |
| `mc_scroll` | Scroll at GUI coordinates |
| `mc_key` | Press a key by name |
| `mc_type` | Type text into the focused field |
| `mc_command` | Run a command and return its output |
| `mc_chat` | Send a chat message |
| `mc_chat_log` | Read recent chat lines |
| `mc_move` | Hold movement keys for N ticks |
| `mc_look` | Set or adjust rotation |
| `mc_hotbar` | Select a hotbar slot |
| `mc_use` | Hold use for N ticks |
| `mc_attack` | Hold attack for N ticks |
| `mc_inventory` | List inventory contents |
| `mc_world` | Manage singleplayer worlds |
| `mc_respawn` | Respawn and close the death screen |
| `mc_capabilities` | Show enabled and disabled tools |

## FAQ

**Other AI clients?** Any MCP client that supports stdio servers works.
**Languages?** English and Russian.

*Not affiliated with Mojang, Microsoft or Anthropic.*
