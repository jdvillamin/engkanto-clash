# Gameplay Reference

This document reflects the current local combat prototype on branch
`villamin/health-and-damage`.

## Controls

- `A` / Left Arrow: move left
- `D` / Right Arrow: move right
- `W` / Up Arrow: jump
- `S` / Down Arrow: drop through floating platforms
- `Space`: glide while playing Aswang during the falling part of a jump
- `P`: switch character
- `J`: move 1
- `K`: move 2
- `E`: move 3
- `L`: special move
- `X`: debug damage self
- `C`: debug heal self
- `Z`: debug death animation

## Arena

- The current game window is `1280x704`.
- The arena has a wide ground platform and six spaced floating platforms.
- Floating platforms can be passed through by holding `S` or Down.
- The player and test dummy both spawn on the ground.

## HUD

- Player health is shown as a bar and numeric value.
- Ability keys `J`, `K`, `E`, and `L` are shown near the bottom of the screen.
- Ability keys grey out while on cooldown, show a descending cooldown bar, and
  show remaining cooldown time.

## Health And Damage Rules

- Player max health is `100`.
- The test dummy max health is `100`.
- Damage and healing are clamped to the target's actual current health.
- Dead entities cannot be healed until revived.
- The test dummy respawns after `4` seconds.
- The player respawns after `1` second.
- Direct character attacks can apply damage only once per locked attack
  animation.
- Projectiles can apply damage only once and use both X and Y hitbox overlap.
- Projectile-spawning moves do not also apply base character damage.

## Character Moves

| Character | `J` | `K` | `E` | `L` |
| --- | --- | --- | --- | --- |
| Tikbalang | 15 damage | 25 damage, stationary, 1.0s cooldown | Dash, no damage | Super dash, 10s cooldown |
| Kapre | 15 damage | 25 damage, stationary, 1.0s cooldown | Log projectile, 10 damage | Smoke release, 10s cooldown |
| Aswang | 15 damage | 25 damage, stationary, 1.0s cooldown | Heal self for 25 | 10 impact damage plus poison, stationary, 10s cooldown |
| Engkanto | 10-damage projectile, 0.20s cooldown | 20-damage projectile, stationary | Vine effect | 25-damage large projectile, 10s cooldown |

## Status Effects

Aswang's `L` poison begins on the third animation frame. It deals `5` damage
every `0.5` seconds for `4` seconds, for `40` poison damage if the target
survives the full duration.
