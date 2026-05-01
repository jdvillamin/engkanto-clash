# System Architecture

This document describes the current codebase state. The repository still keeps
client, server, and common package roots, but the implemented gameplay is
currently a local Java Swing combat prototype. The server entry point is a
placeholder and does not yet run an authoritative multiplayer game loop.

## Current Runtime Flow

```text
ClientMain
  -> GameWindow
      -> GamePanel
          -> fixed update loop at 60 updates/second
          -> Java2D render pass
```

The client owns the current playable state:

- keyboard input
- player movement and character switching
- platform collision and drop-through behavior
- sprite animation
- local test dummy combat
- health, damage, healing, poison, death, and respawn
- projectile spawning, movement, collision, and one-shot hit behavior
- health and ability cooldown UI

## Important Packages

```text
com.engkanto.client
Desktop entry point and Swing window.

com.engkanto.client.input
Keyboard state and one-shot action requests.

com.engkanto.client.game
Game loop, arena setup, rendering order, player-vs-dummy hit resolution.

com.engkanto.client.game.character
Character definitions, sprite animation metadata, per-character movement,
cooldown, projectile, and attack rules.

com.engkanto.client.game.combat
Health, damage, poison, floating damage numbers, health UI, and ability UI.

com.engkanto.client.game.entity
Player, projectile, and test dummy entities.

com.engkanto.client.game.world
Platform geometry and tile rendering.

com.engkanto.client.render
Sprite and asset loading helpers plus debug HUD rendering.

com.engkanto.server
Placeholder server entry point.

com.engkanto.common
Reserved for future shared model/network code.
```

## Combat Flow

```text
KeyboardInput records a move request.
Player consumes the request if the move is not on cooldown.
SpriteAnimator locks the requested attack animation.
CharacterDefinition customizes attack timing, cooldown, movement lock, and hit behavior.
GamePanel checks player/test-dummy overlap for direct attacks.
GamePanel checks projectile/test-dummy hitbox overlap for projectiles.
DamageComponent applies actual clamped damage to HealthComponent.
HealthComponent emits damage, heal, and death events.
```

## Collision Model

- Player world bounds use a `96x96` sprite rectangle.
- The test dummy exposes a `48x72` hitbox.
- Direct attacks use player rectangle vs. test dummy hitbox overlap.
- Projectiles use projectile square vs. test dummy hitbox overlap.
- Floating platforms can be dropped through by holding Down.
- The ground platform remains solid.

## UI Model

The current UI is drawn directly in Java2D:

- `HealthUI` draws player health.
- `AbilityUI` draws `J`, `K`, `E`, and `L` key icons with cooldown state.
- `DebugRenderer` draws development control hints.
- `TestDummy` draws its own label, health bar, hit flash, death label, and
  damage numbers.

## Testing

JUnit coverage currently focuses on `HealthComponent`, including:

- damage and healing clamp behavior
- dead entities ignoring healing
- full-heal reporting
- invalid value handling
- poison tick timing

## Future Architecture Work

The original project target includes networked multiplayer with an
authoritative server. That is not implemented yet. Future work should move
game-state authority, player synchronization, hit resolution, and chat into the
server/common layers while keeping the Swing client focused on input and
rendering.
