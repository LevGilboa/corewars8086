package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.gui.effects.WarEffectsHandler;
import il.co.codeguru.corewars8086.memory.MemoryEventListener;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.utils.Unsigned;
import il.co.codeguru.corewars8086.war.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern High-Tech Battle Arena & Session Viewer:
 * - Sleek dark gaming dashboard with live stats & round counter
 * - Enhanced 256x256 arena map with coordinate axes & 10x magnifier
 * - Rich live participants table with color swatches, status pills, IP & score
 * - Real-time combat log with auto-scroll and quick actions
 * - Integrated CPU Debugger and Warrior Code Editor launch points
 */
public class WarFrame extends JFrame
        implements MemoryEventListener, CompetitionEventListener, MouseAddressRequest {
    private static final long serialVersionUID = 1L;

    /** Canvas showing the core war arena memory */
    private Canvas warCanvas;

    /** Combat log messaging area */
    private JTextArea messagesArea;
    private JCheckBox chkAutoScroll;

    /** Participants Table & Model */
    private JTable warriorTable;
    private WarriorTableModel tableModel;

    /** Round counter and badges */
    private int nRoundNumber;
    private JLabel lblRoundBadge;
    private JLabel lblAliveBadge;
    private JLabel lblSessionStatus;
    private JTextField roundNumber; // Preserved for legacy API compatibility

    // Debugger & Controls
    private JLabel addressFiled;
    private JButton btnCpuState;
    private CpuFrame cpuFrame;
    private JButton btnPause;
    private JButton btnSingleRound;
    private JButton btnWarriorEditor;
    private JSlider speedSlider;
    private JLabel lblSpeedValue;
    private JCheckBox enableEffectsCheckBox;

    private final Competition competition;

    /** Linear address of the first / one-past-last byte of the arena. */
    private static final int ARENA_START = War.ARENA_SEGMENT * RealModeAddress.PARAGRAPH_SIZE;
    private static final int ARENA_END = 2 * ARENA_START;

    private MemoryFrame memoryFrame;
    private WarEffectsHandler effectsHandler;

    public static class WarriorRow {
        public int id;
        public String name;
        public Color color;
        public boolean alive = true;
        public String deathReason = "";
        public int currentIp = 0;
        public float score = 0.0f;

        public WarriorRow(int id, String name, Color color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    class WarriorTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Color", "Warrior", "Status", "IP", "Score"};
        private final List<WarriorRow> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= rows.size()) return null;
            WarriorRow r = rows.get(row);
            switch (col) {
                case 0: return String.format("%02d", r.id + 1);
                case 1: return r.color;
                case 2: return r.name;
                case 3: return r;
                case 4: return r.alive ? String.format("0x%04X", r.currentIp & 0xFFFF) : "--";
                case 5: return String.format("%.1f", r.score);
                default: return "";
            }
        }

        public void addWarrior(WarriorRow r) {
            rows.add(r);
            fireTableDataChanged();
        }

        public void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        public WarriorRow getRow(int idx) {
            return (idx >= 0 && idx < rows.size()) ? rows.get(idx) : null;
        }

        public WarriorRow findByName(String name) {
            for (WarriorRow r : rows) {
                if (r.name.equalsIgnoreCase(name)) return r;
            }
            return null;
        }

        public int getAliveCount() {
            int count = 0;
            for (WarriorRow r : rows) {
                if (r.alive) count++;
            }
            return count;
        }
    }

    public WarFrame(final Competition competition) {
        super("CodeGuru Extreme - Battle Arena & Session Viewer");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.competition = competition;

        setSize(1220, 860);
        setMinimumSize(new Dimension(980, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(14, 17, 24));
        getContentPane().setLayout(new BorderLayout());

        buildUI();

        // Initialize effects handler
        effectsHandler = new WarEffectsHandler(warCanvas, competition);

        // Register listeners
        competition.addCompetitionEventListener(this);
        competition.addMemoryEventLister(this);
    }

    private void buildUI() {
        // 1. Top Dashboard Header
        add(buildHeaderDashboard(), BorderLayout.NORTH);

        // 2. Center Split (Arena on Left, Participants & Log on Right)
        add(buildCenterPanel(), BorderLayout.CENTER);

        // 3. Bottom Control Bar
        add(buildBottomControlBar(), BorderLayout.SOUTH);
    }

    private JPanel buildHeaderDashboard() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(new Color(18, 22, 31));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(38, 45, 62)),
                new EmptyBorder(8, 16, 8, 16)));

        // Left: Branding
        JPanel leftBrand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBrand.setOpaque(false);

        JLabel lblTitle = new JLabel("CODEGURU 8086");
        lblTitle.setFont(new Font("Consolas", Font.BOLD, 17));
        lblTitle.setForeground(new Color(0, 210, 255));
        leftBrand.add(lblTitle);

        JLabel lblSub = new JLabel("EXTREME");
        lblSub.setFont(new Font("Consolas", Font.BOLD, 14));
        lblSub.setForeground(new Color(255, 180, 0));
        leftBrand.add(lblSub);

        lblSessionStatus = new JLabel("READY");
        lblSessionStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblSessionStatus.setForeground(new Color(150, 165, 185));
        lblSessionStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 60, 80)),
                new EmptyBorder(2, 6, 2, 6)));
        leftBrand.add(Box.createHorizontalStrut(8));
        leftBrand.add(lblSessionStatus);
        bar.add(leftBrand, BorderLayout.WEST);

        // Center: Round Counter Badge
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        centerPanel.setOpaque(false);

        lblRoundBadge = new JLabel("ROUND: 0");
        lblRoundBadge.setFont(new Font("Consolas", Font.BOLD, 16));
        lblRoundBadge.setForeground(new Color(255, 215, 0));
        lblRoundBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 80, 20), 1),
                new EmptyBorder(3, 14, 3, 14)));
        lblRoundBadge.setBackground(new Color(35, 30, 15));
        lblRoundBadge.setOpaque(true);
        centerPanel.add(lblRoundBadge);
        bar.add(centerPanel, BorderLayout.CENTER);

        // Hidden JTextField for legacy API compatibility
        roundNumber = new JTextField("0", 4);
        roundNumber.setVisible(false);

        // Right: Survivors Count
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);

        lblAliveBadge = new JLabel("SURVIVORS: 0 / 0");
        lblAliveBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblAliveBadge.setForeground(new Color(0, 230, 120));
        lblAliveBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(20, 80, 45)),
                new EmptyBorder(3, 10, 3, 10)));
        lblAliveBadge.setBackground(new Color(15, 35, 24));
        lblAliveBadge.setOpaque(true);
        rightPanel.add(lblAliveBadge);

        bar.add(rightPanel, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Left Component: The 256x256 Battle Arena Canvas
        JPanel arenaHolder = new JPanel(new BorderLayout());
        arenaHolder.setBackground(new Color(11, 13, 19));
        arenaHolder.setBorder(BorderFactory.createLineBorder(new Color(36, 44, 60), 1));

        warCanvas = new Canvas();
        warCanvas.addListener(this);
        arenaHolder.add(warCanvas, BorderLayout.CENTER);

        // Right Component: Split between Participants Table and Combat Log
        JPanel sidebar = new JPanel(new GridLayout(2, 1, 0, 8));
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(380, 0));

        // Top of sidebar: Participants Table
        JPanel tablePanel = new JPanel(new BorderLayout(0, 4));
        tablePanel.setBackground(new Color(18, 22, 31));
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(36, 44, 60)),
                new EmptyBorder(6, 6, 6, 6)));

        JLabel lblTableTitle = new JLabel("WARRIORS & PARTICIPANTS");
        lblTableTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTableTitle.setForeground(new Color(0, 210, 255));
        tablePanel.add(lblTableTitle, BorderLayout.NORTH);

        tableModel = new WarriorTableModel();
        warriorTable = new JTable(tableModel);
        warriorTable.setRowHeight(26);
        warriorTable.setBackground(new Color(20, 25, 36));
        warriorTable.setForeground(Color.WHITE);
        warriorTable.setSelectionBackground(new Color(35, 55, 85));
        warriorTable.setSelectionForeground(Color.WHITE);
        warriorTable.setShowGrid(true);
        warriorTable.setGridColor(new Color(32, 40, 56));
        warriorTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        warriorTable.getTableHeader().setBackground(new Color(28, 35, 50));
        warriorTable.getTableHeader().setForeground(new Color(180, 195, 215));
        warriorTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Column widths & custom renderers
        warriorTable.getColumnModel().getColumn(0).setPreferredWidth(28); // #
        warriorTable.getColumnModel().getColumn(1).setPreferredWidth(42); // Color
        warriorTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Warrior Name
        warriorTable.getColumnModel().getColumn(3).setPreferredWidth(85); // Status
        warriorTable.getColumnModel().getColumn(4).setPreferredWidth(65); // IP
        warriorTable.getColumnModel().getColumn(5).setPreferredWidth(45); // Score

        // Color Swatch Renderer
        warriorTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JPanel swatch = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (value instanceof Color) {
                            Graphics2D g2 = (Graphics2D) g;
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor((Color) value);
                            g2.fillRoundRect(8, 5, getWidth() - 16, getHeight() - 10, 6, 6);
                            g2.setColor(Color.WHITE);
                            g2.drawRoundRect(8, 5, getWidth() - 16, getHeight() - 10, 6, 6);
                        }
                    }
                };
                swatch.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return swatch;
            }
        });

        // Status Pill Renderer
        warriorTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = new JLabel();
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setOpaque(true);

                if (value instanceof WarriorRow) {
                    WarriorRow wr = (WarriorRow) value;
                    if (wr.alive) {
                        lbl.setText("ALIVE");
                        lbl.setForeground(new Color(0, 230, 120));
                        lbl.setBackground(new Color(15, 42, 26));
                        lbl.setBorder(BorderFactory.createLineBorder(new Color(25, 100, 50), 1));
                    } else {
                        lbl.setText("DEAD 💀");
                        lbl.setForeground(new Color(255, 90, 90));
                        lbl.setBackground(new Color(45, 18, 22));
                        lbl.setBorder(BorderFactory.createLineBorder(new Color(110, 30, 40), 1));
                        if (!wr.deathReason.isEmpty()) {
                            lbl.setToolTipText("Died due to: " + wr.deathReason);
                        }
                    }
                }
                return lbl;
            }
        });

        // Double-click row: Open CPU Debugger for this warrior
        warriorTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int r = warriorTable.getSelectedRow();
                    if (r != -1) {
                        WarriorRow wr = tableModel.getRow(r);
                        openCpuDebugger(wr != null ? wr.name : null);
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(warriorTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(32, 40, 56)));
        tableScroll.getViewport().setBackground(new Color(20, 25, 36));
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        sidebar.add(tablePanel);

        // Bottom of sidebar: Combat Log Console
        JPanel logPanel = new JPanel(new BorderLayout(0, 4));
        logPanel.setBackground(new Color(18, 22, 31));
        logPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(36, 44, 60)),
                new EmptyBorder(6, 6, 6, 6)));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel lblLogTitle = new JLabel("BATTLE COMBAT LOG");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogTitle.setForeground(new Color(255, 180, 0));
        logHeader.add(lblLogTitle, BorderLayout.WEST);

        chkAutoScroll = new JCheckBox("Auto-Scroll", true);
        chkAutoScroll.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkAutoScroll.setForeground(new Color(160, 175, 195));
        chkAutoScroll.setOpaque(false);
        logHeader.add(chkAutoScroll, BorderLayout.EAST);
        logPanel.add(logHeader, BorderLayout.NORTH);

        messagesArea = new JTextArea();
        messagesArea.setEditable(false);
        messagesArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        messagesArea.setBackground(new Color(14, 17, 24));
        messagesArea.setForeground(new Color(210, 220, 235));
        messagesArea.setCaretColor(Color.CYAN);

        JScrollPane logScroll = new JScrollPane(messagesArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(32, 40, 56)));
        logPanel.add(logScroll, BorderLayout.CENTER);
        sidebar.add(logPanel);

        // Add Arena on left, Sidebar on right
        center.add(arenaHolder, BorderLayout.CENTER);
        center.add(sidebar, BorderLayout.EAST);
        return center;
    }

    private JPanel buildBottomControlBar() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(new Color(18, 22, 31));
        container.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(38, 45, 62)));

        // Action Buttons Row
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setOpaque(false);

        btnPause = new JButton("Pause");
        btnPause.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPause.setBackground(new Color(220, 140, 20));
        btnPause.setForeground(Color.WHITE);
        btnPause.setEnabled(false);
        btnPause.addActionListener(event -> togglePauseResume());
        bar.add(btnPause);

        btnSingleRound = new JButton("Single Step");
        btnSingleRound.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnSingleRound.setEnabled(false);
        btnSingleRound.addActionListener(event -> {
            War war = competition.getCurrentWar();
            if (war != null) {
                war.runSingleRound();
            }
        });
        bar.add(btnSingleRound);

        bar.add(Box.createHorizontalStrut(8));

        btnCpuState = new JButton("View CPU");
        btnCpuState.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCpuState.setBackground(new Color(35, 75, 130));
        btnCpuState.setForeground(Color.WHITE);
        btnCpuState.setEnabled(false);
        btnCpuState.addActionListener(event -> openCpuDebugger(null));
        bar.add(btnCpuState);

        btnWarriorEditor = new JButton("Warrior Editor");
        btnWarriorEditor.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnWarriorEditor.setBackground(new Color(25, 90, 60));
        btnWarriorEditor.setForeground(Color.WHITE);
        btnWarriorEditor.addActionListener(event -> {
            WarriorEditorFrame editor = new WarriorEditorFrame(competition);
            editor.setVisible(true);
        });
        bar.add(btnWarriorEditor);

        bar.add(Box.createHorizontalStrut(12));

        // Speed Slider & Multiplier Label
        JLabel lblSpeedTitle = new JLabel("Speed:");
        lblSpeedTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblSpeedTitle.setForeground(new Color(180, 195, 215));
        bar.add(lblSpeedTitle);

        speedSlider = new JSlider(1, 100, competition.getSpeed());
        speedSlider.setPreferredSize(new Dimension(140, 24));
        speedSlider.setBackground(new Color(18, 22, 31));
        speedSlider.addChangeListener(e -> {
            int calculatedSpeed = (int) Math.pow(1.2, speedSlider.getValue());
            competition.setSpeed(calculatedSpeed);
            lblSpeedValue.setText(calculatedSpeed + "x");
        });
        bar.add(speedSlider);

        lblSpeedValue = new JLabel(competition.getSpeed() + "x");
        lblSpeedValue.setFont(new Font("Consolas", Font.BOLD, 12));
        lblSpeedValue.setForeground(new Color(0, 210, 255));
        bar.add(lblSpeedValue);

        bar.add(Box.createHorizontalStrut(10));

        enableEffectsCheckBox = new JCheckBox("Visual Effects ✨", true);
        enableEffectsCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        enableEffectsCheckBox.setForeground(new Color(190, 205, 225));
        enableEffectsCheckBox.setOpaque(false);
        enableEffectsCheckBox.addActionListener(e -> {
            if (effectsHandler != null) {
                effectsHandler.setEffectsEnabled(enableEffectsCheckBox.isSelected());
            }
        });
        bar.add(enableEffectsCheckBox);

        bar.add(Box.createHorizontalStrut(12));

        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeButton.addActionListener(e -> dispose());
        bar.add(closeButton);

        container.add(bar, BorderLayout.NORTH);

        // Bottom status row for memory hover inspection
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
        statusRow.setBackground(new Color(12, 14, 20));
        addressFiled = new JLabel("Click or hover over the arena map to inspect memory");
        addressFiled.setFont(new Font("Consolas", Font.PLAIN, 11));
        addressFiled.setForeground(new Color(140, 155, 175));
        statusRow.add(addressFiled);

        container.add(statusRow, BorderLayout.SOUTH);
        return container;
    }

    private void togglePauseResume() {
        War war = competition.getCurrentWar();
        if (war == null) return;
        if (war.isPaused()) {
            war.resume();
            btnPause.setText("Pause");
            btnPause.setBackground(new Color(220, 140, 20));
            btnSingleRound.setEnabled(false);
            lblSessionStatus.setText("RUNNING 🟢");
            lblSessionStatus.setForeground(new Color(0, 230, 120));
        } else {
            war.pause();
            btnPause.setText("Resume");
            btnPause.setBackground(new Color(0, 160, 80));
            btnSingleRound.setEnabled(true);
            lblSessionStatus.setText("PAUSED ⏸");
            lblSessionStatus.setForeground(new Color(255, 180, 0));
        }
    }

    private void openCpuDebugger(String warriorNameToSelect) {
        if (cpuFrame == null || !cpuFrame.isDisplayable()) {
            cpuFrame = new CpuFrame(competition);
        }
        cpuFrame.setVisible(true);
        cpuFrame.toFront();
        if (warriorNameToSelect != null) {
            // Select specified warrior
            for (int i = 0; i < 20; i++) {
                Warrior w = (competition.getCurrentWar() != null) ? competition.getCurrentWar().getWarrior(i) : null;
                if (w != null && w.getName().equalsIgnoreCase(warriorNameToSelect)) {
                    break;
                }
            }
        }
        cpuFrame.refreshState();
    }

    /** Add a message to the message zone */
    public void addMessage(String message) {
        messagesArea.append(message + "\n");
        if (chkAutoScroll != null && chkAutoScroll.isSelected()) {
            SwingUtilities.invokeLater(() -> messagesArea.setCaretPosition(messagesArea.getDocument().getLength()));
        }
    }

    /** Add a message to the message zone (with round number) */
    public void addMessage(int round, String message) {
        addMessage("[" + round + "] " + message);
    }

    /** @see MemoryEventListener#onMemoryWrite(RealModeAddress) */
    public void onMemoryWrite(RealModeAddress address) {
        int ipInsideArena = address.getLinearAddress() - ARENA_START;
        if (address.getLinearAddress() >= ARENA_START && address.getLinearAddress() < ARENA_END) {
            warCanvas.paintPixel(
                    Unsigned.unsignedShort(ipInsideArena),
                    (byte) competition.getCurrentWarrior());
            effectsHandler.onMemoryWrite(competition.getCurrentWarrior(), address);
        }
    }

    /** @see CompetitionEventListener#onWarStart(long) */
    public void onWarStart(long seed) {
        addMessage("=== Session started (Seed: " + seed + ") ===");
        effectsHandler.onWarStart();
        SwingUtilities.invokeLater(() -> {
            tableModel.clear();
            warCanvas.clear();
            lblSessionStatus.setText("RUNNING 🟢");
            lblSessionStatus.setForeground(new Color(0, 230, 120));

            War war = competition.getCurrentWar();
            if (war != null) {
                if (war.isPaused()) {
                    btnPause.setText("Resume");
                    btnPause.setBackground(new Color(0, 160, 80));
                    btnSingleRound.setEnabled(true);
                    lblSessionStatus.setText("PAUSED ⏸");
                    lblSessionStatus.setForeground(new Color(255, 180, 0));
                } else {
                    btnPause.setText("Pause");
                    btnPause.setBackground(new Color(220, 140, 20));
                    btnSingleRound.setEnabled(false);
                }
            }
        });
    }

    /** @see CompetitionEventListener#onWarEnd(int, String) */
    public void onWarEnd(int reason, String winners) {
        String roundStr = String.format("ROUND: %,d", nRoundNumber);
        lblRoundBadge.setText(roundStr);
        roundNumber.setText(Integer.toString(nRoundNumber));

        switch (reason) {
            case SINGLE_WINNER:
                addMessage(nRoundNumber, "🏆 Session over: The winner is " + winners + "!");
                lblSessionStatus.setText("FINISHED (WINNER: " + winners + ")");
                lblSessionStatus.setForeground(new Color(255, 215, 0));
                break;
            case MAX_ROUND_REACHED:
                addMessage(nRoundNumber, "⏱ Maximum round reached. Winners: " + winners);
                lblSessionStatus.setText("DRAW / MAX ROUNDS");
                lblSessionStatus.setForeground(new Color(255, 180, 0));
                break;
            case ABORTED:
                addMessage(nRoundNumber, "🛑 Session aborted: " + winners);
                lblSessionStatus.setText("ABORTED");
                lblSessionStatus.setForeground(new Color(255, 80, 80));
                break;
            default:
                break;
        }

        effectsHandler.onWarEnd(reason, winners, nRoundNumber);
    }

    /** @see CompetitionEventListener#onRound(int) */
    public void onRound(int round) {
        nRoundNumber = round;
        effectsHandler.onRound(round);
        if ((nRoundNumber % 500) == 0) {
            SwingUtilities.invokeLater(() -> {
                lblRoundBadge.setText(String.format("ROUND: %,d", nRoundNumber));
                roundNumber.setText(Integer.toString(nRoundNumber));
                updateSurvivorBadge();
            });
        }
        btnCpuState.setEnabled(true);
        btnPause.setEnabled(true);
    }

    /** @see CompetitionEventListener#onWarriorBirth(String) */
    public void onWarriorBirth(String warriorName) {
        addMessage(nRoundNumber, "🟢 " + warriorName + " enters the arena.");
        effectsHandler.onWarriorBirth();

        SwingUtilities.invokeLater(() -> {
            int idx = tableModel.getRowCount();
            Color color = warCanvas.getColorForWarrior(idx);
            WarriorRow row = new WarriorRow(idx, warriorName, color);

            // Fetch initial IP
            War war = competition.getCurrentWar();
            if (war != null && idx < war.getNumWarriors()) {
                Warrior w = war.getWarrior(idx);
                if (w != null) {
                    row.currentIp = w.getCpuState().getIP() & 0xFFFF;
                }
            }

            tableModel.addWarrior(row);
            updateSurvivorBadge();
        });
    }

    /** @see CompetitionEventListener#onWarriorDeath(String) */
    public void onWarriorDeath(String warriorName, String reason) {
        addMessage(nRoundNumber, "💀 " + warriorName + " died due to " + reason + ".");
        effectsHandler.onWarriorDeath(warriorName);

        SwingUtilities.invokeLater(() -> {
            WarriorRow row = tableModel.findByName(warriorName);
            if (row != null) {
                row.alive = false;
                row.deathReason = reason;
                tableModel.fireTableDataChanged();
            }
            updateSurvivorBadge();
        });
    }

    private void updateSurvivorBadge() {
        int alive = tableModel.getAliveCount();
        int total = tableModel.getRowCount();
        lblAliveBadge.setText(String.format("SURVIVORS: %d / %d", alive, total));
        if (alive <= 1 && total > 1) {
            lblAliveBadge.setForeground(new Color(255, 180, 0));
        } else {
            lblAliveBadge.setForeground(new Color(0, 230, 120));
        }
    }

    public void onCompetitionStart() {
        btnCpuState.setEnabled(true);
        btnPause.setEnabled(true);
    }

    public void onCompetitionEnd() {
        btnCpuState.setEnabled(false);
        btnPause.setEnabled(false);
        lblSessionStatus.setText("COMPETITION ENDED");
    }

    @Override
    public void onEndRound() {
        this.warCanvas.deletePointers();
        effectsHandler.onEndRound();

        War war = this.competition.getCurrentWar();
        if (war == null) return;

        for (int i = 0; i < war.getNumWarriors(); i++) {
            Warrior w = war.getWarrior(i);
            if (w != null && w.isAlive()) {
                short ip = w.getCpuState().getIP();
                short cs = w.getCpuState().getCS();
                int ipInsideArena = new RealModeAddress(cs, ip).getLinearAddress() - ARENA_START;

                this.warCanvas.paintPointer((char) ipInsideArena, (byte) i);

                // Update row IP in table
                WarriorRow row = tableModel.findByName(w.getName());
                if (row != null) {
                    row.currentIp = ip & 0xFFFF;
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (competition != null && competition.getCurrentWar() != null) {
            competition.getCurrentWar().pause();
            try {
                Thread.sleep(200);
            } catch (Exception ignored) {}
            competition.removeCompetitionEventListener(this);
            competition.removeMemoryEventLister(this);
            competition.getCurrentWar().resume();
        }

        if (cpuFrame != null) {
            cpuFrame.dispose();
        }

        if (warCanvas != null) {
            warCanvas.dispose();
        }

        if (competition != null) {
            competition.setSpeed(Competition.MAXIMUM_SPEED);
        }
        super.dispose();
    }

    @Override
    public void addressAtMouseLocationRequested(int address) {
        RealModeAddress tmp = new RealModeAddress(War.ARENA_SEGMENT, (short) address);
        byte data = 0;
        try {
            data = this.competition.getCurrentWar().getMemory().readByte(tmp);
        } catch (Exception ignored) {}

        this.addressFiled.setText(String.format("INSPECTED: 0x%04X | VALUE: 0x%02X (%d)",
                address & 0xFFFF, data & 0xFF, data & 0xFF));

        if (memoryFrame == null || !memoryFrame.isVisible()) {
            memoryFrame = new MemoryFrame(competition, tmp.getLinearAddress());
            this.competition.addCompetitionEventListener(memoryFrame);
        } else {
            memoryFrame.refresh(tmp.getLinearAddress());
        }
    }

    // Preserved for legacy API compatibility
    public static class WarriorInfo {
        String name;
        boolean alive;

        public WarriorInfo(String name) {
            this.name = name;
            this.alive = true;
        }

        @Override public String toString() { return name; }
        @Override public boolean equals(Object obj) {
            return (obj instanceof String) && (((String) obj).equals(name));
        }
    }
}
