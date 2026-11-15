package il.co.codeguru.corewars8086.utils;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Pure Java 8086 Mini-Assembler for CoreWars warriors.
 * Supports:
 * - ALU instructions (MOV, ADD, SUB, CMP, XOR, OR, AND, ADC, SBB, TEST)
 * - Jumps & Calls (JMP, Jcc, CALL, RET, RETF, LOOP, LOOPZ, LOOPNZ, JCXZ)
 * - Stack (PUSH, POP, PUSHF, POPF)
 * - Increment / Decrement (INC, DEC)
 * - String Operations (MOVSB, MOVSW, STOSB, STOSW, LODSB, LODSW, CMPSB, CMPSW, SCASB, SCASW, REP)
 * - Interrupts (INT 0x86, INT 3, INT n)
 * - CPU Control & Flags (NOP, CLD, STD, CLI, STI, CMC, CLC, STC, HLT)
 * - Directives (DB, DW, ORG, EQU, labels)
 */
public class MiniAssembler {

    public static class AssembleResult {
        public final boolean success;
        public final byte[] bytes;
        public final List<String> errors;
        public final Map<Integer, Integer> lineToOffset; // Source line (1-based) -> byte offset
        public final Map<String, Integer> symbols;

        public AssembleResult(boolean success, byte[] bytes, List<String> errors,
                              Map<Integer, Integer> lineToOffset, Map<String, Integer> symbols) {
            this.success = success;
            this.bytes = bytes;
            this.errors = errors;
            this.lineToOffset = lineToOffset;
            this.symbols = symbols;
        }
    }

    private static final String[] R8 = {"AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH"};
    private static final String[] R16 = {"AX", "CX", "DX", "BX", "SP", "BP", "SI", "DI"};
    private static final String[] SREG = {"ES", "CS", "SS", "DS"};
    private static final Map<String, Integer> ALU_OPS = new HashMap<>();

    static {
        ALU_OPS.put("ADD", 0);
        ALU_OPS.put("OR", 1);
        ALU_OPS.put("ADC", 2);
        ALU_OPS.put("SBB", 3);
        ALU_OPS.put("AND", 4);
        ALU_OPS.put("SUB", 5);
        ALU_OPS.put("XOR", 6);
        ALU_OPS.put("CMP", 7);
    }

    private static final Map<String, Integer> JCC_OPS = new HashMap<>();

    static {
        JCC_OPS.put("JO", 0x70);
        JCC_OPS.put("JNO", 0x71);
        JCC_OPS.put("JB", 0x72);  JCC_OPS.put("JC", 0x72);  JCC_OPS.put("JNAE", 0x72);
        JCC_OPS.put("JAE", 0x73); JCC_OPS.put("JNC", 0x73); JCC_OPS.put("JNB", 0x73);
        JCC_OPS.put("JE", 0x74);  JCC_OPS.put("JZ", 0x74);
        JCC_OPS.put("JNE", 0x75); JCC_OPS.put("JNZ", 0x75);
        JCC_OPS.put("JBE", 0x76); JCC_OPS.put("JNA", 0x76);
        JCC_OPS.put("JA", 0x77);  JCC_OPS.put("JNBE", 0x77);
        JCC_OPS.put("JS", 0x78);
        JCC_OPS.put("JNS", 0x79);
        JCC_OPS.put("JP", 0x7A);  JCC_OPS.put("JPE", 0x7A);
        JCC_OPS.put("JNP", 0x7B); JCC_OPS.put("JPO", 0x7B);
        JCC_OPS.put("JL", 0x7C);  JCC_OPS.put("JNGE", 0x7C);
        JCC_OPS.put("JGE", 0x7D); JCC_OPS.put("JNL", 0x7D);
        JCC_OPS.put("JLE", 0x7E); JCC_OPS.put("JNG", 0x7E);
        JCC_OPS.put("JG", 0x7F);  JCC_OPS.put("JNLE", 0x7F);
    }

    public static AssembleResult assemble(String source) {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> symbols = new HashMap<>();
        Map<Integer, Integer> lineToOffset = new LinkedHashMap<>();

        String[] rawLines = source.split("\\r?\\n");

        // Pass 1: Collect labels and calculate instruction offsets
        int currentOffset = 0;
        List<ParsedLine> parsedLines = new ArrayList<>();

        for (int i = 0; i < rawLines.length; i++) {
            int lineNum = i + 1;
            String line = rawLines[i];
            int commentIdx = line.indexOf(';');
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx);
            }
            line = line.trim();
            if (line.isEmpty()) continue;

