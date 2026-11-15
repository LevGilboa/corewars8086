package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.gui.effects.VisualEffect;
import il.co.codeguru.corewars8086.gui.effects.VisualEffectManager;
import il.co.codeguru.corewars8086.utils.EventMulticaster;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Enhanced High-Performance Battle Arena Canvas:
 * - Crisp coordinate axes (0000-E000 on Y, 00-E0 on X)
 * - Dynamic responsive scaling keeping 1:1 aspect ratio
 * - Smooth double-buffered rendering without tearing
 * - Real-time hover inspection (offset, value, owner)
 * - Integrated 10x Zoom Magnifier Lens
 * - Full visual effect manager integration
 */
public class Canvas extends JComponent implements MouseInputListener {
    private static final long serialVersionUID = 1L;

    public static final int BOARD_SIZE = 256;
    public static final int DOT_SIZE = 3;
    public static final byte EMPTY = -1;

    private static final int MARGIN_LEFT = 42;
    private static final int MARGIN_TOP = 18;
    private static final int MARGIN_RIGHT = 15;
    private static final int MARGIN_BOTTOM = 30;

    private final byte[][] data = new byte[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] pointer = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final Color[][] pointerColors = new Color[BOARD_SIZE][BOARD_SIZE];

    private final EventMulticaster eventCaster;
    private final MouseAddressRequest eventHandler;

    private int mouseGridX = -1;
    private int mouseGridY = -1;

    private final VisualEffectManager effectManager;
    private Timer animationTimer;

    // Fast off-screen raster for 256x256 board
    private final BufferedImage boardImage = new BufferedImage(BOARD_SIZE, BOARD_SIZE, BufferedImage.TYPE_INT_RGB);

    public Canvas() {
        this.eventCaster = new EventMulticaster(MouseAddressRequest.class);
        this.eventHandler = (MouseAddressRequest) eventCaster.getProxy();
        this.addMouseMotionListener(this);
        this.addMouseListener(this);

        this.effectManager = new VisualEffectManager();

        // 60 FPS animation timer for smooth visual effects & repaints
        this.animationTimer = new Timer(16, e -> {
            effectManager.update();
            repaint();
        });
        this.animationTimer.start();

        clear();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(BOARD_SIZE * 2 + MARGIN_LEFT + MARGIN_RIGHT,
                             BOARD_SIZE * 2 + MARGIN_TOP + MARGIN_BOTTOM);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(BOARD_SIZE * DOT_SIZE + MARGIN_LEFT + MARGIN_RIGHT,
                             BOARD_SIZE * DOT_SIZE + MARGIN_TOP + MARGIN_BOTTOM);
    }

    public void paintPixel(int number, byte color) {
        paintPixel(number % BOARD_SIZE, number / BOARD_SIZE, color);
    }

    public void paintPixel(int x, int y, byte color) {
        if (x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE) {
            data[x][y] = color;
            Color c = ColorHolder.getInstance().getColor(color, false);
            boardImage.setRGB(x, y, c.getRGB());
        }
    }

    public Color getColorForWarrior(int id) {
        return ColorHolder.getInstance().getColor(id, false);
    }

    public void paintPointer(int x, int y, Color color) {
        if (x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE) {
            pointer[x][y] = true;
            pointerColors[x][y] = color;
        }
    }

    public void paintPointer(int x, int y, byte color) {
        paintPointer(x, y, ColorHolder.getInstance().getColor(color, true));
    }

    public void paintPointer(int number, byte color) {
        paintPointer(number % BOARD_SIZE, number / BOARD_SIZE, color);
    }

