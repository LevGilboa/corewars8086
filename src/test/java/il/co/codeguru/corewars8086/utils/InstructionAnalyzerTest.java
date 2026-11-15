package il.co.codeguru.corewars8086.utils;

import il.co.codeguru.corewars8086.cpu.CpuState;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import il.co.codeguru.corewars8086.war.War;
import org.junit.Test;

import static org.junit.Assert.*;

public class InstructionAnalyzerTest {

    @Test
    public void testCallFarTooltip() {
        String tip = InstructionAnalyzer.getInstructionTooltip("CALL FAR 0x1000:0x0020", null, null);
        assertNotNull(tip);
        assertTrue(tip.contains("Inter-Segment Subroutine Call"));
        assertTrue(tip.contains("Push Code Segment"));
        assertTrue(tip.contains("Push Return Offset"));
        assertTrue(tip.contains("RETF"));
    }

    @Test
    public void testNearCallTooltip() {
        String tip = InstructionAnalyzer.getInstructionTooltip("CALL 0x0500", null, null);
        assertNotNull(tip);
        assertTrue(tip.contains("Near Subroutine Call"));
        assertTrue(tip.contains("Push Return Address"));
    }

    @Test
    public void testRetAndRetfTooltip() {
        String retTip = InstructionAnalyzer.getInstructionTooltip("RET", null, null);
        assertTrue(retTip.contains("Near Return"));

        String retfTip = InstructionAnalyzer.getInstructionTooltip("RETF", null, null);
        assertTrue(retfTip.contains("Far Return"));
        assertTrue(retfTip.contains("Pop Return CS"));
    }

    @Test
    public void testInt86BombTooltip() {
        CpuState s = new CpuState();
        s.setES((short) War.ARENA_SEGMENT);
        s.setDI((short) 0x1200);

        String tip = InstructionAnalyzer.getInstructionTooltip("INT 0x86", s, null);
        assertTrue(tip.contains("CoreWars Special: Throw Bomb"));
        assertTrue(tip.contains("64 consecutive words"));
    }

    @Test
    public void testEffectiveAddressResolution() {
        CpuState s = new CpuState();
        s.setBX((short) 0x1000);
        s.setSI((short) 0x0020);

        Integer ea1 = InstructionAnalyzer.resolveEffectiveAddress("MOV [BX+SI], AX", s);
        assertEquals(Integer.valueOf(0x1020), ea1);

        Integer ea2 = InstructionAnalyzer.resolveEffectiveAddress("MOV [BX+0x10], CX", s);
        assertEquals(Integer.valueOf(0x1010), ea2);

        Integer ea3 = InstructionAnalyzer.resolveEffectiveAddress("MOV [0x5000], DX", s);
        assertEquals(Integer.valueOf(0x5000), ea3);
    }

    @Test
    public void testGhostPreviewMemoryWrite() {
        RealModeMemoryImpl mem = new RealModeMemoryImpl();
        CpuState s = new CpuState();
        s.setCS((short) War.ARENA_SEGMENT);
        s.setIP((short) 0x0100);
        s.setDS((short) War.ARENA_SEGMENT);
        s.setBX((short) 0x2000);

        // Opcode 89 07: MOV [BX], AX
        mem.writeByte(new il.co.codeguru.corewars8086.memory.RealModeAddress((short) War.ARENA_SEGMENT, (short) 0x0100), (byte) 0x89);
        mem.writeByte(new il.co.codeguru.corewars8086.memory.RealModeAddress((short) War.ARENA_SEGMENT, (short) 0x0101), (byte) 0x07);

        InstructionAnalyzer.StepPreview preview = InstructionAnalyzer.analyze(mem, s);
        assertNotNull(preview);
        assertEquals(Integer.valueOf(0x2000), preview.targetMemoryOffset);
        assertTrue(preview.isWrite);
    }
}
