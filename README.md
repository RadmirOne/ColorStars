# ColorStars

Android puzzle game prototype built with Kotlin and Compose Multiplatform (the existing repository layout).

## Features in this PR
- Interactive stained-glass mosaic with exposed pieces and five jewel colors.
- 4 queues on the first level; 5 queues on later levels, with 4 visible stars per queue.
- Selecting a queue head removes a reachable matching glass fragment, prioritizing its highest vertex, then proximity to the board center, then the rightmost fragment. If none is exposed, the star goes into storage.
- Stars slide up on selection; exhausted queues fade out and the remaining queues smoothly redistribute across the screen.
- 4 storage wells with 2 possible additions (6 max), auto-release of stored stars after pieces become reachable.
- Three boosters: add a well, shatter any piece, or select any of the 4 visible stars in a queue.
- Moving color-matched magical projectile with a **short** glowing tail. Route search traverses empty cells, no full path is drawn.
- Win, loss, continue, and booster purchase dialogs with concise Russian copy.
- Engine tests for level progression, boosters, storage, and route constraints.

## Requirements
- JDK 17
- Android SDK 35
- Android Studio (Kotlin + Compose support)
- Gradle 8.9 (the original repository does not include `gradle/wrapper/gradle-wrapper.jar`)

Open in Android Studio and run `androidApp`. With a local Gradle installation:

```sh
gradle :androidApp:assembleDebug
gradle :shared:testDebugUnitTest
```

## Architecture
- `shared/.../game/ColorStarsEngine.kt`: pure reducer / deterministic level generation.
- `shared/.../game/ProjectileRouter.kt`: BFS over empty grid cells, no rendering concerns.
- `shared/.../ui/ColorStarsGameScreen.kt`: Compose UI + visual effects; game state changes commit after the visual effect finishes.
- `shared/.../App.kt`: shared UI entry point.

## Boundaries of this first implementation
- It is a playable prototype, **not** the final art-asset or production-level system. The glass is drawn natively in Compose rather than importing flattened mockups.
- Prices, rewards, progression, and booster inventories are currently in-memory placeholders; there is no persistence, real-money store, or backend.
- Projectile pathfinding uses cell-based collision-free routes. More complex glass geometry and stronger effects can be added without changing the UI contract.
- The build was not verified in this environment, because Gradle and the Android SDK are not installed. Run the commands above in Android Studio/CI.
