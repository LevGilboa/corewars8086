package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.utils.Disassembler;
import il.co.codeguru.corewars8086.utils.MiniAssembler;
import il.co.codeguru.corewars8086.war.Competition;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Integrated 8086 Warrior Code Editor & Studio:
 * - Line numbers and syntax editing
 * - Built-in native 8086 assembler with live error checking
 * - Live hex dump and disassembled machine code verification
 * - Open & disassemble existing binary warriors from survivors folder
 * - One-click assemble & export into competition survivors folder
 * - Starter warrior templates (Bomber, Crawler, Trap Planter)
 */
public class WarriorEditorFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    public static final int MAX_WARRIOR_SIZE = 512;

    private final Competition competition;
    private File currentSourceFile = null;

    // Editor components
    private JTextArea editorArea;
    private LineNumberComponent lineNumberComponent;
    private JLabel lblCaretStatus;
    private JProgressBar sizeProgressBar;
    private JLabel lblSizeStatus;

    // Output views
    private JTextArea hexDumpArea;
    private JTextArea disasmPreviewArea;
    private JTextArea compilerLogArea;

    // Template warriors
    private static final String TEMPLATE_BOMBER =
            "; ==========================================\n" +
            "; CodeGuru 8086 Warrior: Bomber\n" +
            "; Throws bombs into the arena using INT 0x86\n" +
            "; ==========================================\n" +
            "\n" +
            "start:\n" +
            "    MOV AX, 0x1000       ; Arena segment\n" +
            "    MOV ES, AX\n" +
            "    MOV DI, 0x2000       ; Bomb destination offset\n" +
            "\n" +
            "bomb_loop:\n" +
            "    INT 0x86             ; Throw bomb (64 words)\n" +
            "    ADD DI, 0x0100       ; Move target 256 bytes forward\n" +
            "    JMP bomb_loop        ; Repeat barrage\n";

    private static final String TEMPLATE_CRAWLER =
            "; ==========================================\n" +
            "; CodeGuru 8086 Warrior: Replicator / Worm\n" +
            "; Copies its own body across the arena\n" +
            "; ==========================================\n" +
            "\n" +
            "start:\n" +
            "    MOV AX, CS\n" +
            "    MOV DS, AX\n" +
            "    MOV ES, AX\n" +
            "    MOV SI, 0x0000       ; Source offset\n" +
            "    MOV DI, 0x0400       ; Destination offset\n" +
            "    MOV CX, 0x0020       ; Copy 32 words (64 bytes)\n" +
            "    CLD\n" +
            "    REP MOVSW            ; Replicate warrior\n" +
            "    JMP 0x0400           ; Jump to replicated copy\n";

    private static final String TEMPLATE_TRAP =
            "; ==========================================\n" +
            "; CodeGuru 8086 Warrior: Trap Planter\n" +
            "; Seeds INT 3 (0xCC) breakpoint traps in RAM\n" +
            "; ==========================================\n" +
            "\n" +
            "start:\n" +
            "    MOV AX, 0x1000\n" +
            "    MOV ES, AX\n" +
            "    MOV DI, 0x0100\n" +
            "    MOV AX, 0xCCCC       ; 2x INT 3 opcodes\n" +
            "    CLD\n" +
            "\n" +
            "trap_loop:\n" +
            "    STOSW                ; Write trap\n" +
            "    ADD DI, 0x0020       ; Step forward 32 bytes\n" +
            "    JMP trap_loop\n";

    public WarriorEditorFrame(Competition competition) {
        super("CodeGuru 8086 - Warrior Code Editor & Studio");
        this.competition = competition;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1080, 720);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        buildMenuBar();
        buildUI();

        // Load default template
        setSourceCode(TEMPLATE_BOMBER, null);
    }

    private void buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        // File Menu
        JMenu mFile = new JMenu("File");
        JMenuItem miNew = new JMenuItem("New Warrior");
        miNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        miNew.addActionListener(e -> setSourceCode(TEMPLATE_BOMBER, null));

        JMenuItem miOpen = new JMenuItem("Open Assembly Source...");
        miOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        miOpen.addActionListener(e -> openSourceFile());

        JMenuItem miOpenBinary = new JMenuItem("Open & Disassemble Survivor Binary...");
        miOpenBinary.addActionListener(e -> openAndDisassembleBinary());

        JMenuItem miSave = new JMenuItem("Save");
        miSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        miSave.addActionListener(e -> saveSourceFile(false));

        JMenuItem miSaveAs = new JMenuItem("Save As...");
        miSaveAs.addActionListener(e -> saveSourceFile(true));

        JMenuItem miExit = new JMenuItem("Close Editor");
        miExit.addActionListener(e -> dispose());

        mFile.add(miNew);
        mFile.add(miOpen);
        mFile.add(miOpenBinary);
        mFile.addSeparator();
        mFile.add(miSave);
        mFile.add(miSaveAs);
        mFile.addSeparator();
        mFile.add(miExit);
        mb.add(mFile);

        // Build Menu
        JMenu mBuild = new JMenu("Build");
        JMenuItem miAssemble = new JMenuItem("Assemble / Verify");
        miAssemble.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        miAssemble.addActionListener(e -> runAssemble(false));

        JMenuItem miExport = new JMenuItem("Assemble & Export to Survivors...");
        miExport.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        miExport.addActionListener(e -> runAssemble(true));

        mBuild.add(miAssemble);
        mBuild.add(miExport);
        mb.add(mBuild);

        // Templates Menu
        JMenu mTemplates = new JMenu("Templates");
        JMenuItem miT1 = new JMenuItem("Bomber (INT 0x86)");
        miT1.addActionListener(e -> setSourceCode(TEMPLATE_BOMBER, null));

        JMenuItem miT2 = new JMenuItem("Replicator / Worm (MOVSW)");
        miT2.addActionListener(e -> setSourceCode(TEMPLATE_CRAWLER, null));

        JMenuItem miT3 = new JMenuItem("Trap Planter (INT 3)");
        miT3.addActionListener(e -> setSourceCode(TEMPLATE_TRAP, null));

        mTemplates.add(miT1);
        mTemplates.add(miT2);
        mTemplates.add(miT3);
        mb.add(mTemplates);

        setJMenuBar(mb);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(4, 6, 6, 6));

        // Toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBackground(new Color(245, 247, 250));

        JButton btnNew = new JButton("New");
        btnNew.addActionListener(e -> setSourceCode(TEMPLATE_BOMBER, null));
        tb.add(btnNew);

        JButton btnOpen = new JButton("Open");
        btnOpen.addActionListener(e -> openSourceFile());
        tb.add(btnOpen);

        JButton btnDisasm = new JButton("Disassemble Survivor");
        btnDisasm.setToolTipText("Load an existing compiled survivor and decode to 8086 assembly");
        btnDisasm.addActionListener(e -> openAndDisassembleBinary());
        tb.add(btnDisasm);

        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> saveSourceFile(false));
        tb.add(btnSave);

        tb.addSeparator();

        JButton btnAssemble = new JButton("Verify & Assemble");
        btnAssemble.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnAssemble.setBackground(new Color(230, 245, 230));
        btnAssemble.addActionListener(e -> runAssemble(false));
        tb.add(btnAssemble);

        JButton btnExport = new JButton("Deploy to Survivors");
        btnExport.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnExport.setBackground(new Color(225, 238, 255));
        btnExport.setToolTipText("Assemble and save binary directly into the survivors folder");
        btnExport.addActionListener(e -> runAssemble(true));
        tb.add(btnExport);

        tb.add(Box.createHorizontalGlue());

        // Size progress bar & status in toolbar
        lblSizeStatus = new JLabel("Size: 0 / 512 bytes");
        lblSizeStatus.setFont(new Font("Consolas", Font.BOLD, 12));
        tb.add(lblSizeStatus);
        tb.add(Box.createHorizontalStrut(8));

        sizeProgressBar = new JProgressBar(0, MAX_WARRIOR_SIZE);
        sizeProgressBar.setPreferredSize(new Dimension(140, 18));
        sizeProgressBar.setStringPainted(true);
        tb.add(sizeProgressBar);

        root.add(tb, BorderLayout.NORTH);

        // Main Center Area: Split Pane (Left = Code Editor, Right = Inspection Tabs)
        JSplitPane splitHoriz = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitHoriz.setResizeWeight(0.58);
        splitHoriz.setDividerSize(6);

        // Left Panel: Editor with Line Numbers and Status Bar
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)));

        editorArea = new JTextArea();
        editorArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        editorArea.setTabSize(4);
        editorArea.setBackground(Color.WHITE);
        editorArea.setForeground(new Color(25, 25, 25));
        editorArea.setCaretColor(Color.BLUE);

        lineNumberComponent = new LineNumberComponent(editorArea);

        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorScroll.setRowHeaderView(lineNumberComponent);
        editorScroll.setBorder(null);
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        // Editor bottom bar: Line/Col
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBackground(new Color(245, 247, 250));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(215, 220, 228)));
        lblCaretStatus = new JLabel("Line: 1, Col: 1");
        lblCaretStatus.setFont(new Font("Consolas", Font.PLAIN, 11));
        statusBar.add(lblCaretStatus);
        editorPanel.add(statusBar, BorderLayout.SOUTH);

        // Listen for caret movements
        editorArea.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                updateCaretStatus();
            }
        });

        // Listen for text changes to update line numbers and size
        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onCodeChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onCodeChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onCodeChanged(); }
        });

        splitHoriz.setLeftComponent(editorPanel);

        // Right Panel: Split into Inspection Tabs (Hex Dump & Disassembly) and Compiler Log
        JSplitPane splitRight = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitRight.setResizeWeight(0.65);
        splitRight.setDividerSize(6);

        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: Hex Dump
        hexDumpArea = new JTextArea();
        hexDumpArea.setEditable(false);
        hexDumpArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        hexDumpArea.setBackground(new Color(250, 252, 255));
        tabs.addTab("Hex Machine Code", new JScrollPane(hexDumpArea));

        // Tab 2: Disassembly Check
        disasmPreviewArea = new JTextArea();
        disasmPreviewArea.setEditable(false);
        disasmPreviewArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        disasmPreviewArea.setBackground(new Color(250, 252, 255));
        tabs.addTab("Disassembly Check", new JScrollPane(disasmPreviewArea));

        splitRight.setTopComponent(tabs);

        // Bottom Tab: Compiler / Assembler Log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Assembler Output"));
        compilerLogArea = new JTextArea();
        compilerLogArea.setEditable(false);
        compilerLogArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        compilerLogArea.setForeground(new Color(20, 30, 40));
        logPanel.add(new JScrollPane(compilerLogArea), BorderLayout.CENTER);

        splitRight.setBottomComponent(logPanel);

        splitHoriz.setRightComponent(splitRight);

        root.add(splitHoriz, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void updateCaretStatus() {
        try {
            int pos = editorArea.getCaretPosition();
            int line = editorArea.getLineOfOffset(pos);
            int col = pos - editorArea.getLineStartOffset(line);
            lblCaretStatus.setText(String.format("Line: %d, Col: %d", line + 1, col + 1));
        } catch (Exception ignored) {}
    }

    private void onCodeChanged() {
        lineNumberComponent.repaint();
    }

    private void setSourceCode(String text, File file) {
        editorArea.setText(text);
        editorArea.setCaretPosition(0);
        currentSourceFile = file;
        String titleName = (file != null) ? file.getName() : "Untitled.asm";
        setTitle("CodeGuru 8086 Studio - " + titleName);
        runAssemble(false);
    }

    private void runAssemble(boolean exportToSurvivors) {
        String src = editorArea.getText();
        MiniAssembler.AssembleResult res = MiniAssembler.assemble(src);

        StringBuilder log = new StringBuilder();
        if (res.success) {
            int len = res.bytes.length;
            log.append(String.format("SUCCESS: Assembled %d bytes cleanly!\n", len));
            log.append("--------------------------------------------------\n");

            // Symbol table
            if (!res.symbols.isEmpty()) {
                log.append("Symbols:\n");
                for (Map.Entry<String, Integer> e : res.symbols.entrySet()) {
                    log.append(String.format("  %-16s = 0x%04X (%d)\n", e.getKey(), e.getValue(), e.getValue()));
                }
                log.append("--------------------------------------------------\n");
            }

            // Update Hex Dump
            StringBuilder hex = new StringBuilder();
            hex.append("; Offset   00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII\n");
            hex.append("; ------------------------------------------------------------------\n");
            for (int i = 0; i < len; i += 16) {
                hex.append(String.format("%04X:    ", i));
                for (int j = 0; j < 16; j++) {
                    if (i + j < len) {
                        hex.append(String.format("%02X ", res.bytes[i + j]));
                    } else {
                        hex.append("   ");
                    }
                    if (j == 7) hex.append(" ");
                }
                hex.append("  |");
                for (int j = 0; j < 16 && (i + j) < len; j++) {
                    char c = (char) (res.bytes[i + j] & 0xFF);
                    hex.append((c >= 32 && c <= 126) ? c : '.');
                }
                hex.append("|\n");
            }
            hexDumpArea.setText(hex.toString());
            hexDumpArea.setCaretPosition(0);

            // Update Disassembly Check
            String dis = Disassembler.disassembler(res.bytes);
            disasmPreviewArea.setText(dis);
            disasmPreviewArea.setCaretPosition(0);

            // Update Size indicators
            lblSizeStatus.setText(String.format("Size: %d / %d bytes", len, MAX_WARRIOR_SIZE));
            sizeProgressBar.setValue(Math.min(len, MAX_WARRIOR_SIZE));
            if (len > MAX_WARRIOR_SIZE) {
                sizeProgressBar.setForeground(new Color(220, 50, 50));
                log.append(String.format("WARNING: Warrior size %d exceeds MAX_WARRIOR_SIZE (%d)!\n", len, MAX_WARRIOR_SIZE));
            } else {
                sizeProgressBar.setForeground(new Color(40, 160, 60));
            }

            // Export to survivors folder if requested
            if (exportToSurvivors) {
                exportBinary(res.bytes);
            }

        } else {
            log.append(String.format("ERROR: Assembly failed with %d error(s):\n", res.errors.size()));
            log.append("--------------------------------------------------\n");
            for (String err : res.errors) {
                log.append("  * ").append(err).append("\n");
            }
            lblSizeStatus.setText("Size: Error in code");
            sizeProgressBar.setValue(0);
        }

        compilerLogArea.setText(log.toString());
        compilerLogArea.setCaretPosition(0);
    }

    private void exportBinary(byte[] bytes) {
        String defaultName = "mywarrior";
        if (currentSourceFile != null) {
            String n = currentSourceFile.getName();
            int dot = n.lastIndexOf('.');
            defaultName = (dot != -1) ? n.substring(0, dot) : n;
        }

        String warriorName = JOptionPane.showInputDialog(this,
                "Enter warrior name for the survivors folder:", defaultName);
        if (warriorName == null || warriorName.trim().isEmpty()) return;
        warriorName = warriorName.trim();

        // Target survivors directory
        File targetDir = new File("survivors");
        if (!targetDir.exists()) {
            targetDir = new File("bundle/survivors");
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File outFile = new File(targetDir, warriorName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(bytes);
            JOptionPane.showMessageDialog(this,
                    "Warrior successfully deployed to:\n" + outFile.getAbsolutePath() +
                    "\nSize: " + bytes.length + " bytes\nReady for next competition battle!",
                    "Warrior Deployed", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to write warrior file: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSourceFile() {
        JFileChooser fc = new JFileChooser(new File("."));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                setSourceCode(text, f);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Could not read file: " + e.getMessage());
            }
        }
    }

    private void openAndDisassembleBinary() {
        File survivorsDir = new File("survivors");
        if (!survivorsDir.exists()) survivorsDir = new File("bundle/survivors");
        JFileChooser fc = new JFileChooser(survivorsDir.exists() ? survivorsDir : new File("."));
        fc.setDialogTitle("Select Compiled Warrior Binary to Disassemble");

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] raw = fis.readAllBytes();
                String dis = Disassembler.disassembler(raw);

                // Convert disassembled listing into clean editable assembly
                StringBuilder src = new StringBuilder();
                src.append("; ==========================================\n");
                src.append("; Disassembled Warrior: ").append(f.getName()).append("\n");
                src.append("; Original size: ").append(raw.length).append(" bytes\n");
                src.append("; ==========================================\n\n");
                src.append("start:\n");

                for (String line : dis.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // Format: "C68F:  4F            DEC DI"
                    int col = line.indexOf(':');
                    if (col != -1 && line.length() > col + 1) {
                        String rest = line.substring(col + 1).trim();
                        // Split hex bytes from mnemonic
                        String[] parts = rest.split("\\s{2,}", 2);
                        if (parts.length >= 2) {
                            src.append("    ").append(parts[1]).append("\n");
                        } else {
                            src.append("    ").append(rest).append("\n");
                        }
                    } else {
                        src.append("    ").append(line).append("\n");
                    }
                }

                setSourceCode(src.toString(), new File(f.getParentFile(), f.getName() + ".asm"));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Could not disassemble file: " + e.getMessage());
            }
        }
    }

    private void saveSourceFile(boolean saveAs) {
        if (currentSourceFile == null || saveAs) {
            JFileChooser fc = new JFileChooser(new File("."));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                if (!f.getName().contains(".")) {
                    f = new File(f.getParentFile(), f.getName() + ".asm");
                }
                currentSourceFile = f;
            } else {
                return;
            }
        }

        try {
            Files.writeString(currentSourceFile.toPath(), editorArea.getText(), StandardCharsets.UTF_8);
            setTitle("CodeGuru 8086 Studio - " + currentSourceFile.getName());
            JOptionPane.showMessageDialog(this, "File saved successfully.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save file: " + e.getMessage());
        }
    }

    /**
     * Line numbering component displayed on the left margin of the editor.
     */
    private static class LineNumberComponent extends JComponent {
        private static final long serialVersionUID = 1L;
        private final JTextArea textArea;

        LineNumberComponent(JTextArea textArea) {
            this.textArea = textArea;
            setBackground(new Color(245, 247, 250));
            setFont(new Font("Consolas", Font.PLAIN, 12));
            setPreferredSize(new Dimension(38, 100));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, getWidth(), getHeight());

            g.setColor(new Color(220, 225, 232));
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            g.setColor(new Color(140, 150, 165));
            g.setFont(getFont());

            FontMetrics fm = g.getFontMetrics();
            int lineCount = textArea.getLineCount();
            int fontHeight = fm.getHeight();

            for (int i = 0; i < lineCount; i++) {
                try {
                    int lineY = textArea.modelToView2D(textArea.getLineStartOffset(i)).getBounds().y + fm.getAscent();
                    String numStr = String.valueOf(i + 1);
                    int textW = fm.stringWidth(numStr);
                    g.drawString(numStr, getWidth() - textW - 6, lineY);
                } catch (Exception ignored) {}
            }
        }
    }
}
