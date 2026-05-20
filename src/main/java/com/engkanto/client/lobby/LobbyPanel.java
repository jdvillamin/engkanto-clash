/*
 * Key Objects / Libraries Used
 *
 * JPanel — Base Swing component; LobbyPanel overrides paintComponent() to draw the lobby UI.
 * Graphics2D — Draws shapes, images, and text for the character cards, player slots, and chat.
 * KeyEvent / KeyAdapter — Captures keyboard input for the chat text field.
 * MouseEvent / MouseAdapter — Detects clicks on character cards and the ready button.
 * BufferedImage — Stores character portrait sprites extracted from sprite sheets.
 * NetworkClient — Sends character selection, ready, and chat messages to the server.
 * LobbySnapshot — Server state containing all players' character choices and ready status.
 * SwingUtilities — Runs the game-start callback on the EDT when the server starts the match.
 */

package com.engkanto.client.lobby;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.engkanto.client.game.GameConfig;
import com.engkanto.client.net.NetworkClient;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;
import com.engkanto.common.model.LobbyPlayerSnapshot;
import com.engkanto.common.model.LobbySnapshot;

public final class LobbyPanel extends JPanel implements Runnable {
    private static final String[] CHARACTER_NAMES = {"Tikbalang", "Kapre", "Aswang", "Engkanto"};
    private static final String[] SPRITE_PATHS = {
            "/assets/sprites/tikbalang.png",
            "/assets/sprites/kapre.png",
            "/assets/sprites/aswang.png",
            "/assets/sprites/engkanto.png"
    };

    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 190;
    private static final int CARD_GAP = 30;
    private static final int CARDS_START_Y = 140;
    private static final int CARDS_START_X = (GameConfig.SCREEN_WIDTH - (4 * CARD_WIDTH + 3 * CARD_GAP)) / 2;

    private static final int SLOT_WIDTH = 500;
    private static final int SLOT_HEIGHT = 36;
    private static final int SLOT_GAP = 8;
    private static final int SLOTS_START_Y = 390;
    private static final int SLOTS_START_X = (GameConfig.SCREEN_WIDTH - SLOT_WIDTH) / 2;

    private static final int BTN_WIDTH = 220;
    private static final int BTN_HEIGHT = 50;
    private static final int BTN_X = (GameConfig.SCREEN_WIDTH - BTN_WIDTH) / 2;
    private static final int BTN_Y = 570;

    private static final int CHAT_X = 20;
    private static final int CHAT_Y = 400;
    private static final int CHAT_WIDTH = 340;
    private static final int CHAT_HEIGHT = 200;
    private static final int CHAT_INPUT_HEIGHT = 30;
    private static final int CHAT_MAX_MESSAGES = 8;

    private static final Color TITLE_COLOR = new Color(245, 232, 184);
    private static final Color CARD_BORDER = new Color(74, 52, 30);
    private static final Color CARD_SELECTED_BORDER = new Color(218, 186, 104);
    private static final Color CARD_BG = new Color(40, 48, 44);
    private static final Color CARD_SELECTED_BG = new Color(55, 68, 60);
    private static final Color CARD_HOVER_BG = new Color(48, 58, 52);
    private static final Color READY_COLOR = new Color(83, 218, 112);
    private static final Color NOT_READY_COLOR = new Color(180, 180, 180);
    private static final Color COUNTDOWN_COLOR = new Color(255, 200, 50);

    private final NetworkClient networkClient;
    private final Runnable onGameStart;
    private final BufferedImage[] characterPortraits;
    private final StringBuilder chatInput = new StringBuilder();

    private int selectedCharacterIndex;
    private boolean localReady;
    private int hoveredCard = -1;
    private boolean hoveringReadyButton;
    private Thread lobbyThread;
    private volatile boolean running;

