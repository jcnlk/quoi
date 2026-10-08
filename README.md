[![discord badge](https://img.shields.io/discord/1371490329508839455?label=discord&color=9089DA&logo=discord&style=for-the-badge)](https://discord.gg/QCWgrQ57pN)
![minecraft-version](https://img.shields.io/badge/Minecraft-26.1.x%20%7C%2026.2%20%7C%2026.3%20%7C%2026.4%20snapshot-6BAA57?style=for-the-badge&logoColor=white)

Use [actions tab](https://github.com/jcnlk/quoi/actions) to download (you need to be logged in your GitHub account).

# quoi!

## Commands

- `/quoi` - Opens the Click GUI.
- `/quoi help` - Lists the available `/quoi` subcommands.
- `/quoi hud` - Opens the HUD editor.
- `/quoi fps` - Prints current FPS.
- `/quoi ping` - Prints current and average ping.
- `/quoi tps` - Prints current and average TPS.
- `/quoi pet <name>` - Equips a pet by name.
- `/quoi loadout <1-12>` - Equips a SkyBlock loadout slot.
- `/quoi wardrobe <1-9>` - Equips a wardrobe slot.
- `/quoi equip <item>` - Equips items from your inventory.
- `/quoi leap <name|class>` - Leaps to a dungeon teammate.
- `/quoi order [p1] [p2] [p3] [p4]` - Shows or sets the Leap Menu order.
- `/quoi clearaura` - Clears Secret Aura state.
- `/quoi ct` - Opens the Custom Triggers editor.
- `/quoi autocroesus <go|forcego|api|reset|copylog|loot|alwaysbuy|worthless> ...` - Controls Auto Croesus.
- `/quoi autosell <add|remove|clear|list> [item]` - Manages the Auto Sell list.
- `/quoi autoclicker <add|remove|clear> <left|right>` - Manages Auto Clicker whitelists.
- `/quoi hotbar <save|load|list|delete|setmsg|setfloor|setclass> ...` - Manages Auto Hotbar presets.
- `/quoi petkeybinds <add|get|list|remove|clear|addfromuuidname|removefromuuidname> ...` - Manages pet keybind entries.
- `/quoi antiafk <delay>` - Runs the anti-AFK helper.
- `/quoi findlobby <area> <day|server|player> <value>` - Searches for matching lobbies.
- `/route <em|add|rm|restore|clear|edit|editdb|chain|convert|reload> ...` - Edits and runs Auto Routes.
- `/grieferpro` or `/gp <add|update|remove|get|list|donotgrief> ...` - Manages the Griefer Tracker list.
- `/clearchat` - Clears the in-game chat.
- `/ptr` - Transfers the party to a random member.
- `/quoi <ep|ij|sl|sb|dd|tap|twap> [amount]` - Refills dungeon items from sacks.
- `/f0` to `/f7`, `/m1` to `/m7` - Joins a dungeon floor instance.

## Features

<details>
<summary><b>Dungeon</b></summary>

- **Auto Croesus**
  - Claims profitable Croesus chests, with profit estimates, chest keys, and optional Kismet rerolls.
- **Auto Door Opener**
  - Opens nearby Wither and Blood doors with Aura or Triggerbot mode.
- **Auto Routes** (Beta)
  - Edits and runs dungeon routes for teleporting, item use, and block breaking through `/route`.
- **Blood Camp**
  - Predicts Blood Room spawn positions and timings.
- **Dungeon Abilities**
  - Uses class ultimate abilities at specific boss dialogue and enrage messages.
- **Dungeon Breaker**
  - Tracks charges and mines saved blocks automatically or with a triggerbot, with optional zero ping.
- **Dungeon ESP**
  - Highlights teammates, starred mobs, and Wither bosses.
- **Dungeon Map** (Beta)
  - Shows rooms and doors in a HUD map.
- **Fire Freeze**
  - Shows the F3/M3 Fire Freeze timer and can automate its use.
- **Interactive Map** (Beta)
  - Teleports to rooms and locked doors selected on the map.
- **Leap Menu**
  - Adds sorting, custom order, keybinds, and class-only display to the Spirit Leap menu.
- **Puzzle Solvers**
  - Shows dungeon puzzle solutions, with optional automation.
  - **Ice Fill** - Draws the path and can walk it automatically.
  - **Teleport Maze** - Marks possible exit pads and can navigate the maze.
  - **Quiz** - Highlights correct answers, with auto answers or a triggerbot.
  - **Three Weirdos** - Finds the correct chest, with auto interactions or a triggerbot.
  - **Tic Tac Toe** - Shows the best move, with auto play or a triggerbot.
  - **Water Board** - Shows the lever sequence, with auto clicks or a triggerbot.
  - **Creeper Beams** - Marks matching lanterns, with auto shots or a triggerbot.
  - **Blaze** - Shows the shooting order, with auto shots or a triggerbot.
  - **Ice Path** - Draws the silverfish path and can complete it automatically.
  - **Boulder** - Shows the button sequence, with auto completion or a triggerbot.
- **Secrets**
  - Highlights and automatically collects dungeon secrets.
  - **Highlights** - Marks clicked secrets, locked chests, and dropped items, with optional sounds.
  - **Aura** - Clicks nearby secret chests, levers, and skulls.
  - **Triggerbot** - Clicks secrets you aim at.
  - **Auto close chest** - Closes secret chest menus.
  - **Full block** - Expands button, chest, lever, mushroom, and skull hitboxes.
- **Splits**
  - Shows dungeon and boss phase times, including Goldor sections and optional tick times.
- **Warp Cooldown**
  - Shows the dungeon warp cooldown and can block `/joininstance` while it is active.

</details>

<details>
<summary><b>Floor 7</b></summary>

- **Arrow Align**
  - Shows the solution and can complete the device automatically.
- **Auto Invincibility**
  - Swaps to an available invincibility mask or Phoenix Pet after a proc.
- **Auto Leap**
  - Leaps to configured teammates at clear, boss, device, and relic triggers.
- **Barrier Boom**
  - Uses Superboom when you aim at Goldor's gates.
- **Crystal Aura**
  - Automatically picks up active Energy Crystals in F7 P1.
- **Fuck Diorite**
  - Replaces Storm's pillars with glass.
- **Invincibility Timer**
  - Shows mask and Phoenix cooldowns and can announce procs in party chat.
- **Lights Device**
  - Adds a triggerbot and hides useless levers.
- **P4 Platform Highlight**
  - Marks the 3x3 area to mine after Goldor dies.
- **Simon Says**
  - Shows the sequence and can complete the device automatically.
- **Terminal Aura**
  - Opens nearby terminals in P3, with optional ground and leap-delay checks.
- **Tick Timers**
  - Shows Storm pad and Goldor timers in seconds or server ticks.
- **Wither Cloak**
  - Tracks Creeper Veil, hides cloak creepers, and can activate cloak during the boss countdown.

</details>

<details>
<summary><b>General</b></summary>

- **AntiNick**
  - Detects nicked players.
- **Auto Book Combine**
  - Combines matching enchanted books in the Hypixel Anvil.
- **Auto Clicker**
  - Repeats left or right clicks while a keybind is held, with optional item whitelists.
- **Auto GFS**
  - Refills selected items from sacks by amount or timer.
- **Auto Hotbar**
  - Saves and restores hotbar presets, with chat triggers and dungeon floor or class requirements.
- **Auto Join SkyBlock**
  - Joins SkyBlock after connecting to Hypixel.
- **Auto Kick**
  - Kicks party members by dungeon class or detected Skyblocker messages.
- **Auto Loadout**
  - Equips one of 12 loadout slots through keybinds or `/quoi loadout`.
- **Auto Sell**
  - Sells items from a configurable list in trade and cookie menus.
- **Auto Wardrobe**
  - Equips wardrobe slots through keybinds or `/quoi wardrobe`.
- **Auto Sprint**
  - Keeps sprint enabled.
- **Chat**
  - Customises chat display, messages, and history.
  - **Chat bypass** - Replaces outgoing characters to bypass chat filters.
  - **Chat peek** - Shows chat while a keybind is held.
  - **Compact chat** - Combines duplicate messages.
  - **Copy chat** - Copies clicked messages, optionally including formatting codes.
  - **Infinite chat limit** - Removes the vanilla 100-message limit.
  - **Keep history** - Preserves chat across disconnects.
  - **Disable auto scroll** - Keeps your scroll position when messages arrive.
  - **Auto dialogue** - Continues NPC dialogues automatically.
  - **Chat replacements** - Cleans up dungeon and Party Finder messages and hides unwanted messages.
- **Escrow Fix**
  - Reopens the Auction House or Bazaar after escrow errors.
- **Inventory**
  - Adds inventory search and a HUD.
  - **Search bar** - Finds items by name or lore and evaluates arithmetic.
  - **Inventory HUD** - Shows inventory slots, with an optional player model.
- **Pet Keybinds**
  - Adds Pets menu keybinds and saved pet commands.
- **Player Display**
  - Adds SkyBlock stat HUDs and can hide vanilla status bars.
  - **Health** - Shows health, a health bar, and effective health.
  - **Mana** - Shows mana, overflow mana, and ability usage.
  - **Other stats** - Shows vitality, defence, speed, Crimson stacks, and Salvation.
  - **Secrets** - Shows the current room's secret count, with an optional SBA-style layout.
- **Titles**
  - Shows configurable titles or subtitles for AutoPet rules, invincibility procs, and Shadow Assassin alerts.
- **Tweaks**
  - Adjusts sneaking and sounds, and fixes SkyBlock cooldowns, interactions, fog, and cursor resets.
- **Wardrobe Keybinds**
  - Adds Wardrobe slot, page, and unequip keybinds.

</details>

<details>
<summary><b>Misc</b></summary>

- **Auto Carnival**
  - Shoots the Dart Tube automatically in Carnival's Zombie Shootout.
- **Cat Mode**
  - Adds meows and cat visuals.
  - **Meow sound and text** - Replaces sounds with meows and words with "meow".
  - **Falling cats** - Draws falling cats in menus.
  - **Cat models** - Renders yourself or other players as cats.
- **Chocolate Factory**
  - Automates clicks, upgrades, Time Tower, and stray rabbits, with Chocolate Hunt egg ESP.
- **Custom Triggers** (Beta)
  - Runs actions on configurable chat, keybind, and game events. Open the editor with `/quoi ct`.
- **Dojo** (Beta)
  - Adds challenge-specific overlays and automation.
  - **Force** - Highlights negative-point mobs and can block attacks on them.
  - **Mastery** - Shows target tracers and can aim and shoot automatically.
  - **Discipline** - Highlights valid mobs, blocks wrong attacks, and can swap swords.
  - **Swiftness** - Moves between green wool targets automatically.
  - **Control** - Predicts the skeleton's position, with auto aim and centring.
  - **Tenacity** - Shows fireball trajectories and impact areas.
- **Mirrorverse Solvers**
  - Adds overlays and automation for the Rift's Mirrorverse.
  - **Lava Maze** - Walks the maze route automatically.
  - **Lava Parkour** - Moves and jumps through the parkour.
  - **Craft Room** - Shows mirrored mobs and crafting recipes.
  - **Red Green** - Automates movement and interactions.
  - **Tiny Dancer** - Performs movement, jumps, sneaks, and punches to the beats.
  - **Tubulator** - Climbs the parkour route automatically.
- **Slayers**
  - Adds boss ESP, spawn alerts, and spawn and kill time messages.
  - **Enderman** - Highlights the Yang Glyph beacon and draws a tracer.
  - **Blaze** - Adds dagger attunement, damage dodging, and a Gummy Polar Bear timer.
- **Test**
  - Provides developer debugging tools.

</details>

<details>
<summary><b>Render</b></summary>

- **Click GUI**
  - Configures modules, themes, keybinds, and HUDs.
- **Custom Main Menu**
  - Replaces the vanilla menu with server shortcuts.
- **Etherwarp Overlay**
  - Shows the predicted Etherwarp destination.
- **Hide Players**
  - Hides players by distance or context, with optional click-through.
- **Info HUD**
  - Shows FPS, TPS, ping, Minecraft day, and a clock.
- **Item Animations**
  - Customises held item position, scale, rotation, swings, and other animations.
- **Name Tags**
  - Adds custom tags with distance, colour, background, and shadow settings.
- **Nick Hider**
  - Replaces your displayed name with a configured name and colour.
- **Player ESP**
  - Highlights players through walls.
- **Render Optimiser**
  - Controls text shadows, fog, entity visibility, particles, overlays, and full bright.
- **Revert Master Stars**
  - Restores the old red Master Star display.
- **Trajectories**
  - Shows bow and Ender Pearl paths, with optional impact boxes and planes.
- **Waypoints**
  - Creates temporary waypoints from coordinates in chat.

</details>

<details>
<summary><b>Mining</b></summary>

- **Ability Alert**
  - Alerts when your mining ability is ready.
- **Commission Display**
  - Shows commissions in a HUD with completion titles and highlights.
- **Crystal Hollows Map**
  - Shows players, structures, and route blocks on a map.
- **Crystal Hollows Scanner**
  - Finds structures and mining route blocks in loaded chunks.
- **Griefer Tracker**
  - Tracks saved players and encounter details in Crystal Hollows through `/grieferpro` or `/gp`.
- **Ghost ESP**
  - Highlights or hides Ghosts in the Mist.
- **Glacite Tunnels**
  - Routes collector commissions and adds a base warp keybind.
- **Mineshaft ESP**
  - Highlights corpses, fossils, and Glacite mobs, with an option to hide looted corpses.
- **No Gemstone Desync**
  - Refreshes adjacent gemstone blocks after mining.

</details>

## Discord
Join the [quoi! Discord](https://discord.gg/QCWgrQ57pN) or dm me (@jcnlk).

## Licence Note
GPL-3.0, but I don't give a single fuck if you use the code without crediting. Do whatever.

## Credits

- [Odin](https://github.com/odtheking/OdinFabric) - NanoVG implementation, world renderer, and module config system
- [Zen](https://github.com/StellariumMC/zen) - Event manager inspiration
- [devonian](https://github.com/Synnerz/devonian) - commands API inspiration
- [Stivias](https://github.com/Stivais/) - AuroraUI UI library
