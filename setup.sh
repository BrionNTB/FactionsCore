#!/usr/bin/env bash
# FactionsCore one-shot server setup.
#
# Usage (as root, after building both jars):
#   bash setup.sh YourGamertag
#
# Does everything after the build: installs the jars to the server directory, runs the
# first boot non-interactively (accepts the wizard with defaults, generates the world),
# ops the given gamertag, opens the firewall, and installs + starts a systemd service
# that keeps the server running permanently.
#
# Safe to re-run: re-copies fresh jars and restarts the service, skips what's already done.

set -u

SRC_DIR="${SRC_DIR:-/opt/FactionsCore}"
SERVER_DIR="${SERVER_DIR:-/opt/factions}"
GAMERTAG="${1:-}"
JAVA_FLAGS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED"
JAVA_MEM="-Xms1G -Xmx2G"

fail() { echo "ERROR: $1" >&2; exit 1; }

ENGINE_JAR="$SRC_DIR/engine/powernukkitx/build/powernukkitx.jar"
PLUGIN_JAR=$(ls "$SRC_DIR"/plugins/FactionsCore/build/libs/FactionsCore-*.jar 2>/dev/null | head -1)

[ -f "$ENGINE_JAR" ] || fail "Engine jar not found at $ENGINE_JAR -- run: ./gradlew :powernukkitx:shadowJar"
[ -n "$PLUGIN_JAR" ] || fail "Plugin jar not found -- run: ./gradlew :plugins:FactionsCore:shadowJar"
command -v java >/dev/null || fail "Java not installed -- need Java 21"

echo "[1/6] Installing jars into $SERVER_DIR ..."
mkdir -p "$SERVER_DIR/plugins"
# Stop the service first if it exists, so we don't swap jars under a running server.
systemctl stop factions 2>/dev/null || true
cp "$ENGINE_JAR" "$SERVER_DIR/server.jar"
rm -f "$SERVER_DIR"/plugins/FactionsCore-*.jar
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/"

if [ ! -f "$SERVER_DIR/pnx.yml" ]; then
    echo "[2/6] First boot: accepting license, generating world (takes a minute or two)..."
    ( cd "$SERVER_DIR" && \
      (echo eng; sleep 2; echo y; sleep 2; echo ""; sleep 2; echo ""; sleep 60; echo stop; sleep 25) \
        | java $JAVA_MEM $JAVA_FLAGS -jar server.jar > "$SERVER_DIR/first-boot.log" 2>&1 ) || true
    if [ ! -f "$SERVER_DIR/pnx.yml" ]; then
        fail "First boot did not complete -- check $SERVER_DIR/first-boot.log"
    fi
else
    echo "[2/6] Server already configured, skipping first boot."
fi

# Rename the server in-game: the MOTD lives in pnx.yml. Match only the bare "motd:" key --
# "sub-motd:" also ends in motd: but starts with "sub-", so ^\s*motd: can't hit it.
if [ -f "$SERVER_DIR/pnx.yml" ]; then
    sed -E -i 's/^([[:space:]]*)motd:.*/\1motd: "WickedRaids"/' "$SERVER_DIR/pnx.yml"
    sed -E -i 's/^([[:space:]]*)sub-motd:.*/\1sub-motd: "Factions PvP"/' "$SERVER_DIR/pnx.yml"
fi

if [ -n "$GAMERTAG" ]; then
    LOWER=$(echo "$GAMERTAG" | tr '[:upper:]' '[:lower:]')
    if ! grep -qixF "$LOWER" "$SERVER_DIR/ops.txt" 2>/dev/null; then
        echo "$LOWER" >> "$SERVER_DIR/ops.txt"
    fi
    echo "[3/6] Opped: $GAMERTAG"
else
    echo "[3/6] No gamertag given -- op yourself later with: bash setup.sh YourGamertag"
fi

echo "[4/6] Opening firewall (UDP 19132)..."
if command -v ufw >/dev/null; then
    ufw allow OpenSSH >/dev/null 2>&1 || true
    ufw allow 19132/udp >/dev/null 2>&1 || true
    ufw --force enable >/dev/null 2>&1 || true
else
    echo "      (ufw not installed -- make sure UDP 19132 is open in your provider's firewall)"
fi

echo "[5/6] Installing systemd service..."
cat > /etc/systemd/system/factions.service <<EOF
[Unit]
Description=FactionsCore Bedrock server
After=network.target

[Service]
WorkingDirectory=$SERVER_DIR
ExecStart=/usr/bin/java $JAVA_MEM $JAVA_FLAGS -jar server.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable factions >/dev/null 2>&1
systemctl restart factions

echo "[6/6] Waiting for the server to come up..."
sleep 20
if systemctl is-active --quiet factions; then
    IP=$(hostname -I 2>/dev/null | awk '{print $1}')
    echo ""
    echo "======================================================"
    echo "  Server is RUNNING."
    echo "  Connect in Minecraft Bedrock:"
    echo "    Address: ${IP:-<your VPS IP>}"
    echo "    Port:    19132"
    echo ""
    echo "  Logs:    journalctl -u factions -f   (Ctrl+C to exit)"
    echo "  Stop:    systemctl stop factions"
    echo "  Start:   systemctl start factions"
    echo "======================================================"
else
    fail "Service failed to start -- check: journalctl -u factions -n 50"
fi
