package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.cpu.CpuState;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;
import il.co.codeguru.corewars8086.utils.Disassembler;
import il.co.codeguru.corewars8086.war.Competition;
import il.co.codeguru.corewars8086.war.CompetitionEventListener;
import il.co.codeguru.corewars8086.war.War;
import il.co.codeguru.corewars8086.war.Warrior;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern, rich CPU Debugger & State Viewer for CoreWars 8086.
 */
public class CpuFrame extends JFrame implements CompetitionEventListener {
    private static final long serialVersionUID = 1L;

    private final Competition competition;
    private JComboBox<String> warriorSelector;
    private JButton btnStep;
    private JButton btnPauseResume;
    private JButton btnRefresh;
    private JLabel lblStatus;

    // Registers
    private final Map<String, JTextField> regFields = new HashMap<>();
    private final Map<String, Short> previousRegValues = new HashMap<>();

    // Flags
    private final Map<String, JLabel> flagLabels = new HashMap<>();

    // Disassembly view
    private JTextPane codePane;

    // Stack view
    private JTextArea stackArea;

    private static final Color DARK_BG = new Color(28, 30, 36);
    private static final Color PANEL_BG = new Color(36, 40, 48);
    private static final Color TEXT_COLOR = new Color(220, 224, 232);
    private static final Color ACCENT_COLOR = new Color(74, 144, 226);
    private static final Color HIGHLIGHT_CHANGED = new Color(255, 179, 71);
    private static final Color FLAG_ON_BG = new Color(46, 139, 87);
    private static final Color FLAG_OFF_BG = new Color(60, 64, 72);

    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 13);
    private static final Font MONO_BOLD = new Font("Consolas", Font.BOLD, 13);

    public CpuFrame(Competition competition) {
        super("8086 CPU Debugger & State Viewer");
        this.competition = competition;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(960, 680);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        initComponents();
        updateWarriorList();
        refreshState();

        this.competition.addCompetitionEventListener(this);
        setVisible(true);
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(DARK_BG);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top Toolbar
        root.add(buildTopBar(), BorderLayout.NORTH);

        // Center split: Left (Code & Stack), Right (Registers & Flags)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildCodeAndStackPanel(), buildRegistersPanel());
        splitPane.setResizeWeight(0.55);
        splitPane.setBackground(DARK_BG);
        splitPane.setBorder(null);
        root.add(splitPane, BorderLayout.CENTER);

        // Bottom status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(DARK_BG);
        lblStatus = new JLabel(" Ready");
        lblStatus.setForeground(new Color(160, 170, 185));
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBar.add(lblStatus, BorderLayout.WEST);
        root.add(statusBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        bar.setBackground(PANEL_BG);
        bar.setBorder(BorderFactory.createLineBorder(new Color(55, 60, 72)));

        JLabel selectLabel = new JLabel("Warrior:");
        selectLabel.setForeground(TEXT_COLOR);
        selectLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        bar.add(selectLabel);

        warriorSelector = new JComboBox<>();
        warriorSelector.setPreferredSize(new Dimension(160, 26));
        warriorSelector.addActionListener(e -> {
            previousRegValues.clear();
            refreshState();
        });
        bar.add(warriorSelector);

        bar.add(Box.createHorizontalStrut(10));

        btnStep = new JButton(" Step (Single Opcode) ");
        styleButton(btnStep, new Color(40, 120, 200));
        btnStep.addActionListener(e -> doStep());
        bar.add(btnStep);

        btnPauseResume = new JButton("Pause");
        styleButton(btnPauseResume, new Color(100, 105, 115));
        btnPauseResume.addActionListener(e -> togglePause());
        bar.add(btnPauseResume);

        btnRefresh = new JButton("Refresh");
        styleButton(btnRefresh, new Color(70, 75, 85));
        btnRefresh.addActionListener(e -> refreshState());
        bar.add(btnRefresh);

        return bar;
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
    }

    private JPanel buildCodeAndStackPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DARK_BG);

        // Disassembly Panel
        JPanel codeContainer = new JPanel(new BorderLayout());
        codeContainer.setBackground(PANEL_BG);
        codeContainer.setBorder(createTitledBorder("Disassembly around CS:IP"));

        codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setBackground(new Color(20, 22, 26));
        codePane.setForeground(TEXT_COLOR);
        codePane.setFont(MONO_FONT);
        codePane.setContentType("text/html");

        JScrollPane codeScroll = new JScrollPane(codePane);
        codeScroll.setBorder(null);
        codeContainer.add(codeScroll, BorderLayout.CENTER);

        // Stack Panel
        JPanel stackContainer = new JPanel(new BorderLayout());
        stackContainer.setBackground(PANEL_BG);
        stackContainer.setBorder(createTitledBorder("Stack (SS:SP)"));
        stackContainer.setPreferredSize(new Dimension(100, 150));

        stackArea = new JTextArea();
        stackArea.setEditable(false);
        stackArea.setBackground(new Color(20, 22, 26));
        stackArea.setForeground(new Color(180, 220, 255));
        stackArea.setFont(MONO_FONT);
        JScrollPane stackScroll = new JScrollPane(stackArea);
        stackScroll.setBorder(null);
        stackContainer.add(stackScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeContainer, stackContainer);
        split.setResizeWeight(0.75);
        split.setBackground(DARK_BG);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildRegistersPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(DARK_BG);

        // General Purpose Registers
        JPanel general = new JPanel(new GridLayout(2, 4, 8, 8));
        general.setBackground(PANEL_BG);
        general.setBorder(createTitledBorder("General Registers (16-bit)"));
        String[] gRegs = {"AX", "BX", "CX", "DX"};
        for (String r : gRegs) {
            general.add(createRegBox(r));
        }
        panel.add(general);
        panel.add(Box.createVerticalStrut(8));

        // Index / Pointer Registers
        JPanel pointers = new JPanel(new GridLayout(2, 4, 8, 8));
        pointers.setBackground(PANEL_BG);
        pointers.setBorder(createTitledBorder("Pointers & Index Registers"));
        String[] pRegs = {"SI", "DI", "BP", "SP"};
        for (String r : pRegs) {
            pointers.add(createRegBox(r));
        }
        panel.add(pointers);
        panel.add(Box.createVerticalStrut(8));

        // Segment Registers + IP + Energy
        JPanel segments = new JPanel(new GridLayout(2, 4, 8, 8));
        segments.setBackground(PANEL_BG);
        segments.setBorder(createTitledBorder("Segments, IP & Energy"));
        String[] sRegs = {"CS", "IP", "DS", "ES", "SS", "Energy"};
        for (String r : sRegs) {
            segments.add(createRegBox(r));
        }
        panel.add(segments);
        panel.add(Box.createVerticalStrut(8));

        // Flags
        JPanel flags = new JPanel(new GridLayout(2, 5, 6, 6));
        flags.setBackground(PANEL_BG);
        flags.setBorder(createTitledBorder("Flags Register"));
        String[] flagNames = {"CF", "ZF", "SF", "OF", "PF", "AF", "DF", "IF", "TF"};
        for (String f : flagNames) {
            flags.add(createFlagBadge(f));
        }
        panel.add(flags);

        return panel;
    }

    private JPanel createRegBox(String name) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setOpaque(false);
        JLabel lbl = new JLabel(name + ":");
        lbl.setForeground(new Color(150, 160, 175));
        lbl.setFont(MONO_BOLD);
        p.add(lbl, BorderLayout.NORTH);

        JTextField tf = new JTextField("0000");
        tf.setEditable(false);
        tf.setBackground(new Color(24, 26, 32));
        tf.setForeground(TEXT_COLOR);
        tf.setFont(MONO_BOLD);
        tf.setHorizontalAlignment(JTextField.CENTER);
        tf.setBorder(BorderFactory.createLineBorder(new Color(60, 65, 78)));
        regFields.put(name, tf);
        p.add(tf, BorderLayout.CENTER);
        return p;
    }

    private JLabel createFlagBadge(String name) {
        JLabel lbl = new JLabel(name, SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(FLAG_OFF_BG);
        lbl.setForeground(new Color(180, 185, 195));
        lbl.setFont(MONO_BOLD);
        lbl.setBorder(BorderFactory.createLineBorder(new Color(50, 54, 64)));
        flagLabels.put(name, lbl);
        return lbl;
    }

    private TitledBorder createTitledBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(55, 60, 72)), title);
        b.setTitleColor(ACCENT_COLOR);
        b.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        return b;
    }

    private void updateWarriorList() {
        War war = competition.getCurrentWar();
        if (war == null) return;
        String curSelected = (String) warriorSelector.getSelectedItem();
        warriorSelector.removeAllItems();
        for (int i = 0; i < war.getNumWarriors(); i++) {
            Warrior w = war.getWarrior(i);
            warriorSelector.addItem(w.getName() + (w.isAlive() ? "" : " [DEAD]"));
        }
        if (curSelected != null) {
            for (int i = 0; i < warriorSelector.getItemCount(); i++) {
                if (warriorSelector.getItemAt(i).startsWith(curSelected)) {
                    warriorSelector.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private Warrior getSelectedWarrior() {
        War war = competition.getCurrentWar();
        if (war == null || warriorSelector.getSelectedIndex() < 0) return null;
        int idx = warriorSelector.getSelectedIndex();
        if (idx < war.getNumWarriors()) {
            return war.getWarrior(idx);
        }
        return null;
    }

    private void doStep() {
        War war = competition.getCurrentWar();
        if (war == null) {
            lblStatus.setText(" No active war. Click 'Start!' in the main window.");
            return;
        }
        if (!war.isPaused()) {
            war.pause();
        }
        war.runSingleRound();
        // Give the simulation thread a few milliseconds to process the single round, then refresh
        new Thread(() -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(this::refreshState);
        }).start();
    }

    private void togglePause() {
        War war = competition.getCurrentWar();
        if (war == null) {
            lblStatus.setText(" No active war. Click 'Start!' in the main window.");
            return;
        }
        if (war.isPaused()) {
            war.resume();
            btnPauseResume.setText("Pause");
            lblStatus.setText(" Simulation resumed.");
        } else {
            war.pause();
            btnPauseResume.setText("Resume");
            lblStatus.setText(" Simulation paused.");
            refreshState();
        }
    }

    public void refreshState() {
        SwingUtilities.invokeLater(() -> {
            War war = competition.getCurrentWar();
            if (war == null) {
                lblStatus.setText(" No active war session");
                return;
            }

            if (war.isPaused()) {
                btnPauseResume.setText("Resume");
            } else {
                btnPauseResume.setText("Pause");
            }

            Warrior warrior = getSelectedWarrior();
            if (warrior == null) return;

            CpuState s = warrior.getCpuState();
            RealModeMemory mem = war.getMemory();

            lblStatus.setText(String.format(" Warrior: %s  |  State: %s  |  Arena Segment: 0x%04X",
                    warrior.getName(), warrior.isAlive() ? "ALIVE" : "DEAD", War.ARENA_SEGMENT));

            // Update Registers with change highlights
            updateReg("AX", s.getAX());
            updateReg("BX", s.getBX());
            updateReg("CX", s.getCX());
            updateReg("DX", s.getDX());
            updateReg("SI", s.getSI());
            updateReg("DI", s.getDI());
            updateReg("BP", s.getBP());
            updateReg("SP", s.getSP());
            updateReg("CS", s.getCS());
            updateReg("IP", s.getIP());
            updateReg("DS", s.getDS());
            updateReg("ES", s.getES());
            updateReg("SS", s.getSS());
            updateReg("Energy", s.getEnergy());

            // Update Flags
            updateFlag("CF", s.getCarryFlag());
            updateFlag("ZF", s.getZeroFlag());
            updateFlag("SF", s.getSignFlag());
            updateFlag("OF", s.getOverflowFlag());
            updateFlag("PF", s.getParityFlag());
            updateFlag("AF", s.getAuxFlag());
            updateFlag("DF", s.getDirectionFlag());
            updateFlag("IF", s.getInterruptFlag());
            updateFlag("TF", s.getTrapFlag());

            // Disassemble instructions around CS:IP
            renderDisassembly(mem, s.getCS(), s.getIP());

            // Render Stack
            renderStack(mem, s.getSS(), s.getSP());
        });
    }

    private void updateReg(String name, short val) {
        JTextField tf = regFields.get(name);
        if (tf == null) return;
        String text = String.format("%04X", val & 0xFFFF);
        Short prev = previousRegValues.get(name);
        if (prev != null && prev != val) {
            tf.setForeground(HIGHLIGHT_CHANGED);
        } else {
            tf.setForeground(TEXT_COLOR);
        }
        previousRegValues.put(name, val);
        tf.setText(text);
    }

    private void updateFlag(String name, boolean on) {
        JLabel l = flagLabels.get(name);
        if (l == null) return;
        if (on) {
            l.setBackground(FLAG_ON_BG);
            l.setForeground(Color.WHITE);
            l.setText(name + " [1]");
        } else {
            l.setBackground(FLAG_OFF_BG);
            l.setForeground(new Color(150, 155, 165));
            l.setText(name + " [0]");
        }
    }

    private void renderDisassembly(RealModeMemory mem, short cs, short ip) {
        // Disassemble 14 instructions starting from current IP
        List<Disassembler.DecodedInstruction> instrs = Disassembler.disassembleMemory(mem, cs, ip, 14);

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Consolas,monospace; font-size:11px; background-color:#14161a; color:#dce0e8; padding:6px;'>");

        boolean isFirst = true;
        for (Disassembler.DecodedInstruction di : instrs) {
            if (isFirst) {
                // Highlight current instruction
                html.append(String.format(
                        "<div style='background-color:#1e3a5f; color:#ffdd57; padding:2px; font-weight:bold;'>" +
                                "<span style='color:#61afef;'>%04X:%04X</span> &nbsp;&nbsp;" +
                                "<span style='color:#abb2bf;'>%-10s</span> &nbsp;&nbsp;" +
                                "<span style='color:#98c379;'>%s</span> &nbsp;&nbsp;&nbsp;&larr; [IP]</div>",
                        cs & 0xFFFF, di.offset, di.hexBytes, di.text));
                isFirst = false;
            } else {
                html.append(String.format(
                        "<div style='padding:2px;'>" +
                                "<span style='color:#5c6370;'>%04X:%04X</span> &nbsp;&nbsp;" +
                                "<span style='color:#5c6370;'>%-10s</span> &nbsp;&nbsp;" +
                                "<span style='color:#e5c07b;'>%s</span></div>",
                        cs & 0xFFFF, di.offset, di.hexBytes, di.text));
            }
        }
        html.append("</body></html>");
        codePane.setText(html.toString());
        codePane.setCaretPosition(0);
    }

    private void renderStack(RealModeMemory mem, short ss, short sp) {
        StringBuilder sb = new StringBuilder();
        int baseSp = sp & 0xFFFF;
        sb.append(String.format("SS:SP = %04X:%04X\n", ss & 0xFFFF, baseSp));
        sb.append("--------------------------------------------------\n");

        for (int i = 0; i < 8; i++) {
            short curOffset = (short) (baseSp + (i * 2));
            try {
                short val = mem.readWord(new RealModeAddress(ss, curOffset));
                sb.append(String.format("[%04X:%04X]  ->  %04X", ss & 0xFFFF, curOffset & 0xFFFF, val & 0xFFFF));
                if (i == 0) sb.append("  <-- TOP OF STACK");
                sb.append("\n");
            } catch (Exception e) {
                sb.append(String.format("[%04X:%04X]  ->  <error>\n", ss & 0xFFFF, curOffset & 0xFFFF));
            }
        }
        stackArea.setText(sb.toString());
        stackArea.setCaretPosition(0);
    }

    @Override
    public void onWarStart(long seed) {
        SwingUtilities.invokeLater(() -> {
            updateWarriorList();
            refreshState();
        });
    }

    @Override
    public void onWarEnd(int reason, String winners) {
        SwingUtilities.invokeLater(this::refreshState);
    }

    @Override
    public void onRound(int round) {
    }

    @Override
    public void onWarriorBirth(String warriorName) {
        SwingUtilities.invokeLater(this::updateWarriorList);
    }

    @Override
    public void onWarriorDeath(String warriorName, String reason) {
        SwingUtilities.invokeLater(this::refreshState);
    }

    @Override
    public void onCompetitionStart() {
    }

    @Override
    public void onCompetitionEnd() {
    }

    @Override
    public void onEndRound() {
        refreshState();
    }

    @Override
    public void dispose() {
        competition.removeCompetitionEventListener(this);
        super.dispose();
    }
}
