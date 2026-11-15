package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.cpu.CpuState;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;
import il.co.codeguru.corewars8086.war.War;
import il.co.codeguru.corewars8086.war.Warrior;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Arena Map View with coordinate axes (0000-E000 on Y, 00-E0 on X),
 * register pointer callouts (CS:IP, DS:AX, DS:BX, etc.), pixel value inspection,
 * bold/vibrant preview of the pending instruction's target, and an integrated Magnifier Lens.
 */
public class ArenaMapView extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int ARENA_DIM = 256;
    private static final int MARGIN_LEFT = 45;
    private static final int MARGIN_TOP = 15;
    private static final int MARGIN_BOTTOM = 40;
    private static final int MARGIN_RIGHT = 15;

    private byte[] arenaBytes = new byte[War.ARENA_SIZE];
    private byte[] arenaOwners = new byte[War.ARENA_SIZE];

    private int hoveredOffset = -1;
    private byte hoveredVal = (byte) 0xCC;

    // Ghost preview for the next opcode action
    private Integer ghostTargetOffset = null;
    private boolean ghostIsWrite = false;
    private int ghostLength = 1;
    private String ghostLabel = null;
    private int ghostIpOffset = 0;

    // Callout pointers
    public static class PointerBadge {
        public final String name;
        public final int offset;
        public final Color color;

        public PointerBadge(String name, int offset, Color color) {
            this.name = name;
            this.offset = offset & 0xFFFF;
            this.color = color;
        }
    }

    private final List<PointerBadge> activePointers = new ArrayList<>();

    public ArenaMapView() {
        setBackground(Color.BLACK);
        setOpaque(true);
        for (int i = 0; i < arenaBytes.length; i++) {
            arenaBytes[i] = (byte) 0xCC;
            arenaOwners[i] = -1;
        }

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e.getX(), e.getY());
            }
        });
    }

    private void handleMouseMove(int mx, int my) {
        int mapW = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
        int mapH = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        if (mapW <= 0 || mapH <= 0) return;

        if (mx >= MARGIN_LEFT && mx < MARGIN_LEFT + mapW &&
            my >= MARGIN_TOP && my < MARGIN_TOP + mapH) {
            int gridX = (int) (((long) (mx - MARGIN_LEFT) * ARENA_DIM) / mapW);
            int gridY = (int) (((long) (my - MARGIN_TOP) * ARENA_DIM) / mapH);
            if (gridX >= 0 && gridX < ARENA_DIM && gridY >= 0 && gridY < ARENA_DIM) {
                hoveredOffset = gridY * ARENA_DIM + gridX;
                hoveredVal = arenaBytes[hoveredOffset];
                repaint();
                return;
            }
        }
        if (hoveredOffset != -1) {
            hoveredOffset = -1;
            repaint();
        }
    }

    public void updateArena(RealModeMemory memory, Warrior currentWarrior) {
        if (memory == null) return;
        try {
            for (int i = 0; i < War.ARENA_SIZE; i++) {
                arenaBytes[i] = memory.readByte(new RealModeAddress(War.ARENA_SEGMENT, (short) i));
            }
        } catch (Exception ignored) {}

        activePointers.clear();
        if (currentWarrior != null) {
            CpuState s = currentWarrior.getCpuState();
            // CS:IP (Code pointer)
            activePointers.add(new PointerBadge("CS:IP", s.getIP() & 0xFFFF, new Color(255, 215, 0)));

            // Data pointers (if segment is ARENA_SEGMENT or DS)
            if (s.getDS() == War.ARENA_SEGMENT) {
                activePointers.add(new PointerBadge("DS:AX", s.getAX() & 0xFFFF, new Color(100, 200, 255)));
                activePointers.add(new PointerBadge("DS:BX", s.getBX() & 0xFFFF, new Color(130, 220, 255)));
                activePointers.add(new PointerBadge("DS:DI", s.getDI() & 0xFFFF, new Color(160, 230, 255)));
            }
            if (s.getES() == War.ARENA_SEGMENT) {
                activePointers.add(new PointerBadge("ES:SI", s.getSI() & 0xFFFF, new Color(200, 150, 255)));
                activePointers.add(new PointerBadge("ES:DI", s.getDI() & 0xFFFF, new Color(220, 170, 255)));
            }
            if (s.getSS() == War.ARENA_SEGMENT) {
                activePointers.add(new PointerBadge("SS:BP", s.getBP() & 0xFFFF, new Color(255, 130, 130)));
            }
        }
        repaint();
    }

    public void setGhostPreview(Integer targetOffset, boolean isWrite, int length, String label, int ipOffset) {
        this.ghostTargetOffset = targetOffset;
        this.ghostIsWrite = isWrite;
        this.ghostLength = length;
        this.ghostLabel = label;
        this.ghostIpOffset = ipOffset;
        repaint();
    }

    public void markPixel(int offset, byte warriorColorIndex) {
        if (offset >= 0 && offset < War.ARENA_SIZE) {
            arenaOwners[offset] = warriorColorIndex;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int totalW = getWidth();
        int totalH = getHeight();

        // Background
        g2.setColor(new Color(15, 17, 21));
        g2.fillRect(0, 0, totalW, totalH);

        int mapX = MARGIN_LEFT;
        int mapY = MARGIN_TOP;
        int mapW = totalW - MARGIN_LEFT - MARGIN_RIGHT;
        int mapH = totalH - MARGIN_TOP - MARGIN_BOTTOM;

        if (mapW <= 0 || mapH <= 0) {
            g2.dispose();
            return;
        }

        // Draw Arena Grid
        g2.setColor(Color.BLACK);
        g2.fillRect(mapX, mapY, mapW, mapH);

        // Draw memory bytes
        double scaleX = (double) mapW / ARENA_DIM;
        double scaleY = (double) mapH / ARENA_DIM;

        for (int y = 0; y < ARENA_DIM; y++) {
            for (int x = 0; x < ARENA_DIM; x++) {
                int off = y * ARENA_DIM + x;
                byte owner = arenaOwners[off];
                byte b = arenaBytes[off];

                if (owner != -1) {
                    g2.setColor(ColorHolder.getInstance().getColor(owner, false));
                    int px = mapX + (int) (x * scaleX);
                    int py = mapY + (int) (y * scaleY);
                    int pw = Math.max(1, (int) Math.ceil(scaleX));
                    int ph = Math.max(1, (int) Math.ceil(scaleY));
                    g2.fillRect(px, py, pw, ph);
                } else if (b != (byte) 0xCC) {
                    // Written memory without registered owner
                    g2.setColor(new Color(60, 75, 95));
                    int px = mapX + (int) (x * scaleX);
                    int py = mapY + (int) (y * scaleY);
                    g2.fillRect(px, py, Math.max(1, (int) scaleX), Math.max(1, (int) scaleY));
                }
            }
        }

        // Arena Border
        g2.setColor(new Color(80, 88, 105));
        g2.drawRect(mapX - 1, mapY - 1, mapW + 1, mapH + 1);

        // Draw Axes & Labels
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(150, 160, 175));

        // Y Axis ticks (every 0x2000 = 8192 bytes = 32 rows)
        for (int yOff = 0; yOff <= 0xE000; yOff += 0x2000) {
            int row = yOff / ARENA_DIM;
            int tickY = mapY + (int) (row * scaleY);
            String label = String.format("%04X", yOff);
            g2.drawString(label, 4, tickY + 4);
            g2.drawLine(mapX - 4, tickY, mapX, tickY);
        }

        // X Axis ticks (every 0x20 = 32 columns)
        for (int xOff = 0; xOff < ARENA_DIM; xOff += 0x20) {
            int tickX = mapX + (int) (xOff * scaleX);
            String label = String.format("%02X", xOff);
            g2.drawString(label, tickX - 6, mapY + mapH + 15);
            g2.drawLine(tickX, mapY + mapH, tickX, mapY + mapH + 4);
        }

        // Draw Ghost / Bold Action Preview of Next Opcode
        if (ghostTargetOffset != null) {
            int targetX = ghostTargetOffset % ARENA_DIM;
            int targetY = ghostTargetOffset / ARENA_DIM;
            int pixelX = mapX + (int) (targetX * scaleX);
            int pixelY = mapY + (int) (targetY * scaleY);

            int ipX = ghostIpOffset % ARENA_DIM;
            int ipY = ghostIpOffset / ARENA_DIM;
            int curIpPixelX = mapX + (int) (ipX * scaleX);
            int curIpPixelY = mapY + (int) (ipY * scaleY);

            Color ghostPrimary = ghostIsWrite ? new Color(255, 95, 20) : new Color(0, 220, 255);
            Color ghostHalo = ghostIsWrite ? new Color(255, 100, 20, 100) : new Color(0, 200, 255, 100);
            Color ghostOuterHalo = ghostIsWrite ? new Color(255, 120, 30, 45) : new Color(0, 220, 255, 45);

            // 1. Double-pass high-contrast dashed trajectory line from current CS:IP to target
            Stroke oldStroke = g2.getStroke();
            // Pass 1: Dark drop-shadow outline
            Stroke shadowDashed = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0);
            g2.setStroke(shadowDashed);
            g2.setColor(new Color(0, 0, 0, 220));
            g2.drawLine(curIpPixelX, curIpPixelY, pixelX, pixelY);

            // Pass 2: Vibrant neon foreground dashed line
            Stroke brightDashed = new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0);
            g2.setStroke(brightDashed);
            g2.setColor(ghostPrimary);
            g2.drawLine(curIpPixelX, curIpPixelY, pixelX, pixelY);
            g2.setStroke(oldStroke);

            // 2. Affected bytes preview (e.g. INT 86 bomb 128 bytes or MOVSW 2 bytes)
            if (ghostIsWrite && ghostLength > 1) {
                g2.setColor(new Color(255, 130, 30, 140));
                for (int i = 0; i < Math.min(ghostLength, 256); i++) {
                    int off = (ghostTargetOffset + i) & 0xFFFF;
                    int bx = mapX + (int) ((off % ARENA_DIM) * scaleX);
                    int by = mapY + (int) ((off / ARENA_DIM) * scaleY);
                    g2.fillRect(bx, by, Math.max(2, (int) Math.ceil(scaleX)), Math.max(2, (int) Math.ceil(scaleY)));
                }
            }

            // 3. Large High-Visibility Concentric Reticle (Bullseye)
            // Outer radiant halo
            g2.setColor(ghostOuterHalo);
            g2.fillOval(pixelX - 22, pixelY - 22, 44, 44);

            // Mid vibrant ring
            g2.setColor(ghostHalo);
            g2.fillOval(pixelX - 12, pixelY - 12, 24, 24);

            // Center target core
            g2.setColor(ghostPrimary);
            g2.drawOval(pixelX - 12, pixelY - 12, 24, 24);
            g2.drawRect(pixelX - 4, pixelY - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillRect(pixelX - 2, pixelY - 2, 4, 4);

            // Extended Crosshairs
            g2.setColor(new Color(0, 0, 0, 200));
            g2.drawLine(pixelX - 18, pixelY, pixelX + 18, pixelY);
            g2.drawLine(pixelX, pixelY - 18, pixelX, pixelY + 18);
            g2.setColor(ghostPrimary);
            g2.drawLine(pixelX - 16, pixelY, pixelX + 16, pixelY);
            g2.drawLine(pixelX, pixelY - 16, pixelX, pixelY + 16);

            // 4. Floating preview badge tag
            if (ghostLabel != null && !ghostLabel.isEmpty()) {
                g2.setFont(new Font("Consolas", Font.BOLD, 11));
                int tagW = g2.getFontMetrics().stringWidth(ghostLabel) + 16;
                int tagH = 22;

                int tagX = pixelX + 12;
                int tagY = pixelY - 26;
                if (tagX + tagW > mapX + mapW) tagX = pixelX - tagW - 8;
                if (tagY < mapY + 6) tagY = pixelY + 14;

                g2.setColor(new Color(15, 20, 28, 240));
                g2.fillRoundRect(tagX, tagY, tagW, tagH, 6, 6);
                g2.setColor(ghostPrimary);
                g2.drawRoundRect(tagX, tagY, tagW, tagH, 6, 6);

                g2.setColor(Color.WHITE);
                g2.drawString(ghostLabel, tagX + 8, tagY + 15);
            }
        }

        // Draw Integrated Magnifier Lens (Zoom 10x) if ghost preview or mouse hover is active
        int focusOffset = (hoveredOffset != -1) ? hoveredOffset : (ghostTargetOffset != null ? ghostTargetOffset : -1);
        if (focusOffset >= 0 && focusOffset < War.ARENA_SIZE) {
            int focusX = focusOffset % ARENA_DIM;
            int focusY = focusOffset / ARENA_DIM;

            int lensGrid = 7; // 7x7 cells
            int cellSize = 16; // 16px per cell
            int gridW = lensGrid * cellSize;
            int lensW = gridW + 16;
            int lensH = gridW + 36;

            int lensX = mapX + mapW - lensW - 8;
            int lensY = mapY + 8;

            // If focus point is in top-right, move lens to bottom-right so it doesn't obstruct the target
            int tX = mapX + (int) (focusX * scaleX);
            int tY = mapY + (int) (focusY * scaleY);
            if (tX > lensX - 30 && tY < lensY + lensH + 30) {
                lensY = mapY + mapH - lensH - 8;
            }

            // Lens Panel Background & Border
            g2.setColor(new Color(15, 18, 25, 245));
            g2.fillRoundRect(lensX, lensY, lensW, lensH, 8, 8);
            Color borderColor = (ghostTargetOffset != null && focusOffset == ghostTargetOffset) ?
                    (ghostIsWrite ? new Color(255, 120, 30) : new Color(0, 220, 255)) :
                    new Color(90, 130, 180);
            g2.setColor(borderColor);
            g2.drawRoundRect(lensX, lensY, lensW, lensH, 8, 8);

            // Lens Title
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            String title = String.format("ZOOM: 0x%04X", focusOffset);
            g2.setColor(borderColor);
            g2.drawString(title, lensX + 8, lensY + 15);

            int startGX = lensX + 8;
            int startGY = lensY + 22;

            g2.setFont(new Font("Consolas", Font.PLAIN, 10));
            int half = lensGrid / 2;
            for (int dy = -half; dy <= half; dy++) {
                for (int dx = -half; dx <= half; dx++) {
                    int cx = (focusX + dx + ARENA_DIM) % ARENA_DIM;
                    int cy = (focusY + dy + ARENA_DIM) % ARENA_DIM;
                    int off = cy * ARENA_DIM + cx;

                    int cellX = startGX + (dx + half) * cellSize;
                    int cellY = startGY + (dy + half) * cellSize;

                    byte owner = arenaOwners[off];
                    byte b = arenaBytes[off];

                    if (owner != -1) {
                        g2.setColor(ColorHolder.getInstance().getColor(owner, false));
                    } else if (b != (byte) 0xCC) {
                        g2.setColor(new Color(55, 75, 105));
                    } else {
                        g2.setColor(new Color(24, 27, 34));
                    }
                    g2.fillRect(cellX, cellY, cellSize - 1, cellSize - 1);

                    // Highlight the exact center target byte
                    if (dx == 0 && dy == 0) {
                        g2.setColor(Color.WHITE);
                        g2.drawRect(cellX - 1, cellY - 1, cellSize, cellSize);
                        g2.setColor(borderColor);
                        g2.drawRect(cellX - 2, cellY - 2, cellSize + 2, cellSize + 2);
                    }

                    // Byte hex text inside cell
                    g2.setColor((owner != -1 || b != (byte) 0xCC || (dx == 0 && dy == 0)) ? Color.WHITE : new Color(120, 130, 140));
                    g2.drawString(String.format("%02X", b & 0xFF), cellX + 2, cellY + cellSize - 4);
                }
            }
        }

        // Draw Callout Pointers (Labels & Lines to grid)
        int calloutY = mapY + 20;
        int badgeX = mapX + 8;

        for (PointerBadge badge : activePointers) {
            int targetX = badge.offset % ARENA_DIM;
            int targetY = badge.offset / ARENA_DIM;

            int pixelX = mapX + (int) (targetX * scaleX);
            int pixelY = mapY + (int) (targetY * scaleY);

            // Draw line from callout tag to target pixel
            g2.setColor(new Color(badge.color.getRed(), badge.color.getGreen(), badge.color.getBlue(), 180));
            g2.drawLine(badgeX + 50, calloutY + 8, pixelX, calloutY + 8);
            g2.drawLine(pixelX, calloutY + 8, pixelX, pixelY);

            // Target marker
            g2.setColor(badge.color);
            g2.fillRect(pixelX - 2, pixelY - 2, 5, 5);

            // Callout Badge Box
            g2.setColor(new Color(30, 35, 45, 230));
            g2.fillRoundRect(badgeX, calloutY, 48, 16, 6, 6);
            g2.setColor(badge.color);
            g2.drawRoundRect(badgeX, calloutY, 48, 16, 6, 6);

            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            g2.drawString(badge.name, badgeX + 5, calloutY + 12);

            calloutY += 24;
        }

        // Bottom hover inspection status
        if (hoveredOffset != -1) {
            String hoverText = String.format("OFFSET: 0x%04X | VALUE: 0x%02X (%d)",
                    hoveredOffset, hoveredVal & 0xFF, hoveredVal & 0xFF);
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            int textW = g2.getFontMetrics().stringWidth(hoverText);
            g2.drawString(hoverText, mapX + (mapW - textW) / 2, totalH - 8);
        }

        g2.dispose();
    }
}
