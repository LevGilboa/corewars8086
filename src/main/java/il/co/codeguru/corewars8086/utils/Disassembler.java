package il.co.codeguru.corewars8086.utils;

import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java 8086 Disassembler.
 * Decodes the core 8086 instruction set supported by CoreWars8086.
 */
public class Disassembler {

    public static class DecodedInstruction {
        public final int offset;
        public final int length;
        public final String hexBytes;
        public final String text;

        public DecodedInstruction(int offset, int length, String hexBytes, String text) {
            this.offset = offset;
            this.length = length;
            this.hexBytes = hexBytes;
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format("%04X:  %-12s  %s", offset, hexBytes, text);
        }
    }

    private static final String[] R8 = {"AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH"};
    private static final String[] R16 = {"AX", "CX", "DX", "BX", "SP", "BP", "SI", "DI"};
    private static final String[] SREG = {"ES", "CS", "SS", "DS"};
    private static final String[] EA_BASE = {
            "BX+SI", "BX+DI", "BP+SI", "BP+DI", "SI", "DI", "BP", "BX"
    };

    private static final String[] ALU_OPS = {"ADD", "OR", "ADC", "SBB", "AND", "SUB", "XOR", "CMP"};
    private static final String[] SHIFT_OPS = {"ROL", "ROR", "RCL", "RCR", "SHL", "SHR", "???", "SAR"};

