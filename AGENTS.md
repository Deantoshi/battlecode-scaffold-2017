This is the 2017 version of MITs Battlecode Scaffold Repo. You are on a Ubuntu WSL.T The game uses Java 8. You are in VScode.

## Project Structure

```
battlecode-scaffold-2017/
├── src/                    # Player bot code
│   ├── examplefuncsplayer/ # Example bot
├── engine/                 # Game engine source (battlecode server)
├── client/                 # Web-based match viewer
├── test/                   # Player tests
├── matches/                # Saved match files (.bc17)
└── build.gradle            # Unified build configuration
```

## Prerequisites

- Java 8
- Node.js (for client)

## Commands

### Build everything
```bash
./gradlew build
```

### Run a match (headless)
```bash
./gradlew runWithSummary -PteamA=examplefuncsplayer -PteamB=examplefuncsplayer -Pmaps=Shrine
```

### Start the web client (view matches)
```bash
./gradlew clientWatch
# Then open http://localhost:8080
```

### List players and maps
```bash
./gradlew listPlayers
./gradlew listMaps
```

## Creating a new bot

1. Create folder: `src/mybot/`
2. Add `RobotPlayer.java` with `public static void run(RobotController rc)`
3. Run: `./gradlew runWithSummary -PteamA=mybot -PteamB=examplefuncsplayer -Pmaps=Shrine`

