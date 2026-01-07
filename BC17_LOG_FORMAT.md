# Battlecode 2017 .bc17 File Format: Log Storage

## Overview

`.bc17` files store Battlecode match replays using Google's FlatBuffers serialization format, compressed with GZIP. Logs from robot `System.out` calls are stored per-round within these files.

## File Structure

### Compression Layer
The entire `.bc17` file is GZIP compressed:
```
GZIP compressed data → FlatBuffer binary data
```

### FlatBuffer Structure
The decompressed data contains a `GameWrapper` table that indexes all events:
- `events[]` - Array of event offsets
- `matchHeaders[]` - Indices of match header events  
- `matchFooters[]` - Indices of match footer events

Each event is wrapped in an `EventWrapper` with a type field indicating:
- `GameHeader` - Initial game metadata
- `MatchHeader` - Match start with map info
- `Round` - Individual round data (includes logs)
- `MatchFooter` - Match end with winner
- `GameFooter` - Game completion

## Log Storage in Round Events

Logs are stored within each `Round` event in the `logs` field (FlatBuffer offset 46).

### Location
- **Schema**: `battlecode.schema.Round`
- **Field**: `logs` (string, offset 46)
- **Access**: `Round.logs()` returns a UTF-8 string

### Log Format

Each round's logs are concatenated into a single UTF-8 string containing:
1. Header for each robot that logs during the round
2. The actual log messages from that robot

#### Header Format
```
[$TEAM:$ROBOTTYPE#$ID@$ROUND] 
```

Where:
- `$TEAM` = `A` or `B` (team identifier)
- `$ROBOTTYPE` = Robot type name (e.g., `ARCHON`, `GARDENER`, `SOLDIER`, `SCOUT`, `TANK`, `LUMBERJACK`)
- `$ID` = Unique robot ID (integer)
- `$ROUND` = Current round number (integer)

#### Header is printed **once per robot per round** before any messages from that robot.

### Log Generation Flow

```
Robot code calls System.out.println()
    ↓
RoboPrintStream.maybePrintHeader() - adds header if first output this round
    ↓
Header format: [TEAM:ROBOTTYPE#ID@ROUND]
    ↓
Message appended to header
    ↓
Written to ByteArrayOutputStream (MatchMaker.logger)
    ↓
At end of round, logs converted to byte[]
    ↓
Stored as string in FlatBuffer Round event
```

### Log Storage in Code

#### Writing Logs (RoboPrintStream.java:239-263)
```java
private void maybePrintHeader() {
    if (!this.headerThisRound) {
        this.headerThisRound = true;
        real.print('[');
        real.print(team);           // e.g., "A"
        real.print(':');
        real.print(type);           // e.g., "GARDENER"
        real.print('#');
        real.print(id);             // e.g., 13048
        real.print('@');
        real.print(round);          // e.g., 2186
        real.print("] ");
    }
}
```

#### Storing in Round (GameMaker.java:456-566)
```java
public void makeRound(int roundNum) {
    // ... collect other round data ...
    
    byte[] logs = this.logger.toByteArray();
    this.logger.reset();
    
    int logsP = builder.createString(ByteBuffer.wrap(logs));
    
    Round.startRound(builder);
    // ... add other fields ...
    Round.addLogs(builder, logsP);
    Round.addRoundID(builder, roundNum);
    
    int round = Round.endRound(builder);
    return EventWrapper.createEventWrapper(builder, Event.Round, round);
}
```

## Log Examples

### Example 1: Single message
```
[B:GARDENER#13048@2186] I'm a gardener!
```

### Example 2: Multiple messages from same robot (same round)
```
[A:ARCHON#1@0] Starting up
[A:ARCHON#1@0] Initial scan complete
```

### Example 3: Multiple robots in same round
```
[B:SOLDIER#11348@2039] I'm an soldier!
[B:SOLDIER#11540@2004] I'm an soldier!
```

## Accessing Logs

### From .bc17 File
1. Decompress: `gunzip -c file.bc17 > decompressed.bin`
2. Parse with FlatBuffers Java API
3. Iterate events, find `Round` events
4. Extract `Round.logs()` string
5. Parse headers and messages

### Schema Definition (Round.java:144-165)
```java
/**
 * All logs sent this round.
 * Messages from a particular robot in this round start on a new line, and
 * have a header:
 * '[' $TEAM ':' $ROBOTTYPE '#' $ID '@' $ROUND '] '
 * $TEAM = 'A' | 'B'
 * $ROBOTTYPE = 'ARCHON' | 'GARDENER' | 'LUMBERJACK' 
 *            | 'SOLDIER' | 'TANK' | 'SCOUT' | other names...
 * $ID = a number
 * $ROUND = a number
 * The header is not necessarily followed by a newline.
 * This header should only be sent once per robot per round (although
 * players may forge it, so don't crash if you get strange input.)
 *
 * You should try to only read this value once, and cache it. Reading
 * strings from a flatbuffer is much less efficient than reading other
 * buffers, because they need to be copied into an environment-provided
 * buffer and validated.
 *
 * (haha i guess you can never really escape string parsing can you)
 */
public String logs() { int o = __offset(46); return o != 0 ? __string(o + bb_pos) : null; }
```

## Notes

- Logs are UTF-8 encoded strings
- Only robots that call `System.out`/`System.err` during a round will have entries
- Empty rounds have empty or null logs string
- Headers are prepended automatically by `RoboPrintStream` - robot code doesn't add them
- Logs accumulate throughout a round and are flushed at round end
- Reading logs from FlatBuffers is less efficient than other data types; cache if accessed multiple times
