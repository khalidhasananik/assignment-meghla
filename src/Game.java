
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import javax.sound.midi.*;

import game2D.*;

// Game demonstrates how we can override the GameCore class
// to create our own 'game'. We usually need to implement at
// least 'draw' and 'update' (not including any local event handling)
// to begin the process. You should also add code to the 'init'
// method that will initialise event handlers etc. 

// Student ID: 3366206

@SuppressWarnings("serial")

public class Game extends GameCore {
    // Dynamic pathing block for Eclipse vs Command Line execution
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
    /** Spawn a small burst of coloured particles at the given world position. */
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
     * Draw the current state of the game. Note the sample use of
     * debugging output that is drawn directly to the game screen.
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
     * Update any sprites and check for collisions
     * 
     * @param elapsed The elapsed time between this call and the previous call of
     *                elapsed
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
}
