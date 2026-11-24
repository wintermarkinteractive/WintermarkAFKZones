# Changelog

## 1.0.0 - 2026-11-24
First public release.

- AFK zones (cuboids) created with a selection wand (left/right-click) or `/afkzone pos1` / `pos2`. Zones are at least 3 blocks tall so jumping doesn't leave them; the smallest zone wins when zones overlap.
- Particle outlines of zones (gold) and your selection (green) while holding the wand.
- Bossbar countdown to the next reward, enter/leave messages and sounds.
- Reward profiles with items, console commands, messages and a chance per reward; one profile per zone.
- Anti-alt: maximum number of earning players per IP (`anti-alt.max-per-ip`), bypass permission.
- Stats (AFK time, rewards) saved in `data.yml`, `/afkzone stats [player]` and `/afkzone top [time|rewards]`.
- `/afkzone tp [zone]` with a teleport point per zone (`/afkzone setspawn <zone>`).
- Admin commands: create, delete, list, setprofile, reload; tab completion.
- Everything configurable in `config.yml` with MiniMessage colors; new settings are added to existing configs automatically.
