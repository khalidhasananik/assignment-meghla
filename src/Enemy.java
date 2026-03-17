import game2D.Animation;
import game2D.Sprite;
import game2D.Tile;
import game2D.TileMap;

/**
 * Encapsulates Enemy patrol logic, gravity, and tile collision.
 */
public class Enemy extends Sprite {

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
