package il.co.codeguru.corewars8086.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class MiniAssemblerTest {

    @Test
    public void testBasicInstructions() {
        String src = "MOV AX, 0x1234\n" +
                     "ADD BX, AX\n" +
                     "INT 0x86\n" +
                     "RET\n";
        MiniAssembler.AssembleResult res = MiniAssembler.assemble(src);
        assertTrue(res.errors.toString(), res.success);
        assertNotNull(res.bytes);
        assertTrue(res.bytes.length > 0);

        // Disassemble the bytes and verify
        String dis = Disassembler.disassembler(res.bytes);
        assertTrue(dis.contains("MOV AX, 0x1234") || dis.contains("MOV AX, 0x1234"));
        assertTrue(dis.contains("ADD BX, AX"));
        assertTrue(dis.contains("INT 0x86"));
        assertTrue(dis.contains("RET"));
    }

    @Test
    public void testLabelsAndJumps() {
        String src = "loop_start:\n" +
                     "DEC CX\n" +
                     "JNZ loop_start\n" +
                     "RET\n";
        MiniAssembler.AssembleResult res = MiniAssembler.assemble(src);
        assertTrue(res.errors.toString(), res.success);
        assertEquals(4, res.bytes.length);
        assertEquals((byte) 0x49, res.bytes[0]); // DEC CX
        assertEquals((byte) 0x75, res.bytes[1]); // JNZ
        assertEquals((byte) 0xFD, res.bytes[2]); // -3 bytes displacement
        assertEquals((byte) 0xC3, res.bytes[3]); // RET
    }

    @Test
    public void testStringAndBombInstructions() {
        String src = "CLD\n" +
                     "REP MOVSW\n" +
                     "INT 0x86\n";
        MiniAssembler.AssembleResult res = MiniAssembler.assemble(src);
        assertTrue(res.errors.toString(), res.success);
        assertEquals(5, res.bytes.length);
        assertEquals((byte) 0xFC, res.bytes[0]); // CLD
        assertEquals((byte) 0xF3, res.bytes[1]); // REP
        assertEquals((byte) 0xA5, res.bytes[2]); // MOVSW
        assertEquals((byte) 0xCD, res.bytes[3]); // INT
        assertEquals((byte) 0x86, res.bytes[4]); // 0x86
    }
}
