package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.cpu.CpuState;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;
import il.co.codeguru.corewars8086.utils.Disassembler;
import il.co.codeguru.corewars8086.utils.Unsigned;
import il.co.codeguru.corewars8086.war.Competition;
import il.co.codeguru.corewars8086.war.CompetitionEventListener;
import il.co.codeguru.corewars8086.war.War;
import il.co.codeguru.corewars8086.war.Warrior;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-featured 8086 Integrated Debugger:
 * - Code Disassembly with Go-To navigation
 * - Stack, Shared Memory & Watch tables
 * - Full 16-bit Register & Flag bank
 * - Interactive Arena Map with Axes & Dynamic Register Pointers
 */
public class CpuFrame extends JFrame implements CompetitionEventListener {
    private static final long serialVersionUID = 1L;

    private final Competition competition;

    // Controls
    private JComboBox<String> warriorSelector;
    private JTextField txtGoTo;
    private JButton btnGoTo;
    private JLabel lblStepCounter;
    private JButton btnResumePause;
    private JButton btnStep;
    private JSlider speedSlider;
    private JLabel lblSpeedValue;

    // Disassembly
    private JTextPane codePane;
    private int customViewOffset = -1;
    private int stepCount = 0;

    // Registers
    private final Map<String, JTextField> regFields = new HashMap<>();
    private final Map<String, Short> prevRegs = new HashMap<>();
    private final Map<String, JCheckBox> flagBoxes = new HashMap<>();

    // Middle column views
    private JTextArea stackArea;
    private JTextArea sharedArea;
    private JTable watchTable;
    private DefaultTableModel watchModel;

