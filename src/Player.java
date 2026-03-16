import game2D.Animation;
import game2D.Sprite;
import game2D.Tile;
import game2D.TileMap;
import java.util.ArrayList;

/**
 * Encapsulates all player-specific logic, physics, and state.
 */
public class Player extends Sprite {
    
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
     * Check and handle collisions with a tile map.
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
