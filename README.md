# CraftPilot

![License: MIT](https://img.shields.io/badge/license-MIT-green)
![npm](https://img.shields.io/npm/v/craftpilot-mcp)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.1-blue)
![Loader](https://img.shields.io/badge/loader-Fabric-lightgrey)

CraftPilot lets AI assistants such as Claude see and control a running Minecraft client. It has two parts:

- **CraftPilot mod** (`craftpilot-mod`): a client-side Fabric mod that runs a local HTTP API inside the game.
- **craftpilot-mcp** (this package): an MCP (Model Context Protocol) server that exposes that API as tools over stdio.

## Architecture

```
Claude / MCP client
        |  (MCP over stdio)
        v
  craftpilot-mcp
        |  (HTTP + Bearer token)
        v
  http://127.0.0.1:25599
        |
        v
  CraftPilot mod in Minecraft
```

## Install

1. Put the CraftPilot jar and Fabric API into your Minecraft client's `mods` folder (Fabric 1.21.1).
2. Install Node.js.
3. Start the game once. The mod generates a token and writes `%USERPROFILE%\.craftpilot\connection.json`.
4. Add the server to your MCP client, e.g. `claude_desktop_config.json`, and restart it:

```json
{"mcpServers":{"craftpilot":{"command":"npx","args":["-y","github:laur221/craftpilot"]}}}
```

Local development variant (after `npm run build`):

```json
{"mcpServers":{"craftpilot":{"command":"node","args":["D:\\mods\\craftpilot-mcp\\dist\\index.js"]}}}
```

In game, `/craftpilot setup` or the "Copy AI setup" button copies the config for you.

## Configuration

- `%USERPROFILE%\.craftpilot\connection.json` holds `port`, `token` and `gameDir`. The MCP server re-reads it on every call, so restarting the game with a new token just works.
- Environment overrides: `CRAFTPILOT_PORT`, `CRAFTPILOT_TOKEN`.
- Mod settings live in `<gameDir>/config/craftpilot.json` (port, token, `enabled`, `keepRunningUnfocused`, per-command toggles). Edit them in game via the pause-menu icon button, Mod Menu, or `/craftpilot config`.
- Other commands: `/craftpilot status`, `/craftpilot token reset`.

If the game is not running or the connection file is missing, tools return `Minecraft with CraftPilot is not running`. Each HTTP call has a 15 s timeout. Responses with `ok:false` are returned as tool errors.

## Tools

| Tool | Description |
|---|---|
| `mc_status` | Client state: world, screen, player, window, FPS, version |
| `mc_screenshot` | Screenshot as an image (`maxWidth`, default 1280) |
| `mc_screen` | List widgets on the current screen with labels and GUI coordinates |
| `mc_click_widget` | Click a widget by `label` or `id` |
| `mc_click` | Click at scaled GUI coordinates |
| `mc_scroll` | Scroll the mouse wheel at GUI coordinates |
| `mc_key` | Press a key by GLFW name |
| `mc_type` | Type text into the focused field |
| `mc_command` | Run a slash command and return chat output |
| `mc_chat` | Send a chat message |
| `mc_chat_log` | Read the last N chat lines |
| `mc_move` | Hold movement keys for N ticks |
| `mc_look` | Set rotation (absolute or relative) |
| `mc_hotbar` | Select hotbar slot 0-8 |
| `mc_use` | Hold use (right-click) for N ticks |
| `mc_attack` | Hold attack (left-click) for N ticks |
| `mc_inventory` | List inventory contents |
| `mc_world` | List/open/create/leave singleplayer worlds |
| `mc_respawn` | Respawn and close the death screen |
| `mc_capabilities` | Show which tools are enabled or disabled in settings |

## Security notes

- The API binds to `127.0.0.1` only and every request needs `Authorization: Bearer <token>`.
- The token is 32 random hex characters; reset it with `/craftpilot token reset`.
- Each endpoint can be disabled in the mod settings; a master switch disables the whole API.
- The mod is client-only and disabled on dedicated servers. It sends nothing to the internet.
- Intended for singleplayer and testing. Do not use it on public servers or to cheat.
- Anyone who can read your `connection.json` can control your client, so keep it private.

## Development

Build the mod:

```
cd craftpilot-mod
gradlew build
```

Build the MCP server:

```
cd craftpilot-mcp
npm install
npm run build
```

The HTTP API is documented in `craftpilot-mod/API.md`.

## License

MIT

## Disclaimer

CraftPilot is not affiliated with Mojang, Microsoft or Anthropic.