    /**
     * Legacy string interface used by existing callers.
     */
    public static String disassembler(byte[] bytes) {
        List<DecodedInstruction> instrs = decodeStream(bytes, 0, bytes.length, 0);
        StringBuilder sb = new StringBuilder();
        for (DecodedInstruction i : instrs) {
            sb.append(i.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Disassembles a block of instructions from memory around a given segment:offset.
     */
    /**
     * Disassembles a window of instructions centered on targetOffset:
     * attempts to find valid instructions immediately before targetOffset,
     * includes targetOffset in the middle, and decodes instructions after targetOffset.
     */
    public static List<DecodedInstruction> disassembleCentered(RealModeMemory memory, short segment, short targetOffset, int beforeCount, int afterCount) {
        List<DecodedInstruction> beforeList = new ArrayList<>();
        int target = targetOffset & 0xFFFF;

        // Try candidate start offsets before target (from 24 down to 2 bytes prior)
        // to find an instruction boundary that aligns cleanly with target
        for (int backBytes = Math.min(24, target); backBytes >= 2; backBytes--) {
            int candidateOffset = (target - backBytes) & 0xFFFF;
            List<DecodedInstruction> candidateList = new ArrayList<>();
            int cur = candidateOffset;
            boolean aligned = false;
            while (cur < target && candidateList.size() < 12) {
                byte[] buf = readAhead(memory, segment, (short) cur, 15);
                DecodedInstruction di = decodeOne(buf, 0, cur);
                candidateList.add(di);
                cur += di.length;
                if (cur == target) {
                    aligned = true;
                    break;
                }
            }
            if (aligned) {
                // Take the last `beforeCount` instructions
                int startIdx = Math.max(0, candidateList.size() - beforeCount);
                beforeList.addAll(candidateList.subList(startIdx, candidateList.size()));
                break;
            }
        }

        List<DecodedInstruction> fullList = new ArrayList<>(beforeList);

        // Current instruction + after instructions
        int cur = target;
        for (int i = 0; i <= afterCount; i++) {
            byte[] buf = readAhead(memory, segment, (short) cur, 15);
            DecodedInstruction di = decodeOne(buf, 0, cur);
            fullList.add(di);
            cur = (cur + di.length) & 0xFFFF;
        }

        return fullList;
    }

    private static byte[] readAhead(RealModeMemory memory, short segment, short offset, int len) {
        byte[] buf = new byte[len];
        for (int b = 0; b < len; b++) {
            try {
                buf[b] = memory.readByte(new RealModeAddress(segment, (short) (offset + b)));
            } catch (Exception e) {
                buf[b] = (byte) 0x90;
            }
        }
        return buf;
    }

    public static List<DecodedInstruction> disassembleMemory(RealModeMemory memory, short segment, short startOffset, int count) {
        List<DecodedInstruction> result = new ArrayList<>();
        short curOffset = startOffset;
        for (int i = 0; i < count; i++) {
            byte[] buf = new byte[15];
            for (int b = 0; b < buf.length; b++) {
                try {
                    buf[b] = memory.readByte(new RealModeAddress(segment, (short) (curOffset + b)));
                } catch (Exception e) {
                    buf[b] = (byte) 0x90;
                }
            }
            DecodedInstruction decoded = decodeOne(buf, 0, curOffset & 0xFFFF);
            result.add(decoded);
            curOffset += (short) decoded.length;
        }
        return result;
    }

    public static List<DecodedInstruction> decodeStream(byte[] bytes, int startIdx, int maxBytes, int baseOffset) {
        List<DecodedInstruction> list = new ArrayList<>();
        int idx = startIdx;
        int end = Math.min(bytes.length, startIdx + maxBytes);
        while (idx < end) {
            int remaining = end - idx;
            byte[] chunk = new byte[Math.min(15, remaining)];
            System.arraycopy(bytes, idx, chunk, 0, chunk.length);
            DecodedInstruction di = decodeOne(chunk, 0, (baseOffset + idx - startIdx) & 0xFFFF);
            list.add(di);
            idx += di.length;
        }
        return list;
    }

    private static class Reader {
        final byte[] b;
        int pos;

        Reader(byte[] b, int pos) {
            this.b = b;
            this.pos = pos;
        }

        boolean has(int n) {
            return pos + n <= b.length;
        }

        int u8() {
            return has(1) ? (b[pos++] & 0xFF) : 0;
        }

        int s8() {
            return has(1) ? b[pos++] : 0;
        }

        int u16() {
            int low = u8();
            int high = u8();
            return (high << 8) | low;
        }

        int s16() {
            return (short) u16();
        }
    }

    private static class ModRm {
        int mod;
        int reg;
        int rm;
        String ea;
        int bytesRead;

        static ModRm parse(Reader r, boolean isWord) {
            ModRm m = new ModRm();
            int start = r.pos;
            int b = r.u8();
            m.mod = (b >> 6) & 0x03;
            m.reg = (b >> 3) & 0x07;
            m.rm = b & 0x07;

            if (m.mod == 3) {
                m.ea = isWord ? R16[m.rm] : R8[m.rm];
            } else {
                String ptr = isWord ? "WORD [" : "BYTE [";
                if (m.mod == 0 && m.rm == 6) {
                    int disp = r.u16();
                    m.ea = ptr + String.format("0x%04X", disp) + "]";
                } else {
                    String base = EA_BASE[m.rm];
                    if (m.mod == 0) {
                        m.ea = ptr + base + "]";
                    } else if (m.mod == 1) {
                        int disp = r.s8();
                        m.ea = ptr + base + (disp >= 0 ? "+" + disp : String.valueOf(disp)) + "]";
                    } else {
                        int disp = r.s16();
                        m.ea = ptr + base + (disp >= 0 ? "+0x" + Integer.toHexString(disp) : "-0x" + Integer.toHexString(-disp)) + "]";
                    }
                }
            }
            m.bytesRead = r.pos - start;
            return m;
        }
    }

    public static DecodedInstruction decodeOne(byte[] bytes, int startOffset, int logicalOffset) {
        Reader r = new Reader(bytes, startOffset);
        String prefix = "";
        int b = r.u8();

        // Check for prefixes (REP/REPNZ/segment overrides)
        if (b == 0xF2) {
            prefix = "REPNZ ";
            b = r.u8();
        } else if (b == 0xF3) {
            prefix = "REP ";
            b = r.u8();
        }

        String mnemonic;

        switch (b) {
            case 0x90: mnemonic = "NOP"; break;

            // Arithmetic: ADD, OR, ADC, SBB, AND, SUB, XOR, CMP
            case 0x00: case 0x08: case 0x10: case 0x18: case 0x20: case 0x28: case 0x30: case 0x38: {
                int opIdx = b / 8;
                ModRm m = ModRm.parse(r, false);
                mnemonic = ALU_OPS[opIdx] + " " + m.ea + ", " + R8[m.reg];
                break;
            }
            case 0x01: case 0x09: case 0x11: case 0x19: case 0x21: case 0x29: case 0x31: case 0x39: {
                int opIdx = b / 8;
                ModRm m = ModRm.parse(r, true);
                mnemonic = ALU_OPS[opIdx] + " " + m.ea + ", " + R16[m.reg];
                break;
            }
            case 0x02: case 0x0A: case 0x12: case 0x1A: case 0x22: case 0x2A: case 0x32: case 0x3A: {
                int opIdx = b / 8;
                ModRm m = ModRm.parse(r, false);
                mnemonic = ALU_OPS[opIdx] + " " + R8[m.reg] + ", " + m.ea;
                break;
            }
            case 0x03: case 0x0B: case 0x13: case 0x1B: case 0x23: case 0x2B: case 0x33: case 0x3B: {
                int opIdx = b / 8;
                ModRm m = ModRm.parse(r, true);
                mnemonic = ALU_OPS[opIdx] + " " + R16[m.reg] + ", " + m.ea;
                break;
            }
            case 0x04: case 0x0C: case 0x14: case 0x1C: case 0x24: case 0x2C: case 0x34: case 0x3C: {
                int opIdx = (b - 4) / 8;
                int imm = r.u8();
                mnemonic = ALU_OPS[opIdx] + " AL, 0x" + Integer.toHexString(imm);
                break;
            }
            case 0x05: case 0x0D: case 0x15: case 0x1D: case 0x25: case 0x2D: case 0x35: case 0x3D: {
                int opIdx = (b - 5) / 8;
                int imm = r.u16();
                mnemonic = ALU_OPS[opIdx] + " AX, 0x" + Integer.toHexString(imm);
                break;
            }

            // PUSH / POP Segment
            case 0x06: mnemonic = "PUSH ES"; break;
            case 0x07: mnemonic = "POP ES"; break;
            case 0x0E: mnemonic = "PUSH CS"; break;
            case 0x16: mnemonic = "PUSH SS"; break;
            case 0x17: mnemonic = "POP SS"; break;
            case 0x1E: mnemonic = "PUSH DS"; break;
            case 0x1F: mnemonic = "POP DS"; break;

            // INC / DEC Reg16 (0x40 - 0x4F)
            case 0x40: case 0x41: case 0x42: case 0x43: case 0x44: case 0x45: case 0x46: case 0x47:
                mnemonic = "INC " + R16[b & 7]; break;
            case 0x48: case 0x49: case 0x4A: case 0x4B: case 0x4C: case 0x4D: case 0x4E: case 0x4F:
                mnemonic = "DEC " + R16[b & 7]; break;

            // PUSH / POP Reg16 (0x50 - 0x5F)
            case 0x50: case 0x51: case 0x52: case 0x53: case 0x54: case 0x55: case 0x56: case 0x57:
                mnemonic = "PUSH " + R16[b & 7]; break;
            case 0x58: case 0x59: case 0x5A: case 0x5B: case 0x5C: case 0x5D: case 0x5E: case 0x5F:
                mnemonic = "POP " + R16[b & 7]; break;

            // Jcc short jumps (0x70 - 0x7F)
            case 0x70: mnemonic = "JO " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x71: mnemonic = "JNO " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x72: mnemonic = "JB " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x73: mnemonic = "JAE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x74: mnemonic = "JE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x75: mnemonic = "JNE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x76: mnemonic = "JBE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x77: mnemonic = "JA " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x78: mnemonic = "JS " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x79: mnemonic = "JNS " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7A: mnemonic = "JP " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7B: mnemonic = "JNP " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7C: mnemonic = "JL " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7D: mnemonic = "JGE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7E: mnemonic = "JLE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0x7F: mnemonic = "JG " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;

            // Group 1: 0x80 - 0x83
            case 0x80: case 0x82: {
                ModRm m = ModRm.parse(r, false);
                int imm = r.u8();
                mnemonic = ALU_OPS[m.reg] + " " + m.ea + ", 0x" + Integer.toHexString(imm);
                break;
            }
            case 0x81: {
                ModRm m = ModRm.parse(r, true);
                int imm = r.u16();
                mnemonic = ALU_OPS[m.reg] + " " + m.ea + ", 0x" + Integer.toHexString(imm);
                break;
            }
            case 0x83: {
                ModRm m = ModRm.parse(r, true);
                int imm = r.s8();
                mnemonic = ALU_OPS[m.reg] + " " + m.ea + ", " + imm;
                break;
            }

            // TEST, XCHG, MOV
            case 0x84: {
                ModRm m = ModRm.parse(r, false);
                mnemonic = "TEST " + m.ea + ", " + R8[m.reg];
                break;
            }
            case 0x85: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "TEST " + m.ea + ", " + R16[m.reg];
                break;
            }
            case 0x86: {
                ModRm m = ModRm.parse(r, false);
                mnemonic = "XCHG " + R8[m.reg] + ", " + m.ea;
                break;
            }
            case 0x87: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "XCHG " + R16[m.reg] + ", " + m.ea;
                break;
            }
            case 0x88: {
                ModRm m = ModRm.parse(r, false);
                mnemonic = "MOV " + m.ea + ", " + R8[m.reg];
                break;
            }
            case 0x89: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "MOV " + m.ea + ", " + R16[m.reg];
                break;
            }
            case 0x8A: {
                ModRm m = ModRm.parse(r, false);
                mnemonic = "MOV " + R8[m.reg] + ", " + m.ea;
                break;
            }
            case 0x8B: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "MOV " + R16[m.reg] + ", " + m.ea;
                break;
            }
            case 0x8C: {
                ModRm m = ModRm.parse(r, true);
                int sregIdx = m.reg & 3;
                mnemonic = "MOV " + m.ea + ", " + SREG[sregIdx];
                break;
            }
            case 0x8D: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "LEA " + R16[m.reg] + ", " + m.ea;
                break;
            }
            case 0x8E: {
                ModRm m = ModRm.parse(r, true);
                int sregIdx = m.reg & 3;
                mnemonic = "MOV " + SREG[sregIdx] + ", " + m.ea;
                break;
            }
            case 0x8F: {
                ModRm m = ModRm.parse(r, true);
                mnemonic = "POP " + m.ea;
                break;
            }

