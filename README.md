# RuneDiary

A [RuneLite](https://runelite.net/) plugin that automatically tracks your Old School RuneScape progress and syncs it to [RuneDiary](https://www.runediary.com).

## Features

**Zero-config setup** — install the plugin, log in, done. No tokens or URLs to configure.

### Real-time Event Tracking
- Deaths (items lost, killer, location)
- Level ups
- Valuable loot drops
- Boss kill counts
- Slayer task completions
- Quest completions
- Pet drops
- Clue scroll rewards
- Combat achievement completions
- Achievement diary tier completions
- Collection log additions

### Profile Sync
Automatically syncs your full character profile on logout:
- All 25 skills with XP
- Quest completion states (Free / Members / Miniquests)
- Achievement diary progress per area with task counts
- Combat achievement progress per tier
- Collection log items (obtained status from browsing in-game)
- Bank value snapshot
- Session stats (XP gained, loot, deaths, duration)
- Boss personal best times and kill counts
- Player 3D model and pet model
- Current equipment

### Server-Controlled Limits
Loot thresholds, clue minimums, and kill count intervals are set by the RuneDiary server — not configurable locally. This prevents abuse and ensures consistent data quality.

### Screenshots
Optional screenshot capture for events. Toggle globally or per-event type in the plugin settings.

## How It Works

1. On login, the plugin authenticates using your account hash (no manual setup needed)
2. Game events are detected via RuneLite's event system and sent to RuneDiary in real-time
3. On logout, a full profile snapshot is synced
4. View your progress at `runediary.com/your-name/character-summary`

## Configuration

The plugin shows two settings sections:

- **Event Notifications** — toggle which events are tracked
- **Screenshots** — toggle screenshot capture per event type

All thresholds (minimum loot value, clue value, KC intervals) are managed server-side.

## Building

```bash
# Run in developer mode
./gradlew run

# Build JAR
./gradlew jar

# Run tests
./gradlew test
```

Requires Java 11. Uses RuneLite `latest.release`.

## License

BSD 2-Clause — see [LICENSE](LICENSE).
