#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
const NOT_RUNNING = "Minecraft with CraftPilot is not running";
const TIMEOUT_MS = 15000;
async function getConnection() {
    let port;
    let token;
    try {
        const file = join(process.env.USERPROFILE ?? homedir(), ".craftpilot", "connection.json");
        const j = JSON.parse(await readFile(file, "utf8"));
        port = Number(j.port);
        token = String(j.token);
    }
    catch {
        // fall through to env
    }
    if (process.env.CRAFTPILOT_PORT)
        port = Number(process.env.CRAFTPILOT_PORT);
    if (process.env.CRAFTPILOT_TOKEN)
        token = process.env.CRAFTPILOT_TOKEN;
    if (!port || !token)
        throw new Error(NOT_RUNNING);
    return { port, token };
}
async function callApi(name, body) {
    const { port, token } = await getConnection();
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
    try {
        const res = await fetch(`http://127.0.0.1:${port}/api/${name}`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
            body: JSON.stringify(body),
            signal: ctrl.signal,
        });
        if (res.status === 401)
            throw new Error("CraftPilot rejected the token (401). Restart the game or check CRAFTPILOT_TOKEN.");
        return await res.json();
    }
    catch (e) {
        if (e?.name === "AbortError")
            throw new Error(`CraftPilot request timed out after ${TIMEOUT_MS / 1000}s`);
        if (e instanceof Error && (e.message === NOT_RUNNING || e.message.startsWith("CraftPilot")))
            throw e;
        // fetch failed (ECONNREFUSED etc.)
        throw new Error(NOT_RUNNING);
    }
    finally {
        clearTimeout(timer);
    }
}
const errorResult = (msg) => ({ content: [{ type: "text", text: msg }], isError: true });
const server = new McpServer({ name: "craftpilot-mcp", version: "1.0.0" });
function def(endpoint, description, shape, opts = {}) {
    server.tool(`mc_${endpoint}`, description, shape, async (args) => {
        try {
            const body = {};
            for (const [k, v] of Object.entries(args ?? {}))
                if (v !== undefined)
                    body[k] = v;
            const r = await callApi(endpoint, body);
            if (!r || r.ok === false) {
                return errorResult(r?.error ? String(r.error) : JSON.stringify(r, null, 2));
            }
            if (opts.image && typeof r.png === "string") {
                return {
                    content: [
                        { type: "image", data: r.png, mimeType: "image/png" },
                        { type: "text", text: `width=${r.width}, height=${r.height}` },
                    ],
                };
            }
            return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
        }
        catch (e) {
            return errorResult(e?.message ?? String(e));
        }
    });
}
def("status", "Get Minecraft client state: in world or not, current screen, player position/health/food, window size, GUI scale, FPS, version.", {});
def("screenshot", "Take a screenshot of the Minecraft window (returned as an image). Downscaled to maxWidth.", { maxWidth: z.number().int().positive().default(1280).describe("Max image width in pixels (default 1280)") }, { image: true });
def("screen", "List widgets on the current Minecraft screen with labels and GUI coordinates; use with mc_click_widget.", {});
def("click_widget", "Click a widget on the current screen by label (case-insensitive, exact then contains) or by id from mc_screen.", {
    label: z.string().optional().describe("Widget label, e.g. 'Respawn'"),
    id: z.number().int().optional().describe("Widget id (index) from mc_screen"),
    button: z.number().int().optional().describe("Mouse button, 0=left (default), 1=right"),
});
def("click", "Click at scaled GUI coordinates on the current screen (see mc_status scaledWidth/Height).", {
    x: z.number().describe("X in scaled GUI coordinates"),
    y: z.number().describe("Y in scaled GUI coordinates"),
    button: z.number().int().optional().describe("Mouse button, 0=left (default), 1=right"),
});
def("scroll", "Scroll the mouse wheel at scaled GUI coordinates on the current screen.", {
    x: z.number().describe("X in scaled GUI coordinates"),
    y: z.number().describe("Y in scaled GUI coordinates"),
    amount: z.number().describe("Scroll amount; negative scrolls down, positive up"),
});
def("key", "Press a key by GLFW name (escape, enter, tab, backspace, up/down/left/right, f1-f12, space, letters, digits...). Behaves like a real key press in-game and in screens.", {
    key: z.string().describe("Key name, e.g. 'escape', 'e', 'f3'"),
    action: z.enum(["press", "down", "up"]).optional().describe("Default 'press' (down+up)"),
    mods: z.number().int().optional().describe("GLFW modifier bitmask (shift=1, ctrl=2, alt=4)"),
});
def("type", "Type text into the focused field/screen, character by character.", {
    text: z.string().describe("Text to type"),
});
def("command", "Run a slash command (without leading slash) and return chat output received in the next ~500ms.", { command: z.string().describe("Command without leading slash, e.g. 'time set day'") });
def("chat", "Send a chat message as the player.", { message: z.string().describe("Chat message") });
def("chat_log", "Get the last N chat lines (newest last).", {
    limit: z.number().int().positive().default(30).describe("Number of lines (default 30)"),
});
def("move", "Hold movement keys for N client ticks (20 ticks = 1 second).", {
    forward: z.boolean().optional(),
    back: z.boolean().optional(),
    left: z.boolean().optional(),
    right: z.boolean().optional(),
    jump: z.boolean().optional(),
    sneak: z.boolean().optional(),
    sprint: z.boolean().optional(),
    ticks: z.number().int().positive().default(20).describe("Duration in ticks (default 20)"),
});
def("look", "Set player rotation: absolute (yaw/pitch) or relative (dyaw/dpitch), in degrees.", {
    yaw: z.number().optional().describe("Absolute yaw"),
    pitch: z.number().optional().describe("Absolute pitch (-90 up .. 90 down)"),
    dyaw: z.number().optional().describe("Relative yaw change"),
    dpitch: z.number().optional().describe("Relative pitch change"),
});
def("hotbar", "Select a hotbar slot (0-8).", { slot: z.number().int().min(0).max(8).describe("Slot 0-8") });
def("use", "Press the use (right-click) key for N ticks.", {
    ticks: z.number().int().positive().default(1).describe("Ticks to hold (default 1)"),
});
def("attack", "Press the attack (left-click) key for N ticks.", {
    ticks: z.number().int().positive().default(1).describe("Ticks to hold (default 1)"),
});
def("inventory", "List inventory contents (main, armor, offhand): slot, item id, count, name.", {});
def("world", "Manage singleplayer worlds from the title screen: list, open, create, or leave the current world.", {
    action: z.enum(["list", "open", "create", "leave"]).describe("Action to perform"),
    name: z.string().optional().describe("World name (for open/create)"),
    gameMode: z.string().optional().describe("For create: survival, creative, adventure, spectator"),
    cheats: z.boolean().optional().describe("For create: allow cheats"),
});
def("respawn", "Respawn the player and close the death screen.", {});
def("goto", "Walk to world coordinates by itself using simple client-side pathfinding (loaded chunks only, max 128 blocks). Returns immediately; poll mc_goto_status. Fails with 'no path' if unreachable.", {
    x: z.number().describe("Target X"),
    y: z.number().describe("Target Y (feet block)"),
    z: z.number().describe("Target Z"),
    sprint: z.boolean().optional().describe("Sprint while walking (default false)"),
});
def("goto_status", "Status of the current mc_goto walk: active, remaining nodes, position, result (running|arrived|failed:<reason>|stopped).", {});
def("goto_stop", "Stop the current mc_goto walk and release the movement keys.", {});
def("capabilities", "List which CraftPilot commands are enabled/disabled in the in-game CraftPilot settings. Disabled tools return an error saying so.", {});
await server.connect(new StdioServerTransport());