    // Right column: Arena Map
    private ArenaMapView arenaMapView;

    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 12);
    private static final Font MONO_BOLD = new Font("Consolas", Font.BOLD, 12);

    public CpuFrame(Competition competition) {
        super("CodeGuru 8086 - Professional Debugger");
        this.competition = competition;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1220, 720);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);

        buildUI();
        updateWarriorList();
        refreshState();

        this.competition.addCompetitionEventListener(this);
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new GridLayout(1, 3, 6, 6));
        root.setBackground(new Color(240, 242, 245));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));

        // Column 1: Code, Controls & Registers
        root.add(buildLeftColumn());

        // Column 2: Stack, Shared Memory, Watch
        root.add(buildMiddleColumn());

        // Column 3: Arena Map with coordinate axes & pointers
        root.add(buildRightColumn());

        setContentPane(root);
    }

    private JPanel buildLeftColumn() {
        JPanel col = new JPanel(new BorderLayout(0, 6));
        col.setOpaque(false);

        // Top bar: Warrior, Go To, Step Counter
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)));

        warriorSelector = new JComboBox<>();
        warriorSelector.setPreferredSize(new Dimension(130, 24));
        warriorSelector.addActionListener(e -> {
            customViewOffset = -1;
            refreshState();
        });
        topBar.add(warriorSelector);

        txtGoTo = new JTextField(4);
        btnGoTo = new JButton("go to");
        btnGoTo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnGoTo.addActionListener(e -> {
            try {
                customViewOffset = Integer.parseInt(txtGoTo.getText().trim(), 16) & 0xFFFF;
                refreshState();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid hex offset (e.g. C69B)");
            }
        });
        topBar.add(txtGoTo);
        topBar.add(btnGoTo);

        lblStepCounter = new JLabel("Step: 0");
        lblStepCounter.setFont(new Font("SansSerif", Font.BOLD, 12));
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(lblStepCounter);

        col.add(topBar, BorderLayout.NORTH);

        // Code Disassembly Box
        codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setContentType("text/html");
        codePane.setBackground(Color.WHITE);
        codePane.setFont(MONO_FONT);
        JScrollPane codeScroll = new JScrollPane(codePane);
        codeScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)));
        col.add(codeScroll, BorderLayout.CENTER);

        // Bottom section: Controls + Registers + Flags
        JPanel bottomSection = new JPanel();
        bottomSection.setLayout(new BoxLayout(bottomSection, BoxLayout.Y_AXIS));
        bottomSection.setBackground(Color.WHITE);
        bottomSection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 215)),
                new EmptyBorder(6, 6, 6, 6)));

        // Buttons & Speed
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        controlRow.setOpaque(false);
        btnResumePause = new JButton("Resume");
        btnResumePause.addActionListener(e -> togglePause());
        btnStep = new JButton("Step");
        btnStep.addActionListener(e -> doStep());
        controlRow.add(btnResumePause);
        controlRow.add(btnStep);

        controlRow.add(new JLabel("Speed:"));
        speedSlider = new JSlider(0, 10, 0);
        speedSlider.setPreferredSize(new Dimension(80, 20));
        lblSpeedValue = new JLabel("0");
        speedSlider.addChangeListener(e -> {
            int val = speedSlider.getValue();
            lblSpeedValue.setText(String.valueOf(val));
            competition.setSpeed(val == 0 ? 1 : (int) Math.pow(1.5, val));
        });
        controlRow.add(speedSlider);
        controlRow.add(lblSpeedValue);
        bottomSection.add(controlRow);
        bottomSection.add(Box.createVerticalStrut(4));

        // Registers grid: 4 columns x 4 rows
        JPanel regGrid = new JPanel(new GridLayout(4, 4, 4, 3));
        regGrid.setOpaque(false);

        String[][] rLayout = {
                {"AX", "BX", "CX", "DX"},
                {"SI", "DI", "BP", "SP"},
                {"CS", "DS", "SS", "ES"},
                {"IP", "Energy", "Flags", ""}
        };

        for (String[] row : rLayout) {
            for (String rName : row) {
                if (rName.isEmpty()) {
                    regGrid.add(new JLabel(""));
                    continue;
                }
                JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                box.setOpaque(false);
                JLabel lbl = new JLabel(rName + ":");
                lbl.setFont(new Font("Consolas", Font.BOLD, 11));
                JTextField tf = new JTextField("0000", rName.equals("Energy") ? 5 : 4);
                tf.setFont(MONO_FONT);
                tf.setEditable(false);
                tf.setBackground(new Color(250, 250, 252));
                tf.setHorizontalAlignment(JTextField.CENTER);
                regFields.put(rName, tf);
                box.add(lbl);
                box.add(tf);
                regGrid.add(box);
            }
        }
        bottomSection.add(regGrid);
        bottomSection.add(Box.createVerticalStrut(4));

        // Flags row
        JPanel flagRow = new JPanel(new GridLayout(1, 9, 2, 0));
        flagRow.setOpaque(false);
        String[] flagNames = {"OF", "DF", "IF", "TF", "SF", "ZF", "AF", "PF", "CF"};
        for (String f : flagNames) {
            JPanel fBox = new JPanel(new BorderLayout());
            fBox.setOpaque(false);
            JLabel fLbl = new JLabel(f, SwingConstants.CENTER);
            fLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            JCheckBox cb = new JCheckBox();
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cb.setEnabled(false);
            flagBoxes.put(f, cb);
            fBox.add(fLbl, BorderLayout.NORTH);
            fBox.add(cb, BorderLayout.CENTER);
            flagRow.add(fBox);
        }
        bottomSection.add(flagRow);

        col.add(bottomSection, BorderLayout.SOUTH);
        return col;
    }

    private JPanel buildMiddleColumn() {
        JPanel col = new JPanel(new GridLayout(3, 1, 0, 6));
        col.setOpaque(false);

        // Stack Panel
        JPanel stackPanel = new JPanel(new BorderLayout());
        stackPanel.setBackground(Color.WHITE);
        stackPanel.setBorder(createPanelHeader("Stack"));
        stackArea = new JTextArea();
        stackArea.setFont(MONO_FONT);
        stackArea.setEditable(false);
        stackPanel.add(new JScrollPane(stackArea), BorderLayout.CENTER);
        col.add(stackPanel);

        // Shared Memory Panel
        JPanel sharedPanel = new JPanel(new BorderLayout());
        sharedPanel.setBackground(Color.WHITE);
        sharedPanel.setBorder(createPanelHeader("Shared Memory"));
        sharedArea = new JTextArea();
        sharedArea.setFont(MONO_FONT);
        sharedArea.setEditable(false);
        sharedPanel.add(new JScrollPane(sharedArea), BorderLayout.CENTER);
        col.add(sharedPanel);

        // Watch Panel
        JPanel watchPanel = new JPanel(new BorderLayout());
        watchPanel.setBackground(Color.WHITE);
        watchPanel.setBorder(createPanelHeader("Watch"));
        watchModel = new DefaultTableModel(new String[]{"Address", "Value (Word)"}, 0);
        watchTable = new JTable(watchModel);
        watchTable.setFont(MONO_FONT);
        watchPanel.add(new JScrollPane(watchTable), BorderLayout.CENTER);

        JPanel watchBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton btnAddWatch = new JButton("+ Add");
        btnAddWatch.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnAddWatch.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "Enter Linear Address (Hex):", "10000");
            if (input != null && !input.trim().isEmpty()) {
                watchModel.addRow(new Object[]{input.trim().toUpperCase(), "0000"});
                refreshState();
            }
        });
        watchBtnRow.add(btnAddWatch);
        watchPanel.add(watchBtnRow, BorderLayout.SOUTH);
        col.add(watchPanel);

        return col;
    }

    private JPanel buildRightColumn() {
        JPanel col = new JPanel(new BorderLayout());
        col.setOpaque(false);
        arenaMapView = new ArenaMapView();
        col.add(arenaMapView, BorderLayout.CENTER);
        return col;
    }

    private TitledBorder createPanelHeader(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 215)), title);
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        tb.setTitleColor(new Color(60, 65, 75));
        return tb;
    }

    private void updateWarriorList() {
        War war = competition.getCurrentWar();
        if (war == null) return;
        String curSelected = (String) warriorSelector.getSelectedItem();
        warriorSelector.removeAllItems();
        for (int i = 0; i < war.getNumWarriors(); i++) {
            Warrior w = war.getWarrior(i);
            warriorSelector.addItem(w.getName());
        }
        if (curSelected != null) {
            for (int i = 0; i < warriorSelector.getItemCount(); i++) {
                if (warriorSelector.getItemAt(i).equals(curSelected)) {
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
        if (war == null) return;
        if (!war.isPaused()) {
            war.pause();
        }
        stepCount++;
        lblStepCounter.setText("Step: " + stepCount);
        war.runSingleRound();
        new Thread(() -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(this::refreshState);
        }).start();
    }

    private void togglePause() {
        War war = competition.getCurrentWar();
        if (war == null) return;
        if (war.isPaused()) {
            war.resume();
            btnResumePause.setText("Pause");
        } else {
            war.pause();
            btnResumePause.setText("Resume");
            refreshState();
        }
    }

    public void refreshState() {
        SwingUtilities.invokeLater(() -> {
            War war = competition.getCurrentWar();
            if (war == null) return;

            Warrior w = getSelectedWarrior();
            if (w == null) return;

            CpuState s = w.getCpuState();
            RealModeMemory mem = war.getMemory();

            btnResumePause.setText(war.isPaused() ? "Resume" : "Pause");

            // Update Registers
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
            updateReg("SS", s.getSS());
            updateReg("ES", s.getES());
            updateReg("Energy", s.getEnergy());
            updateReg("Flags", s.getFlags());

            // Update Flags
            flagBoxes.get("OF").setSelected(s.getOverflowFlag());
            flagBoxes.get("DF").setSelected(s.getDirectionFlag());
            flagBoxes.get("IF").setSelected(s.getInterruptFlag());
            flagBoxes.get("TF").setSelected(s.getTrapFlag());
            flagBoxes.get("SF").setSelected(s.getSignFlag());
            flagBoxes.get("ZF").setSelected(s.getZeroFlag());
            flagBoxes.get("AF").setSelected(s.getAuxFlag());
            flagBoxes.get("PF").setSelected(s.getParityFlag());
            flagBoxes.get("CF").setSelected(s.getCarryFlag());

            // Render Disassembly
            short baseOffset = (customViewOffset != -1) ? (short) customViewOffset : s.getIP();
            renderDisassembly(mem, s.getCS(), baseOffset, s.getIP());

            // Render Stack
            renderStack(mem, s.getSS(), s.getSP());

            // Render Shared Memory
            renderShared(mem, s.getES());

            // Update Watch
            renderWatch(mem);

            // Update Arena Map
            arenaMapView.updateArena(mem, w);
        });
    }

    private void updateReg(String name, short val) {
        JTextField tf = regFields.get(name);
        if (tf == null) return;
        tf.setText(String.format("%04X", val & 0xFFFF));
        Short prev = prevRegs.get(name);
        if (prev != null && prev != val) {
            tf.setBackground(new Color(255, 235, 175));
        } else {
            tf.setBackground(new Color(250, 250, 252));
        }
        prevRegs.put(name, val);
    }

    private void renderDisassembly(RealModeMemory mem, short cs, short targetOffset, short currentIP) {
        // Disassemble 6 instructions before target, current instruction in middle, and 8 instructions after
        List<Disassembler.DecodedInstruction> instrs =
                Disassembler.disassembleCentered(mem, cs, targetOffset, 6, 8);

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Consolas,monospace; font-size:12px; margin:2px;'>");

        for (Disassembler.DecodedInstruction di : instrs) {
            boolean isCur = (di.offset == (currentIP & 0xFFFF));
            String bg = isCur ? "background-color:#c8e1ff; border:1px solid #7baaf7; font-weight:bold;" : "";
            boolean isTrap = di.text.toLowerCase().contains("int 3") || di.text.startsWith("DB 0xCC");
            String color = isCur ? "color:#002266;" : (isTrap ? "color:#999999;" : "color:#111111;");

            html.append(String.format(
                    "<div style='%s padding:2px;'>", bg));
            html.append(String.format("<span style='color:#003366; font-weight:%s;'>%04X</span>&nbsp;&nbsp;", isCur ? "bold" : "normal", di.offset));
            html.append(String.format("<span style='color:#666666;'>%-10s</span>&nbsp;&nbsp;", di.hexBytes));
            html.append(String.format("<span style='%s'>%s</span>", color, di.text));
            if (isCur) {
                html.append("&nbsp;&nbsp;<span style='color:#0044aa; font-weight:bold;'>&larr; [NEXT OP]</span>");
            }
            html.append("</div>");
        }
        html.append("</body></html>");
        codePane.setText(html.toString());
        codePane.setCaretPosition(0);
    }

    private void renderStack(RealModeMemory mem, short ss, short sp) {
        StringBuilder sb = new StringBuilder();
        int base = sp & 0xFFFF;
        int linearBase = (Unsigned.unsignedShort(ss) * 16) + base;

        for (int i = -2; i <= 6; i++) {
            int off = base + (i * 2);
            int lin = linearBase + (i * 2);
            try {
                short val = mem.readWord(new RealModeAddress(ss, (short) off));
                int low = val & 0xFF;
                int high = (val >> 8) & 0xFF;
                String marker = (i == 0) ? " <=" : "";
                sb.append(String.format("%05X  %02X %02X%s\n", lin & 0xFFFFF, low, high, marker));
            } catch (Exception e) {
                sb.append(String.format("%05X  -- --\n", lin & 0xFFFFF));
            }
        }
        stackArea.setText(sb.toString());
    }

    private void renderShared(RealModeMemory mem, short es) {
        StringBuilder sb = new StringBuilder();
        int lin = Unsigned.unsignedShort(es) * 16;
        for (int i = 0; i < 8; i++) {
            int addr = lin + (i * 2);
            try {
                short val = mem.readWord(new RealModeAddress(es, (short) (i * 2)));
                sb.append(String.format("%05X  %02X %02X\n", addr & 0xFFFFF, val & 0xFF, (val >> 8) & 0xFF));
            } catch (Exception e) {
                sb.append(String.format("%05X  -- --\n", addr & 0xFFFFF));
            }
        }
        sharedArea.setText(sb.toString());
    }

    private void renderWatch(RealModeMemory mem) {
        for (int row = 0; row < watchModel.getRowCount(); row++) {
            String addrStr = (String) watchModel.getValueAt(row, 0);
            try {
                int addr = Integer.parseInt(addrStr, 16);
                short val = mem.readWord(new RealModeAddress(addr));
                watchModel.setValueAt(String.format("%04X", val & 0xFFFF), row, 1);
            } catch (Exception e) {
                watchModel.setValueAt("ERR", row, 1);
            }
        }
    }

    @Override
    public void onWarStart(long seed) {
        SwingUtilities.invokeLater(() -> {
            stepCount = 0;
            updateWarriorList();
            refreshState();
        });
    }

    @Override
    public void onWarEnd(int reason, String winners) {
        SwingUtilities.invokeLater(this::refreshState);
    }

    @Override
    public void onRound(int round) {}

    @Override
    public void onWarriorBirth(String warriorName) {
        SwingUtilities.invokeLater(this::updateWarriorList);
    }

    @Override
    public void onWarriorDeath(String warriorName, String reason) {
        SwingUtilities.invokeLater(this::refreshState);
    }

    @Override
    public void onCompetitionStart() {}

    @Override
    public void onCompetitionEnd() {}

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
