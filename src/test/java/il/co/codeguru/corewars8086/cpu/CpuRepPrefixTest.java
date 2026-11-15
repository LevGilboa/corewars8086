package il.co.codeguru.corewars8086.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import org.junit.Before;
import org.junit.Test;

/** Targeted tests for the REP / REPZ / REPNZ string-instruction prefixes. */
public class CpuRepPrefixTest {

    private static final short CODE_SEG = 0x1000;
    private static final short DATA_SEG = 0x2000;

    private RealModeMemoryImpl memory;
    private CpuState state;
    private Cpu cpu;

    @Before
    public void setUp() {
        memory = new RealModeMemoryImpl();
        state = new CpuState();
        cpu = new Cpu(state, memory);
        state.setCS(CODE_SEG);
        state.setIP((short) 0);
        state.setDS(DATA_SEG);
        state.setES(DATA_SEG);
        state.setSS(DATA_SEG);
        state.setSP((short) 0xF000);
    }

    private void code(int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            memory.writeByte(new RealModeAddress(CODE_SEG, (short) i), (byte) bytes[i]);
        }
    }

    private void poke(int offset, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            memory.writeByte(new RealModeAddress(DATA_SEG, (short) (offset + i)), (byte) bytes[i]);
        }
    }

    private int peek(int offset) {
        return memory.readByte(new RealModeAddress(DATA_SEG, (short) offset)) & 0xFF;
    }

    /** Runs until IP moves past the two-byte prefix+opcode (or the step limit is hit). */
    private int runInstruction() throws Exception {
        int steps = 0;
        while (state.getIP() != 2 && steps < 100_000) {
            cpu.nextOpcode();
            steps++;
        }
        return steps;
    }

    @Test
    public void repMovsbCopiesCxBytes() throws Exception {
        code(0xF3, 0xA4);
        poke(0x100, 1, 2, 3, 4, 5);
        state.setSI((short) 0x100);
        state.setDI((short) 0x200);
        state.setCX((short) 5);
        runInstruction();
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, peek(0x200 + i));
        }
        assertEquals(0, peek(0x205));
        assertEquals(0, state.getCX());
        assertEquals((short) 0x105, state.getSI());
        assertEquals((short) 0x205, state.getDI());
    }

    @Test
    public void repWithZeroCxDoesNothing() throws Exception {
        code(0xF3, 0xA4);
        poke(0x100, 9);
        state.setSI((short) 0x100);
        state.setDI((short) 0x200);
        state.setCX((short) 0);
        runInstruction();
        assertEquals(0, peek(0x200));
        assertEquals((short) 0x100, state.getSI());
    }

    @Test
    public void repStosbFillsWithAl() throws Exception {
        code(0xF3, 0xAA);
        state.setAL((byte) 0x7E);
        state.setDI((short) 0x300);
        state.setCX((short) 4);
        runInstruction();
        for (int i = 0; i < 4; i++) {
            assertEquals(0x7E, peek(0x300 + i));
        }
        assertEquals(0, peek(0x304));
    }

    @Test
    public void repzCmpsbStopsAtFirstMismatch() throws Exception {
        code(0xF3, 0xA6);
        poke(0x100, 1, 2, 3, 4);
        poke(0x200, 1, 2, 9, 4);
        state.setSI((short) 0x100);
        state.setDI((short) 0x200);
        state.setCX((short) 4);
        runInstruction();
        assertEquals(1, state.getCX());
        assertEquals((short) 0x103, state.getSI());
        assertFalse(state.getZeroFlag());
    }

    @Test
    public void repnzScasbStopsAtMatch() throws Exception {
        code(0xF2, 0xAE);
        poke(0x200, 5, 6, 7, 8);
        state.setAL((byte) 7);
        state.setDI((short) 0x200);
        state.setCX((short) 4);
        runInstruction();
        assertEquals(1, state.getCX());
        assertEquals((short) 0x203, state.getDI());
        assertEquals(true, state.getZeroFlag());
    }

    @Test(expected = InvalidOpcodeException.class)
    public void repnzMovsbIsInvalid() throws Exception {
        code(0xF2, 0xA4);
        state.setCX((short) 3);
        cpu.nextOpcode();
    }

    @Test(expected = InvalidOpcodeException.class)
    public void repWithNonStringOpcodeIsInvalid() throws Exception {
        code(0xF3, 0x90);
        cpu.nextOpcode();
    }

    @Test
    public void invalidRepStillConsumesCx() {
        code(0xF2, 0xA4);
        state.setCX((short) 3);
        try {
            cpu.nextOpcode();
        } catch (Exception expected) {
            // InvalidOpcodeException
        }
        assertEquals(2, state.getCX());
    }
}