    /*
     * LobbyPanel — Sets up the panel, loads portraits, and registers mouse/key listeners.
     */
    public LobbyPanel(NetworkClient networkClient, Runnable onGameStart) {
        this.networkClient = networkClient;
        this.onGameStart = onGameStart;
        this.characterPortraits = loadCharacterPortraits();

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(new Color(28, 36, 32));
        setDoubleBuffered(true);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleChatKey(e);
            }
        });
    }

    /* start — Starts the lobby polling thread. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        lobbyThread = new Thread(this, "engkanto-lobby-loop");
        lobbyThread.start();
        requestFocusInWindow();
    }

    public void stop() {
        running = false;
    }

    /* run — Polls for game start and repaints the lobby at ~60 fps. */
    @Override
    public void run() {
        while (running) {
            if (networkClient.isGameStarted()) {
                SwingUtilities.invokeLater(onGameStart);
                running = false;
                return;
            }
            repaint();
            try {
                Thread.sleep(16L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    /* paintComponent — Draws background, cards, player slots, ready button, chat, and countdown. */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawBackground(g);
            drawTitle(g);
            drawCharacterCards(g);
            drawPlayerSlots(g);
            drawReadyButton(g);
            drawChat(g);
            drawCountdown(g);

            if (!networkClient.isConnected()) {
                drawDisconnected(g);
            }
        } finally {
            g.dispose();
        }
    }

    /* handleClick — Selects a character card or toggles ready on button click. */
    private void handleClick(int mx, int my) {
        if (!networkClient.isConnected() || networkClient.isGameStarted()) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            int cardX = CARDS_START_X + i * (CARD_WIDTH + CARD_GAP);
            if (mx >= cardX && mx <= cardX + CARD_WIDTH
                    && my >= CARDS_START_Y && my <= CARDS_START_Y + CARD_HEIGHT) {
                if (!localReady) {
                    selectedCharacterIndex = i;
                    networkClient.sendCharacterSelect(i);
                }
                return;
            }
        }

        if (mx >= BTN_X && mx <= BTN_X + BTN_WIDTH
                && my >= BTN_Y && my <= BTN_Y + BTN_HEIGHT) {
            localReady = !localReady;
            networkClient.sendReady();
        }
    }

    /* updateHover — Tracks which card or button the mouse is over for hover effects. */
    private void updateHover(int mx, int my) {
        hoveredCard = -1;
        hoveringReadyButton = false;

        for (int i = 0; i < 4; i++) {
            int cardX = CARDS_START_X + i * (CARD_WIDTH + CARD_GAP);
            if (mx >= cardX && mx <= cardX + CARD_WIDTH
                    && my >= CARDS_START_Y && my <= CARDS_START_Y + CARD_HEIGHT) {
                hoveredCard = i;
                return;
            }
        }

        if (mx >= BTN_X && mx <= BTN_X + BTN_WIDTH
                && my >= BTN_Y && my <= BTN_Y + BTN_HEIGHT) {
            hoveringReadyButton = true;
        }
    }

    /* handleChatKey — Handles Enter to send, Backspace to delete, and typing for chat. */
    private void handleChatKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            String text = chatInput.toString().trim();
            if (!text.isEmpty()) {
                networkClient.sendChat(text);
                chatInput.setLength(0);
            }
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

    /* drawBackground — Fills the background and draws the tile grid. */
    private void drawBackground(Graphics2D g) {
        g.setColor(new Color(42, 92, 76));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(34, 74, 62));
        for (int x = 0; x < getWidth(); x += GameConfig.TILE_SIZE) {
            g.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += GameConfig.TILE_SIZE) {
            g.drawLine(0, y, getWidth(), y);
        }
    }

    /* drawTitle — Draws the game title and subtitle text. */
    private void drawTitle(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        g.setColor(TITLE_COLOR);
        drawCenteredString(g, "ENGKANTO CLASH", 70);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.setColor(new Color(200, 200, 200));
        drawCenteredString(g, "Select your character", 105);
    }

    /* drawCharacterCards — Draws the four character selection cards with portraits and highlight. */
    private void drawCharacterCards(Graphics2D g) {
        for (int i = 0; i < 4; i++) {
            int cardX = CARDS_START_X + i * (CARD_WIDTH + CARD_GAP);
            boolean selected = i == selectedCharacterIndex;
            boolean hovered = i == hoveredCard && !localReady;

            g.setColor(selected ? CARD_SELECTED_BG : (hovered ? CARD_HOVER_BG : CARD_BG));
            g.fillRoundRect(cardX, CARDS_START_Y, CARD_WIDTH, CARD_HEIGHT, 12, 12);

            g.setColor(selected ? CARD_SELECTED_BORDER : CARD_BORDER);
            int borderWidth = selected ? 3 : 2;
            for (int b = 0; b < borderWidth; b++) {
                g.drawRoundRect(cardX + b, CARDS_START_Y + b,
                        CARD_WIDTH - 2 * b, CARD_HEIGHT - 2 * b, 12, 12);
            }

            if (characterPortraits[i] != null) {
                int spriteSize = 110;
                int spriteX = cardX + (CARD_WIDTH - spriteSize) / 2;
                if (i == 0 || i == 3) {
                    spriteX -= 4;
                }
                int spriteY = CARDS_START_Y + 15;
                g.drawImage(characterPortraits[i], spriteX, spriteY, spriteSize, spriteSize, null);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(selected ? TITLE_COLOR : Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            String name = CHARACTER_NAMES[i];
            int nameX = cardX + (CARD_WIDTH - fm.stringWidth(name)) / 2;
            g.drawString(name, nameX, CARDS_START_Y + CARD_HEIGHT - 20);

            if (selected) {
                g.setColor(CARD_SELECTED_BORDER);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                fm = g.getFontMetrics();
                String label = "SELECTED";
                int labelX = cardX + (CARD_WIDTH - fm.stringWidth(label)) / 2;
                g.drawString(label, labelX, CARDS_START_Y + CARD_HEIGHT - 6);
            }
        }
    }

    /* drawPlayerSlots — Draws four player slots showing name, character, and ready status. */
    private void drawPlayerSlots(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(TITLE_COLOR);
        drawCenteredString(g, "PLAYERS", SLOTS_START_Y - 15);

        LobbySnapshot lobbyState = networkClient.getLatestLobbyState();
        int localId = networkClient.getLocalPlayerId();

        for (int i = 0; i < 4; i++) {
            int slotY = SLOTS_START_Y + i * (SLOT_HEIGHT + SLOT_GAP);

            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(SLOTS_START_X, slotY, SLOT_WIDTH, SLOT_HEIGHT, 8, 8);

            LobbyPlayerSnapshot playerInfo = getPlayerAtSlot(lobbyState, i);

            if (playerInfo != null) {
                boolean isLocal = playerInfo.id == localId;

                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.setColor(isLocal ? TITLE_COLOR : Color.WHITE);
                String text = "Player " + playerInfo.id + " — " + playerInfo.characterName;
                if (isLocal) {
                    text += " (You)";
                }
                g.drawString(text, SLOTS_START_X + 15, slotY + 23);

                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                if (playerInfo.ready) {
                    g.setColor(READY_COLOR);
                    g.drawString("READY", SLOTS_START_X + SLOT_WIDTH - 80, slotY + 23);
                } else {
                    g.setColor(NOT_READY_COLOR);
                    g.drawString("NOT READY", SLOTS_START_X + SLOT_WIDTH - 100, slotY + 23);
                }
            } else {
                g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
                g.setColor(new Color(120, 120, 120));
                g.drawString("Waiting for player...", SLOTS_START_X + 15, slotY + 23);
            }
        }
    }

    /* drawReadyButton — Draws the ready/cancel button with hover color change. */
    private void drawReadyButton(Graphics2D g) {
        Color btnColor;
        String btnText;
        if (localReady) {
            btnColor = hoveringReadyButton ? new Color(200, 50, 50) : new Color(178, 34, 34);
            btnText = "CANCEL READY";
        } else {
            btnColor = hoveringReadyButton ? new Color(60, 179, 113) : new Color(46, 139, 87);
            btnText = "READY UP";
        }

        g.setColor(btnColor);
        g.fillRoundRect(BTN_X, BTN_Y, BTN_WIDTH, BTN_HEIGHT, 12, 12);

        g.setColor(new Color(255, 255, 255, 60));
        g.drawRoundRect(BTN_X, BTN_Y, BTN_WIDTH, BTN_HEIGHT, 12, 12);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int textX = BTN_X + (BTN_WIDTH - fm.stringWidth(btnText)) / 2;
        int textY = BTN_Y + (BTN_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(btnText, textX, textY);
    }

    /* drawChat — Draws the chat message history and input box. */
    private void drawChat(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(CHAT_X, CHAT_Y, CHAT_WIDTH, CHAT_HEIGHT, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.setColor(new Color(180, 180, 180));
        g.drawString("CHAT", CHAT_X + 8, CHAT_Y - 4);

        List<NetworkClient.ChatMessage> messages = networkClient.getChatMessages();
        int start = Math.max(0, messages.size() - CHAT_MAX_MESSAGES);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int lineHeight = 22;
        int textY = CHAT_Y + 18;
        for (int i = start; i < messages.size(); i++) {
            NetworkClient.ChatMessage msg = messages.get(i);
            boolean isLocal = msg.senderId == networkClient.getLocalPlayerId();
            g.setColor(isLocal ? TITLE_COLOR : Color.WHITE);
            g.drawString(msg.toString(), CHAT_X + 8, textY);
            textY += lineHeight;
        }

        int inputY = CHAT_Y + CHAT_HEIGHT + 4;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(CHAT_X, inputY, CHAT_WIDTH, CHAT_INPUT_HEIGHT, 6, 6);
        g.setColor(CARD_SELECTED_BORDER);
        g.drawRoundRect(CHAT_X, inputY, CHAT_WIDTH, CHAT_INPUT_HEIGHT, 6, 6);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(Color.WHITE);
        g.drawString(chatInput.toString() + "|", CHAT_X + 8, inputY + 20);
    }

    /* drawCountdown — Shows the countdown timer text when all players are ready. */
    private void drawCountdown(Graphics2D g) {
        LobbySnapshot lobbyState = networkClient.getLatestLobbyState();
        if (lobbyState == null || lobbyState.countdownSeconds <= 0) {
            return;
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.setColor(COUNTDOWN_COLOR);
        drawCenteredString(g, "Game starting in " + lobbyState.countdownSeconds + "...", 660);
    }

    /* drawDisconnected — Draws a fullscreen overlay with a disconnection message. */
    private void drawDisconnected(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.setColor(new Color(230, 70, 70));
        drawCenteredString(g, "Disconnected from server", getHeight() / 2);
    }

    private void drawCenteredString(Graphics2D g, String text, int y) {
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private LobbyPlayerSnapshot getPlayerAtSlot(LobbySnapshot lobbyState, int slotIndex) {
        if (lobbyState == null || slotIndex >= lobbyState.players.size()) {
            return null;
        }
        return lobbyState.players.get(slotIndex);
    }

    /* loadCharacterPortraits — Loads the first idle frame from each character's sprite sheet. */
    private BufferedImage[] loadCharacterPortraits() {
        BufferedImage[] portraits = new BufferedImage[4];
        for (int i = 0; i < 4; i++) {
            SpriteSheet sheet = new SpriteSheet(AssetLoader.loadImage(SPRITE_PATHS[i]), 0, 0, 4, 7);
            portraits[i] = sheet.getFrame(0, 0);
        }
        return portraits;
    }
}