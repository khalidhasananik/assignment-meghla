import java.awt.*;

/**
 * A simple visual particle used for fruit-collect burst effects.
 * Each particle has a position, velocity, remaining life, and colour.
 */
public class Particle {

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
