package il.co.codeguru.corewars8086.cpu;

import static org.junit.Assert.assertEquals;

import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import java.util.Random;
import org.junit.Test;

/**
 * Characterization test: executes a large, deterministic stream of random
 * opcodes and hashes the full CPU/memory state after every step. Guards the
 * CPU behaviour while the implementation is refactored.
 */
public class CpuCharacterizationTest {

    private static final long EXPECTED_HASH = 7410140052835542945L;

    @Test
    public void randomOpcodeStreamIsStable() {
        long hash = run(12345L, 300_000);
        assertEquals(EXPECTED_HASH, hash);
    }

    private static long run(long seed, int steps) {
        Random rnd = new Random(seed);
        RealModeMemoryImpl memory = new RealModeMemoryImpl();
        CpuState state = new CpuState();
        Cpu cpu = new Cpu(state, memory);

        long hash = 1125899906842597L;
        for (int i = 0; i < steps; i++) {
            if (i % 50 == 0) {
                randomize(rnd, state, memory);
            }
            try {
                cpu.nextOpcode();
            } catch (Exception e) {
                hash = hash * 31 + e.getClass().getSimpleName().hashCode();
            }
            hash = mix(hash, state);
        }
        // Fold in a sample of memory contents.
        for (int a = 0; a < RealModeAddress.MEMORY_SIZE; a += 97) {
            hash = hash * 31 + memory.readByte(new RealModeAddress(a));
        }
        return hash;
    }

    private static void randomize(Random rnd, CpuState s, RealModeMemoryImpl mem) {
        s.setAX((short) rnd.nextInt());
        s.setBX((short) rnd.nextInt());
        s.setCX((short) (rnd.nextInt(8)));
        s.setDX((short) rnd.nextInt());
        s.setSI((short) rnd.nextInt());
        s.setDI((short) rnd.nextInt());
        s.setBP((short) rnd.nextInt());
        s.setSP((short) rnd.nextInt());
        s.setDS((short) rnd.nextInt());
        s.setES((short) rnd.nextInt());
        s.setSS((short) rnd.nextInt());
        s.setCS((short) rnd.nextInt());
        s.setIP((short) rnd.nextInt());
        s.setFlags((short) rnd.nextInt());
        RealModeAddress code = new RealModeAddress(s.getCS(), s.getIP());
        for (int k = 0; k < 64; k++) {
            mem.writeByte(new RealModeAddress(code.getLinearAddress() + k), (byte) rnd.nextInt());
        }
    }

    private static long mix(long h, CpuState s) {
        short[] regs = {s.getAX(), s.getBX(), s.getCX(), s.getDX(), s.getSI(), s.getDI(),
            s.getBP(), s.getSP(), s.getDS(), s.getES(), s.getSS(), s.getCS(), s.getIP(),
            s.getFlags()};
        for (short r : regs) {
            h = h * 31 + r;
        }
        return h;
    }
}
