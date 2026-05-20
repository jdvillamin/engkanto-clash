/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file is the main gameplay panel. It updates local or network gameplay and draws the world, players, HUD, chat, leaderboard, and results screen.
 */
/*
 * Key Objects / Libraries Used
 *
 * JPanel
 * - Base Swing component that provides a surface for custom 2D rendering.
 * - GamePanel extends JPanel and overrides paintComponent() to draw the game.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/JPanel.html
 *
 * Graphics2D
 * - Provides methods for drawing shapes, images, and text on a component.
 * - Used in all draw methods to render the game world, players, and UI.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/Graphics2D.html
 *
 * KeyEvent
 * - Represents a keyboard press, release, or type event.
 * - Used to capture player input and chat keystrokes.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/event/KeyEvent.html
 *
 * BufferedImage
 * - An image stored in memory that can be drawn to or read from.
 * - Used for the vine overlay image drawn on rooted test dummies.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/image/BufferedImage.html
 *
 * AlphaComposite
 * - Controls how pixels are blended when drawing on top of existing content.
 * - Used for transparency effects like the vine overlay and chat fade-out.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/AlphaComposite.html
 *
 * Iterator
 * - Allows safe removal of elements while iterating a collection.
 * - Used in resolveProjectileHits() to remove projectiles on contact.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html
 *
 * Thread
 * - Allows code to run in the background.
 * - Used to run the game loop on a separate thread from the Swing EDT.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
 */

package com.engkanto.client.game;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.engkanto.client.audio.AudioCue;
import com.engkanto.client.audio.AudioManager;
import com.engkanto.client.game.character.EngkantoCharacter;
import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.combat.AbilityUI;
import com.engkanto.client.game.combat.HealthUI;
import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.entity.Projectile;
import com.engkanto.client.game.entity.RemotePlayerRenderer;
import com.engkanto.client.game.entity.TestDummy;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;
import com.engkanto.client.game.world.Platform;
import com.engkanto.client.input.KeyboardInput;
import com.engkanto.client.net.NetworkClient;
import com.engkanto.client.render.DebugRenderer;
import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.PlayerSnapshot;

public final class GamePanel extends JPanel implements Runnable {
    private static final String BACKGROUND_MAP_PATH = "/assets/maps/grassland.png";

    private static final int CHAT_X = 16;
    private static final int CHAT_Y = 420;
    private static final int CHAT_WIDTH = 320;
    private static final int CHAT_HEIGHT = 160;
    private static final int CHAT_INPUT_HEIGHT = 28;
    private static final int CHAT_MAX_MESSAGES = 6;
    private static final double CHAT_VISIBLE_SECONDS = 3.0;
    private static final Color CHAT_GOLD = new Color(245, 232, 184);

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
    private final Runnable onLobbyReturn;
    private final RemotePlayerRenderer remotePlayerRenderer;
    private final BufferedImage vineOverlayImage;
    private final StringBuilder chatInput = new StringBuilder();
    private final Map<Integer, NetworkAudioState> networkAudioStates;

    private Thread gameThread;
    private volatile boolean running;
    private PlayerAction activeDirectAttack;
    private boolean directAttackHitApplied;
    private long inputSequence;
    private boolean chatFocused;
    private double chatVisibleTimer;
    private int lastSeenMessageCount;
    private boolean lobbyReturnRequested;

    public GamePanel() {
        this(null, AudioManager.getInstance());
    }

    /*
     * GamePanel(NetworkClient networkClient)
     *
     * - Initializes the game panel with input, platforms, player, dummy, and UI components.
     * - Loads the vine overlay image from the Engkanto sprite sheet for drawing on rooted dummies.
     * - Configures the JPanel for double-buffered rendering and keyboard focus.
     */
    public GamePanel(NetworkClient networkClient) {
        this(networkClient, AudioManager.getInstance());
    }

    public GamePanel(NetworkClient networkClient, AudioManager audioManager) {
        this(networkClient, audioManager, null);
    }

