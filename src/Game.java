
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import javax.sound.midi.*;

import game2D.*;

/**
 * Main Game Class for CSCU9N6 2D Platformer
 * Extends GameCore to implement a complete tile-based platformer featuring:
 * - Parallax scrolling backgrounds
 * - State-based character animations
 * - Advanced bounding-box collision detection
 * - Async sound effects and custom audio filtering
 */

// Student ID: 3366206

@SuppressWarnings("serial")

public class Game extends GameCore {
    /**
     * Dynamic Path Resolution Block:
     * When running via Command Line, the working directory is usually /src.
     * When running via Eclipse IDE, the working directory is usually the project root.
     * This static block detects the environment and sets a BASE_PATH prefix string 
     * so that asset loading never throws a FileNotFoundException regardless of how the game is launched.
     */
    public static String BASE_PATH = "";
    static {
        if (new java.io.File("../assets").exists()) {
            BASE_PATH = "../";
        }
    }

    // Useful game constants
    static int screenWidth = 512;
    static int screenHeight = 384;

    // Game state flags
    boolean debug = false;

    // Game resources
    Animation idleAnim, runAnim, jumpAnim, fallAnim;
    Animation appleAnim, collectAnim, flagAnim;
    Animation enemyRunAnim;
    Animation strawberryAnim; // power-up

    Player player = null;
    Sprite checkpoint = null;
    ArrayList<Sprite> fruits = new ArrayList<Sprite>();
    ArrayList<Sprite> powerups = new ArrayList<Sprite>();   // strawberry extra lives
    ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    ArrayList<Particle> particles = new ArrayList<Particle>();

    HashSet<Sprite> collectedFruits = new HashSet<Sprite>();
    HashSet<Sprite> collectedPowerups = new HashSet<Sprite>();

    TileMap tmap = new TileMap();

    int currentLevel = 1;

    // Game state: 0=MENU, 1=PLAYING, 2=GAME_OVER, 3=WIN
    static final int STATE_MENU = 0;
    static final int STATE_PLAYING = 1;
    static final int STATE_GAME_OVER = 2;
    static final int STATE_WIN = 3;
    int gameState = STATE_MENU;

    // Player lives
    int lives = 3;
    boolean playerHit = false;
    long hitTimer = 0;
    static final long HIT_INVINCIBILITY_MS = 1500;

    // Screen shake
    int shakeTimer = 0;
    static final int SHAKE_DURATION = 400;
    static final int SHAKE_MAG = 5;
    Random random = new Random();

    // Parallax background
    Image bgImage = null;
    Image[] bgImages = new Image[4]; // one per level

    // Scoring
    long total;
    int highScore = 0;
    static final String SAVE_FILE = BASE_PATH + "saves/highscore.txt";

    /**
     * The obligatory main method that creates
     * an instance of our class and starts it running
     * 
     * @param args The list of parameters this program might use (ignored)
     */
    public static void main(String[] args) {

        Game gct = new Game();
        gct.init();
        // Start in windowed mode with the given screen height and width
        gct.run(false, screenWidth, screenHeight);
    }

    /**
     * Initialise the class, e.g. set up variables, load images,
     * create animations, register event handlers.
     */
    public void init() {
        setSize(screenWidth, screenHeight);
        setVisible(true);

        // Load per-level backgrounds
        bgImages[0] = loadImage(BASE_PATH + "assets/images/Background/Blue.png");
        bgImages[1] = loadImage(BASE_PATH + "assets/images/Background/Blue.png");   // level 1
        bgImages[2] = loadImage(BASE_PATH + "assets/images/Background/Green.png");  // level 2
        bgImages[3] = loadImage(BASE_PATH + "assets/images/Background/Purple.png"); // level 3
        bgImage = bgImages[1];

        // Load player animations from Pixel Adventure sprite sheets
        idleAnim = new Animation();
        idleAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Ninja Frog/Idle (32x32).png", 11, 1, 60);

        runAnim = new Animation();
        runAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Ninja Frog/Run (32x32).png", 12, 1, 60);

        jumpAnim = new Animation();
        jumpAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Ninja Frog/Jump (32x32).png", 1, 1, 200);

        fallAnim = new Animation();
        fallAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Ninja Frog/Fall (32x32).png", 1, 1, 200);

        appleAnim = new Animation();
        appleAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Apple.png", 17, 1, 60);

        collectAnim = new Animation();
        collectAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Collected.png", 6, 1, 60);
        collectAnim.setLoop(false);

        flagAnim = new Animation();
        flagAnim.loadAnimationFromSheet(
                BASE_PATH + "assets/images/Items/Checkpoints/Checkpoint/Checkpoint (Flag Idle)(64x64).png", 10, 1, 100);

        // Enemy animation - use Pink Man as the patrol enemy
        enemyRunAnim = new Animation();
        enemyRunAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Pink Man/Run (32x32).png", 12, 1, 60);

        // Strawberry power-up animation
        strawberryAnim = new Animation();
        strawberryAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Strawberry.png", 17, 1, 60);

        // Initialise the player with the animations
        player = new Player(idleAnim, runAnim, jumpAnim, fallAnim);

        // Debug: verify animation loaded
        if (player.getImage() != null)
            System.out.println("Player sprite loaded: " + player.getWidth() + "x" + player.getHeight());
        else
            System.out.println("WARNING: Player sprite image is NULL!");

        // Load high score from disk
        loadHighScore();

        // Start background music (MIDI)
        try {
            Sequence sequence = MidiSystem.getSequence(new File(BASE_PATH + "assets/audio/bgm.mid"));
            Sequencer sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
        } catch (Exception e) {
            System.err.println("Could not load background music: " + e.getMessage());
        }

        loadLevel(1);
    }

