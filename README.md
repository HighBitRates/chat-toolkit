# Chat Toolkit

A client-side chat quality-of-life mod for Minecraft 26.3 (Fabric).

## Install

1. Install Fabric Loader 0.19 or newer for Minecraft 26.3.
2. Put `chattoolkit-1.0.0.jar` and Fabric API in your `mods` folder.
3. Mod Menu is optional but recommended for the settings screen.

Works alongside Chat Animation and Client Commands. When Client Commands is installed, Chat Toolkit takes over `/ccalc` and `/cnote` by default (you can turn this off in settings).

## Features

- Search bar under the chat. It sits flush with the chat box. Click it or press Ctrl+F in chat, then type. Toggles for case sensitivity (Aa), whole word (W) and regex (.*). Matching messages are filtered and highlighted, with a match counter. Right-click the bar to clear it.
- In survival the chat and search bar move up above your hearts, armor and hunger. In creative they stay at the normal height. You can turn this off or add extra height in settings.
- Right-click any chat message: copy text, copy with colors, paste into chat, copy sender name, message the sender, find messages by sender, copy link, copy coordinates, save as note, remove it from your chat.
- Middle-click a message to copy it instantly.
- Chat history raised above the vanilla 100 line limit.
- Pinned notes and timers show in a small panel. Open the chat and drag it anywhere on screen. Right-click it to send it back to its corner.
- Optional timestamps.
- Settings for opacity, colors, search bar width, animations and more, via Mod Menu, `/ctk` or a keybind.

## Chat tabs

Open chat with T and the tabs sit on top of the message box. Click one to only see those messages. A red dot means something new came in there.

- Hover a tab and click the little arrow to pop it out into its own window. Drag the window by its title, resize it from the corner, and scroll it with the mouse wheel. With the chat closed it shows recent lines like the normal chat. Drag it back onto the tabs (or click its x) to dock it again.
- Click a window before typing to send your message through that tab. If the tab has "Send messages as" set (like `/ooc {message}`), your message goes through that command.
- Settings > Edit tabs (or right-click a tab, or click +) to change names, rules, colors and more. A rule can be "contain", "start with" or "everything else", and it uses & color codes, so `&8[&7OOC&8]` only catches a gray [OOC] tag.
- Presets save your whole tab setup. SRP default comes built in (RP, OOC, LOOC, Friends). Give a preset a server address and it loads by itself when you join. Copy a preset to your clipboard to share it, or export it to a file in `config/chattoolkit/presets`. Import files from that folder, or drag .json files onto the game window while the tab editor is open.

- With Chat Animation installed, messages that go to another tab don't make the main chat slide.

Tabs and presets are saved in `config/chattoolkit/tabs.json`.

## Commands

| Command | What it does |
| --- | --- |
| `/ccalc <expression>` | Calculator with implicit multiplication, `%`, units (st, sb, chest, dc, k), functions like `nether()`, `stacks()`, `dist()`, variables and `ans` |
| `/cnote ...` / `/cnotes ...` | Notes: list, add, here (saves your coords), remove, pin, unpin, copy, show, edit, search, clear |
| `/ctimer <duration> [label]` | Countdown timer with a sound when it ends |
| `/ccoords` | Copy or share your coordinates, with nether conversion |
| `/chattoolkit` or `/ctk` | Open settings. `/ctk help` lists everything, `/ctk reload` reloads config |

## Building from source

Requires JDK 25.

```
./gradlew build
```

The jar ends up in `build/libs/`.

## License

MIT