            // XCHG AX, reg16 (0x91 - 0x97)
            case 0x91: case 0x92: case 0x93: case 0x94: case 0x95: case 0x96: case 0x97:
                mnemonic = "XCHG AX, " + R16[b & 7]; break;

            case 0x98: mnemonic = "CBW"; break;
            case 0x99: mnemonic = "CWD"; break;
            case 0x9A: {
                int off = r.u16();
                int seg = r.u16();
                mnemonic = String.format("CALL FAR 0x%04X:0x%04X", seg, off);
                break;
            }
            case 0x9B: mnemonic = "WAIT"; break;
            case 0x9C: mnemonic = "PUSHF"; break;
            case 0x9D: mnemonic = "POPF"; break;
            case 0x9E: mnemonic = "SAHF"; break;
            case 0x9F: mnemonic = "LAHF"; break;

            // MOV imm to reg (0xB0 - 0xBF)
            case 0xB0: case 0xB1: case 0xB2: case 0xB3: case 0xB4: case 0xB5: case 0xB6: case 0xB7:
                mnemonic = "MOV " + R8[b & 7] + ", 0x" + Integer.toHexString(r.u8()); break;
            case 0xB8: case 0xB9: case 0xBA: case 0xBB: case 0xBC: case 0xBD: case 0xBE: case 0xBF:
                mnemonic = "MOV " + R16[b & 7] + ", 0x" + String.format("%04X", r.u16()); break;

