# LAN Multiplayer Setup

Use this when players are on the same Wi-Fi or LAN. One PC runs the server.
Each player runs a client.

## Requirements

- Java 17 on every PC
- all PCs on the same network
- server PC allows TCP connections on port `50137`
- up to `4` players

## 1. Find The Server IP

On the server PC, get the LAN IP address.

Windows:

```powershell
ipconfig
```

Linux:

```bash
ip addr
```

macOS:

```bash
ifconfig
```

Use the active Wi-Fi or Ethernet IPv4 address, like `192.168.1.20`.

## 2. Start The Server

On the server PC:

```bash
./gradlew runServer
```

Windows PowerShell:

```powershell
.\gradlew.bat runServer
```

Use a custom port if needed:

```bash
./gradlew runServer --args="50138"
```

Keep this terminal open.

## 3. Start The Clients

On each player PC:

```bash
./gradlew runClient --args="--host=192.168.1.20 --port=50137"
```

Windows PowerShell:

```powershell
.\gradlew.bat runClient --args="--host=192.168.1.20 --port=50137"
```

Replace `192.168.1.20` with the server PC's real LAN IP.

## 4. Lobby

1. Pick a character.
2. Press ready.
3. When all players are ready, a `10` second countdown starts.
4. After the countdown, the match starts.

New players cannot join after the match starts. Restart the server for a new
lobby.

## Troubleshooting

- Use the server PC's LAN IP, not `127.0.0.1`, when connecting from another PC.
- Make sure all PCs are on the same network.
- Allow Java through the server PC firewall.
- Allow inbound TCP traffic on port `50137`.
- Check that the server is still running.

Quick port test from a client PC:

```powershell
Test-NetConnection 192.168.1.20 -Port 50137
```

Linux/macOS:

```bash
nc -vz 192.168.1.20 50137
```

## Optional Build

You can build a client distribution:

```bash
./gradlew installDist
```

Run the generated client:

```bash
build/install/engkanto-clash/bin/engkanto-clash --host=192.168.1.20 --port=50137
```

The generated launcher starts the client. Keep using `./gradlew runServer` for
the server.
