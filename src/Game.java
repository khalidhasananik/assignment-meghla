
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;

import game2D.*;

// Game demonstrates how we can override the GameCore class
// to create our own 'game'. We usually need to implement at
// least 'draw' and 'update' (not including any local event handling)
// to begin the process. You should also add code to the 'init'
// method that will initialise event handlers etc. 

// Student ID: ???????

@SuppressWarnings("serial")

public class Game extends GameCore {
    // Useful game constants
    static int screenWidth = 512;
    static int screenHeight = 384;

    // Game constants
    float gravity = 0.0008f;
    float jumpStrength = -0.32f;
    float moveSpeed = 0.12f;

    // Game state flags
    boolean moveLeft = false;
    boolean moveRight = false;
    boolean jumping = false;
    boolean grounded = false;
    boolean debug = false;

    // Game resources
    Animation idleAnim, runAnim, jumpAnim, fallAnim;
    Animation appleAnim, collectAnim, flagAnim;
    
    Sprite	player = null;
    Sprite  checkpoint = null;
    ArrayList<Sprite> fruits = new ArrayList<Sprite>();
    HashSet<Sprite> collectedFruits = new HashSet<Sprite>();
    ArrayList<Tile> collidedTiles = new ArrayList<Tile>();

    TileMap tmap = new TileMap(); // Our tile map, note that we load it in init()
    
    int currentLevel = 1;

    // Parallax background
    Image bgImage = null;

    long total; // The score will be the total time elapsed since a crash

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

        // Load the parallax background image
        bgImage = loadImage("../assets/images/Background/Blue.png");

        // Load player animations from Pixel Adventure sprite sheets
        idleAnim = new Animation();
        idleAnim.loadAnimationFromSheet("../assets/images/Main Characters/Ninja Frog/Idle (32x32).png", 11, 1, 60);

        runAnim = new Animation();
        runAnim.loadAnimationFromSheet("../assets/images/Main Characters/Ninja Frog/Run (32x32).png", 12, 1, 60);

        jumpAnim = new Animation();
        jumpAnim.loadAnimationFromSheet("../assets/images/Main Characters/Ninja Frog/Jump (32x32).png", 1, 1, 200);

        fallAnim = new Animation();
        fallAnim.loadAnimationFromSheet("../assets/images/Main Characters/Ninja Frog/Fall (32x32).png", 1, 1, 200);

        appleAnim = new Animation();
        appleAnim.loadAnimationFromSheet("../assets/images/Items/Fruits/Apple.png", 17, 1, 60);

        collectAnim = new Animation();
        collectAnim.loadAnimationFromSheet("../assets/images/Items/Fruits/Collected.png", 6, 1, 60);
        collectAnim.setLoop(false);

        flagAnim = new Animation();
        flagAnim.loadAnimationFromSheet("../assets/images/Items/Checkpoints/Checkpoint/Checkpoint (Flag Idle)(64x64).png", 10, 1, 100);

        // Initialise the player with the idle animation
        player = new Sprite(idleAnim);

        // Debug: verify animation loaded
        if (player.getImage() != null)
            System.out.println("Player sprite loaded: " + player.getWidth() + "x" + player.getHeight());
        else
            System.out.println("WARNING: Player sprite image is NULL!");

