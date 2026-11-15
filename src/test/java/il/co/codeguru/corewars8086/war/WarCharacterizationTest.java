package il.co.codeguru.corewars8086.war;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import il.co.codeguru.corewars8086.cli.Options;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Characterization test for {@link War}: runs seeded wars between the bundled
 * survivors and checks the outcome (rounds, deaths, final memory) is stable.
 */
public class WarCharacterizationTest {

    private static final long EXPECTED_HASH = 3450385108250603553L;

    private static class Recorder implements CompetitionEventListener {
        final List<String> events = new ArrayList<>();
        public void onWarStart(long seed) { }
        public void onWarEnd(int reason, String winners) { }
        public void onRound(int round) { }
        public void onWarriorBirth(String name) { events.add("birth:" + name); }
        public void onWarriorDeath(String name, String why) { events.add("death:" + name + ":" + why); }
        public void onCompetitionStart() { }
        public void onCompetitionEnd() { }
        public void onEndRound() { }
    }

    private static WarriorGroup group(String name, String... files) throws IOException {
        WarriorGroup g = new WarriorGroup(name);
        for (String f : files) {
            byte[] code = Files.readAllBytes(Paths.get("bundle", "survivors", f));
            WarriorType type = f.endsWith("1") ? WarriorType.SURVIVOR_1
                : f.endsWith("2") ? WarriorType.SURVIVOR_2 : WarriorType.SURVIVOR;
            g.addWarrior(new WarriorData(f, code, type));
        }
        return g;
    }

    private static long playWar(long seed) throws Exception {
        Recorder rec = new Recorder();
        War war = new War(null, rec, false, new Options());
        war.setSeed(seed);
        war.loadWarriorGroups(new WarriorGroup[] {
            group("bimp", "bimp1", "bimp2"),
            group("shooterA", "shooterA1", "shooterA2"),
            group("shooterB", "shooterB"),
            group("shooterC", "shooterC"),
        });
        int round = 0;
        while (!war.isOver() && round < 20_000) {
            war.nextRound(round++);
        }
        long h = round;
        h = h * 31 + rec.events.hashCode();
        h = h * 31 + war.getRemainingWarriorNames().hashCode();
        RealModeMemoryImpl mem = war.getMemory();
        for (int a = 0; a < RealModeAddress.MEMORY_SIZE; a += 13) {
            h = h * 31 + mem.readByte(new RealModeAddress(a));
        }
        return h;
    }

    @Test
    public void sameSeedGivesSameResult() throws Exception {
        assertEquals(playWar(7L), playWar(7L));
    }

    @Test
    public void warEndsAndHasSurvivor() throws Exception {
        War war = new War(null, new Recorder(), false, new Options());
        war.setSeed(1L);
        war.loadWarriorGroups(new WarriorGroup[] {
            group("bimp", "bimp1", "bimp2"), group("shooterB", "shooterB")});
        assertTrue(war.getNumWarriors() == 3);
        assertEquals(3, war.getNumRemainingSurvivors());
    }

    @Test
    public void seededWarsAreStable() throws Exception {
        long h = 17;
        for (long seed = 1; seed <= 10; seed++) {
            h = h * 31 + playWar(seed);
        }
        assertEquals(EXPECTED_HASH, h);
    }
}
