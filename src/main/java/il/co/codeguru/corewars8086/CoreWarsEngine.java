package il.co.codeguru.corewars8086;

import com.google.devtools.common.options.OptionsParser;
import il.co.codeguru.corewars8086.cli.HeadlessCompetitionRunner;
import il.co.codeguru.corewars8086.cli.Options;
import il.co.codeguru.corewars8086.gui.CompetitionWindow;

import javax.swing.JOptionPane;
import java.io.IOException;

public class CoreWarsEngine {
  public static void main(String[] args) {
    OptionsParser optionsParser = OptionsParser.newOptionsParser(Options.class);
    optionsParser.parseAndExitUponError(args);
    Options options = optionsParser.getOptions(Options.class);

    boolean headless = options != null && options.headless;  // options should never be null
    try {
      if (headless) {
        new HeadlessCompetitionRunner(options);
      } else {
        CompetitionWindow c = new CompetitionWindow(options);
        c.setVisible(true);
        c.pack();
      }
    } catch (IOException e) {
      if (headless) {
        System.err.println(e.getMessage());
      } else {
        JOptionPane.showMessageDialog(null, e.getMessage());
      }
      System.exit(1);
    }
  }
}