            // String operations (0xA4 - 0xAF)
            case 0xA4: mnemonic = prefix + "MOVSB"; break;
            case 0xA5: mnemonic = prefix + "MOVSW"; break;
            case 0xA6: mnemonic = prefix + "CMPSB"; break;
            case 0xA7: mnemonic = prefix + "CMPSW"; break;
            case 0xAA: mnemonic = prefix + "STOSB"; break;
            case 0xAB: mnemonic = prefix + "STOSW"; break;
            case 0xAC: mnemonic = prefix + "LODSB"; break;
            case 0xAD: mnemonic = prefix + "LODSW"; break;
            case 0xAE: mnemonic = prefix + "SCASB"; break;
            case 0xAF: mnemonic = prefix + "SCASW"; break;

            // Shift / Rotate: 0xD0 - 0xD3
            case 0xD0: case 0xD2: {
                ModRm m = ModRm.parse(r, false);
                String cnt = (b == 0xD0) ? "1" : "CL";
                mnemonic = SHIFT_OPS[m.reg] + " " + m.ea + ", " + cnt;
                break;
            }
            case 0xD1: case 0xD3: {
                ModRm m = ModRm.parse(r, true);
                String cnt = (b == 0xD1) ? "1" : "CL";
                mnemonic = SHIFT_OPS[m.reg] + " " + m.ea + ", " + cnt;
                break;
            }