        loadLevel(1);
    }
    
    /**
     * Loads the specified level map and resets the player and level state.
     */
    public void loadLevel(int level)
    {
    	currentLevel = level;
    	String mapFile = "map" + level + ".txt";
    	if (!tmap.loadMap("../maps", mapFile)) {
    		System.out.println("Could not load level " + level + ". Returning to Title or quitting.");
    		stop();
    		return;
    	}
    	System.out.println(tmap);
    	initialiseGame();
    }

    /**
     * You will probably want to put code to restart a game in
     * a separate method so that you can call it when restarting
     * the game when the player loses.
     */
    public void initialiseGame()
    {
    	// Note: total (score) is NOT reset here so it carries across levels
    	grounded = false;
    	fruits.clear();
    	collectedFruits.clear();
        checkpoint = null;
    	      
        // Spawn in the open space area
        player.setPosition(100, 200);
        player.setVelocity(0, 0);
        player.show();
        
        // Scan the tile map for spawn markers
        for (int r = 0; r < tmap.getMapHeight(); r++)
        {
            for (int c = 0; c < tmap.getMapWidth(); c++)
            {
                char ch = tmap.getTileChar(c, r);
                if (ch == 'a')
                {
                    // Each fruit needs its OWN Animation to avoid shared state
                    Animation fruitAnim = new Animation();
                    fruitAnim.loadAnimationFromSheet("../assets/images/Items/Fruits/Apple.png", 17, 1, 60);
                    Sprite apple = new Sprite(fruitAnim);
                    // Center the 32x32 fruit on the 16x16 tile
                    apple.setPosition(c * tmap.getTileWidth() - 8, r * tmap.getTileHeight() - 8);
                    fruits.add(apple);
                    tmap.setTileChar('.', c, r); // remove from map
                }
                else if (ch == 'F')
                {
                	checkpoint = new Sprite(flagAnim);
                	// Bottom-align the 64x64 flag to the tile
                	checkpoint.setPosition(c * tmap.getTileWidth(), r * tmap.getTileHeight() - (64 - 16));
                	tmap.setTileChar('.', c, r);
                }
            }
        }
    }

    /**
     * Draw the current state of the game. Note the sample use of
     * debugging output that is drawn directly to the game screen.
     */
    public void draw(Graphics2D g) {
        // First work out how much we need to shift the view
        // in order to see where the player is.
        int xo = -(int) player.getX() + screenWidth / 4;
        int yo = -(int) player.getY() + screenHeight / 2;

        // Clamp camera so it doesn't go beyond map edges
        int maxXo = 0;
        int minXo = -(tmap.getPixelWidth() - screenWidth);
        int maxYo = 0;
        int minYo = -(tmap.getPixelHeight() - screenHeight);

        if (xo > maxXo)
            xo = maxXo;
        if (xo < minXo)
            xo = minXo;
        if (yo > maxYo)
            yo = maxYo;
        if (yo < minYo)
            yo = minYo;

        // Draw the parallax scrolling background
        // The background scrolls at half the speed of the foreground
        drawParallaxBackground(g, xo, yo);

        // Apply offsets to tile map and draw it
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

        // Apply offsets to player and draw
        player.setOffsets(xo, yo);
        player.draw(g);

        // Debug: draw a red rectangle at player position to verify location
        if (debug) {
            g.setColor(Color.red);
            g.fillRect((int) player.getX() + xo, (int) player.getY() + yo, player.getWidth(), player.getHeight());
        }

        // Show score and status information
        String msg = String.format("Score: %d", total / 100);
        g.setColor(Color.white);
        g.drawString(msg, getWidth() - 100, 50);

        if (debug) {
            tmap.drawBorder(g, xo, yo, Color.black);
            g.setColor(Color.red);
            player.drawBoundingBox(g);
            g.drawString(String.format("Player: %.0f,%.0f", player.getX(), player.getY()),
                    getWidth() - 160, 70);
            g.drawString(String.format("Grounded: %b", grounded), getWidth() - 160, 90);
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
     * 
     * @param g       The graphics device to draw to
     * @param map     The tile map being checked
     * @param xOffset The x offset to apply
     * @param yOffset The y offset to apply
     */
    public void drawCollidedTiles(Graphics2D g, TileMap map, int xOffset, int yOffset) {
        if (collidedTiles.size() > 0) {
            int tileWidth = map.getTileWidth();
            int tileHeight = map.getTileHeight();

            g.setColor(Color.blue);
            for (Tile t : collidedTiles) {
                g.drawRect(t.getXC() + xOffset, t.getYC() + yOffset, tileWidth, tileHeight);
            }
        }
    }

    /**
     * Update any sprites and check for collisions
     * 
     * @param elapsed The elapsed time between this call and the previous call of
     *                elapsed
     */
    public void update(long elapsed) {
        // Prevent jumps in movement if elapsed time is too large
        if (elapsed > 40)
            elapsed = 40;

        // Apply gravity to the player
        player.setVelocityY(player.getVelocityY() + (gravity * elapsed));

        // Handle horizontal movement
        if (moveLeft && !moveRight) {
            player.setVelocityX(-moveSpeed);
            player.setScale(-1, 1); // Flip sprite to face left
        } else if (moveRight && !moveLeft) {
            player.setVelocityX(moveSpeed);
            player.setScale(1, 1); // Face right (normal)
        } else {
            player.setVelocityX(0);
        }

        // Handle jumping - only allow when grounded
        if (jumping && grounded) {
            player.setVelocityY(jumpStrength);
            grounded = false;
        }

        // Choose the right animation based on state
        if (!grounded) {
            if (player.getVelocityY() < 0)
                player.setAnimation(jumpAnim);
            else
                player.setAnimation(fallAnim);
        } else if (moveLeft || moveRight) {
            player.setAnimation(runAnim);
        } else {
            player.setAnimation(idleAnim);
        }

        // Now update the sprites animation and position
        player.update(elapsed);

        // Keep player within the map boundaries (left edge)
        if (player.getX() < 0)
            player.setX(0);

        // Keep player within the map boundaries (right edge)
        if (player.getX() + player.getWidth() > tmap.getPixelWidth())
            player.setX(tmap.getPixelWidth() - player.getWidth());

        // Then check for any collisions that may have occurred
        checkTileCollision(player, tmap);
        
        // Check for collisions with fruits
        ArrayList<Sprite> toRemove = new ArrayList<Sprite>();
        for (Sprite f : fruits) {
            f.update(elapsed);
            // Only check collision if this fruit hasn't been collected yet
        	if (!collectedFruits.contains(f) && boundingBoxCollision(player, f)) {
        		// Create a new collect animation for THIS fruit only
        		Animation cAnim = new Animation();
        		cAnim.loadAnimationFromSheet("../assets/images/Items/Fruits/Collected.png", 6, 1, 60);
        		cAnim.setLoop(false);
        		f.setAnimation(cAnim);
        		collectedFruits.add(f);
        		total += 100; // Add score when collected
        	}
        	// Remove fruit when its non-looping collect animation finishes
        	if (collectedFruits.contains(f) && f.getAnimation().hasLooped()) {
        		toRemove.add(f);
        	}
        }
        fruits.removeAll(toRemove);
        
        // Check checkpoint
        if (checkpoint != null) {
        	checkpoint.update(elapsed);
        	if (boundingBoxCollision(player, checkpoint)) {
        		// Reached the end of the level! (Transition will be added here later)
        		System.out.println("Checkpoint reached! Loading next level.");
        		loadLevel(currentLevel + 1);
        		return; // exit update to prevent issues with partially updated state
        	}
        }

        // Update score only when moving
        if (moveLeft || moveRight)
            total += elapsed;
    }

    /**
     * Override of the keyPressed event defined in GameCore to catch our
     * own events
     * 
     * @param e The event that has been generated
     */
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        switch (key) {
            case KeyEvent.VK_LEFT:
                moveLeft = true;
                break;
            case KeyEvent.VK_RIGHT:
                moveRight = true;
                break;
            case KeyEvent.VK_SPACE:
                jumping = true;
                break;
            case KeyEvent.VK_UP:
                jumping = true;
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
        // Get the bounding boxes of both sprites
        float s1x = s1.getX();
        float s1y = s1.getY();
        float s1w = s1.getWidth();
        float s1h = s1.getHeight();

        float s2x = s2.getX();
        float s2y = s2.getY();
        float s2w = s2.getWidth();
        float s2h = s2.getHeight();

        // Check for overlap
        return (s1x < s2x + s2w && s1x + s1w > s2x &&
                s1y < s2y + s2h && s1y + s1h > s2y);
    }

    /**
     * Check and handle collisions with a tile map for the
     * given sprite 's'. Checks all four corners plus midpoints
     * to determine proper collision response.
     * 
     * @param s    The Sprite to check collisions for
     * @param tmap The tile map to check
     */
    public void checkTileCollision(Sprite s, TileMap tmap) {
        // Empty out our current set of collided tiles
        collidedTiles.clear();

        float sx = s.getX();
        float sy = s.getY();
        float sw = s.getWidth();
        float sh = s.getHeight();

        float tileW = tmap.getTileWidth();
        float tileH = tmap.getTileHeight();

        // We assume grounded is false until proven otherwise
        grounded = false;

        // --- Bottom collision (landing on ground) ---
        // Check bottom-left and bottom-right corners
        int btileY = (int) ((sy + sh) / tileH);
        int btileXL = (int) ((sx + 4) / tileW); // slight inset to avoid edge sticking
        int btileXR = (int) ((sx + sw - 4) / tileW);

        Tile blTile = tmap.getTile(btileXL, btileY);
        Tile brTile = tmap.getTile(btileXR, btileY);

        if ((blTile != null && blTile.getCharacter() != '.') ||
                (brTile != null && brTile.getCharacter() != '.')) {
            // Land on top of the tile
            s.setY(btileY * tileH - sh);
            s.setVelocityY(0);
            grounded = true;
            if (blTile != null && blTile.getCharacter() != '.')
                collidedTiles.add(blTile);
            if (brTile != null && brTile.getCharacter() != '.')
                collidedTiles.add(brTile);
        }

        // --- Top collision (hitting ceiling) ---
        int ttileY = (int) (sy / tileH);
        int ttileXL = (int) ((sx + 4) / tileW);
        int ttileXR = (int) ((sx + sw - 4) / tileW);

        Tile tlTile = tmap.getTile(ttileXL, ttileY);
        Tile trTile = tmap.getTile(ttileXR, ttileY);

        if ((tlTile != null && tlTile.getCharacter() != '.') ||
                (trTile != null && trTile.getCharacter() != '.')) {
            // Push player down out of the ceiling
            s.setY((ttileY + 1) * tileH);
            s.setVelocityY(0);
            if (tlTile != null && tlTile.getCharacter() != '.')
                collidedTiles.add(tlTile);
            if (trTile != null && trTile.getCharacter() != '.')
                collidedTiles.add(trTile);
        }

        // --- Left collision (hitting wall on left side) ---
        int ltileX = (int) (sx / tileW);
        int ltileYT = (int) ((sy + 4) / tileH);
        int ltileYB = (int) ((sy + sh - 4) / tileH);

        Tile ltTile = tmap.getTile(ltileX, ltileYT);
        Tile lbTile = tmap.getTile(ltileX, ltileYB);

        if ((ltTile != null && ltTile.getCharacter() != '.') ||
                (lbTile != null && lbTile.getCharacter() != '.')) {
            // Push player right out of the wall
            s.setX((ltileX + 1) * tileW);
            s.setVelocityX(0);
            if (ltTile != null && ltTile.getCharacter() != '.')
                collidedTiles.add(ltTile);
            if (lbTile != null && lbTile.getCharacter() != '.')
                collidedTiles.add(lbTile);
        }

        // --- Right collision (hitting wall on right side) ---
        int rtileX = (int) ((sx + sw) / tileW);
        int rtileYT = (int) ((sy + 4) / tileH);
        int rtileYB = (int) ((sy + sh - 4) / tileH);

        Tile rtTile = tmap.getTile(rtileX, rtileYT);
        Tile rbTile = tmap.getTile(rtileX, rtileYB);

        if ((rtTile != null && rtTile.getCharacter() != '.') ||
                (rbTile != null && rbTile.getCharacter() != '.')) {
            // Push player left out of the wall
            s.setX(rtileX * tileW - sw);
            s.setVelocityX(0);
            if (rtTile != null && rtTile.getCharacter() != '.')
                collidedTiles.add(rtTile);
            if (rbTile != null && rbTile.getCharacter() != '.')
                collidedTiles.add(rbTile);
        }
    }

    public void keyReleased(KeyEvent e) {

        int key = e.getKeyCode();

        switch (key) {
            case KeyEvent.VK_ESCAPE:
                stop();
                break;
            case KeyEvent.VK_LEFT:
                moveLeft = false;
                break;
            case KeyEvent.VK_RIGHT:
                moveRight = false;
                break;
            case KeyEvent.VK_SPACE:
                jumping = false;
                break;
            case KeyEvent.VK_UP:
                jumping = false;
                break;
            default:
                break;
        }
    }
}
