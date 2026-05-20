package com.engkanto.client.game;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.engkanto.client.audio.AudioCue;
import com.engkanto.client.audio.AudioManager;
import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.combat.AbilityUI;
import com.engkanto.client.game.combat.HealthUI;
import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.entity.Projectile;
import com.engkanto.client.game.entity.RemotePlayerRenderer;
import com.engkanto.client.game.entity.TestDummy;
import com.engkanto.client.game.world.Platform;
import com.engkanto.client.input.KeyboardInput;
import com.engkanto.client.net.NetworkClient;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.PlayerSnapshot;
import com.engkanto.client.render.DebugRenderer;

import java.util.Comparator;

public final class GamePanel extends JPanel implements Runnable {
    private static final String BACKGROUND_MAP_PATH = "/assets/maps/grassland.png";
    private static final String NETWORK_ACTION_IDLE = "IDLE";
    private static final String NETWORK_ACTION_WALK = "WALK";
    private static final String NETWORK_ACTION_JUMP = "JUMP";
    private static final String NETWORK_ACTION_MOVE_1 = "MOVE_1";
    private static final String NETWORK_ACTION_MOVE_2 = "MOVE_2";
    private static final String NETWORK_ACTION_MOVE_3 = "MOVE_3";
    private static final String NETWORK_ACTION_SPECIAL = "SPECIAL";

    private final AudioManager audioManager;
    private final KeyboardInput keyboardInput;
    private final List<Platform> platforms;
    private final Player player;
    private final TestDummy dummy;
    private final DebugRenderer debugRenderer;
    private final HealthUI healthUI;
    private final AbilityUI abilityUI;
    private final BufferedImage backgroundImage;
    private final NetworkClient networkClient;
    private final RemotePlayerRenderer remotePlayerRenderer;
    private final Map<Integer, NetworkAudioState> networkAudioStates;

    private Thread gameThread;
    private boolean running;
    private PlayerAction activeDirectAttack;
    private boolean directAttackHitApplied;
    private long inputSequence;

    public GamePanel() {
        this(null, AudioManager.getInstance());
    }

    public GamePanel(NetworkClient networkClient) {
        this(networkClient, AudioManager.getInstance());
    }