            // LOOP & control (0xE0 - 0xEB)
            case 0xE0: mnemonic = "LOOPNE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0xE1: mnemonic = "LOOPE " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0xE2: mnemonic = "LOOP " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0xE3: mnemonic = "JCXZ " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;
            case 0xE8: {
                int disp = r.s16();
                int target = (logicalOffset + (r.pos - startOffset) + disp) & 0xFFFF;
                mnemonic = String.format("CALL 0x%04X", target);
                break;
            }
            case 0xE9: {
                int disp = r.s16();
                int target = (logicalOffset + (r.pos - startOffset) + disp) & 0xFFFF;
                mnemonic = String.format("JMP 0x%04X", target);
                break;
            }
            case 0xEA: {
                int off = r.u16();
                int seg = r.u16();
                mnemonic = String.format("JMP %04X:%04X", seg, off);
                break;
            }
            case 0xEB: mnemonic = "JMP " + relTarget(logicalOffset, r.s8(), r.pos - startOffset); break;

            // INT
            case 0xCD: {
                int intNum = r.u8();
                if (intNum == 0x86) mnemonic = "INT 0x86 (Throw Bomb)";
                else if (intNum == 0x87) mnemonic = "INT 0x87 (Survivor Debug)";
                else mnemonic = "INT 0x" + Integer.toHexString(intNum);
                break;
            }
            case 0xCC: mnemonic = "INT 3 (Arena Trap)"; break;

            // Flags
            case 0xF5: mnemonic = "CMC"; break;
            case 0xF8: mnemonic = "CLC"; break;
            case 0xF9: mnemonic = "STC"; break;
            case 0xFC: mnemonic = "CLD"; break;
            case 0xFD: mnemonic = "STD"; break;

            // Group 3: 0xF6 / 0xF7
            case 0xF6: {
                ModRm m = ModRm.parse(r, false);
                if (m.reg == 0) mnemonic = "TEST " + m.ea + ", 0x" + Integer.toHexString(r.u8());
                else if (m.reg == 2) mnemonic = "NOT " + m.ea;
                else if (m.reg == 3) mnemonic = "NEG " + m.ea;
                else if (m.reg == 4) mnemonic = "MUL " + m.ea;
                else if (m.reg == 6) mnemonic = "DIV " + m.ea;
                else mnemonic = "GRP3 " + m.ea;
                break;
            }
            case 0xF7: {
                ModRm m = ModRm.parse(r, true);
                if (m.reg == 0) mnemonic = "TEST " + m.ea + ", 0x" + String.format("%04X", r.u16());
                else if (m.reg == 2) mnemonic = "NOT " + m.ea;
                else if (m.reg == 3) mnemonic = "NEG " + m.ea;
                else if (m.reg == 4) mnemonic = "MUL " + m.ea;
                else if (m.reg == 6) mnemonic = "DIV " + m.ea;
                else mnemonic = "GRP3 " + m.ea;
                break;
            }

            // RET / RETF / IRET
            case 0xC2: mnemonic = String.format("RET 0x%04X", r.u16()); break;
            case 0xC3: mnemonic = "RET"; break;
            case 0xCA: mnemonic = String.format("RETF 0x%04X", r.u16()); break;
            case 0xCB: mnemonic = "RETF"; break;
            case 0xCF: mnemonic = "IRET"; break;

            // Group 4 (INC/DEC r/m8): 0xFE
            case 0xFE: {
                ModRm m = ModRm.parse(r, false);
                switch (m.reg) {
                    case 0: mnemonic = "INC " + m.ea; break;
                    case 1: mnemonic = "DEC " + m.ea; break;
                    default: mnemonic = "GRP4 " + m.ea; break;
                }
                break;
            }

            // Group 5 (INC/DEC/CALL/JMP/PUSH r/m16): 0xFF
            case 0xFF: {
                ModRm m = ModRm.parse(r, true);
                switch (m.reg) {
                    case 0: mnemonic = "INC " + m.ea; break;
                    case 1: mnemonic = "DEC " + m.ea; break;
                    case 2: mnemonic = "CALL " + m.ea; break;
                    case 3: mnemonic = "CALL FAR " + m.ea; break;
                    case 4: mnemonic = "JMP " + m.ea; break;
                    case 5: mnemonic = "JMP FAR " + m.ea; break;
                    case 6: mnemonic = "PUSH " + m.ea; break;
                    default: mnemonic = "GRP5 " + m.ea; break;
                }
                break;
            }

            default:
                mnemonic = String.format("DB 0x%02X", b);
                break;
        }

        int length = r.pos - startOffset;
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hex.append(String.format("%02X ", bytes[startOffset + i] & 0xFF));
        }

        return new DecodedInstruction(logicalOffset, length, hex.toString().trim(), mnemonic);
    }

    private static String relTarget(int baseOffset, int rel, int insnLength) {
        int target = (baseOffset + insnLength + rel) & 0xFFFF;
        return String.format("0x%04X", target);
    }
}
