package il.co.codeguru.corewars8086.gui;

import il.co.codeguru.corewars8086.cli.Options;
import il.co.codeguru.corewars8086.war.Competition;
import il.co.codeguru.corewars8086.war.CompetitionEventListener;
import il.co.codeguru.corewars8086.war.ScoreEventListener;
import il.co.codeguru.corewars8086.war.WarriorRepository;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * @author BS
 */
public class CompetitionWindow extends JFrame
        implements ScoreEventListener, ActionListener, CompetitionEventListener {
    private static final long serialVersionUID = 1L;
    
    private final Competition competition;
    private final ColumnGraph columnGraph;
    
    // widgets
    private final JButton runWarButton;
    private final JLabel warCounterDisplay;
    private final JCheckBox showBattleCheckBox;
    private final JTextField battlesPerGroupField;
    private final JTextField warriorsPerGroupField;
    private WarFrame battleFrame;
    
    private int warCounter;
    private int totalWars;
    private volatile Thread warThread;
    private volatile boolean competitionRunning;
    
    private static final String SEED_PREFIX = "SEED!@#=";
    private static final String START_LABEL = "<html><font color=red>Start!</font></html>";
    private static final String STOP_LABEL = "<html><font color=red>Stop!</font></html>";
    private final JTextField seed;
    
    private final JCheckBox startPausedCheckBox;
    
    private final Options options;
    
    public CompetitionWindow(Options options) throws IOException {
        super("CodeGuru Extreme - Competition Viewer");
        this.options = options;
        getContentPane().setLayout(new BorderLayout());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        competition = new Competition(options);
        competition.addCompetitionEventListener(this);
        WarriorRepository warriorRepository = competition.getWarriorRepository();
        warriorRepository.addScoreEventListener(this);
        columnGraph = new ColumnGraph(warriorRepository.getGroupNames(), options);
        getContentPane().add(columnGraph, BorderLayout.CENTER);
        // -------------
        JPanel controlArea = new JPanel();
        controlArea.setLayout(new BoxLayout(controlArea, BoxLayout.Y_AXIS));
        // -------------- Button Panel
        JPanel buttonPanel = new JPanel();
        runWarButton = new JButton(START_LABEL);
        runWarButton.addActionListener(this);
        buttonPanel.add(runWarButton);
        warCounterDisplay = new JLabel("");
        buttonPanel.add(warCounterDisplay);
        buttonPanel.add(Box.createHorizontalStrut(30));
        showBattleCheckBox = new JCheckBox("Show war on start");
        buttonPanel.add(showBattleCheckBox);
        
        startPausedCheckBox = new JCheckBox("Start Paused");
        startPausedCheckBox.setEnabled(!options.parallel); // TODO enable functionality in 5.0.1
        startPausedCheckBox.addActionListener(event -> {
            if (startPausedCheckBox.isSelected())
                showBattleCheckBox.setSelected(true);
        });
        buttonPanel.add(startPausedCheckBox);
        
        controlArea.add(buttonPanel);
        // -------------
        controlArea.add(new JSeparator(JSeparator.HORIZONTAL));
        // ------------ Control panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(new JLabel("Survivor groups per war:"));
        
        // If total number of teams is less then four, make it the defauld number
        int numberOfGroups = Math.min(4,
                competition.getWarriorRepository().getNumberOfGroups());
        
        warriorsPerGroupField = new JTextField(String.format("%d", numberOfGroups), 3);
        controlPanel.add(warriorsPerGroupField);
        controlPanel.add(new JLabel("Wars per groups combination:"));
        battlesPerGroupField = new JTextField("100", 4);
        controlPanel.add(battlesPerGroupField);
        seed = new JTextField(15);
        seed.setText("guru");
        controlPanel.add(new JLabel("seed:"));
        controlPanel.add(seed);
        
        controlArea.add(controlPanel);
        
        // ------------
        getContentPane().add(controlArea, BorderLayout.SOUTH);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (warThread != null) {
                    competition.setAbort(true);
                }
            }
        });
    }
    
    /**
     * Starts a new war.
     *
     * @return whether or not a new war was started.
     */
    public boolean runWar() {
        try {
            long seedValue;
            if (seed.getText().startsWith(SEED_PREFIX)) {
                seedValue = Long.parseLong(seed.getText().substring(SEED_PREFIX.length()));
            } else {
                seedValue = seed.getText().hashCode();
            }
            competition.setSeed(seedValue);
            final int battlesPerGroup = Integer.parseInt(
                    battlesPerGroupField.getText().trim());
            final int warriorsPerGroup = Integer.parseInt(
                    warriorsPerGroupField.getText().trim());
            if (competition.getWarriorRepository().getNumberOfGroups() < warriorsPerGroup) {
                JOptionPane.showMessageDialog(this,
                        "Not enough survivors (got " +
                                competition.getWarriorRepository().getNumberOfGroups() +
                                " but " + warriorsPerGroup + " are needed)");
                return false;
            }
            warThread = new Thread(() -> {
                try {
                    if (options.parallel) {
                        competition.runCompetitionInParallel(battlesPerGroup, warriorsPerGroup, options.threads);
                    } else {
                        competition.runCompetition(battlesPerGroup, warriorsPerGroup, startPausedCheckBox.isSelected());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "CompetitionThread");
            if (!competitionRunning) {
                warThread.start();
                return true;
            }
        } catch (NumberFormatException e2) {
            JOptionPane.showMessageDialog(this, "Error in configuration");
        }
        return false;
    }
    
    public void scoreChanged(String name, float addedValue, int groupIndex, int subIndex) {
        SwingUtilities.invokeLater(() -> columnGraph.addToValue(groupIndex, subIndex, addedValue));
    }
    
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == runWarButton) {
            showBattleFrameIfNeeded();
            switch (runWarButton.getText().trim()) {
                case START_LABEL:
                    if (runWar()) {
                        competitionRunning = true;
                        
                        if (options.parallel) {
                            showBattleCheckBox.setEnabled(false); // TODO enable functionality in 5.0.1
                        }
                    }
                    break;
                case STOP_LABEL:
                    competition.setAbort(true);
                    competitionRunning = false;
                    break;
                default:
                    break;
            }
        }
    }
    
    
    @Override
    public void onWarStart(long seed) {
        SwingUtilities.invokeLater(() -> {
            this.seed.setText(SEED_PREFIX + seed);
            showBattleFrameIfNeeded();
        });
    }
    
    private void showBattleFrameIfNeeded() {
        if (showBattleCheckBox.isSelected() && battleFrame == null) {
            showBattleRoom();
            showBattleCheckBox.setSelected(false);
            
            if (options.parallel) {
                showBattleCheckBox.setEnabled(false);
            }
        }
    }
    
    private void showBattleRoom() {
        competition.setSpeed(5);
        battleFrame = new WarFrame(competition);
        battleFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                battleFrame = null;
                competition.setSpeed(Competition.MAXIMUM_SPEED);
            }
        });

        competition.addMemoryEventLister(battleFrame);
        competition.addCompetitionEventListener(battleFrame);
        Rectangle battleFrameRect = new Rectangle(0, getY(), 750, 700);
        Rectangle screen = getGraphicsConfiguration().getBounds(); //for multiple monitors
        
        if (getX() + getWidth() <= screen.getX() + screen.getWidth()
                - battleFrameRect.width) {
            battleFrameRect.x = getX() + getWidth();
        } else if (screen.getX() + screen.getWidth() - battleFrameRect.width
                - getWidth() >= screen.getX()) {
            setLocation((int) (screen.getX() + screen.getWidth() - battleFrameRect.width
                    - getWidth()), getY());
            battleFrameRect.x = getX() + getWidth();
        } else {
            setLocation((int) screen.getX(), getY());
            battleFrameRect.x = getWidth();
        }
        
        battleFrame.setBounds(battleFrameRect);
        battleFrame.setVisible(true);
    }
    
    public void onWarEnd(int reason, String winners) {
        warCounter++;
        final int warsSoFar = warCounter;
        final int warsTotal = totalWars;
        final long currentSeed = competition.getSeed();
        SwingUtilities.invokeLater(() -> {
            seed.setText(SEED_PREFIX + currentSeed);
            warCounterDisplay.setText("Wars so far:" + warsSoFar + " (out of " + warsTotal + ")");
        });
    }
    
    public void onRound(int round) {
    }
    
    public void onWarriorBirth(String warriorName) {
    }
    
    public void onWarriorDeath(String warriorName, String reason) {
    }
    
    public void onCompetitionStart() {
        warCounter = 0;
        totalWars = competition.getTotalNumberOfWars();
        competition.setAbort(false);
        SwingUtilities.invokeLater(() -> runWarButton.setText(STOP_LABEL));
    }
    
    public void onCompetitionEnd() {
        SwingUtilities.invokeLater(() -> {
            warCounterDisplay.setText(String.format("The competition is over. %d wars were run.", warCounter));
            runWarButton.setText(START_LABEL);
            showBattleCheckBox.setEnabled(true);
        });
        warThread = null;
        competitionRunning = false;
    }
    
    @Override
    public void onEndRound() {
    }
    
}