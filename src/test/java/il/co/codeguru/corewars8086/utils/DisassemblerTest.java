package il.co.codeguru.corewars8086.utils;

import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemoryImpl;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DisassemblerTest {

    @Test
    public void testDisassembleNop() {
        Disassembler.DecodedInstruction di = Disassembler.decodeOne(new byte[]{(byte) 0x90}, 0, 0x100);
        assertEquals("NOP", di.text);
        assertEquals(1, di.length);
        assertEquals(0x100, di.offset);
    }

    @Test
    public void testDisassembleMovImm() {
        Disassembler.DecodedInstruction di = Disassembler.decodeOne(new byte[]{(byte) 0xB8, (byte) 0x34, (byte) 0x12}, 0, 0);
        assertEquals("MOV AX, 0x1234", di.text);
        assertEquals(3, di.length);
    }

    @Test
    public void testDisassembleJmp() {
        // JMP short +4 (from offset 0, instruction length is 2, target is 0 + 2 + 4 = 6)
        Disassembler.DecodedInstruction di = Disassembler.decodeOne(new byte[]{(byte) 0xEB, (byte) 0x04}, 0, 0);
        assertEquals("JMP 0x0006", di.text);
        assertEquals(2, di.length);
    }

    @Test
    public void testDisassembleAluMemReg() {
        // ADD [BX+SI], AX -> opcode 0x01, ModR/M 0x00 (reg=000 (AX), rm=000 (BX+SI))
        Disassembler.DecodedInstruction di = Disassembler.decodeOne(new byte[]{(byte) 0x01, (byte) 0x00}, 0, 0);
        assertEquals("ADD WORD [BX+SI], AX", di.text);
        assertEquals(2, di.length);
    }

    @Test
    public void testDisassembleInt86() {
        Disassembler.DecodedInstruction di = Disassembler.decodeOne(new byte[]{(byte) 0xCD, (byte) 0x86}, 0, 0);
        assertTrue(di.text.contains("0x86"));
        assertEquals(2, di.length);
    }

    @Test
    public void testDisassembleCenteredWindow() {
        RealModeMemoryImpl mem = new RealModeMemoryImpl();
        // Write a sequence of NOPs and instructions around offset 0x100
        // 0x0F8 .. 0x0FF: NOPs
        for (int i = 0x0F0; i < 0x120; i++) {
            mem.writeByte(new RealModeAddress((short) 0x1000, (short) i), (byte) 0x90);
        }
        // At 0x100: MOV AX, 0x1234 (3 bytes)
        mem.writeByte(new RealModeAddress((short) 0x1000, (short) 0x100), (byte) 0xB8);
        mem.writeByte(new RealModeAddress((short) 0x1000, (short) 0x101), (byte) 0x34);
        mem.writeByte(new RealModeAddress((short) 0x1000, (short) 0x102), (byte) 0x12);

        List<Disassembler.DecodedInstruction> window =
                Disassembler.disassembleCentered(mem, (short) 0x1000, (short) 0x100, 4, 4);

        assertNotNull(window);
        assertTrue(window.size() >= 5);

        // Find instruction at 0x100
        boolean foundAt100 = false;
        int targetIdx = -1;
        for (int i = 0; i < window.size(); i++) {
            if (window.get(i).offset == 0x100) {
                foundAt100 = true;
                targetIdx = i;
                assertEquals("MOV AX, 0x1234", window.get(i).text);
            }
        }
        assertTrue("Target instruction at 0x100 should be in window", foundAt100);
        // It should not be the very first instruction if instructions existed before it
        assertTrue("Target instruction should be centered (not at index 0)", targetIdx > 0);
    }
}
