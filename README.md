Harvester - Effortless Crop Farming for Vanilla Survival​

Simplify farming with a single right-click! Harvest crops and replant instantly, perfect for survival servers.​

Overview
Harvester is a lightweight, vanilla-friendly plugin that enhances crop farming in Minecraft. With a single right-click, players can harvest fully grown crops and automatically replant them using one seed, saving time and keeping the vanilla experience intact. Supporting wheat, carrots, potatoes, beetroots, and nether wart, Harvester is ideal for survival servers looking to streamline farming without complex mechanics.

Whether you're running a small community server or a large network, Harvester offers configurable options, permission-based access, and seamless integration, making it a must-have for any vanilla-oriented server.

Features
Instant Harvest & Replant: Right-click fully grown crops to harvest their yield and replant using just one seed (e.g., 2 seeds dropped, keep 1!).
Supported Crops: Works with wheat (farmland), carrots (farmland), potatoes (farmland), beetroots (farmland), and nether wart (soul sand).
Vanilla-Friendly: Preserves Minecraft’s core farming mechanics, with configurable particle and sound effects for immersion.
Permission-Based: Restrict harvesting to specific ranks (e.g., donors) with the
Code (Text):
harvester.use
permission.
Highly Configurable: Enable/disable crops, toggle effects, and set permissions in a clean, user-friendly config.
Lightweight & Compatible: Built for Spigot/Paper 1.21.4, with no dependencies and minimal server impact.

Configuration
Harvester’s config is designed for ease of use, letting you customize every aspect:
Crop Control: Toggle crops individually e.g.
Code (Text):
- wheat: true
Code (Text):
- carrots: false
Effects: Enable/disable particles (e.g., VILLAGER_HAPPY) and sounds (e.g., BLOCK_CROP_BREAK), with adjustable count, volume, and pitch.
Permissions: Require permission node for access, perfect for ranks, perks, etc.
Code (Text):
harvester.use

Spoiler: Example Config

Permissions
Code (Text):
harvester.use
Allows players to harvest and replant crops. Default: true (configurable to false for donor-only access).
