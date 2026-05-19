# LAN Multiplayer Setup

Engkanto Clash can run on multiple PCs on the same LAN. One computer runs the
server, and each player runs a client that connects to the server computer's LAN
IP address.

## Requirements

- Java 17 on every PC.
- All PCs connected to the same LAN or Wi-Fi network.
- The server PC must allow inbound TCP connections on the game port.
- By default, the game uses port `50137`.
- Multiplayer supports up to `4` players.

## 1. Choose The Server PC

Pick one PC to host the match. On that PC, find its LAN IP address.

Windows:

```powershell
ipconfig
```

Look for the active adapter's `IPv4 Address`, for example:

```text
192.168.1.20
```

Linux/macOS:

```bash
ip addr
```

or:

```bash
ifconfig
```

Use the IP address for the active LAN or Wi-Fi adapter.

## 2. Start The Server

On the server PC, run:

```bash
./gradlew runServer
```

Windows PowerShell:

```powershell
.\gradlew.bat runServer
```

The default port is `50137`. To use another port:

```bash
./gradlew runServer --args="50138"
```

Keep the server terminal open while playing.

## 3. Start Each Client

On every player PC, connect to the server PC's LAN IP:

```bash
./gradlew runClient --args="--host=192.168.1.20 --port=50137"
```

Windows PowerShell:

```powershell
.\gradlew.bat runClient --args="--host=192.168.1.20 --port=50137"
```

Replace `192.168.1.20` with the actual IP address of the server PC.

If a client is run without `--host`, it tries `127.0.0.1`, which only connects
to a server running on the same PC. For a PC lab or LAN setup, always pass the
server PC's LAN IP.

If the client cannot reach the server, it falls back to local mode and prints a
message in the terminal. Check the terminal output if a client opens but does
not appear in the multiplayer lobby.

## 4. Lobby Flow

1. Each client selects a character.
2. Each player toggles ready.
3. When all connected players are ready, the server starts a countdown.
4. After the countdown, the match begins.

New clients cannot join once the match is already in progress. Restart the
server to start a new lobby.

## Firewall Notes

If clients cannot connect, the server PC's firewall is the most common cause.

On Windows, allow Java through Windows Defender Firewall on the active network,
or create an inbound TCP rule for port `50137`.

If the PC lab network blocks peer-to-peer connections between computers, ask the
lab administrator to allow TCP traffic between the player PCs and the server PC
on the chosen port.

## Quick Connection Test

From a client PC, test whether the server port is reachable.

Windows PowerShell:

```powershell
Test-NetConnection 192.168.1.20 -Port 50137
```

Linux/macOS:

```bash
nc -vz 192.168.1.20 50137
```

Use the server PC's actual LAN IP. If the test fails, check that the server is
running, the IP address is correct, both PCs are on the same network, and the
firewall allows the port.

## Optional: Run From A Built Distribution

Instead of running through Gradle on every PC, you can build a distribution:

```bash
./gradlew installDist
```

Then copy `build/install/engkanto-clash` to the lab PCs.

Run a copied client:

```bash
build/install/engkanto-clash/bin/engkanto-clash --host=192.168.1.20 --port=50137
```

The generated default launcher starts the client. To run the server, keep using
`./gradlew runServer` on the host PC or add a separate server launcher task
before packaging.

## Expected LAN Behavior

The server listens on the configured port using Java's `ServerSocket`, so it can
accept connections from other PCs that can reach the server PC over the LAN. The
client supports `--host` and `--port`, so no code changes are needed for a basic
same-network PC lab setup.