            // Check for labels (e.g. "loop_start:" or "loop_start: MOV AX, BX")
            while (line.contains(":") && !line.startsWith("DB") && !line.startsWith("DW")) {
                int colon = line.indexOf(':');
                String labelName = line.substring(0, colon).trim().toUpperCase();
                if (!labelName.isEmpty() && isValidIdentifier(labelName)) {
                    symbols.put(labelName, currentOffset);
                }
                line = line.substring(colon + 1).trim();
            }
            if (line.isEmpty()) continue;

            // Check for EQU directive: NAME EQU VALUE
            if (line.toUpperCase().contains(" EQU ")) {
                String[] parts = line.split("(?i)\\s+EQU\\s+");
                if (parts.length == 2) {
                    try {
                        int val = parseValue(parts[1].trim(), symbols);
                        symbols.put(parts[0].trim().toUpperCase(), val);
                        continue;
                    } catch (Exception e) {
                        errors.add("Line " + lineNum + ": Invalid EQU value: " + parts[1]);
                        continue;
                    }
                }
            }

            // Estimate length or parse instruction
            ParsedLine pl = new ParsedLine(lineNum, line, currentOffset);
            parsedLines.add(pl);
            int estLen = estimateLength(line);
            currentOffset += estLen;
        }

        // Pass 2: Generate machine code bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        currentOffset = 0;

        for (ParsedLine pl : parsedLines) {
            lineToOffset.put(pl.lineNum, currentOffset);
            try {
                byte[] code = assembleInstruction(pl.text, currentOffset, symbols);
                out.write(code);
                currentOffset += code.length;
            } catch (Exception e) {
                errors.add("Line " + pl.lineNum + " [" + pl.text + "]: " + e.getMessage());
            }
        }

        boolean success = errors.isEmpty();
        return new AssembleResult(success, out.toByteArray(), errors, lineToOffset, symbols);
    }

    private static class ParsedLine {
        final int lineNum;
        final String text;
        final int offset;

        ParsedLine(int lineNum, String text, int offset) {
            this.lineNum = lineNum;
            this.text = text;
            this.offset = offset;
        }
    }

    private static boolean isValidIdentifier(String s) {
        if (s.isEmpty()) return false;
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '@') return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '@') return false;
        }
        return true;
    }

    private static int estimateLength(String line) {
        String u = line.trim().toUpperCase();
        if (u.startsWith("DB ")) {
            String args = line.substring(3).trim();
            return args.split(",").length;
        }
        if (u.startsWith("DW ")) {
            String args = line.substring(3).trim();
            return args.split(",").length * 2;
        }
        if (u.equals("NOP") || u.startsWith("INT 3") || u.startsWith("RET") || u.startsWith("RETF") ||
            u.startsWith("IRET") || u.startsWith("CLD") || u.startsWith("STD") || u.startsWith("CLI") ||
            u.startsWith("STI") || u.startsWith("CMC") || u.startsWith("CLC") || u.startsWith("STC") ||
            u.startsWith("HLT") || u.startsWith("MOVSB") || u.startsWith("MOVSW") || u.startsWith("STOSB") ||
            u.startsWith("STOSW") || u.startsWith("LODSB") || u.startsWith("LODSW") || u.startsWith("CMPSB") ||
            u.startsWith("CMPSW") || u.startsWith("SCASB") || u.startsWith("SCASW")) {
            return 1;
        }
        if (u.startsWith("INT ") || u.startsWith("JMP SHORT") || u.startsWith("LOOP") ||
            JCC_OPS.keySet().stream().anyMatch(u::startsWith)) {
            return 2;
        }
        if (u.startsWith("PUSH ") || u.startsWith("POP ") || u.startsWith("INC ") || u.startsWith("DEC ")) {
            String op = u.substring(u.indexOf(' ') + 1).trim();
            if (findRegister16(op) != -1) return 1;
            return 2;
        }
        if (u.startsWith("CALL FAR")) return 5;
        if (u.startsWith("CALL ") || u.startsWith("JMP ")) return 3;
        // Most MOV, ALU operations: 2 to 4 bytes
        return 3;
    }

    public static byte[] assembleInstruction(String line, int currentOffset, Map<String, Integer> symbols) throws Exception {
        String u = line.trim().toUpperCase();

        // 1. Prefixes (REP, REPE, REPZ, REPNE, REPNZ)
        if (u.startsWith("REP ") || u.startsWith("REPE ") || u.startsWith("REPZ ")) {
            byte[] rest = assembleInstruction(line.substring(line.indexOf(' ') + 1).trim(), currentOffset + 1, symbols);
            byte[] res = new byte[rest.length + 1];
            res[0] = (byte) 0xF3;
            System.arraycopy(rest, 0, res, 1, rest.length);
            return res;
        }
        if (u.startsWith("REPNE ") || u.startsWith("REPNZ ")) {
            byte[] rest = assembleInstruction(line.substring(line.indexOf(' ') + 1).trim(), currentOffset + 1, symbols);
            byte[] res = new byte[rest.length + 1];
            res[0] = (byte) 0xF2;
            System.arraycopy(rest, 0, res, 1, rest.length);
            return res;
        }

        // 2. Simple 1-byte opcodes
        switch (u) {
            case "NOP": return new byte[]{(byte) 0x90};
            case "INT 3": return new byte[]{(byte) 0xCC};
            case "RET": return new byte[]{(byte) 0xC3};
            case "RETF": return new byte[]{(byte) 0xCB};
            case "IRET": return new byte[]{(byte) 0xCF};
            case "PUSHF": return new byte[]{(byte) 0x9C};
            case "POPF": return new byte[]{(byte) 0x9D};
            case "MOVSB": return new byte[]{(byte) 0xA4};
            case "MOVSW": return new byte[]{(byte) 0xA5};
            case "CMPSB": return new byte[]{(byte) 0xA6};
            case "CMPSW": return new byte[]{(byte) 0xA7};
            case "STOSB": return new byte[]{(byte) 0xAA};
            case "STOSW": return new byte[]{(byte) 0xAB};
            case "LODSB": return new byte[]{(byte) 0xAC};
            case "LODSW": return new byte[]{(byte) 0xAD};
            case "SCASB": return new byte[]{(byte) 0xAE};
            case "SCASW": return new byte[]{(byte) 0xAF};
            case "CLD": return new byte[]{(byte) 0xFC};
            case "STD": return new byte[]{(byte) 0xFD};
            case "CLI": return new byte[]{(byte) 0xFA};
            case "STI": return new byte[]{(byte) 0xFB};
            case "CMC": return new byte[]{(byte) 0xF5};
            case "CLC": return new byte[]{(byte) 0xF8};
            case "STC": return new byte[]{(byte) 0xF9};
            case "HLT": return new byte[]{(byte) 0xF4};
        }

        // 3. Directives (DB, DW)
        if (u.startsWith("DB ")) {
            String[] parts = line.substring(3).split(",");
            byte[] res = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                res[i] = (byte) parseValue(parts[i].trim(), symbols);
            }
            return res;
        }
        if (u.startsWith("DW ")) {
            String[] parts = line.substring(3).split(",");
            byte[] res = new byte[parts.length * 2];
            for (int i = 0; i < parts.length; i++) {
                int val = parseValue(parts[i].trim(), symbols);
                res[i * 2] = (byte) (val & 0xFF);
                res[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
            }
            return res;
        }

        // 4. INT imm
        if (u.startsWith("INT ")) {
            String arg = u.substring(4).trim();
            int num = parseValue(arg, symbols);
            if (num == 3) return new byte[]{(byte) 0xCC};
            return new byte[]{(byte) 0xCD, (byte) num};
        }

        // 5. Jcc conditional jumps
        String firstWord = u.split("\\s+")[0];
        if (JCC_OPS.containsKey(firstWord)) {
            int op = JCC_OPS.get(firstWord);
            String target = u.substring(firstWord.length()).trim();
            int targetAddr = parseValue(target, symbols);
            int disp = targetAddr - (currentOffset + 2);
            if (disp < -128 || disp > 127) {
                throw new Exception("Short jump out of range: " + disp + " bytes (must be -128..+127)");
            }
            return new byte[]{(byte) op, (byte) disp};
        }

        // 6. LOOP, LOOPZ, LOOPNZ, JCXZ
        if (firstWord.equals("LOOP") || firstWord.equals("LOOPZ") || firstWord.equals("LOOPE") ||
            firstWord.equals("LOOPNZ") || firstWord.equals("LOOPNE") || firstWord.equals("JCXZ")) {
            int op = 0xE2;
            if (firstWord.equals("LOOPZ") || firstWord.equals("LOOPE")) op = 0xE1;
            else if (firstWord.equals("LOOPNZ") || firstWord.equals("LOOPNE")) op = 0xE0;
            else if (firstWord.equals("JCXZ")) op = 0xE3;

            String target = u.substring(firstWord.length()).trim();
            int targetAddr = parseValue(target, symbols);
            int disp = targetAddr - (currentOffset + 2);
            return new byte[]{(byte) op, (byte) disp};
        }

        // 7. JMP & CALL
        if (firstWord.equals("JMP") || firstWord.equals("CALL")) {
            boolean isCall = firstWord.equals("CALL");
            String arg = u.substring(firstWord.length()).trim();
            if (arg.startsWith("SHORT ") && !isCall) {
                arg = arg.substring(6).trim();
                int targetAddr = parseValue(arg, symbols);
                int disp = targetAddr - (currentOffset + 2);
                return new byte[]{(byte) 0xEB, (byte) disp};
            }
            if (arg.startsWith("FAR ")) {
                String targetPart = arg.substring(4).trim();
                if (targetPart.contains("[")) {
                    ModRM modrm = parseModRM(targetPart, isCall ? 3 : 5, symbols);
                    return buildModRMInstruction((byte) 0xFF, modrm);
                }
                String[] segOff = targetPart.split(":");
                if (segOff.length == 2) {
                    int seg = parseValue(segOff[0].trim(), symbols);
                    int off = parseValue(segOff[1].trim(), symbols);
                    return new byte[]{
                            (byte) (isCall ? 0x9A : 0xEA),
                            (byte) (off & 0xFF), (byte) ((off >> 8) & 0xFF),
                            (byte) (seg & 0xFF), (byte) ((seg >> 8) & 0xFF)
                    };
                }
            }
            if (arg.contains("[")) {
                ModRM modrm = parseModRM(arg, isCall ? 2 : 4, symbols);
                return buildModRMInstruction((byte) 0xFF, modrm);
            }
            int r16 = findRegister16(arg);
            if (r16 != -1) {
                // CALL r16: FF /2, JMP r16: FF /4
                int subcode = isCall ? 2 : 4;
                return new byte[]{(byte) 0xFF, (byte) (0xC0 | (subcode << 3) | r16)};
            }
            // Direct near jump / call
            int targetAddr = parseValue(arg, symbols);
            int disp = targetAddr - (currentOffset + 3);
            return new byte[]{
                    (byte) (isCall ? 0xE8 : 0xE9),
                    (byte) (disp & 0xFF),
                    (byte) ((disp >> 8) & 0xFF)
            };
        }

        // 8. PUSH & POP
        if (firstWord.equals("PUSH") || firstWord.equals("POP")) {
            boolean isPush = firstWord.equals("PUSH");
            String arg = u.substring(firstWord.length()).trim();
            int r16 = findRegister16(arg);
            if (r16 != -1) {
                return new byte[]{(byte) ((isPush ? 0x50 : 0x58) + r16)};
            }
            int sreg = findSegmentRegister(arg);
            if (sreg != -1) {
                if (isPush) {
                    return new byte[]{(byte) (0x06 | (sreg << 3))};
                } else {
                    if (sreg == 1) throw new Exception("POP CS is not supported by 8086 hardware");
                    return new byte[]{(byte) (0x07 | (sreg << 3))};
                }
            }
        }

        // 9. INC & DEC
        if (firstWord.equals("INC") || firstWord.equals("DEC")) {
            boolean isInc = firstWord.equals("INC");
            String arg = u.substring(firstWord.length()).trim();
            int r16 = findRegister16(arg);
            if (r16 != -1) {
                return new byte[]{(byte) ((isInc ? 0x40 : 0x48) + r16)};
            }
            int r8 = findRegister8(arg);
            if (r8 != -1) {
                return new byte[]{(byte) 0xFE, (byte) (0xC0 | ((isInc ? 0 : 1) << 3) | r8)};
            }
            // Memory: INC [BX] / DEC WORD [DI]
            ModRM modrm = parseModRM(arg, isInc ? 0 : 1, symbols);
            boolean isWord = isWordMemory(arg);
            return buildModRMInstruction((byte) (isWord ? 0xFF : 0xFE), modrm);
        }

        // 10. LEA reg, [mem]
        if (firstWord.equals("LEA")) {
            String[] parts = splitOperands(u.substring(3).trim());
            int r16 = findRegister16(parts[0]);
            if (r16 == -1) throw new Exception("LEA destination must be a 16-bit register");
            ModRM modrm = parseModRM(parts[1], r16, symbols);
            return buildModRMInstruction((byte) 0x8D, modrm);
        }

        // 11. MOV destination, source
        if (firstWord.equals("MOV")) {
            String[] parts = splitOperands(u.substring(3).trim());
            String dst = parts[0];
            String src = parts[1];

            int d16 = findRegister16(dst);
            int s16 = findRegister16(src);
            int d8 = findRegister8(dst);
            int s8 = findRegister8(src);
            int dSeg = findSegmentRegister(dst);
            int sSeg = findSegmentRegister(src);

            // MOV sreg, r/m16 (8E)
            if (dSeg != -1) {
                if (dSeg == 1) throw new Exception("Cannot MOV to CS register directly");
                if (s16 != -1) return new byte[]{(byte) 0x8E, (byte) (0xC0 | (dSeg << 3) | s16)};
                ModRM modrm = parseModRM(src, dSeg, symbols);
                return buildModRMInstruction((byte) 0x8E, modrm);
            }
            // MOV r/m16, sreg (8C)
            if (sSeg != -1) {
                if (d16 != -1) return new byte[]{(byte) 0x8C, (byte) (0xC0 | (sSeg << 3) | d16)};
                ModRM modrm = parseModRM(dst, sSeg, symbols);
                return buildModRMInstruction((byte) 0x8C, modrm);
            }

            // MOV reg16, reg16
            if (d16 != -1 && s16 != -1) {
                return new byte[]{(byte) 0x89, (byte) (0xC0 | (s16 << 3) | d16)};
            }
            // MOV reg8, reg8
            if (d8 != -1 && s8 != -1) {
                return new byte[]{(byte) 0x88, (byte) (0xC0 | (s8 << 3) | d8)};
            }

            // MOV reg, imm
            if (d16 != -1 && isValue(src, symbols)) {
                int val = parseValue(src, symbols);
                return new byte[]{(byte) (0xB8 + d16), (byte) (val & 0xFF), (byte) ((val >> 8) & 0xFF)};
            }
            if (d8 != -1 && isValue(src, symbols)) {
                int val = parseValue(src, symbols);
                return new byte[]{(byte) (0xB0 + d8), (byte) (val & 0xFF)};
            }

            // MOV [mem], reg16 / MOV [mem], reg8
            if (dst.contains("[")) {
                if (s16 != -1) {
                    ModRM modrm = parseModRM(dst, s16, symbols);
                    return buildModRMInstruction((byte) 0x89, modrm);
                }
                if (s8 != -1) {
                    ModRM modrm = parseModRM(dst, s8, symbols);
                    return buildModRMInstruction((byte) 0x88, modrm);
                }
                if (isValue(src, symbols)) {
                    int val = parseValue(src, symbols);
                    boolean isWord = isWordMemory(dst);
                    ModRM modrm = parseModRM(dst, 0, symbols);
                    byte op = (byte) (isWord ? 0xC7 : 0xC6);
                    byte[] base = buildModRMInstruction(op, modrm);
                    if (isWord) {
                        return appendBytes(base, (byte) (val & 0xFF), (byte) ((val >> 8) & 0xFF));
                    } else {
                        return appendBytes(base, (byte) (val & 0xFF));
                    }
                }
            }

            // MOV reg, [mem]
            if (src.contains("[")) {
                if (d16 != -1) {
                    ModRM modrm = parseModRM(src, d16, symbols);
                    return buildModRMInstruction((byte) 0x8B, modrm);
                }
                if (d8 != -1) {
                    ModRM modrm = parseModRM(src, d8, symbols);
                    return buildModRMInstruction((byte) 0x8A, modrm);
                }
            }
        }

        // 12. ALU operations (ADD, OR, ADC, SBB, AND, SUB, XOR, CMP)
        if (ALU_OPS.containsKey(firstWord)) {
            int aluCode = ALU_OPS.get(firstWord);
            String[] parts = splitOperands(u.substring(firstWord.length()).trim());
            String dst = parts[0];
            String src = parts[1];

            int d16 = findRegister16(dst);
            int s16 = findRegister16(src);
            int d8 = findRegister8(dst);
            int s8 = findRegister8(src);

            // ALU reg16, reg16
            if (d16 != -1 && s16 != -1) {
                return new byte[]{(byte) ((aluCode << 3) | 0x01), (byte) (0xC0 | (s16 << 3) | d16)};
            }
            // ALU reg8, reg8
            if (d8 != -1 && s8 != -1) {
                return new byte[]{(byte) ((aluCode << 3) | 0x00), (byte) (0xC0 | (s8 << 3) | d8)};
            }

            // ALU reg, imm
            if (d16 != -1 && isValue(src, symbols)) {
                int val = parseValue(src, symbols);
                if (d16 == 0) { // AX, imm16
                    return new byte[]{(byte) ((aluCode << 3) | 0x05), (byte) (val & 0xFF), (byte) ((val >> 8) & 0xFF)};
                }
                return new byte[]{(byte) 0x81, (byte) (0xC0 | (aluCode << 3) | d16), (byte) (val & 0xFF), (byte) ((val >> 8) & 0xFF)};
            }
            if (d8 != -1 && isValue(src, symbols)) {
                int val = parseValue(src, symbols);
                if (d8 == 0) { // AL, imm8
                    return new byte[]{(byte) ((aluCode << 3) | 0x04), (byte) (val & 0xFF)};
                }
                return new byte[]{(byte) 0x80, (byte) (0xC0 | (aluCode << 3) | d8), (byte) (val & 0xFF)};
            }

            // ALU [mem], reg
            if (dst.contains("[")) {
                if (s16 != -1) {
                    ModRM modrm = parseModRM(dst, s16, symbols);
                    return buildModRMInstruction((byte) ((aluCode << 3) | 0x01), modrm);
                }
                if (s8 != -1) {
                    ModRM modrm = parseModRM(dst, s8, symbols);
                    return buildModRMInstruction((byte) ((aluCode << 3) | 0x00), modrm);
                }
            }

            // ALU reg, [mem]
            if (src.contains("[")) {
                if (d16 != -1) {
                    ModRM modrm = parseModRM(src, d16, symbols);
                    return buildModRMInstruction((byte) ((aluCode << 3) | 0x03), modrm);
                }
                if (d8 != -1) {
                    ModRM modrm = parseModRM(src, d8, symbols);
                    return buildModRMInstruction((byte) ((aluCode << 3) | 0x02), modrm);
                }
            }
        }

        throw new Exception("Unrecognized instruction: " + line);
    }

    private static String[] splitOperands(String s) throws Exception {
        int comma = s.indexOf(',');
        if (comma == -1) throw new Exception("Missing comma between operands in: " + s);
        return new String[]{s.substring(0, comma).trim(), s.substring(comma + 1).trim()};
    }

    private static int findRegister16(String s) {
        for (int i = 0; i < R16.length; i++) {
            if (R16[i].equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    private static int findRegister8(String s) {
        for (int i = 0; i < R8.length; i++) {
            if (R8[i].equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    private static int findSegmentRegister(String s) {
        for (int i = 0; i < SREG.length; i++) {
            if (SREG[i].equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    private static boolean isWordMemory(String s) {
        String u = s.toUpperCase();
        if (u.contains("WORD")) return true;
        if (u.contains("BYTE")) return false;
        return true; // Default to WORD in CoreWars
    }

    private static boolean isValue(String s, Map<String, Integer> symbols) {
        try {
            parseValue(s, symbols);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int parseValue(String s, Map<String, Integer> symbols) throws Exception {
        s = s.trim().toUpperCase();
        if (symbols != null && symbols.containsKey(s)) {
            return symbols.get(s);
        }
        if (s.startsWith("0X")) {
            return (int) Long.parseLong(s.substring(2), 16);
        }
        if (s.endsWith("H") && s.matches("[0-9A-F]+H")) {
            return (int) Long.parseLong(s.substring(0, s.length() - 1), 16);
        }
        if (s.matches("-?\\d+")) {
            return Integer.parseInt(s);
        }
        throw new Exception("Unknown symbol or invalid value: " + s);
    }

    private static class ModRM {
        int mod;
        int reg;
        int rm;
        byte[] displacement;

        ModRM(int mod, int reg, int rm, byte[] displacement) {
            this.mod = mod;
            this.reg = reg;
            this.rm = rm;
            this.displacement = displacement;
        }
    }

    private static ModRM parseModRM(String memExpr, int regField, Map<String, Integer> symbols) throws Exception {
        int open = memExpr.indexOf('[');
        int close = memExpr.indexOf(']');
        if (open == -1 || close <= open) {
            throw new Exception("Invalid memory expression (missing brackets): " + memExpr);
        }
        String inner = memExpr.substring(open + 1, close).trim().toUpperCase();
        if (inner.startsWith("WORD ") || inner.startsWith("BYTE ")) {
            inner = inner.substring(5).trim();
        }

        // Direct addressing [1234h]
        if (!inner.contains("BX") && !inner.contains("BP") && !inner.contains("SI") && !inner.contains("DI")) {
            int addr = parseValue(inner, symbols);
            return new ModRM(0, regField, 6, new byte[]{(byte) (addr & 0xFF), (byte) ((addr >> 8) & 0xFF)});
        }

        int rmBase;
        if (inner.contains("BX") && inner.contains("SI")) rmBase = 0; // [BX+SI]
        else if (inner.contains("BX") && inner.contains("DI")) rmBase = 1; // [BX+DI]
        else if (inner.contains("BP") && inner.contains("SI")) rmBase = 2; // [BP+SI]
        else if (inner.contains("BP") && inner.contains("DI")) rmBase = 3; // [BP+DI]
        else if (inner.contains("SI")) rmBase = 4; // [SI]
        else if (inner.contains("DI")) rmBase = 5; // [DI]
        else if (inner.contains("BP")) rmBase = 6; // [BP]
        else if (inner.contains("BX")) rmBase = 7; // [BX]
        else throw new Exception("Unsupported addressing mode in [" + inner + "]");

        // Check for displacement
        int disp = 0;
        boolean hasDisp = false;
        if (inner.contains("+") || inner.contains("-")) {
            String[] parts = inner.split("[+-]");
            String last = parts[parts.length - 1].trim();
            if (!last.equals("BX") && !last.equals("BP") && !last.equals("SI") && !last.equals("DI")) {
                disp = parseValue(last, symbols);
                if (inner.contains("-" + last)) disp = -disp;
                hasDisp = true;
            }
        }

        if (!hasDisp) {
            if (rmBase == 6) { // [BP] without disp requires [BP+0] (disp8 = 0)
                return new ModRM(1, regField, 6, new byte[]{0});
            }
            return new ModRM(0, regField, rmBase, new byte[0]);
        }

        if (disp >= -128 && disp <= 127) {
            return new ModRM(1, regField, rmBase, new byte[]{(byte) disp});
        } else {
            return new ModRM(2, regField, rmBase, new byte[]{(byte) (disp & 0xFF), (byte) ((disp >> 8) & 0xFF)});
        }
    }

    private static byte[] buildModRMInstruction(byte opcode, ModRM modrm) {
        byte modrmByte = (byte) ((modrm.mod << 6) | (modrm.reg << 3) | modrm.rm);
        byte[] res = new byte[2 + modrm.displacement.length];
        res[0] = opcode;
        res[1] = modrmByte;
        System.arraycopy(modrm.displacement, 0, res, 2, modrm.displacement.length);
        return res;
    }

    private static byte[] appendBytes(byte[] base, byte... extra) {
        byte[] res = new byte[base.length + extra.length];
        System.arraycopy(base, 0, res, 0, base.length);
        System.arraycopy(extra, 0, res, base.length, extra.length);
        return res;
    }
}