    // ── SOUND ──────────────────────────────────────────────────────────────────
    /** Fire and forget sound playback on its own thread. */
    private void playSound(String path) {
        try {
            Sound snd = new Sound(path);
            snd.start();
        } catch (Exception e) {
            // Silently ignore any missing audio; it won't crash the game.
        }
    }

    /** Plays a sound using our custom byte-array Echo filter. */
    private void playFilteredSound(String path) {
        try {
            FilteredSound fsnd = new FilteredSound(path);
            fsnd.start();
        } catch (Exception e) {
            // Silently ignore
        }
    }

    // ── HIGH SCORE ──────────────────────────────────────────────────────────────
    private void loadHighScore() {
        try (java.util.Scanner sc = new java.util.Scanner(new File(SAVE_FILE))) {
            if (sc.hasNextInt())
                highScore = sc.nextInt();
        } catch (Exception ex) {
            highScore = 0;
        }
    }

    private void saveHighScore() {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new FileWriter(SAVE_FILE))) {
            pw.println(highScore);
        } catch (Exception ex) { /* ignore */ }
    }

    // ── PARTICLES ─────────────────────────────────────────────────────────────
    /** 
     * Spawns a small burst of coloured 2D particles at a specific world coordinate. 
     * The particles calculate their own physics trajectories using basic sine/cosine math
     * to explode outwards in a 360-degree circle.
     * 
     * @param wx The world X coordinate to spawn particles at.
     * @param wy The world Y coordinate to spawn particles at.
     * @param color The RGB color the particles should be drawn as.
     */
    private void spawnFruitParticles(float wx, float wy, Color color) {
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * 2 * i / 8);
            float speed = 0.04f + random.nextFloat() * 0.06f;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed - 0.05f;
            int life = 200 + random.nextInt(200);
            particles.add(new Particle(wx + 8, wy + 8, vx, vy, life, color));
        }
    }

    /**
     * Loads the specified level map and resets the player and level state.
     */
    public void loadLevel(int level) {
        // Level 4 means we have beaten all levels — WIN
        if (level > 3) {
            if (total / 100 > highScore) {
                highScore = (int) (total / 100);
                saveHighScore();
            }
            gameState = STATE_WIN;
            return;
        }
        currentLevel = level;
        // Set background for this level
        bgImage = bgImages[Math.min(level, bgImages.length - 1)];

        String mapFile = "map" + level + ".txt";
        if (!tmap.loadMap(BASE_PATH + "maps", mapFile)) {
            System.out.println("Could not load level " + level + ". Returning to Title or quitting.");
            stop();
            return;
        }
        initialiseGame();
    }

    /**
     * You will probably want to put code to restart a game in
     * a separate method so that you can call it when restarting
     * the game when the player loses.
     */
    public void initialiseGame() {
        // Note: total (score) is NOT reset here so it carries across levels
        fruits.clear();
        powerups.clear();
        collectedFruits.clear();
        collectedPowerups.clear();
        enemies.clear();
        particles.clear();
        checkpoint = null;

        // Spawn in the open space area
        player.setPosition(100, 200);
        player.setVelocity(0, 0);
        player.show();
        playerHit = false;
        hitTimer = 0;
        shakeTimer = 0;

        // Scan the tile map for spawn markers
        for (int r = 0; r < tmap.getMapHeight(); r++) {
            for (int c = 0; c < tmap.getMapWidth(); c++) {
                char ch = tmap.getTileChar(c, r);
                if (ch == 'a') {
                    // Each fruit needs its OWN Animation to avoid shared state
                    Animation fruitAnim = new Animation();
                    fruitAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Apple.png", 17, 1, 60);
                    Sprite apple = new Sprite(fruitAnim);
                    // Center the 32x32 fruit on the 16x16 tile
                    apple.setPosition(c * tmap.getTileWidth() - 8, r * tmap.getTileHeight() - 8);
                    fruits.add(apple);
                    tmap.setTileChar('.', c, r); // remove from map
                } else if (ch == 's') {
                    // Strawberry power-up — grants +1 life
                    Animation sa = new Animation();
                    sa.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Strawberry.png", 17, 1, 60);
                    Sprite berry = new Sprite(sa);
                    berry.setPosition(c * tmap.getTileWidth() - 8, r * tmap.getTileHeight() - 8);
                    powerups.add(berry);
                    tmap.setTileChar('.', c, r);
                } else if (ch == 'F') {
                    checkpoint = new Sprite(flagAnim);
                    // Bottom-align the 64x64 flag to the tile
                    checkpoint.setPosition(c * tmap.getTileWidth(), r * tmap.getTileHeight() - (64 - 16));
                    tmap.setTileChar('.', c, r);
                } else if (ch == 'e') {
                    // Create an enemy with its own animation instance
                    Animation ea = new Animation();
                    ea.loadAnimationFromSheet(BASE_PATH + "assets/images/Main Characters/Pink Man/Run (32x32).png", 12, 1, 60);
                    float ex = c * tmap.getTileWidth();
                    float ey = r * tmap.getTileHeight();
                    float pMin = Math.max(0, ex - 10 * tmap.getTileWidth());
                    float pMax = Math.min(tmap.getPixelWidth(), ex + 10 * tmap.getTileWidth());

                    Enemy enemy = new Enemy(ea, ex, ey, pMin, pMax);
                    enemies.add(enemy);
                    tmap.setTileChar('.', c, r);
                }
            }
        }
    }

    // ── DRAW ───────────────────────────────────────────────────────────────────
    /**
     * Core Render Loop: Draws the current state of the game every frame.
     * This method handles the camera logic by calculating 'xo' and 'yo' (X and Y offsets).
     * Instead of moving the player around the screen, the player stays relatively centered 
     * while the entire game world (tilemaps, sprites, backgrounds) is inversely offset 
     * and drawn "underneath" them to create a scrolling camera effect.
     * 
     * @param g The graphics context to draw to.
     */
    public void draw(Graphics2D g) {

        // ── MENU SCREEN ──────────────────────────────────────────
        if (gameState == STATE_MENU) {
            g.setColor(new Color(20, 20, 40));
            g.fillRect(0, 0, screenWidth, screenHeight);
            drawParallaxBackground(g, 0, 0);

            g.setFont(new Font("Monospaced", Font.BOLD, 42));
            String title = "Pixel Adventure";
            int tw = g.getFontMetrics().stringWidth(title);
            g.setColor(Color.white);
            g.drawString(title, (screenWidth - tw) / 2, screenHeight / 2 - 50);

            g.setFont(new Font("Monospaced", Font.BOLD, 22));
            String prompt = "Press ENTER to Start";
            int pw = g.getFontMetrics().stringWidth(prompt);
            g.setColor(new Color(255, 255, 100));
            g.drawString(prompt, (screenWidth - pw) / 2, screenHeight / 2 + 10);

            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            String controls = "Arrow Keys: Move   Space: Jump";
            int cw = g.getFontMetrics().stringWidth(controls);
            g.setColor(Color.white);
            g.drawString(controls, (screenWidth - cw) / 2, screenHeight / 2 + 55);

            // Show high score on menu
            if (highScore > 0) {
                g.setFont(new Font("Monospaced", Font.BOLD, 16));
                String hs = "Best: " + highScore;
                int hsw = g.getFontMetrics().stringWidth(hs);
                // Draw drop shadow for visibility
                g.setColor(Color.black);
                g.drawString(hs, (screenWidth - hsw) / 2 + 2, screenHeight / 2 + 87);
                g.setColor(Color.white);
                g.drawString(hs, (screenWidth - hsw) / 2, screenHeight / 2 + 85);
            }
            return;
        }

        // ── GAME OVER SCREEN ──────────────────────────────────────
        if (gameState == STATE_GAME_OVER) {
            g.setColor(new Color(20, 20, 40));
            g.fillRect(0, 0, screenWidth, screenHeight);

            g.setFont(new Font("Monospaced", Font.BOLD, 48));
            g.setColor(new Color(220, 60, 60));
            String go = "GAME OVER";
            int gw = g.getFontMetrics().stringWidth(go);
            g.drawString(go, (screenWidth - gw) / 2, screenHeight / 2 - 50);

            g.setFont(new Font("Monospaced", Font.PLAIN, 22));
            g.setColor(Color.white);
            String scoreMsg = "Score: " + (total / 100);
            int sw = g.getFontMetrics().stringWidth(scoreMsg);
            g.drawString(scoreMsg, (screenWidth - sw) / 2, screenHeight / 2);

            g.setFont(new Font("Monospaced", Font.PLAIN, 18));
            g.setColor(new Color(255, 255, 150));
            String hsMsg = "Best: " + highScore;
            int hsw = g.getFontMetrics().stringWidth(hsMsg);
            g.drawString(hsMsg, (screenWidth - hsw) / 2, screenHeight / 2 + 30);

            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(new Color(180, 180, 180));
            String restart = "Press R to Restart  |  Q to Quit";
            int rw = g.getFontMetrics().stringWidth(restart);
            g.drawString(restart, (screenWidth - rw) / 2, screenHeight / 2 + 65);
            return;
        }

        // ── WIN SCREEN ────────────────────────────────────────────
        if (gameState == STATE_WIN) {
            g.setColor(new Color(20, 40, 20));
            g.fillRect(0, 0, screenWidth, screenHeight);
            drawParallaxBackground(g, 0, 0);

            g.setFont(new Font("Monospaced", Font.BOLD, 48));
            g.setColor(new Color(100, 255, 100));
            String win = "YOU WIN!";
            int winW = g.getFontMetrics().stringWidth(win);
            g.drawString(win, (screenWidth - winW) / 2, screenHeight / 2 - 50);

            g.setFont(new Font("Monospaced", Font.PLAIN, 22));
            g.setColor(Color.white);
            String scoreMsg = "Score: " + (total / 100);
            int sw = g.getFontMetrics().stringWidth(scoreMsg);
            g.drawString(scoreMsg, (screenWidth - sw) / 2, screenHeight / 2);

            g.setFont(new Font("Monospaced", Font.PLAIN, 18));
            g.setColor(new Color(255, 255, 150));
            String hsMsg = "Best: " + highScore;
            int hsw = g.getFontMetrics().stringWidth(hsMsg);
            g.drawString(hsMsg, (screenWidth - hsw) / 2, screenHeight / 2 + 30);

            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(new Color(180, 255, 180));
            String playAgain = "Press R to Play Again  |  Q to Quit";
            int paw = g.getFontMetrics().stringWidth(playAgain);
            g.drawString(playAgain, (screenWidth - paw) / 2, screenHeight / 2 + 65);
            return;
        }

        // ── PLAYING ──────────────────────────────────────────────
        int xo = -(int) player.getX() + screenWidth / 4;
        int yo = -(int) player.getY() + screenHeight / 2;

        // Clamp camera so it doesn't go beyond map edges
        int maxXo = 0;
        int minXo = -(tmap.getPixelWidth() - screenWidth);
        int maxYo = 0;
        int minYo = -(tmap.getPixelHeight() - screenHeight);

        if (xo > maxXo) xo = maxXo;
        if (xo < minXo) xo = minXo;
        if (yo > maxYo) yo = maxYo;
        if (yo < minYo) yo = minYo;

        // ── SCREEN SHAKE ─────────────────────────────────────────
        if (shakeTimer > 0) {
            xo += (random.nextInt(SHAKE_MAG * 2 + 1) - SHAKE_MAG);
            yo += (random.nextInt(SHAKE_MAG * 2 + 1) - SHAKE_MAG);
        }

        drawParallaxBackground(g, xo, yo);
        tmap.draw(g, xo, yo);

        // Draw checkpoint
        if (checkpoint != null) {
            checkpoint.setOffsets(xo, yo);
            checkpoint.draw(g);
        }

        // Draw fruits
        for (Sprite f : fruits) {
            f.setOffsets(xo, yo);
            f.draw(g);
        }

        // Draw power-ups (strawberries)
        for (Sprite s : powerups) {
            s.setOffsets(xo, yo);
            s.draw(g);
        }

        // Draw particles
        for (Particle p : particles) {
            p.draw(g, xo, yo);
        }

        // Draw enemies
        for (Enemy e : enemies) {
            e.setOffsets(xo, yo);
            e.draw(g);
        }

        // Draw player (flash when hit)
        boolean showPlayer = !playerHit || (hitTimer / 150) % 2 == 0;
        if (showPlayer) {
            player.setOffsets(xo, yo);
            player.draw(g);
        }

        if (debug) {
            g.setColor(Color.red);
            g.fillRect((int) player.getX() + xo, (int) player.getY() + yo, player.getWidth(), player.getHeight());
        }

        // ── HUD ──────────────────────────────────────────────────
        int textX = getWidth() - 160;

        // Score  (white text + black drop shadow)
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        String msg = String.format("Score: %d", total / 100);
        g.setColor(Color.black);
        g.drawString(msg, textX + 2, 58);
        g.setColor(Color.white);
        g.drawString(msg, textX, 56);

        // High-score line beneath score
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        String hsTxt = "Best: " + highScore;
        g.setColor(Color.black);
        g.drawString(hsTxt, textX + 2, 78);
        g.setColor(Color.white);
        g.drawString(hsTxt, textX, 76);

        // Lives (hearts)
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        for (int i = 0; i < 3; i++) {
            g.setColor(Color.black);
            g.drawString("\u2665", 17 + i * 28, 58);
            g.setColor(i < lives ? new Color(255, 50, 50) : new Color(100, 100, 100));
            g.drawString("\u2665", 15 + i * 28, 56);
        }

        // Level indicator
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        String lvlMsg = "Level " + currentLevel;
        g.setColor(Color.black);
        g.drawString(lvlMsg, textX + 2, 102);
        g.setColor(new Color(255, 255, 150));
        g.drawString(lvlMsg, textX, 100);

        if (debug) {
            tmap.drawBorder(g, xo, yo, Color.black);
            g.setColor(Color.red);
            player.drawBoundingBox(g);
            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(Color.yellow);
            g.drawString(String.format("Player: %.0f,%.0f", player.getX(), player.getY()), getWidth() - 160, 120);
            g.drawString(String.format("Grounded: %b", player.isGrounded()), getWidth() - 160, 140);
            drawCollidedTiles(g, tmap, xo, yo);
        }
    }

    /**
     * Draw the parallax scrolling background. The background image is
     * tiled across the screen and scrolls at a fraction of the camera speed.
     * 
     * @param g  The Graphics2D object to draw to
     * @param xo The camera x offset
     * @param yo The camera y offset
     */
    public void drawParallaxBackground(Graphics2D g, int xo, int yo) {
        if (bgImage == null)
            return;

        int bgW = bgImage.getWidth(null);
        int bgH = bgImage.getHeight(null);

        if (bgW <= 0 || bgH <= 0)
            return;

        // Parallax factor: background scrolls slower than the foreground
        float parallaxX = 0.3f;
        float parallaxY = 0.3f;

        int bgXOffset = (int) (xo * parallaxX);
        int bgYOffset = (int) (yo * parallaxY);

        // Tile the background image across the entire screen
        for (int x = bgXOffset % bgW - bgW; x < screenWidth; x += bgW) {
            for (int y = bgYOffset % bgH - bgH; y < screenHeight; y += bgH) {
                g.drawImage(bgImage, x, y, null);
            }
        }
    }

    /**
     * Draws blue rectangles around tiles that have been collided with.
     * Used for debugging tile collision detection.
     */
    public void drawCollidedTiles(Graphics2D g, TileMap map, int xOffset, int yOffset) {
        if (player != null && player.getCollidedTiles().size() > 0) {
            int tileWidth = map.getTileWidth();
            int tileHeight = map.getTileHeight();

            g.setColor(Color.blue);
            for (Tile t : player.getCollidedTiles()) {
                g.drawRect(t.getXC() + xOffset, t.getYC() + yOffset, tileWidth, tileHeight);
            }
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    /**
     * Core Physics & Logic Loop.
     * This runs every frame before draw() and is responsible for:
     * 1. Updating sprite physics (gravity, velocity calculations)
     * 2. Processing game logic (timers, invincibility)
     * 3. Calculating all collisions (Player vs Enemy, Player vs Items)
     * 
     * @param elapsed The elapsed time (in milliseconds) since the last update call.
     */
    public void update(long elapsed) {
        // Only run game logic when actively playing
        if (gameState != STATE_PLAYING)
            return;

        // Prevent jumps in movement if elapsed time is too large
        if (elapsed > 40)
            elapsed = 40;

        // Tick down hit invincibility timer
        if (playerHit) {
            hitTimer += elapsed;
            if (hitTimer >= HIT_INVINCIBILITY_MS) {
                playerHit = false;
                hitTimer = 0;
            }
        }

        // Tick down screen shake timer
        if (shakeTimer > 0)
            shakeTimer -= (int) elapsed;

        // --- Player Physics ---
        player.updatePhysics(elapsed, tmap);

        // ── ENEMY PATROL ──────────────────────────────────────────
        ArrayList<Enemy> deadEnemies = new ArrayList<Enemy>();
        for (int i = 0; i < enemies.size(); i++) {
            Enemy en = enemies.get(i);
            en.updatePhysics(elapsed, tmap);

            // ── PLAYER-ENEMY COLLISION ────────────────────────────
            if (boundingBoxCollision(player, en)) {
                float playerBottom = player.getY() + player.getHeight();
                float enemyCentre = en.getY() + en.getHeight() / 2f;
                // If player is falling downwards and bottom is above center of enemy
                if (player.getVelocityY() > 0 && playerBottom < enemyCentre + 4) {
                    // Stomped! Remove enemy and bounce player
                    deadEnemies.add(en);
                    total += 200;
                    player.bounce();
                    playSound(BASE_PATH + "assets/audio/bounce.wav");
                } else if (!playerHit) {
                    // Lateral hit — lose a life
                    lives--;
                    playerHit = true;
                    hitTimer = 0;
                    shakeTimer = SHAKE_DURATION; // trigger screen shake!
                    playSound(BASE_PATH + "assets/audio/hit.wav");
                    if (lives <= 0) {
                        int score = (int) (total / 100);
                        if (score > highScore) {
                            highScore = score;
                            saveHighScore();
                        }
                        gameState = STATE_GAME_OVER;
                        return;
                    }
                }
            }
        }
        // Remove stomped enemies outside the loop
        for (Enemy dead : deadEnemies) {
            enemies.remove(dead);
        }

        // ── FRUIT COLLECTION ──────────────────────────────────────
        ArrayList<Sprite> toRemove = new ArrayList<Sprite>();
        for (Sprite f : fruits) {
            f.update(elapsed);
            if (!collectedFruits.contains(f) && boundingBoxCollision(player, f)) {
                Animation cAnim = new Animation();
                cAnim.loadAnimationFromSheet(BASE_PATH + "assets/images/Items/Fruits/Collected.png", 6, 1, 60);
                cAnim.setLoop(false);
                f.setAnimation(cAnim);
                collectedFruits.add(f);
                total += 100;
                // Particle burst + sound on collect
                spawnFruitParticles(f.getX(), f.getY(), new Color(255, 180, 60));
                playSound(BASE_PATH + "assets/audio/collect_fruit.wav");
            }
            if (collectedFruits.contains(f) && f.getAnimation().hasLooped()) {
                toRemove.add(f);
            }
        }
        fruits.removeAll(toRemove);

        // ── POWER-UP COLLECTION (Strawberry = +1 life) ────────────
        ArrayList<Sprite> powToRemove = new ArrayList<Sprite>();
        for (Sprite s : powerups) {
            s.update(elapsed);
            if (!collectedPowerups.contains(s) && boundingBoxCollision(player, s)) {
                collectedPowerups.add(s);
                if (lives < 3) lives++;           // cap at 3
                total += 50;
                // Pink particle burst for strawberry
                spawnFruitParticles(s.getX(), s.getY(), new Color(255, 80, 120));
                playSound(BASE_PATH + "assets/audio/collect_fruit.wav");
                powToRemove.add(s);
            }
        }
        powerups.removeAll(powToRemove);

        // ── UPDATE PARTICLES ──────────────────────────────────────
        ArrayList<Particle> deadParticles = new ArrayList<Particle>();
        for (Particle p : particles) {
            if (!p.update(elapsed))
                deadParticles.add(p);
        }
        particles.removeAll(deadParticles);

        // ── CHECKPOINT ────────────────────────────────────────────
        if (checkpoint != null) {
            checkpoint.update(elapsed);
            if (boundingBoxCollision(player, checkpoint)) {
                playSound(BASE_PATH + "assets/audio/collect_fruit.wav");
                loadLevel(currentLevel + 1);
                return;
            }
        }

        // Score ticks up while moving
        if (player.isMoveLeft() || player.isMoveRight())
            total += elapsed;
    }

    // ── KEY INPUT ─────────────────────────────────────────────────────────────
    /**
     * Override of the keyPressed event defined in GameCore to catch our
     * own events
     * 
     * @param e The event that has been generated
     */
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        // Handle MENU state
        if (gameState == STATE_MENU) {
            if (key == KeyEvent.VK_ENTER) {
                total = 0;
                lives = 3;
                gameState = STATE_PLAYING;
                loadLevel(1);
            }
            return;
        }

        // Handle GAME OVER and WIN states
        if (gameState == STATE_GAME_OVER || gameState == STATE_WIN) {
            if (key == KeyEvent.VK_R) {
                total = 0;
                lives = 3;
                gameState = STATE_PLAYING;
                loadLevel(1);
            } else if (key == KeyEvent.VK_Q) {
                stop();
            }
            return;
        }

        // Normal playing controls
        switch (key) {
            case KeyEvent.VK_LEFT:
                player.setMoveLeft(true);
                break;
            case KeyEvent.VK_RIGHT:
                player.setMoveRight(true);
                break;
            case KeyEvent.VK_SPACE:
                if (player.isGrounded()) playFilteredSound(BASE_PATH + "assets/audio/jump.wav");
                player.setJumping(true);
                break;
            case KeyEvent.VK_UP:
                if (player.isGrounded()) playFilteredSound(BASE_PATH + "assets/audio/jump.wav");
                player.setJumping(true);
                break;
            case KeyEvent.VK_ESCAPE:
                stop();
                break;
            case KeyEvent.VK_B:
                debug = !debug;
                break;
            default:
                break;
        }
    }

    /**
     * Use the sample code in the lecture notes to properly detect
     * a bounding box collision between sprites s1 and s2.
     * 
     * @return true if a collision may have occurred, false if it has not.
     */
    public boolean boundingBoxCollision(Sprite s1, Sprite s2) {
        float s1x = s1.getX(), s1y = s1.getY(), s1w = s1.getWidth(), s1h = s1.getHeight();
        float s2x = s2.getX(), s2y = s2.getY(), s2w = s2.getWidth(), s2h = s2.getHeight();
        return (s1x < s2x + s2w && s1x + s1w > s2x && s1y < s2y + s2h && s1y + s1h > s2y);
    }

    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        switch (key) {
            case KeyEvent.VK_ESCAPE:
                stop();
                break;
            case KeyEvent.VK_LEFT:
                player.setMoveLeft(false);
                break;
            case KeyEvent.VK_RIGHT:
                player.setMoveRight(false);
                break;
            case KeyEvent.VK_SPACE:
                player.setJumping(false);
                break;
            case KeyEvent.VK_UP:
                player.setJumping(false);
                break;
            default:
                break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INNER CLASSES — Player, Enemy, Particle, FilteredSound
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The Player class encapsulates all physics, states, and inputs for the playable character.
     * It extends the Game2D Sprite class, allowing it to natively draw itself to the screen.
     * State is tracked via boolean flags (grounded, jumping) which automatically
     * switch the current Animation (Idle, Run, Jump, Fall) on every frame.
     */
    static class Player extends Sprite {

        private float gravity = 0.0008f;
        private float jumpStrength = -0.32f;
        private float moveSpeed = 0.12f;

        private Animation idleAnim;
        private Animation runAnim;
        private Animation jumpAnim;
        private Animation fallAnim;

        private boolean grounded = false;
        private boolean jumping = false;
        private boolean moveLeft = false;
        private boolean moveRight = false;

        // List to store tiles the player collided with this frame
        private ArrayList<Tile> collidedTiles = new ArrayList<Tile>();

        public Player(Animation idleAnim, Animation runAnim, Animation jumpAnim, Animation fallAnim) {
            super(idleAnim); // Start idle
            this.idleAnim = idleAnim;
            this.runAnim = runAnim;
            this.jumpAnim = jumpAnim;
            this.fallAnim = fallAnim;
        }

        public void setMoveLeft(boolean moveLeft) { this.moveLeft = moveLeft; }
        public void setMoveRight(boolean moveRight) { this.moveRight = moveRight; }
        public void setJumping(boolean jumping) { this.jumping = jumping; }

        public boolean isMoveLeft() { return moveLeft; }
        public boolean isMoveRight() { return moveRight; }

        public boolean isGrounded() { return grounded; }
        public ArrayList<Tile> getCollidedTiles() { return collidedTiles; }

        /**
         * Updates player physics, movement, and animation states.
         */
        public void updatePhysics(long elapsed, TileMap tmap) {
            // Apply gravity
            setVelocityY(getVelocityY() + (gravity * elapsed));

            // Horizontal movement
            if (moveLeft && !moveRight) {
                setVelocityX(-moveSpeed);
                setScale(-1, 1);
            } else if (moveRight && !moveLeft) {
                setVelocityX(moveSpeed);
                setScale(1, 1);
            } else {
                setVelocityX(0);
            }

            // Jumping
            if (jumping && grounded) {
                setVelocityY(jumpStrength);
                grounded = false;
            }

            // Clamp upwards jump velocity (so they can't fly out of frame)
            if (getVelocityY() < jumpStrength) {
                setVelocityY(jumpStrength);
            }

            // Update animation based on state
            if (!grounded) {
                if (getVelocityY() < 0)
                    setAnimation(jumpAnim);
                else
                    setAnimation(fallAnim);
            } else if (moveLeft || moveRight) {
                setAnimation(runAnim);
            } else {
                setAnimation(idleAnim);
            }

            // Apply velocities
            super.update(elapsed);

            // Keep player in map boundaries horizontally
            if (getX() < 0)
                setX(0);
            if (getX() + getWidth() > tmap.getPixelWidth())
                setX(tmap.getPixelWidth() - getWidth());

            // Perform tile map collision detection
            checkTileCollision(tmap);
        }

        /**
         * Bounces the player upwards (e.g., when stomping an enemy)
         */
        public void bounce() {
            setVelocityY(jumpStrength * 0.6f);
        }

        /**
         * Advanced Tile Map Collision Detection.
         * Resolves clipping issues by checking the exact pixel boundaries of the Player
         * against the grid location of rigid map tiles (like grass or stone).
         * If the player's bounding box mathematically overlaps with a non-empty '.' tile,
         * their position is snapped to the edge of the tile, and their velocity is set to 0.
         * 
         * @param tmap The TileMap to check against.
         */
        private void checkTileCollision(TileMap tmap) {
            collidedTiles.clear();

            float sx = getX();
            float sy = getY();
            float sw = getWidth();
            float sh = getHeight();

            float tileW = tmap.getTileWidth();
            float tileH = tmap.getTileHeight();

            grounded = false;

            // --- Bottom collision ---
            int btileY = (int) ((sy + sh) / tileH);
            int btileXL = (int) ((sx + 4) / tileW);
            int btileXR = (int) ((sx + sw - 4) / tileW);

            Tile blTile = tmap.getTile(btileXL, btileY);
            Tile brTile = tmap.getTile(btileXR, btileY);

            if ((blTile != null && blTile.getCharacter() != '.') ||
                    (brTile != null && brTile.getCharacter() != '.')) {
                setY(btileY * tileH - sh);
                setVelocityY(0);
                grounded = true;
                if (blTile != null && blTile.getCharacter() != '.') collidedTiles.add(blTile);
                if (brTile != null && brTile.getCharacter() != '.') collidedTiles.add(brTile);
            }

            // --- Top collision ---
            int ttileY = (int) (sy / tileH);
            int ttileXL = (int) ((sx + 4) / tileW);
            int ttileXR = (int) ((sx + sw - 4) / tileW);

            Tile tlTile = tmap.getTile(ttileXL, ttileY);
            Tile trTile = tmap.getTile(ttileXR, ttileY);

            if ((tlTile != null && tlTile.getCharacter() != '.') ||
                    (trTile != null && trTile.getCharacter() != '.')) {
                setY((ttileY + 1) * tileH);
                setVelocityY(0);
                if (tlTile != null && tlTile.getCharacter() != '.') collidedTiles.add(tlTile);
                if (trTile != null && trTile.getCharacter() != '.') collidedTiles.add(trTile);
            }

            // --- Left collision ---
            int ltileX = (int) (sx / tileW);
            int ltileYT = (int) ((sy + 4) / tileH);
            int ltileYB = (int) ((sy + sh - 4) / tileH);

            Tile ltTile = tmap.getTile(ltileX, ltileYT);
            Tile lbTile = tmap.getTile(ltileX, ltileYB);

            if ((ltTile != null && ltTile.getCharacter() != '.') ||
                    (lbTile != null && lbTile.getCharacter() != '.')) {
                setX((ltileX + 1) * tileW);
                setVelocityX(0);
                if (ltTile != null && ltTile.getCharacter() != '.') collidedTiles.add(ltTile);
                if (lbTile != null && lbTile.getCharacter() != '.') collidedTiles.add(lbTile);
            }

            // --- Right collision ---
            int rtileX = (int) ((sx + sw) / tileW);
            int rtileYT = (int) ((sy + 4) / tileH);
            int rtileYB = (int) ((sy + sh - 4) / tileH);

            Tile rtTile = tmap.getTile(rtileX, rtileYT);
            Tile rbTile = tmap.getTile(rtileX, rtileYB);

            if ((rtTile != null && rtTile.getCharacter() != '.') ||
                    (rbTile != null && rbTile.getCharacter() != '.')) {
                setX(rtileX * tileW - sw);
                setVelocityX(0);
                if (rtTile != null && rtTile.getCharacter() != '.') collidedTiles.add(rtTile);
                if (rbTile != null && rbTile.getCharacter() != '.') collidedTiles.add(rbTile);
            }
        }
    }

    /**
     * The Enemy class encapsulates rudimentary AI patrol behaviour.
     * Enemies are assigned a specific starting coordinate and an invisible "Patrol Radius" (min/max X).
     * They walk back and forth until they hit their boundary, at which point they reverse direction.
     * They possess their own distinct physics gravity and collision handlers.
     */
    static class Enemy extends Sprite {

        private float gravity = 0.0008f;
        private float patrolMin;
        private float patrolMax;
        private boolean facingRight = true;

        public Enemy(Animation anim, float startX, float startY, float patrolMin, float patrolMax) {
            super(anim);
            setPosition(startX, startY);
            setVelocity(0.08f, 0); // start moving right
            this.patrolMin = patrolMin;
            this.patrolMax = patrolMax;
            this.facingRight = true;
        }

        /**
         * Updates the enemy's position, applies gravity, handles patrol boundaries,
         * and performs collision detection.
         */
        public void updatePhysics(long elapsed, TileMap tmap) {
            // Apply gravity
            setVelocityY(getVelocityY() + (gravity * elapsed));

            // Reverse direction at patrol boundaries
            if (getX() <= patrolMin && !facingRight) {
                facingRight = true;
                setVelocityX(0.08f);
                setScale(1, 1);
            } else if (getX() + getWidth() >= patrolMax && facingRight) {
                facingRight = false;
                setVelocityX(-0.08f);
                setScale(-1, 1);
            }

            super.update(elapsed);
            checkTileCollision(tmap);
        }

        /**
         * Basic tile collision for the enemy to stand on floors and not walk through walls.
         */
        private void checkTileCollision(TileMap tmap) {
            float sx = getX();
            float sy = getY();
            float sw = getWidth();
            float sh = getHeight();

            float tileW = tmap.getTileWidth();
            float tileH = tmap.getTileHeight();

            // --- Bottom collision ---
            int btileY = (int) ((sy + sh) / tileH);
            int btileXL = (int) ((sx + 4) / tileW);
            int btileXR = (int) ((sx + sw - 4) / tileW);

            Tile blTile = tmap.getTile(btileXL, btileY);
            Tile brTile = tmap.getTile(btileXR, btileY);

            if ((blTile != null && blTile.getCharacter() != '.') ||
                    (brTile != null && brTile.getCharacter() != '.')) {
                setY(btileY * tileH - sh);
                setVelocityY(0);
            }

            // --- Left collision ---
            int ltileX = (int) (sx / tileW);
            int ltileYT = (int) ((sy + 4) / tileH);
            int ltileYB = (int) ((sy + sh - 4) / tileH);

            Tile ltTile = tmap.getTile(ltileX, ltileYT);
            Tile lbTile = tmap.getTile(ltileX, ltileYB);

            if ((ltTile != null && ltTile.getCharacter() != '.') ||
                    (lbTile != null && lbTile.getCharacter() != '.')) {
                setX((ltileX + 1) * tileW);
                // Wall hit — reverse direction
                facingRight = true;
                setVelocityX(0.08f);
                setScale(1, 1);
            }

            // --- Right collision ---
            int rtileX = (int) ((sx + sw) / tileW);
            int rtileYT = (int) ((sy + 4) / tileH);
            int rtileYB = (int) ((sy + sh - 4) / tileH);

            Tile rtTile = tmap.getTile(rtileX, rtileYT);
            Tile rbTile = tmap.getTile(rtileX, rtileYB);

            if ((rtTile != null && rtTile.getCharacter() != '.') ||
                    (rbTile != null && rbTile.getCharacter() != '.')) {
                setX(rtileX * tileW - sw);
                // Wall hit — reverse direction
                facingRight = false;
                setVelocityX(-0.08f);
                setScale(-1, 1);
            }
        }
    }

    /**
     * A simple visual particle used for fruit-collect burst effects.
     * Each particle has a position, velocity, remaining life, and colour.
     */
    static class Particle {

        float x, y;     // World position
        float vx, vy;   // Velocity (pixels per ms)
        int life;        // Remaining life in ms
        Color color;

        public Particle(float x, float y, float vx, float vy, int life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
        }

        /** Move and age the particle. Returns false when it should be removed. */
        public boolean update(long elapsed) {
            x += vx * elapsed;
            y += vy * elapsed;
            vy += 0.001f * elapsed; // mini gravity
            life -= (int) elapsed;
            return life > 0;
        }

        /** Draw the particle at world-space offset (xo, yo). */
        public void draw(Graphics2D g, int xo, int yo) {
            // Fade out as life decreases
            int alpha = Math.min(255, (int) (life * 2.5f));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            int size = Math.max(2, life / 30);
            g.fillOval((int) x + xo - size / 2, (int) y + yo - size / 2, size, size);
        }
    }

    /**
     * FilteredSound plays a .wav file but applies a custom byte-level mathematical
     * filter to the audio data before sending it to the speakers.
     * This fulfils the "novel sound filter" requirement.
     */
    static class FilteredSound extends Thread {

        String filename; // The name of the file to play
        boolean finished; // A flag showing that the thread has finished

        public FilteredSound(String fname) {
            filename = fname;
            finished = false;
        }

        /**
         * Applies a mathematical Acoustic Echo algorithm to raw PCM data.
         * Rather than using a pre-built Java sound filter, this manually intercepts
         * a sequence of bytes, calculates a temporal delay window based on the sample rate,
         * reconstructing the 16-bit little-endian values, blending them with historic values,
         * and then re-packing the 16-bit results back into the byte array frame-by-frame.
         * 
         * @param audioBytes The raw, unprocessed file array.
         * @param format The format metadata representing structure (channels, bits).
         */
        private void applyEchoFilter(byte[] audioBytes, javax.sound.sampled.AudioFormat format) {
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            if (bytesPerSample != 2) return; // Only process 16-bit audio

            int channels = format.getChannels();

            // Echo delay in samples (e.g. 100ms delay)
            int delayInSamples = (int)(format.getSampleRate() * 0.10) * channels;
            int delayInBytes = delayInSamples * bytesPerSample;

            // We need an array to hold the original audio so we can mix it with a delay
            byte[] original = new byte[audioBytes.length];
            System.arraycopy(audioBytes, 0, original, 0, audioBytes.length);

            for (int i = delayInBytes; i < audioBytes.length - 1; i += 2) {
                // Reconstruct the 16-bit little-endian sample for the current time
                short currentSample = (short) ((audioBytes[i+1] << 8) | (audioBytes[i] & 0xFF));

                // Reconstruct the 16-bit little-endian sample from the past (the echo)
                int pastIndex = i - delayInBytes;
                short pastSample = (short) ((original[pastIndex+1] << 8) | (original[pastIndex] & 0xFF));

                // Mix them together (current + 50% volume of past)
                int mixed = currentSample + (pastSample / 2);

                // Clamp the value to prevent clipping/distortion
                if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
                if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;

                // Put the mixed 16-bit sample back into the byte array
                audioBytes[i] = (byte) (mixed & 0xFF);
                audioBytes[i+1] = (byte) ((mixed >> 8) & 0xFF);
            }
        }

        public void run() {
            try {
                File file = new File(filename);
                javax.sound.sampled.AudioInputStream stream = javax.sound.sampled.AudioSystem.getAudioInputStream(file);
                javax.sound.sampled.AudioFormat format = stream.getFormat();

                // Read all bytes from the stream
                int frameLength = (int) stream.getFrameLength();
                int frameSize = format.getFrameSize();
                byte[] bytes = new byte[frameLength * frameSize];
                int bytesRead = stream.read(bytes);

                // ── APPLY OUR NOVEL FILTER HERE ──
                if (bytesRead > 0) {
                    applyEchoFilter(bytes, format);
                }

                // Create a new stream from the filtered bytes
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                javax.sound.sampled.AudioInputStream filteredStream = new javax.sound.sampled.AudioInputStream(bais, format, frameLength);

                // Play the filtered stream
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(javax.sound.sampled.Clip.class, format);
                javax.sound.sampled.Clip clip = (javax.sound.sampled.Clip) javax.sound.sampled.AudioSystem.getLine(info);
                clip.open(filteredStream);
                clip.start();
                Thread.sleep(100);
                while (clip.isRunning()) {
                    Thread.sleep(100);
                }
                clip.close();
            } catch (Exception e) {
                System.out.println("Error playing filtered sound: " + e.getMessage());
            }
            finished = true;
        }
    }
}
