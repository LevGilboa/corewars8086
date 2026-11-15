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
 * and faint/ghost preview of the pending instruction's memory write or branch target.
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

        // Draw Ghost / Faint Preview of Next Opcode Action
        if (ghostTargetOffset != null) {
            int targetX = ghostTargetOffset % ARENA_DIM;
            int targetY = ghostTargetOffset / ARENA_DIM;
            int pixelX = mapX + (int) (targetX * scaleX);
            int pixelY = mapY + (int) (targetY * scaleY);

            int ipX = ghostIpOffset % ARENA_DIM;
            int ipY = ghostIpOffset / ARENA_DIM;
            int curIpPixelX = mapX + (int) (ipX * scaleX);
            int curIpPixelY = mapY + (int) (ipY * scaleY);

            Color ghostPrimary = ghostIsWrite ? new Color(255, 110, 40) : new Color(40, 200, 255);
            Color ghostHalo = ghostIsWrite ? new Color(255, 100, 30, 80) : new Color(30, 180, 255, 80);
            Color ghostDashed = ghostIsWrite ? new Color(255, 140, 50, 180) : new Color(80, 210, 255, 180);

            // 1. Dashed trajectory line from current CS:IP to target
            Stroke oldStroke = g2.getStroke();
            Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5, 4}, 0);
            g2.setStroke(dashed);
            g2.setColor(ghostDashed);
            g2.drawLine(curIpPixelX, curIpPixelY, pixelX, pixelY);
            g2.setStroke(oldStroke);

            // 2. Affected bytes preview (for writes spanning multiple bytes, e.g. INT 86 bomb or MOVSW)
            if (ghostIsWrite && ghostLength > 1) {
                g2.setColor(new Color(255, 120, 40, 120));
                for (int i = 0; i < Math.min(ghostLength, 256); i++) {
                    int off = (ghostTargetOffset + i) & 0xFFFF;
                    int bx = mapX + (int) ((off % ARENA_DIM) * scaleX);
                    int by = mapY + (int) ((off / ARENA_DIM) * scaleY);
                    g2.fillRect(bx, by, Math.max(2, (int) Math.ceil(scaleX)), Math.max(2, (int) Math.ceil(scaleY)));
                }
            }

            // 3. Faint pulsating halo / highlight box at target
            int haloSize = 14;
            g2.setColor(ghostHalo);
            g2.fillOval(pixelX - haloSize / 2, pixelY - haloSize / 2, haloSize, haloSize);

            // 4. Crosshair / Target reticle
            g2.setColor(ghostPrimary);
            g2.drawRect(pixelX - 4, pixelY - 4, 8, 8);
            g2.drawLine(pixelX - 6, pixelY, pixelX + 6, pixelY);
            g2.drawLine(pixelX, pixelY - 6, pixelX, pixelY + 6);

            // 5. Floating preview badge tag
            if (ghostLabel != null && !ghostLabel.isEmpty()) {
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                int tagW = g2.getFontMetrics().stringWidth(ghostLabel) + 12;
                int tagH = 18;

                int tagX = pixelX + 10;
                int tagY = pixelY - 22;
                if (tagX + tagW > mapX + mapW) tagX = pixelX - tagW - 8;
                if (tagY < mapY + 4) tagY = pixelY + 10;

                g2.setColor(new Color(20, 24, 32, 220));
                g2.fillRoundRect(tagX, tagY, tagW, tagH, 6, 6);
                g2.setColor(ghostPrimary);
                g2.drawRoundRect(tagX, tagY, tagW, tagH, 6, 6);

                g2.drawString(ghostLabel, tagX + 6, tagY + 13);
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
            String hoverText = String.format("0x%04X: 0x%02X (%d)",
                    hoveredOffset, hoveredVal & 0xFF, hoveredVal & 0xFF);
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            int textW = g2.getFontMetrics().stringWidth(hoverText);
            g2.drawString(hoverText, mapX + (mapW - textW) / 2, totalH - 8);
        }

        g2.dispose();
    }
}