    public GamePanel(NetworkClient networkClient, AudioManager audioManager, Runnable onLobbyReturn) {
        this.networkClient = networkClient;
        this.onLobbyReturn = onLobbyReturn;
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

        BufferedImage engkantoSheet = SpriteSheet.removeWhiteBackground(
                AssetLoader.loadImage("/assets/sprites/engkanto.png"));
        int frameWidth = engkantoSheet.getWidth() / 4;
        int frameHeight = engkantoSheet.getHeight() / 7;
        vineOverlayImage = engkantoSheet.getSubimage(
                2 * frameWidth, 4 * frameHeight, frameWidth, frameHeight);

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(new Color(28, 36, 32));
        setDoubleBuffered(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        enableEvents(AWTEvent.KEY_EVENT_MASK);
    }

    /*
     * start()
     *
     * - Starts the game loop thread if not already running.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        gameThread = new Thread(this, "engkanto-game-loop");
        gameThread.start();
        requestFocusInWindow();
    }

    public synchronized void stop() {
        running = false;
        if (gameThread != null) {
            gameThread.interrupt();
            gameThread = null;
        }
    }

    /*
     * processKeyEvent(KeyEvent e)
     *
     * - Routes key events to chat handling and keyboard input.
     */
    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            handleChatKey(e);
        }
        if (!chatFocused) {
            keyboardInput.dispatch(e);
        }
        super.processKeyEvent(e);
    }

    /*
     * run()
     *
     * - Main game loop using a fixed-timestep accumulator at 60 updates per second.
     * - Calls update() for each accumulated tick and repaints the panel each iteration.
     */
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

    /*
     * update(double deltaSeconds)
     *
     * - In network mode, sends input to the server and updates remote player visuals.
     * - In offline mode, updates the player and dummy, then resolves attacks, projectiles, and vines.
     */
    private void update(double deltaSeconds) {
        if (isNetworkMode()) {
            if (!networkClient.isGameStarted()) {
                requestLobbyReturn();
                return;
            }
            GameStateSnapshot state = networkClient.getLatestState();
            if (state == null || !state.gameOver) {
                networkClient.sendInput(keyboardInput.consumeNetworkSnapshot(++inputSequence));
            }
            emitNetworkAudio(state);
            remotePlayerRenderer.update(deltaSeconds, state);
            tickChatVisibility(deltaSeconds);
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
        resolveVineRoots();
    }

    private void requestLobbyReturn() {
        if (onLobbyReturn == null || lobbyReturnRequested) {
            return;
        }
        lobbyReturnRequested = true;
        SwingUtilities.invokeLater(onLobbyReturn);
    }

    /*
     * tickChatVisibility(double deltaSeconds)
     *
     * - Fades out the chat box over time and resets the timer when new messages arrive.
     */
    private void tickChatVisibility(double deltaSeconds) {
        if (chatVisibleTimer > 0.0) {
            chatVisibleTimer = Math.max(0.0, chatVisibleTimer - deltaSeconds);
        }
        List<NetworkClient.ChatMessage> messages = networkClient.getChatMessages();
        if (messages.size() > lastSeenMessageCount) {
            lastSeenMessageCount = messages.size();
            chatVisibleTimer = CHAT_VISIBLE_SECONDS;
        }
    }

    /*
     * handleChatKey(KeyEvent e)
     *
     * - Handles Enter to toggle chat focus and send messages.
     * - Handles Escape to cancel, Backspace to delete, and printable keys to type.
     */
    private void handleChatKey(KeyEvent e) {
        if (!isNetworkMode()) return;

        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (chatFocused) {
                String text = chatInput.toString().trim();
                if (!text.isEmpty()) {
                    networkClient.sendChat(text);
                    chatInput.setLength(0);
                }
                chatFocused = false;
            } else {
                chatFocused = true;
                chatVisibleTimer = CHAT_VISIBLE_SECONDS;
            }
            return;
        }

        if (!chatFocused) return;

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            chatFocused = false;
            chatInput.setLength(0);
        } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (chatInput.length() > 0) {
                chatInput.deleteCharAt(chatInput.length() - 1);
            }
        } else if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED && !e.isActionKey()) {
            if (chatInput.length() < 80) {
                chatInput.append(e.getKeyChar());
            }
        }
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

    /*
     * resolvePlayerAttacks()
     *
     * - Checks if the player's current melee attack overlaps the test dummy.
     * - Uses a one-hit-per-attack guard to prevent multi-frame damage.
     * - Plays the hit sound effect when damage is applied.
     */
    private void resolvePlayerAttacks() {
        if (player.isDead() || dummy.getHealthComponent().isDead() || dummy.isInvulnerable()) {
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
            if (directAttackHitApplied) {
                audioManager.playSound(AudioCue.PLAYER_HURT);
            }
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

    /*
     * resolveVineRoots()
     *
     * - Checks each active vine for overlap with the dummy and applies root if it hits.
     */
    private void resolveVineRoots() {
        for (EngkantoCharacter.Vine vine : player.getActiveCharacterVines()) {
            if (!vine.isActive() || !vine.canRoot()) {
                continue;
            }
            if (dummy.getHealthComponent().isDead()) {
                continue;
            }
            if (vine.overlaps(dummy.getX(), dummy.getY(), TestDummy.HEIGHT)) {
                vine.markRootApplied();
                dummy.applyRoot(vine.getRootDuration());
            }
        }
    }

    /*
     * resolveProjectileHits()
     *
     * - Iterates all active projectiles and checks for overlap with the dummy.
     * - Removes projectiles immediately on hit using Iterator, matching server behavior.
     */
    private void resolveProjectileHits() {
        Iterator<Projectile> iter = player.getActiveCharacterProjectiles().iterator();
        while (iter.hasNext()) {
            Projectile projectile = iter.next();
            if (!projectile.isActive()) {
                iter.remove();
                continue;
            }
            if (dummy.getHealthComponent().isDead() || dummy.isInvulnerable()) {
                continue;
            }
            boolean overlaps = dummy.overlapsHitbox(
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getX() + projectile.getDrawSize(),
                    projectile.getY() + projectile.getDrawSize()
            );
            if (overlaps) {
                projectile.hit(dummy.getHealthComponent());
                if (!projectile.isActive()) {
                    audioManager.playSound(AudioCue.PLAYER_HURT);
                    iter.remove();
                }
            }
        }
    }

    /*
     * paintComponent(Graphics graphics)
     *
     * - Draws the world background and platforms for both modes.
     * - In network mode, renders remote players, effects, HUD, timer, leaderboard, and chat.
     * - In offline mode, renders the local player, dummy, vine overlay, health bar, and ability UI.
     */
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
                if (state != null) {
                    drawNetworkTimer(graphics2D, state);
                }
                PlayerSnapshot localPlayer = findLocalPlayer(state);
                if (localPlayer != null) {
                    drawNetworkHud(graphics2D, localPlayer);
                    drawNetworkAbilityUI(graphics2D, localPlayer);
                }
                if (state != null && state.gameOver) {
                    drawResultsOverlay(graphics2D, state);
                } else if (state != null && keyboardInput.isTabPressed()) {
                    drawLeaderboard(graphics2D, state);
                } else {
                    drawTabHint(graphics2D);
                    drawChat(graphics2D);
                }
            } else {
                player.draw(graphics2D);
                dummy.draw(graphics2D);
                if (dummy.isRooted()) {
                    Composite prevComposite = graphics2D.getComposite();
                    graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                    int vineX = (int) dummy.getX() + TestDummy.WIDTH / 2 - TestDummy.HEIGHT / 2;
                    int vineY = (int) dummy.getY();
                    graphics2D.drawImage(vineOverlayImage, vineX, vineY,
                            TestDummy.HEIGHT, TestDummy.HEIGHT, null);
                    graphics2D.setComposite(prevComposite);
                }
                healthUI.draw(graphics2D);
                abilityUI.draw(graphics2D);
                debugRenderer.drawHud(graphics2D);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    /*
     * drawChat(Graphics2D g)
     *
     * - Draws the chat message history and input box with fade-out transparency.
     * - Shows recent messages in the chat window and a text cursor when focused.
     * - Only visible when the chat is focused or the visibility timer is active.
     */
    private void drawChat(Graphics2D g) {
        boolean visible = chatFocused || chatVisibleTimer > 0.0;
        if (!visible) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float alpha = chatFocused ? 1.0f : (float) Math.min(1.0, chatVisibleTimer);
        Composite original = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(CHAT_X, CHAT_Y, CHAT_WIDTH, CHAT_HEIGHT, 8, 8);

        List<NetworkClient.ChatMessage> messages = networkClient.getChatMessages();
        int start = Math.max(0, messages.size() - CHAT_MAX_MESSAGES);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int lineHeight = 22;
        int textY = CHAT_Y + 18;
        for (int i = start; i < messages.size(); i++) {
            NetworkClient.ChatMessage msg = messages.get(i);
            boolean isLocal = msg.senderId == networkClient.getLocalPlayerId();
            g.setColor(isLocal ? CHAT_GOLD : Color.WHITE);
            g.drawString(msg.toString(), CHAT_X + 8, textY);
            textY += lineHeight;
        }

        int inputY = CHAT_Y + CHAT_HEIGHT + 4;
        g.setColor(new Color(0, 0, 0, chatFocused ? 200 : 140));
        g.fillRoundRect(CHAT_X, inputY, CHAT_WIDTH, CHAT_INPUT_HEIGHT, 6, 6);
        g.setColor(chatFocused ? CHAT_GOLD : new Color(120, 120, 120));
        g.drawRoundRect(CHAT_X, inputY, CHAT_WIDTH, CHAT_INPUT_HEIGHT, 6, 6);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (chatFocused) {
            g.setColor(Color.WHITE);
            g.drawString(chatInput.toString() + "|", CHAT_X + 8, inputY + 18);
        } else {
            g.setColor(new Color(140, 140, 140));
            g.drawString("Press Enter to chat", CHAT_X + 8, inputY + 18);
        }

        g.setComposite(original);
    }

    private boolean isNetworkMode() {
        return networkClient != null && networkClient.isConnected();
    }

    /*
     * drawNetworkPlayers(Graphics2D graphics, GameStateSnapshot state)
     *
     * - Renders all players from the latest server snapshot using RemotePlayerRenderer.
     */
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

    /*
     * findLocalPlayer(GameStateSnapshot state)
     *
     * - Finds this client's own player in the server snapshot by matching IDs.
     */
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

    /*
     * drawNetworkTimer(Graphics2D graphics, GameStateSnapshot state)
     *
     * - Draws the centered match countdown timer, turning red in the last 10 seconds.
     */
    private void drawNetworkTimer(Graphics2D graphics, GameStateSnapshot state) {
        String timeText = formatTimer(state.secondsRemaining);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        FontMetrics fm = graphics.getFontMetrics();
        int boxW = fm.stringWidth(timeText) + 42;
        int boxH = 38;
        int boxX = (GameConfig.SCREEN_WIDTH - boxW) / 2;
        int boxY = 12;

        Object prevAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        graphics.setColor(state.secondsRemaining <= 10.0 ? new Color(255, 120, 100) : new Color(245, 232, 184));
        graphics.drawString(timeText, boxX + (boxW - fm.stringWidth(timeText)) / 2, boxY + 27);

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    /*
     * drawNetworkHud(Graphics2D graphics, PlayerSnapshot localPlayer)
     *
     * - Draws the player name label and color-coded health bar in the top-left corner.
     * - Health bar color changes based on percentage: green, yellow, red, or grey when dead.
     */
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

    /*
     * drawNetworkAbilityUI(Graphics2D graphics, PlayerSnapshot localPlayer)
     *
     * - Draws the four ability key indicators (J, K, E, L) centered at the bottom of the screen.
     * - Each key shows its cooldown state using drawAbilityKey().
     */
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

    /*
     * drawAbilityKey(Graphics2D graphics, int x, int y, String label, int keySize, double remaining, double duration)
     *
     * - Draws a single ability key with its label.
     * - Shows a darkened overlay and fill-up animation with a timer when on cooldown.
     * - Dims the key label color while the ability is unavailable.
     */
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

    /*
     * drawTabHint(Graphics2D graphics)
     *
     * - Draws a small "TAB — Leaderboard" hint at the bottom of the screen.
     */
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

    /*
     * drawLeaderboard(Graphics2D graphics, GameStateSnapshot state)
     *
     * - Draws a fullscreen overlay with a centered leaderboard panel.
     * - Sorts players by kills and displays rank, name, character, and kill count.
     * - Highlights the local player's row.
     */
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
            graphics.setColor(new Color(83, 218, 112));
            graphics.drawString(String.valueOf(p.kills), colKills, rowY);
        }

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    /*
     * drawResultsOverlay(Graphics2D graphics, GameStateSnapshot state)
     *
     * - Draws the game-over results screen with a centered panel.
     * - Shows the winner at the top and all players sorted by kills.
     * - Highlights the 1st place row in gold and the local player's row in blue.
     */
    private void drawResultsOverlay(Graphics2D graphics, GameStateSnapshot state) {
        Object prevAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(new Color(0, 0, 0, 190));
        graphics.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        List<PlayerSnapshot> sorted = sortedByKills(state);
        int panelW = 560;
        int headerH = 92;
        int rowH = 42;
        int panelH = headerH + sorted.size() * rowH + 34;
        int panelX = (GameConfig.SCREEN_WIDTH - panelW) / 2;
        int panelY = (GameConfig.SCREEN_HEIGHT - panelH) / 2;

        graphics.setColor(new Color(30, 38, 34, 245));
        graphics.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        graphics.setColor(new Color(218, 186, 104));
        graphics.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        graphics.drawRoundRect(panelX + 1, panelY + 1, panelW - 2, panelH - 2, 16, 16);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        graphics.setColor(new Color(245, 232, 184));
        FontMetrics titleFm = graphics.getFontMetrics();
        String title = "RESULTS";
        graphics.drawString(title, panelX + (panelW - titleFm.stringWidth(title)) / 2, panelY + 42);

        String firstText = sorted.isEmpty() ? "No players" : "1st: Player " + sorted.get(0).id;
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        FontMetrics firstFm = graphics.getFontMetrics();
        graphics.setColor(Color.WHITE);
        graphics.drawString(firstText, panelX + (panelW - firstFm.stringWidth(firstText)) / 2, panelY + 70);

        int colRank = panelX + 30;
        int colName = panelX + 86;
        int colChar = panelX + 260;
        int colKills = panelX + 470;
        int rowTop = panelY + headerH;

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        graphics.setColor(new Color(180, 180, 180));
        graphics.drawString("RANK", colRank, rowTop - 10);
        graphics.drawString("PLAYER", colName, rowTop - 10);
        graphics.drawString("CHARACTER", colChar, rowTop - 10);
        graphics.drawString("KILLS", colKills, rowTop - 10);

        int localId = networkClient.getLocalPlayerId();
        for (int i = 0; i < sorted.size(); i++) {
            PlayerSnapshot player = sorted.get(i);
            int y = rowTop + i * rowH;
            boolean isLocal = player.id == localId;

            if (i == 0) {
                graphics.setColor(new Color(218, 186, 104, 55));
                graphics.fillRoundRect(panelX + 14, y - 6, panelW - 28, rowH - 4, 8, 8);
            } else if (isLocal) {
                graphics.setColor(new Color(145, 231, 255, 35));
                graphics.fillRoundRect(panelX + 14, y - 6, panelW - 28, rowH - 4, 8, 8);
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            graphics.setColor(i == 0 ? new Color(245, 232, 184) : (isLocal ? new Color(145, 231, 255) : Color.WHITE));
            graphics.drawString(rankLabel(i + 1), colRank, y + 20);
            graphics.drawString("Player " + player.id, colName, y + 20);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.drawString(player.characterName, colChar, y + 20);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.setColor(new Color(83, 218, 112));
            graphics.drawString(String.valueOf(player.kills), colKills, y + 20);
        }

        if (prevAA != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAA);
        }
    }

    private List<PlayerSnapshot> sortedByKills(GameStateSnapshot state) {
        List<PlayerSnapshot> sorted = new ArrayList<>(state.players);
        sorted.sort(Comparator.comparingInt((PlayerSnapshot p) -> p.kills).reversed()
                .thenComparingInt(p -> p.id));
        return sorted;
    }

    private String rankLabel(int rank) {
        return switch (rank) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> rank + "th";
        };
    }

    private String formatTimer(double secondsRemaining) {
        int totalSeconds = (int) Math.ceil(Math.max(0.0, secondsRemaining));
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /*
     * drawWorld(Graphics2D graphics)
     *
     * - Draws the background color and tile grid lines.
     */
    private void drawWorld(Graphics2D graphics) {
        graphics.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
    }

    private void drawPlatforms(Graphics2D graphics) {
        for (Platform platform : platforms) {
            platform.draw(graphics);
        }
    }

    /*
     * createPlatforms()
     *
     * - Creates the client-side platform layout with one ground and six floating platforms.
     * - Must stay in sync with the server-side layout in GameServer.
     */
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