    public GamePanel(NetworkClient networkClient, AudioManager audioManager) {
        this.networkClient = networkClient;
        this.audioManager = audioManager;
        keyboardInput = new KeyboardInput();
        platforms = createPlatforms();
        player = new Player(
                GameConfig.SCREEN_WIDTH / 2.0 - Player.SIZE / 2.0,
                getGroundPlatformTop() - Player.SIZE
        );
        dummy = new TestDummy(700, getGroundPlatformTop() - TestDummy.HEIGHT);

        debugRenderer = new DebugRenderer();
        healthUI = new HealthUI(player);
        abilityUI = new AbilityUI(player);
        backgroundImage = AssetLoader.loadImage(BACKGROUND_MAP_PATH);
        remotePlayerRenderer = new RemotePlayerRenderer();
        networkAudioStates = new HashMap<>();

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(new Color(28, 36, 32));
        setDoubleBuffered(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(keyboardInput);
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        gameThread = new Thread(this, "engkanto-game-loop");
        gameThread.start();
        requestFocusInWindow();
    }

    @Override
    public void run() {
        final double secondsPerUpdate = 1.0 / GameConfig.TARGET_UPDATES_PER_SECOND;
        final double nanosPerUpdate = secondsPerUpdate * 1_000_000_000.0;

        long previousTime = System.nanoTime();
        double accumulatedUpdates = 0.0;

        while (running) {
            long currentTime = System.nanoTime();
            accumulatedUpdates += (currentTime - previousTime) / nanosPerUpdate;
            previousTime = currentTime;

            while (accumulatedUpdates >= 1.0) {
                update(secondsPerUpdate);
                accumulatedUpdates--;
            }

            repaint();
            sleepBriefly();
        }
    }

    private void update(double deltaSeconds) {
        if (isNetworkMode()) {
            GameStateSnapshot state = networkClient.getLatestState();
            networkClient.sendInput(keyboardInput.consumeNetworkSnapshot(++inputSequence));
            emitNetworkAudio(state);
            remotePlayerRenderer.update(deltaSeconds, state);
            return;
        }

        PlayerAction previousAction = player.getCurrentAction();
        String previousCharacterName = player.getCharacterName();
        boolean wasOnGround = player.isOnGroundState();
        boolean wasDead = player.isDead();

        if (keyboardInput.consumeDamageRequested()) {
            player.takeDamage(25.0);
            audioManager.playSound(AudioCue.PLAYER_HURT);
        }
        if (keyboardInput.consumeHealRequested()) {
            player.heal(25.0);
            audioManager.playSound(AudioCue.PLAYER_HEAL);
        }
        player.update(keyboardInput, platforms, deltaSeconds);
        emitPlayerAudio(previousAction, previousCharacterName, wasOnGround, wasDead);
        dummy.update(deltaSeconds);
        resolvePlayerAttacks();
        resolveProjectileHits();
    }

    private void emitNetworkAudio(GameStateSnapshot state) {
        if (state == null) {
            return;
        }

        Map<Integer, NetworkAudioState> nextStates = new HashMap<>();
        for (PlayerSnapshot snapshot : state.players) {
            NetworkAudioState previous = networkAudioStates.get(snapshot.id);
            if (previous != null) {
                emitNetworkPlayerAudio(previous, snapshot);
            }
            nextStates.put(snapshot.id, new NetworkAudioState(snapshot));
        }
        networkAudioStates.clear();
        networkAudioStates.putAll(nextStates);
    }

    private void emitNetworkPlayerAudio(NetworkAudioState previous, PlayerSnapshot current) {
        boolean localPlayer = current.id == networkClient.getLocalPlayerId();
        String currentAction = normalizeAction(current.action);
        boolean actionChanged = !currentAction.equals(previous.action);

        if (localPlayer && NETWORK_ACTION_WALK.equals(previous.action)
                && !NETWORK_ACTION_WALK.equals(currentAction)) {
            audioManager.stopLoopingSound(AudioCue.MOVE_START);
        } else if (!localPlayer && actionChanged && NETWORK_ACTION_WALK.equals(currentAction)) {
            audioManager.playSound(AudioCue.MOVE_START);
        }
        if (localPlayer && actionChanged && NETWORK_ACTION_WALK.equals(currentAction)) {
            audioManager.playLoopingSound(AudioCue.MOVE_START);
        }
        if (actionChanged && NETWORK_ACTION_JUMP.equals(currentAction)) {
            audioManager.playSound(AudioCue.JUMP);
        }
        if (NETWORK_ACTION_JUMP.equals(previous.action) && !NETWORK_ACTION_JUMP.equals(currentAction)) {
            audioManager.playSound(AudioCue.LAND);
        }
        if (actionChanged) {
            playNetworkActionSound(currentAction);
        }
        if (current.characterIndex != previous.characterIndex) {
            audioManager.playSound(AudioCue.CHARACTER_SWITCH);
        }
        if (current.health < previous.health) {
            audioManager.playSound(AudioCue.PLAYER_HURT);
        }
        if (current.health > previous.health) {
            audioManager.playSound(AudioCue.PLAYER_HEAL);
        }
        if (!previous.dead && current.dead) {
            audioManager.playSound(AudioCue.PLAYER_DEATH);
        }
    }

    private void playNetworkActionSound(String action) {
        switch (action) {
            case NETWORK_ACTION_MOVE_1 -> audioManager.playSound(AudioCue.ATTACK_1);
            case NETWORK_ACTION_MOVE_2 -> audioManager.playSound(AudioCue.ATTACK_2);
            case NETWORK_ACTION_MOVE_3 -> audioManager.playSound(AudioCue.ATTACK_3);
            case NETWORK_ACTION_SPECIAL -> audioManager.playSound(AudioCue.SPECIAL);
            default -> {
            }
        }
    }

    private String normalizeAction(String action) {
        return action == null ? NETWORK_ACTION_IDLE : action;
    }

    private void emitPlayerAudio(PlayerAction previousAction, String previousCharacterName,
            boolean wasOnGround, boolean wasDead) {
        PlayerAction currentAction = player.getCurrentAction();

        if (previousAction == PlayerAction.WALK && currentAction != PlayerAction.WALK) {
            audioManager.stopLoopingSound(AudioCue.MOVE_START);
        }
        if (currentAction == PlayerAction.WALK && previousAction != PlayerAction.WALK) {
            audioManager.playLoopingSound(AudioCue.MOVE_START);
        }
        if (wasOnGround && !player.isOnGroundState()) {
            audioManager.playSound(AudioCue.JUMP);
        }
        if (!wasOnGround && player.isOnGroundState()) {
            audioManager.playSound(AudioCue.LAND);
        }
        if (currentAction != previousAction) {
            playActionSound(currentAction);
        }
        if (!previousCharacterName.equals(player.getCharacterName())) {
            audioManager.playSound(AudioCue.CHARACTER_SWITCH);
        }
        if (!wasDead && player.isDead()) {
            audioManager.playSound(AudioCue.PLAYER_DEATH);
        }
    }

    private void playActionSound(PlayerAction action) {
        switch (action) {
            case MOVE_1 -> audioManager.playSound(AudioCue.ATTACK_1);
            case MOVE_2 -> audioManager.playSound(AudioCue.ATTACK_2);
            case MOVE_3 -> audioManager.playSound(AudioCue.ATTACK_3);
            case SPECIAL -> audioManager.playSound(AudioCue.SPECIAL);
            default -> {
            }
        }
    }

    private static final class NetworkAudioState {
        private final String action;
        private final int characterIndex;
        private final double health;
        private final boolean dead;

        private NetworkAudioState(PlayerSnapshot snapshot) {
            this.action = snapshot.action == null ? NETWORK_ACTION_IDLE : snapshot.action;
            this.characterIndex = snapshot.characterIndex;
            this.health = snapshot.health;
            this.dead = snapshot.dead;
        }
    }

    private void resolvePlayerAttacks() {
        if (player.isDead() || dummy.getHealthComponent().isDead()) {
            resetDirectAttackTracking();
            return;
        }

        PlayerAction action = player.getCurrentAction();
        if (!isDirectAttack(action) || !player.isActionLocked()) {
            resetDirectAttackTracking();
            return;
        }

        if (activeDirectAttack != action) {
            activeDirectAttack = action;
            directAttackHitApplied = false;
        }
        if (directAttackHitApplied) {
            return;
        }
        if (!player.canActiveDirectAttackHit()) {
            return;
        }

        boolean overlaps = dummy.overlapsHitbox(
                player.getX(),
                player.getY(),
                player.getX() + Player.SIZE,
                player.getY() + Player.SIZE
        );

        if (overlaps) {
            directAttackHitApplied = player.applyActiveDirectAttack(dummy.getHealthComponent());
        }
    }

    private boolean isDirectAttack(PlayerAction action) {
        return action == PlayerAction.MOVE_1
                || action == PlayerAction.MOVE_2
                || action == PlayerAction.MOVE_3
                || action == PlayerAction.SPECIAL;
    }

    private void resetDirectAttackTracking() {
        activeDirectAttack = null;
        directAttackHitApplied = false;
    }

    private void resolveProjectileHits() {
        for (Projectile projectile : player.getActiveCharacterProjectiles()) {
            if (!projectile.isActive()) continue;
            boolean overlaps = dummy.overlapsHitbox(
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getX() + projectile.getDrawSize(),
                    projectile.getY() + projectile.getDrawSize()
            );
            if (overlaps) {
                projectile.hit(dummy.getHealthComponent());
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            drawWorld(graphics2D);
            drawPlatforms(graphics2D);
            if (isNetworkMode()) {
                GameStateSnapshot state = networkClient.getLatestState();
                drawNetworkPlayers(graphics2D, state);
                remotePlayerRenderer.drawEffects(graphics2D);
                PlayerSnapshot localPlayer = findLocalPlayer(state);
                if (localPlayer != null) {
                    drawNetworkHud(graphics2D, localPlayer);
                    drawNetworkAbilityUI(graphics2D, localPlayer);
                }
                drawTabHint(graphics2D);
                if (keyboardInput.isTabPressed() && state != null) {
                    drawLeaderboard(graphics2D, state);
                }
            } else {
                player.draw(graphics2D);
                dummy.draw(graphics2D);
                healthUI.draw(graphics2D);
                abilityUI.draw(graphics2D);
                debugRenderer.drawHud(graphics2D);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private boolean isNetworkMode() {
        return networkClient != null && networkClient.isConnected();
    }

    private void drawNetworkPlayers(Graphics2D graphics, GameStateSnapshot state) {
        if (state == null) {
            graphics.setColor(Color.WHITE);
            graphics.drawString("Connected. Waiting for server state...", 24, 32);
            return;
        }

        int localPlayerId = networkClient.getLocalPlayerId();
        for (PlayerSnapshot snapshot : state.players) {
            remotePlayerRenderer.draw(graphics, snapshot, snapshot.id == localPlayerId);
        }
    }

    private PlayerSnapshot findLocalPlayer(GameStateSnapshot state) {
        if (state == null) {
            return null;
        }
        int localId = networkClient.getLocalPlayerId();
        for (PlayerSnapshot snapshot : state.players) {
            if (snapshot.id == localId) {
                return snapshot;
            }
        }
        return null;
    }

    private void drawNetworkHud(Graphics2D graphics, PlayerSnapshot localPlayer) {
        graphics.setColor(new Color(0, 0, 0, 140));
        graphics.fillRoundRect(16, 12, 280, 26, 8, 8);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString("Player " + localPlayer.id + " — " + localPlayer.characterName, 26, 30);

        int barX = 18;
        int barY = 46;
        int barW = 200;
        int barH = 18;
        double hp = localPlayer.maxHealth <= 0 ? 0 :
                Math.max(0.0, Math.min(1.0, localPlayer.health / localPlayer.maxHealth));

        graphics.setColor(Color.BLACK);
        graphics.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);
        graphics.setColor(new Color(84, 34, 42));
        graphics.fillRoundRect(barX, barY, barW, barH, 6, 6);
        Color hpColor = localPlayer.dead ? new Color(126, 126, 126) :
                hp > 0.6 ? new Color(83, 218, 112) :
                hp > 0.3 ? new Color(255, 200, 50) :
                new Color(230, 70, 70);
        graphics.setColor(hpColor);
        graphics.fillRoundRect(barX, barY, (int) Math.round(barW * hp), barH, 6, 6);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        String hpText = String.format("%.0f / %.0f", localPlayer.health, localPlayer.maxHealth);
        graphics.drawString(hpText, barX + barW + 8, barY + 14);
    }

    private void drawNetworkAbilityUI(Graphics2D graphics, PlayerSnapshot localPlayer) {
        int keySize = 46;
        int gap = 10;
        int totalWidth = keySize * 4 + gap * 3;
        int startX = GameConfig.SCREEN_WIDTH / 2 - totalWidth / 2;
        int y = GameConfig.SCREEN_HEIGHT - 84;

        Object prevAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawAbilityKey(graphics, startX, y, "J", keySize,
                localPlayer.move1CooldownRemaining, localPlayer.move1CooldownDuration);
        drawAbilityKey(graphics, startX + keySize + gap, y, "K", keySize,
                localPlayer.move2CooldownRemaining, localPlayer.move2CooldownDuration);
        drawAbilityKey(graphics, startX + (keySize + gap) * 2, y, "E", keySize,
                localPlayer.move3CooldownRemaining, localPlayer.move3CooldownDuration);
        drawAbilityKey(graphics, startX + (keySize + gap) * 3, y, "L", keySize,
                localPlayer.specialCooldownRemaining, localPlayer.specialCooldownDuration);

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    private void drawAbilityKey(Graphics2D graphics, int x, int y, String label,
            int keySize, double remaining, double duration) {
        double fraction = duration <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, remaining / duration));
        boolean onCooldown = fraction > 0.0;

        graphics.setColor(new Color(74, 52, 30));
        graphics.fillRoundRect(x - 2, y - 2, keySize + 4, keySize + 4, 10, 10);
        graphics.setColor(new Color(245, 232, 184));
        graphics.fillRoundRect(x, y, keySize, keySize, 8, 8);

        if (onCooldown) {
            graphics.setColor(new Color(0, 0, 0, 160));
            graphics.fillRoundRect(x, y, keySize, keySize, 8, 8);
            int fillH = (int) Math.round(keySize * fraction);
            graphics.setColor(new Color(30, 30, 30, 120));
            graphics.fillRoundRect(x, y + keySize - fillH, keySize, fillH, 8, 8);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            graphics.setColor(Color.WHITE);
            FontMetrics tm = graphics.getFontMetrics();
            String timer = String.format("%.1f", remaining);
            graphics.drawString(timer, x + keySize / 2 - tm.stringWidth(timer) / 2, y + keySize - 6);
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        graphics.setColor(onCooldown ? new Color(185, 176, 150) : new Color(34, 26, 18));
        FontMetrics fm = graphics.getFontMetrics();
        int tx = x + keySize / 2 - fm.stringWidth(label) / 2;
        int ty = y + keySize / 2 + (fm.getAscent() - fm.getDescent()) / 2;
        graphics.drawString(label, tx, ty);
    }

    private void drawTabHint(Graphics2D graphics) {
        Object prevAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        String text = "TAB — Leaderboard";
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int px = GameConfig.SCREEN_WIDTH / 2 - (textWidth + 20) / 2;
        int py = GameConfig.SCREEN_HEIGHT - 28;

        graphics.setColor(new Color(0, 0, 0, 120));
        graphics.fillRoundRect(px, py, textWidth + 20, 22, 8, 8);
        graphics.setColor(new Color(200, 200, 200));
        graphics.drawString(text, px + 10, py + 16);

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    private void drawLeaderboard(Graphics2D graphics, GameStateSnapshot state) {
        Object prevAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(new Color(0, 0, 0, 160));
        graphics.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        int panelW = 460;
        int headerH = 40;
        int rowH = 36;
        int playerCount = state.players.size();
        int panelH = headerH + 36 + playerCount * rowH + 16;
        int panelX = (GameConfig.SCREEN_WIDTH - panelW) / 2;
        int panelY = (GameConfig.SCREEN_HEIGHT - panelH) / 2;

        graphics.setColor(new Color(30, 38, 34, 230));
        graphics.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        graphics.setColor(new Color(74, 52, 30));
        graphics.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        graphics.drawRoundRect(panelX + 1, panelY + 1, panelW - 2, panelH - 2, 14, 14);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        graphics.setColor(new Color(245, 232, 184));
        FontMetrics titleFm = graphics.getFontMetrics();
        String title = "LEADERBOARD";
        graphics.drawString(title, panelX + (panelW - titleFm.stringWidth(title)) / 2, panelY + 30);

        int colRank = panelX + 20;
        int colName = panelX + 60;
        int colChar = panelX + 220;
        int colKills = panelX + 380;
        int headerY = panelY + headerH + 24;

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        graphics.setColor(new Color(180, 180, 180));
        graphics.drawString("#", colRank, headerY);
        graphics.drawString("PLAYER", colName, headerY);
        graphics.drawString("CHARACTER", colChar, headerY);
        graphics.drawString("KILLS", colKills, headerY);

        List<PlayerSnapshot> sorted = new ArrayList<>(state.players);
        sorted.sort(Comparator.comparingInt((PlayerSnapshot p) -> p.kills).reversed()
                .thenComparingInt(p -> p.id));

        int localId = networkClient.getLocalPlayerId();

        for (int i = 0; i < sorted.size(); i++) {
            PlayerSnapshot p = sorted.get(i);
            int rowY = headerY + 10 + (i + 1) * rowH;
            boolean isLocal = p.id == localId;

            if (isLocal) {
                graphics.setColor(new Color(218, 186, 104, 40));
                graphics.fillRoundRect(panelX + 10, rowY - rowH + 10, panelW - 20, rowH, 6, 6);
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            graphics.setColor(isLocal ? new Color(245, 232, 184) : Color.WHITE);
            graphics.drawString(String.valueOf(i + 1), colRank, rowY);
            graphics.drawString("Player " + p.id, colName, rowY);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            graphics.drawString(p.characterName, colChar, rowY);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            graphics.setColor(isLocal ? new Color(83, 218, 112) : new Color(83, 218, 112));
            graphics.drawString(String.valueOf(p.kills), colKills, rowY);
        }

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    private void drawWorld(Graphics2D graphics) {
        graphics.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
    }

    private void drawPlatforms(Graphics2D graphics) {
        for (Platform platform : platforms) {
            platform.draw(graphics);
        }
    }

    private List<Platform> createPlatforms() {
        List<Platform> mapPlatforms = new ArrayList<>();
        mapPlatforms.add(new Platform(0, GameConfig.SCREEN_HEIGHT - 96, GameConfig.SCREEN_WIDTH, 96, Platform.Type.GROUND));
        mapPlatforms.add(new Platform(144, 492, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(456, 392, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(768, 492, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(296, 292, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(608, 292, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(1000, 392, 224, 24, Platform.Type.FLOATING));
        return mapPlatforms;
    }

    private double getGroundPlatformTop() {
        return platforms.get(0).getTop();
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
