# CraftPilot HTTP API (v1)

Server: `http://127.0.0.1:<port>` (default port 25599, only binds to loopback).
Auth: every request needs header `Authorization: Bearer <token>`. Token is generated on first start
(32 random hex chars) and stored in `<gameDir>/config/craftpilot.json` as `{"port":25599,"token":"..."}`.
Also written to `%USERPROFILE%/.craftpilot/connection.json` (same JSON plus "gameDir") so the MCP server can find it.

All requests: `POST /api/<name>` with JSON body (may be `{}`), response JSON `{"ok":true, ...}` or
`{"ok":false,"error":"..."}` with HTTP 200 (HTTP 401 on bad token). All game access happens on the
client (render) thread via `MinecraftClient.getInstance().submit(...)` and waits up to 10 s.

| name | body | response |
|---|---|---|
| `status` | `{}` | `inWorld`, `screen` (class simple name or null), `screenTitle`, `player` {name, x,y,z, yaw,pitch, health, maxHealth, food, xpLevel, dimension, gameMode, dead}, `windowWidth/Height`, `guiScale`, `scaledWidth/Height`, `fps`, `mcVersion`, `loader` |
| `screenshot` | `{"maxWidth":1280}` optional | `png` (base64), `width`, `height` (downscaled keeping aspect if wider than maxWidth) |
| `screen` | `{}` | `screen` class name, `title`, `widgets`: list of {id (index), type (button/checkbox/textfield/slider/other), label, x,y,w,h (scaled GUI coords), active, visible, focused, value (for textfield)} — collected recursively from Screen.children() (ClickableWidget instances, including nested ParentElement/layout children) |
| `click_widget` | `{"label":"Respawn"}` or `{"id":3}`; optional `"button":0` | clicks the widget center via screen.mouseClicked + mouseReleased; label match is case-insensitive, exact first then contains |
| `click` | `{"x":100,"y":50,"button":0}` scaled GUI coords | mouseClicked + mouseReleased on current screen |
| `scroll` | `{"x":..,"y":..,"amount":-1}` | screen.mouseScrolled |
| `key` | `{"key":"escape"}` GLFW key name (letters, digits, escape, enter, tab, backspace, up/down/left/right, f1-f12, space, e, t, …) optional `"action":"press"|"down"|"up"`, `"mods":0` | calls `client.keyboard.onKey(window, key, scancode, action, mods)` so it behaves exactly like a real key press (works in-game and in screens) |
| `type` | `{"text":"hello"}` | for each char calls `client.keyboard.onChar(window, codepoint, 0)` |
| `command` | `{"command":"time set day"}` (no leading slash) | sends via `player.networkHandler.sendChatCommand`, then waits ~500 ms and returns `output`: chat lines received after sending (captured from ChatHud / ClientReceiveMessageEvents) |
| `chat` | `{"message":"hi"}` | sends chat message |
| `chat_log` | `{"limit":30}` | last N chat lines (plain strings, newest last) |
| `move` | `{"forward":true,"back":false,"left":false,"right":false,"jump":false,"sneak":false,"sprint":false,"ticks":20}` | holds those KeyBindings pressed for N ticks (setPressed true, released after N client ticks) |
| `look` | `{"yaw":90,"pitch":0}` absolute or `{"dyaw":10,"dpitch":-5}` relative | sets player rotation |
| `hotbar` | `{"slot":0..8}` | selects hotbar slot |
| `use` / `attack` | `{"ticks":1}` | presses use / attack keybinding for N ticks |
| `inventory` | `{}` | list of {slot, item id, count, name} for main+armor+offhand |
| `world` | `{"action":"list"}` / `{"action":"open","name":"..."}` / `{"action":"create","name":"...","gameMode":"creative","cheats":true}` / `{"action":"leave"}` | manage singleplayer worlds from the title screen. `open`/`create` are fire-and-forget: they return `{"ok":true,"started":true}` immediately; poll `status` until `inWorld` is true. |
| `respawn` | `{}` | player.requestRespawn() and close death screen |
| `goto` | `{"x":..,"y":..,"z":..,"maxDistance":128,"sprint":false}` | Client-side A* pathfinding over loaded chunks (walk, diagonal, step up 1, drop up to 3; avoids lava/fire/cactus/magma/sweet berries/powder snow; 20000 node limit; `maxDistance` capped at 128). Returns immediately `{"ok":true,"pathLength":N,"started":true}` or `{"ok":false,"error":"no path"}`. Each client tick the player turns (max 30 deg/tick) toward the next node, holds forward (and sprint), and jumps for higher nodes or when blocked. Advances within 0.35 blocks; re-paths up to 3 times if no progress for 40 ticks; stops within 1 block of the goal, on `goto_stop`, death, or when a screen opens. All keys are released on stop. |
| `goto_status` | `{}` | `{"ok":true,"active":bool,"remaining":nodes,"position":{x,y,z},"result":"running|arrived|failed:<reason>|stopped"}` |
| `goto_stop` | `{}` | stops walking and releases keys: `{"ok":true,"wasActive":bool}` |
| `capabilities` | `{}` | always enabled: `{"ok":true,"enabled":["status",...],"disabled":["command",...]}` |

Notes:
- The server must never block the render thread; HTTP handling runs on its own executor.
- Disabled entirely on dedicated servers (client-only entrypoint).
- Config option `"enabled": true`; first start shows a one-time toast "CraftPilot API active on 127.0.0.1:25599".

- Config option `keepRunningUnfocused` (default `true`): at startup sets `pauseOnLostFocus=false` at runtime only (options.txt untouched), so the game doesn't open the pause menu when the window loses OS focus.
- `command` runs Fabric client commands (e.g. `/craftpilot status`) via the client dispatcher; messages added to the chat HUD locally are captured into the chat log (de-duplicated within 50 ms).
- Per-command toggles: `craftpilot.json` has `"commands": {"<endpoint>": true|false}` (missing = true), editable in-game via `/craftpilot config` or Mod Menu. A disabled endpoint returns HTTP 200 `{"ok":false,"error":"'<name>' is disabled in CraftPilot settings"}`.
- `goto`, `goto_status` and `goto_stop` are all governed by the single `"goto"` per-command toggle.
