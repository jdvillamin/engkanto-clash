# Gameplay Guide

Engkanto Clash can run as a local test game or as a multiplayer game through
the TCP server. Local mode uses one player and a test dummy. Multiplayer uses
the server as the source of truth.

## Controls

- `A` / Left Arrow: move left
- `D` / Right Arrow: move right
- `W` / Up Arrow: jump
- `S` / Down Arrow: drop through floating platforms
- `Space`: glide while falling as Aswang
- `P`: switch character
- `J`, `K`, `E`, `L`: skills
- `Enter`: chat in multiplayer
- `Tab`: hold leaderboard in multiplayer
- `X`, `C`, `Z`: local debug damage, heal, and death

## Arena

- Window size is `1280x704`.
- The map has one ground platform and six floating platforms.
- Floating platforms can be dropped through with `S` or Down.
- Local mode has a test dummy on the ground.

## Health

- Players have `100` HP.
- The local test dummy also has `100` HP.
- Damage and healing are clamped, so HP does not go below `0` or above max HP.
- Dead targets cannot be healed until they respawn or revive.
- Local test dummy respawn time is `4` seconds.
- Player respawn time is `1` second.
- Multiplayer respawn gives `3` seconds of invulnerability.

## Skills

Default cooldowns:

- `J`: `0.30s`
- `K`: `1.50s`
- `E`: `1.20s`
- `L`: `12.00s`

Special cooldown cases:

- Engkanto `J`: `0.25s`
- Aswang `E`: `4.00s`

| Character | `J` | `K` | `E` | `L` |
| --- | --- | --- | --- | --- |
| Tikbalang | `12` damage | `22` damage | dash, no damage | dash + `35` damage |
| Kapre | `12` damage | `22` damage | log projectile, `8` damage | `30` damage |
| Aswang | `12` damage | `18` damage | heal self by `20` HP | `8` damage + poison |
| Engkanto | projectile, `6` damage | projectile, `12` damage | vine/root effect | large projectile, `18` damage |

Aswang poison deals `4` damage every `0.5` seconds for `5` seconds. If all
ticks land, it deals `40` poison damage.

Engkanto's vine root lasts `1.5` seconds in multiplayer. Local dummy mode is
not a full PvP simulation. In local dummy mode, Engkanto `E` can also deal the
default `25` direct damage if the player overlaps the dummy.

## Combat Notes

- Direct attacks can hit only once per skill animation.
- Projectiles disappear after one hit or after leaving the screen.
- Projectile skills do not also use normal direct attack damage.
- Multiplayer matches last `180` seconds and rank players by kills.
