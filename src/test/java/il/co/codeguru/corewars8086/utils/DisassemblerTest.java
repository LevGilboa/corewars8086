package il.co.codeguru.corewars8086.utils;

import org.junit.Test;
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
}