    public void deletePointers() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (pointer[i][j]) {
                    pointer[i][j] = false;
                    pointerColors[i][j] = null;
                }
            }
        }
    }

    public void clear() {
        int emptyRgb = new Color(15, 17, 24).getRGB();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                data[i][j] = EMPTY;
                pointer[i][j] = false;
                pointerColors[i][j] = null;
                boardImage.setRGB(i, j, emptyRgb);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int totalW = getWidth();
        int totalH = getHeight();

        // Dark background
        g2.setColor(new Color(11, 13, 19));
        g2.fillRect(0, 0, totalW, totalH);

        // Compute arena square maintaining 1:1 aspect ratio
        int availW = totalW - MARGIN_LEFT - MARGIN_RIGHT;
        int availH = totalH - MARGIN_TOP - MARGIN_BOTTOM;
        if (availW <= 0 || availH <= 0) {
            g2.dispose();
            return;
        }

        int mapDim = Math.min(availW, availH);
        int mapX = MARGIN_LEFT + (availW - mapDim) / 2;
        int mapY = MARGIN_TOP;

        // Draw Arena Background & Board Image
        g2.setColor(Color.BLACK);
        g2.fillRect(mapX, mapY, mapDim, mapDim);
        g2.drawImage(boardImage, mapX, mapY, mapDim, mapDim, null);

        double scale = (double) mapDim / BOARD_SIZE;

        // Draw Warrior IP Pointers (Pulsing / Glowing dots)
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (pointer[x][y]) {
                    Color pc = pointerColors[x][y] != null ? pointerColors[x][y] : Color.WHITE;
                    int px = mapX + (int) (x * scale);
                    int py = mapY + (int) (y * scale);
                    int pSize = Math.max(3, (int) Math.ceil(scale * 1.5));

                    // Glowing halo
                    g2.setColor(new Color(pc.getRed(), pc.getGreen(), pc.getBlue(), 120));
                    g2.fillOval(px - 3, py - 3, pSize + 6, pSize + 6);

                    // Solid pointer core
                    g2.setColor(pc);
                    g2.fillRect(px, py, pSize, pSize);
                }
            }
        }

        // Draw Subtle Sector Grid Lines (every 32 bytes = 0x2000)
        g2.setColor(new Color(30, 36, 48, 80));
        for (int sec = 32; sec < BOARD_SIZE; sec += 32) {
            int pos = (int) (sec * scale);
            g2.drawLine(mapX + pos, mapY, mapX + pos, mapY + mapDim);
            g2.drawLine(mapX, mapY + pos, mapX + mapDim, mapY + pos);
        }

        // Render Visual Effects on top (scaled to match arena)
        double effectScale = (double) mapDim / (BOARD_SIZE * DOT_SIZE);
        g2.translate(mapX, mapY);
        g2.scale(effectScale, effectScale);
        effectManager.render(g2);
        g2.scale(1.0 / effectScale, 1.0 / effectScale);
        g2.translate(-mapX, -mapY);

        // Arena Border
        g2.setColor(new Color(60, 72, 95));
        g2.drawRect(mapX - 1, mapY - 1, mapDim + 1, mapDim + 1);

        // Draw Coordinate Axes (Y on Left, X on Bottom)
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(130, 142, 160));

        // Y Axis ticks (0000 - E000)
        for (int yOff = 0; yOff <= 0xE000; yOff += 0x2000) {
            int row = yOff / BOARD_SIZE;
            int tickY = mapY + (int) (row * scale);
            String label = String.format("%04X", yOff);
            g2.drawString(label, mapX - 36, tickY + 4);
            g2.drawLine(mapX - 4, tickY, mapX, tickY);
        }

        // X Axis ticks (00 - E0)
        for (int xOff = 0; xOff < BOARD_SIZE; xOff += 0x20) {
            int tickX = mapX + (int) (xOff * scale);
            String label = String.format("%02X", xOff);
            g2.drawString(label, tickX - 6, mapY + mapDim + 15);
            g2.drawLine(tickX, mapY + mapDim, tickX, mapY + mapDim + 4);
        }

        // Draw Hover Reticle & Integrated Magnifier Lens (10x Zoom)
        if (mouseGridX >= 0 && mouseGridX < BOARD_SIZE && mouseGridY >= 0 && mouseGridY < BOARD_SIZE) {
            int hx = mapX + (int) (mouseGridX * scale);
            int hy = mapY + (int) (mouseGridY * scale);

            // Hover crosshair reticle on arena
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawRect(hx - 2, hy - 2, Math.max(4, (int) scale + 2), Math.max(4, (int) scale + 2));
            g2.setColor(new Color(0, 200, 255, 180));
            g2.drawLine(hx - 8, hy, hx + 8, hy);
            g2.drawLine(hx, hy - 8, hx, hy + 8);

            // Magnifier Lens (7x7 cells, 16px per cell)
            int lensGrid = 7;
            int cellSize = 16;
            int gridW = lensGrid * cellSize;
            int lensW = gridW + 16;
            int lensH = gridW + 36;

            int lensX = mapX + mapDim - lensW - 8;
            int lensY = mapY + 8;
            // Flip lens position if cursor is under it
            if (hx > lensX - 25 && hy < lensY + lensH + 25) {
                lensY = mapY + mapDim - lensH - 8;
            }

            g2.setColor(new Color(15, 18, 25, 245));
            g2.fillRoundRect(lensX, lensY, lensW, lensH, 8, 8);
            g2.setColor(new Color(0, 200, 255));
            g2.drawRoundRect(lensX, lensY, lensW, lensH, 8, 8);

            int currentOffset = mouseGridY * BOARD_SIZE + mouseGridX;
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.drawString(String.format("LENS: 0x%04X", currentOffset), lensX + 8, lensY + 15);

            int startGX = lensX + 8;
            int startGY = lensY + 22;

            g2.setFont(new Font("Consolas", Font.PLAIN, 10));
            int half = lensGrid / 2;
            for (int dy = -half; dy <= half; dy++) {
                for (int dx = -half; dx <= half; dx++) {
                    int cx = (mouseGridX + dx + BOARD_SIZE) % BOARD_SIZE;
                    int cy = (mouseGridY + dy + BOARD_SIZE) % BOARD_SIZE;
                    byte owner = data[cx][cy];

                    int cellX = startGX + (dx + half) * cellSize;
                    int cellY = startGY + (dy + half) * cellSize;

                    if (owner != EMPTY) {
                        g2.setColor(ColorHolder.getInstance().getColor(owner, false));
                    } else {
                        g2.setColor(new Color(24, 27, 34));
                    }
                    g2.fillRect(cellX, cellY, cellSize - 1, cellSize - 1);

                    // Center cell reticle
                    if (dx == 0 && dy == 0) {
                        g2.setColor(Color.WHITE);
                        g2.drawRect(cellX - 1, cellY - 1, cellSize, cellSize);
                        g2.setColor(new Color(0, 220, 255));
                        g2.drawRect(cellX - 2, cellY - 2, cellSize + 2, cellSize + 2);
                    }

                    // Value label
                    g2.setColor((owner != EMPTY || (dx == 0 && dy == 0)) ? Color.WHITE : new Color(110, 120, 135));
                    String valStr = (owner != EMPTY) ? String.format("%02X", owner & 0xFF) : "CC";
                    g2.drawString(valStr, cellX + 2, cellY + cellSize - 4);
                }
            }

            // Bottom inspection label
            int off = mouseGridY * BOARD_SIZE + mouseGridX;
            byte owner = data[mouseGridX][mouseGridY];
            String ownerName = (owner != EMPTY) ? ("Warrior #" + (owner & 0xFF)) : "Unwritten [0xCC]";
            String inspectText = String.format("ADDRESS: 0x%04X (X:%d, Y:%d) | %s", off, mouseGridX, mouseGridY, ownerName);
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            int textW = g2.getFontMetrics().stringWidth(inspectText);
            g2.drawString(inspectText, mapX + (mapDim - textW) / 2, totalH - 8);
        }

        g2.dispose();
    }

    public void addVisualEffect(VisualEffect effect) {
        effectManager.addEffect(effect);
    }

    public void clearEffects() {
        effectManager.clear();
    }

    public void dispose() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        if (effectManager != null) {
            effectManager.clear();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int availW = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
        int availH = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        if (availW <= 0 || availH <= 0) return;

        int mapDim = Math.min(availW, availH);
        int mapX = MARGIN_LEFT + (availW - mapDim) / 2;
        int mapY = MARGIN_TOP;

        int mx = e.getX() - mapX;
        int my = e.getY() - mapY;

        if (mx >= 0 && mx < mapDim && my >= 0 && my < mapDim) {
            mouseGridX = (int) (((long) mx * BOARD_SIZE) / mapDim);
            mouseGridY = (int) (((long) my * BOARD_SIZE) / mapDim);
            repaint();
        } else if (mouseGridX != -1) {
            mouseGridX = -1;
            mouseGridY = -1;
            repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (mouseGridX != -1) {
            mouseGridX = -1;
            mouseGridY = -1;
            repaint();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int availW = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
        int availH = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        if (availW <= 0 || availH <= 0) return;

        int mapDim = Math.min(availW, availH);
        int mapX = MARGIN_LEFT + (availW - mapDim) / 2;
        int mapY = MARGIN_TOP;

        int mx = e.getX() - mapX;
        int my = e.getY() - mapY;

        if (mx >= 0 && mx < mapDim && my >= 0 && my < mapDim) {
            int gx = (int) (((long) mx * BOARD_SIZE) / mapDim);
            int gy = (int) (((long) my * BOARD_SIZE) / mapDim);
            eventHandler.addressAtMouseLocationRequested(gx + BOARD_SIZE * gy);
        }
    }

    public void addListener(MouseAddressRequest l) {
        eventCaster.add(l);
    }

    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
}
