package il.co.codeguru.corewars8086.utils;

import il.co.codeguru.corewars8086.cpu.CpuState;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;
import il.co.codeguru.corewars8086.war.War;

/**
 * Analyzes 8086 instructions:
 * 1. Generates micro-step breakdowns (tooltips) for complex instructions (CALL FAR, CALL, RET, PUSH, POP, INT, MOVS, etc.)
 * 2. Predicts memory write target or branch target for the faint/ghost preview on the Arena map.
 */
public class InstructionAnalyzer {

    public static class StepPreview {
        public final Integer targetMemoryOffset; // 0..65535 on Arena, or null
        public final boolean isWrite;            // true if writing memory, false if jump target
        public final int length;                 // affected bytes count (e.g. 1, 2, 128 for INT 86)
        public final String label;               // Short description for badge
        public final String tooltipHtml;         // Full detailed breakdown

        public StepPreview(Integer targetMemoryOffset, boolean isWrite, int length, String label, String tooltipHtml) {
            this.targetMemoryOffset = targetMemoryOffset;
            this.isWrite = isWrite;
            this.length = length;
            this.label = label;
            this.tooltipHtml = tooltipHtml;
        }
    }

    /**
     * Deep analysis of the current pending instruction at CS:IP using live CPU state.
     */
    public static StepPreview analyze(RealModeMemory mem, CpuState s) {
        if (mem == null || s == null) {
            return new StepPreview(null, false, 0, "", "");
        }

        short cs = s.getCS();
        short ip = s.getIP();

        byte[] buf = new byte[8];
        for (int i = 0; i < buf.length; i++) {
            try {
                buf[i] = mem.readByte(new RealModeAddress(cs, (short) (ip + i)));
            } catch (Exception e) {
                buf[i] = (byte) 0x90;
            }
        }

        Disassembler.DecodedInstruction di = Disassembler.decodeOne(buf, 0, ip & 0xFFFF);
        String text = di.text.trim();

        Integer targetOffset = null;
        boolean isWrite = false;
        int length = 1;
        String label = null;

        // Determine destination & effect based on instruction type
        String upperText = text.toUpperCase();
        if (upperText.contains("INT 0X86") || upperText.contains("INT 86")) {
            // Bomb throw in CoreWars
            if (s.getES() == War.ARENA_SEGMENT) {
                targetOffset = s.getDI() & 0xFFFF;
                isWrite = true;
                length = 128; // 64 words = 128 bytes
                label = "INT 0x86 BOMB [128 BYTES]";
            }
        } else if (text.startsWith("MOVSB") || text.startsWith("MOVSW")) {
            boolean isWord = text.contains("MOVSW");
            length = isWord ? 2 : 1;
            if (s.getES() == War.ARENA_SEGMENT) {
                targetOffset = s.getDI() & 0xFFFF;
                isWrite = true;
                label = "MOVS WRITE [ES:DI]";
            }
        } else if (text.startsWith("STOSB") || text.startsWith("STOSW")) {
            boolean isWord = text.contains("STOSW");
            length = isWord ? 2 : 1;
            if (s.getES() == War.ARENA_SEGMENT) {
                targetOffset = s.getDI() & 0xFFFF;
                isWrite = true;
                label = "STOS WRITE [ES:DI]";
            }
        } else if (isMemoryWrite(text)) {
            Integer ea = resolveEffectiveAddress(text, s);
            int seg = resolveSegment(text, s);
            if (ea != null && seg == War.ARENA_SEGMENT) {
                targetOffset = ea;
                isWrite = true;
                length = isWordOp(text) ? 2 : 1;
                label = String.format("MEM WRITE [0x%04X]", ea);
            }
        } else if (text.startsWith("PUSH")) {
            if (s.getSS() == War.ARENA_SEGMENT) {
                targetOffset = (s.getSP() - 2) & 0xFFFF;
                isWrite = true;
                length = 2;
                label = String.format("STACK PUSH [0x%04X]", targetOffset);
            }
        } else if (text.startsWith("CALL 0x") || text.startsWith("CALL near")) {
            int target = parseHexTarget(text);
            targetOffset = target;
            isWrite = false;
            label = String.format("CALL -> 0x%04X", target);
        } else if (text.startsWith("CALL FAR") || (buf[0] == (byte) 0x9A)) {
            int target = 0;
            int targetSeg = 0;
            if (buf[0] == (byte) 0x9A) {
                target = (buf[1] & 0xFF) | ((buf[2] & 0xFF) << 8);
                targetSeg = (buf[3] & 0xFF) | ((buf[4] & 0xFF) << 8);
            } else if (text.contains(":")) {
                int colon = text.indexOf(':');
                targetSeg = parseHexTarget(text.substring(0, colon));
                target = parseHexTarget(text.substring(colon + 1));
            } else {
                target = parseHexTarget(text);
                targetSeg = s.getCS() & 0xFFFF;
            }
            if (targetSeg == War.ARENA_SEGMENT) {
                targetOffset = target;
            }
            isWrite = false;
            label = String.format("CALL FAR -> %04X:%04X", targetSeg, target);
        } else if (text.startsWith("JMP 0x") || text.startsWith("JMP near") || text.startsWith("JMP short")) {
            int target = parseHexTarget(text);
            targetOffset = target;
            isWrite = false;
            label = String.format("JMP -> 0x%04X", target);
        } else if (isConditionalJump(text)) {
            boolean cond = evaluateJump(text, s);
            int target = parseHexTarget(text);
            if (cond) {
                targetOffset = target;
                isWrite = false;
                label = String.format("BRANCH TAKEN -> 0x%04X", target);
            } else {
                label = "BRANCH NOT TAKEN";
            }
        } else if (text.startsWith("LOOP")) {
            boolean cond = evaluateLoop(text, s);
            int target = parseHexTarget(text);
            if (cond) {
                targetOffset = target;
                isWrite = false;
                label = String.format("LOOP TAKEN -> 0x%04X", target);
            } else {
                label = "LOOP EXIT";
            }
        }

        String tooltip = getInstructionTooltip(text, s, di);
        return new StepPreview(targetOffset, isWrite, length, label, tooltip);
    }

    /**
     * Generates a rich, step-by-step micro-execution breakdown tooltip for any instruction.
     */
    public static String getInstructionTooltip(String instructionText, CpuState s, Disassembler.DecodedInstruction di) {
        if (instructionText == null || instructionText.trim().isEmpty()) {
            return "<html><i>No instruction</i></html>";
        }

        String text = instructionText.trim();
        String upper = text.toUpperCase();

        StringBuilder html = new StringBuilder();
        html.append("<html><div style='font-family:Segoe UI,Tahoma,Sans-Serif; font-size:13px; line-height:1.35; padding:8px; max-width:440px; color:#1a1a1a;'>");

        // Header Title Box
        html.append("<div style='background-color:#1e3a5f; color:#ffffff; padding:6px 10px; border-radius:5px; font-weight:bold; font-size:14px; margin-bottom:8px;'>");
        html.append("<span style='color:#a8d5ff; font-size:11px; text-transform:uppercase;'>8086 Opcode:</span><br/><code>").append(escapeHtml(text)).append("</code>");
        html.append("</div>");

        // Instruction details & micro-steps
        if (upper.startsWith("CALL FAR") || (di != null && di.text.startsWith("CALL FAR"))) {
            int curCs = (s != null) ? (s.getCS() & 0xFFFF) : 0x1000;
            int nextIp = (di != null && s != null) ? ((s.getIP() + di.length) & 0xFFFF) : 0;
            String newCsStr = "operands";
            String newIpStr = "operands";
            if (upper.contains(":")) {
                try {
                    int colon = upper.indexOf(':');
                    String p1 = upper.substring(upper.indexOf("FAR") + 3, colon).trim();
                    String p2 = upper.substring(colon + 1).trim();
                    int sVal = parseHexTarget(p1);
                    int oVal = parseHexTarget(p2);
                    newCsStr = String.format("0x%04X", sVal);
                    newIpStr = String.format("0x%04X", oVal);
                } catch (Exception ignored) {}
            }
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Inter-Segment Subroutine Call (FAR)</div>");
            html.append("Executes a far subroutine call across segments by saving full context to the stack:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Decrement Stack Pointer:</b> <code>SP = SP - 2</code></li>");
            html.append(String.format("<li><b>Push Code Segment:</b> <code>[SS:SP] = CS (0x%04X)</code></li>", curCs));
            html.append("<li><b>Decrement Stack Pointer:</b> <code>SP = SP - 2</code></li>");
            html.append(String.format("<li><b>Push Return Offset:</b> <code>[SS:SP] = IP + %d (0x%04X)</code></li>", di != null ? di.length : 5, nextIp));
            html.append(String.format("<li><b>Update CS:</b> Load new Code Segment: <code>CS = %s</code></li>", newCsStr));
            html.append(String.format("<li><b>Update IP:</b> Load new Instruction Pointer: <code>IP = %s</code></li>", newIpStr));
            html.append("</ol>");
            html.append("<div style='font-size:10px; color:#555;'><i>Return with <code>RETF</code> to restore both CS and IP.</i></div>");

        } else if (upper.startsWith("CALL")) {
            int nextIp = (di != null && s != null) ? ((s.getIP() + di.length) & 0xFFFF) : 0;
            int target = parseHexTarget(upper);
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Near Subroutine Call</div>");
            html.append("Executes a near subroutine call within the current code segment:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Decrement Stack Pointer:</b> <code>SP = SP - 2</code></li>");
            html.append(String.format("<li><b>Push Return Address:</b> <code>[SS:SP] = IP + %d (0x%04X)</code></li>", di != null ? di.length : 3, nextIp));
            html.append(String.format("<li><b>Jump to Target:</b> <code>IP = 0x%04X</code></li>", target));
            html.append("</ol>");
            html.append("<div style='font-size:10px; color:#555;'><i>Return with <code>RET</code> to pop return offset back into IP.</i></div>");

        } else if (upper.equals("RETF") || upper.startsWith("RETF ")) {
            html.append("<div style='color:#146b3a; font-weight:bold; margin-bottom:4px;'>Far Return (RETF)</div>");
            html.append("Returns from a far procedure by popping IP then CS:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Pop Return IP:</b> <code>IP = [SS:SP]</code>, <code>SP = SP + 2</code></li>");
            html.append("<li><b>Pop Return CS:</b> <code>CS = [SS:SP]</code>, <code>SP = SP + 2</code></li>");
            if (upper.contains(" ")) {
                html.append("<li><b>Discard Stack Arguments:</b> <code>SP = SP + imm16</code></li>");
            }
            html.append("</ol>");

        } else if (upper.equals("RET") || upper.startsWith("RET ")) {
            html.append("<div style='color:#146b3a; font-weight:bold; margin-bottom:4px;'>Near Return (RET)</div>");
            html.append("Returns from a subroutine within the same segment:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Pop Return Address:</b> <code>IP = [SS:SP]</code></li>");
            html.append("<li><b>Restore Stack:</b> <code>SP = SP + 2</code></li>");
            if (upper.contains(" ")) {
                html.append("<li><b>Discard Stack Parameters:</b> <code>SP = SP + imm16</code></li>");
            }
            html.append("</ol>");

        } else if (upper.equals("IRET")) {
            html.append("<div style='color:#146b3a; font-weight:bold; margin-bottom:4px;'>Interrupt Return (IRET)</div>");
            html.append("Restores state saved during interrupt execution:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Pop Return IP:</b> <code>IP = [SS:SP]</code>, <code>SP = SP + 2</code></li>");
            html.append("<li><b>Pop Return CS:</b> <code>CS = [SS:SP]</code>, <code>SP = SP + 2</code></li>");
            html.append("<li><b>Restore Flags:</b> <code>FLAGS = [SS:SP]</code>, <code>SP = SP + 2</code></li>");
            html.append("</ol>");

        } else if (upper.contains("INT 0X86") || upper.contains("INT 86") || upper.contains("INT 0x86")) {
            html.append("<div style='color:#b30000; font-weight:bold; margin-bottom:4px;'>CoreWars Special: Throw Bomb (INT 0x86)</div>");
            html.append("Fires a heavy destructive bomb into the Arena:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Verify Bomb Quota:</b> Checks remaining bomb count</li>");
            html.append("<li><b>Decrement Counter:</b> Decrements available bombs by 1</li>");
            html.append("<li><b>Barrage Arena:</b> Writes <b>64 consecutive words</b> (128 bytes of <code>INT 3</code> traps) starting at <code>[ES:DI]</code>!</li>");
            html.append("</ol>");
            if (s != null) {
                html.append(String.format("<div style='background:#fff0f0; border-left:3px solid #cc0000; padding:3px 6px; font-size:10px;'>Target Area: <code>[ES:0x%04X]</code> &rarr; <code>[ES:0x%04X]</code></div>",
                        s.getDI() & 0xFFFF, (s.getDI() + 128) & 0xFFFF));
            }

        } else if (upper.startsWith("INT ")) {
            html.append("<div style='color:#994d00; font-weight:bold; margin-bottom:4px;'>Software Interrupt (INT)</div>");
            html.append("Triggers a software interrupt service routine:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Push Flags:</b> <code>SP = SP - 2</code>, <code>[SS:SP] = FLAGS</code></li>");
            html.append("<li><b>Clear Flags:</b> Clears IF (Interrupt) and TF (Trap) flags</li>");
            html.append("<li><b>Push CS:</b> <code>SP = SP - 2</code>, <code>[SS:SP] = CS</code></li>");
            html.append("<li><b>Push IP:</b> <code>SP = SP - 2</code>, <code>[SS:SP] = IP + length</code></li>");
            html.append("<li><b>Vector Lookup:</b> Loads new CS:IP from interrupt vector table</li>");
            html.append("</ol>");

        } else if (upper.startsWith("PUSH")) {
            String src = upper.substring(4).trim();
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Push Word to Stack</div>");
            html.append("Saves a 16-bit word onto the runtime stack:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Decrement Stack Pointer:</b> <code>SP = SP - 2</code></li>");
            html.append(String.format("<li><b>Write to Top of Stack:</b> <code>[SS:SP] = %s</code></li>", src));
            html.append("</ol>");
            if (s != null) {
                int newSp = (s.getSP() - 2) & 0xFFFF;
                html.append(String.format("<div style='font-size:10px; color:#555;'>New SP will be: <code>0x%04X</code></div>", newSp));
            }

        } else if (upper.startsWith("POP")) {
            String dst = upper.substring(3).trim();
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Pop Word from Stack</div>");
            html.append("Retrieves a 16-bit word from the runtime stack:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append(String.format("<li><b>Read from Top of Stack:</b> <code>%s = [SS:SP]</code></li>", dst));
            html.append("<li><b>Increment Stack Pointer:</b> <code>SP = SP + 2</code></li>");
            html.append("</ol>");

        } else if (upper.contains("MOVSB") || upper.contains("MOVSW")) {
            boolean isWord = upper.contains("MOVSW");
            html.append("<div style='color:#d95f02; font-weight:bold; margin-bottom:4px;'>Move String (MOVS)</div>");
            html.append("Copies data from source to destination strings in memory:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append(String.format("<li><b>Read Source:</b> Fetch %s from <code>[DS:SI]</code></li>", isWord ? "word" : "byte"));
            html.append(String.format("<li><b>Write Destination:</b> Store to <code>[ES:DI]</code></li>"));
            html.append(String.format("<li><b>Update Pointers:</b> Adjust SI & DI by %d (%s)</li>",
                    isWord ? 2 : 1, (s != null && s.getDirectionFlag()) ? "backward (DF=1)" : "forward (DF=0)"));
            if (upper.startsWith("REP")) {
                html.append("<li><b>Repeat Counter:</b> Decrement <code>CX</code>; repeat while <code>CX &gt; 0</code></li>");
            }
            html.append("</ol>");

        } else if (upper.contains("STOSB") || upper.contains("STOSW")) {
            boolean isWord = upper.contains("STOSW");
            html.append("<div style='color:#d95f02; font-weight:bold; margin-bottom:4px;'>Store String (STOS)</div>");
            html.append("Stores accumulator value into destination string memory:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append(String.format("<li><b>Store Value:</b> Write <code>%s</code> to <code>[ES:DI]</code></li>", isWord ? "AX" : "AL"));
            html.append(String.format("<li><b>Advance Pointer:</b> <code>DI = DI %s %d</code> (based on DF)</li>",
                    (s != null && s.getDirectionFlag()) ? "-" : "+", isWord ? 2 : 1));
            if (upper.startsWith("REP")) {
                html.append("<li><b>Repeat Counter:</b> Decrement <code>CX</code>; repeat while <code>CX &gt; 0</code></li>");
            }
            html.append("</ol>");

        } else if (upper.contains("LODSB") || upper.contains("LODSW")) {
            boolean isWord = upper.contains("LODSW");
            html.append("<div style='color:#d95f02; font-weight:bold; margin-bottom:4px;'>Load String (LODS)</div>");
            html.append("Loads memory into accumulator and advances pointer:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append(String.format("<li><b>Load:</b> <code>%s = [DS:SI]</code></li>", isWord ? "AX" : "AL"));
            html.append(String.format("<li><b>Advance SI:</b> <code>SI = SI %s %d</code></li>",
                    (s != null && s.getDirectionFlag()) ? "-" : "+", isWord ? 2 : 1));
            html.append("</ol>");

        } else if (upper.contains("CMPSB") || upper.contains("CMPSW")) {
            html.append("<div style='color:#d95f02; font-weight:bold; margin-bottom:4px;'>Compare Strings (CMPS)</div>");
            html.append("Compares source string element with destination element:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Compare:</b> Subtract <code>[DS:SI] - [ES:DI]</code></li>");
            html.append("<li><b>Update Flags:</b> Sets ZF, CF, SF, OF, AF, PF</li>");
            html.append("<li><b>Advance SI & DI:</b> Update pointers according to Direction Flag (DF)</li>");
            html.append("</ol>");

        } else if (upper.startsWith("LOOP")) {
            html.append("<div style='color:#4a148c; font-weight:bold; margin-bottom:4px;'>Loop Control</div>");
            html.append("Decrements CX and conditionally jumps:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Decrement Counter:</b> <code>CX = CX - 1</code></li>");
            html.append("<li><b>Evaluate Condition:</b> Check CX != 0");
            if (upper.startsWith("LOOPZ") || upper.startsWith("LOOPE")) html.append(" AND ZF == 1");
            if (upper.startsWith("LOOPNZ") || upper.startsWith("LOOPNE")) html.append(" AND ZF == 0");
            html.append("</li>");
            html.append("<li><b>Branch:</b> If condition met, jump to target; else continue</li>");
            html.append("</ol>");
            if (s != null) {
                boolean cond = evaluateLoop(upper, s);
                html.append(String.format("<div style='background:%s; padding:3px 6px; font-weight:bold; border-radius:3px;'>Loop Condition: %s (CX=%d)</div>",
                        cond ? "#e8f5e9; color:#1b5e20;" : "#ffebee; color:#b71c1c;",
                        cond ? "BRANCH TAKEN" : "LOOP TERMINATED",
                        s.getCX() & 0xFFFF));
            }

        } else if (isConditionalJump(upper)) {
            html.append("<div style='color:#4a148c; font-weight:bold; margin-bottom:4px;'>Conditional Branch</div>");
            html.append("Branches to target address if CPU flag condition is satisfied:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Evaluate Flags:</b> Inspect CPU status flags</li>");
            html.append("<li><b>Branch:</b> If condition TRUE &rarr; <code>IP = Target</code>; else advance to next opcode</li>");
            html.append("</ol>");
            if (s != null) {
                boolean cond = evaluateJump(upper, s);
                int target = parseHexTarget(upper);
                html.append(String.format("<div style='background:%s; padding:3px 6px; font-weight:bold; border-radius:3px;'>Condition: %s &rarr; <code>0x%04X</code></div>",
                        cond ? "#e8f5e9; color:#1b5e20;" : "#ffebee; color:#b71c1c;",
                        cond ? "TAKEN" : "NOT TAKEN", target));
            }

        } else if (upper.startsWith("XCHG")) {
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Exchange Register/Memory</div>");
            html.append("Atomically swaps the contents of two operands:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Fetch Operands:</b> Read values of both source and destination</li>");
            html.append("<li><b>Swap & Store:</b> Write original source to destination, original destination to source</li>");
            html.append("</ol>");

        } else if (upper.startsWith("LEA")) {
            html.append("<div style='color:#0b4f6c; font-weight:bold; margin-bottom:4px;'>Load Effective Address</div>");
            html.append("Computes memory offset without performing memory read:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Calculate Offset:</b> Base + Index + Displacement</li>");
            html.append("<li><b>Store in Register:</b> Save computed 16-bit offset into destination</li>");
            html.append("</ol>");

        } else if (isMemoryWrite(upper)) {
            html.append("<div style='color:#b30000; font-weight:bold; margin-bottom:4px;'>Memory Write Operation</div>");
            html.append("Calculates effective address and modifies memory:");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Address Resolution:</b> Compute ModR/M effective address</li>");
            html.append("<li><b>Evaluate Value:</b> Fetch or compute value to store</li>");
            html.append("<li><b>Store to RAM:</b> Write result into segment:offset</li>");
            html.append("</ol>");

        } else {
            html.append("<div style='color:#333333; font-weight:bold; margin-bottom:4px;'>8086 Instruction</div>");
            html.append("<ol style='margin:4px 0 6px 18px; padding:0;'>");
            html.append("<li><b>Execute Operation:</b> Perform internal register/ALU logic</li>");
            html.append("<li><b>Update Flags:</b> Adjust arithmetic status flags (ZF, CF, SF, OF)</li>");
            html.append("<li><b>Advance IP:</b> Advance instruction pointer to next instruction</li>");
            html.append("</ol>");
        }

        // Live Context Box
        if (s != null) {
            html.append("<div style='margin-top:6px; padding:4px 6px; background:#f4f6f9; border-left:3px solid #4a90e2; font-size:10px;'>");
            html.append(String.format("<b>Live CPU State:</b> IP=0x%04X SP=0x%04X | ZF=%d CF=%d SF=%d OF=%d",
                    s.getIP() & 0xFFFF, s.getSP() & 0xFFFF,
                    s.getZeroFlag() ? 1 : 0, s.getCarryFlag() ? 1 : 0,
                    s.getSignFlag() ? 1 : 0, s.getOverflowFlag() ? 1 : 0));
            html.append("</div>");
        }

        html.append("</div></html>");
        return html.toString();
    }

    private static boolean isMemoryWrite(String text) {
        String u = text.toUpperCase();
        if (!u.contains("[")) return false;
        int bracket = u.indexOf('[');
        int comma = u.indexOf(',');
        // Dest is before comma: MOV [BX], AX
        if (comma != -1 && bracket < comma) {
            return u.startsWith("MOV") || u.startsWith("ADD") || u.startsWith("SUB") ||
                   u.startsWith("AND") || u.startsWith("OR") || u.startsWith("XOR") ||
                   u.startsWith("ADC") || u.startsWith("SBB") || u.startsWith("ROL") ||
                   u.startsWith("ROR") || u.startsWith("SHL") || u.startsWith("SHR");
        }
        // Unary ops on memory: INC [BX], DEC [BX], NOT [BX], NEG [BX]
        return u.startsWith("INC") || u.startsWith("DEC") || u.startsWith("NOT") || u.startsWith("NEG");
    }

    private static boolean isWordOp(String text) {
        String u = text.toUpperCase();
        if (u.contains("WORD")) return true;
        if (u.contains("BYTE")) return false;
        if (u.contains(", AX") || u.contains(", BX") || u.contains(", CX") || u.contains(", DX") ||
            u.contains(", SI") || u.contains(", DI") || u.contains(", BP") || u.contains(", SP")) return true;
        return false;
    }

    private static int resolveSegment(String text, CpuState s) {
        String u = text.toUpperCase();
        if (u.contains("ES:")) return s.getES() & 0xFFFF;
        if (u.contains("CS:")) return s.getCS() & 0xFFFF;
        if (u.contains("SS:")) return s.getSS() & 0xFFFF;
        if (u.contains("DS:")) return s.getDS() & 0xFFFF;
        // Default: BP uses SS, others use DS
        int bracket = u.indexOf('[');
        int close = u.indexOf(']');
        if (bracket != -1 && close > bracket) {
            String mem = u.substring(bracket, close);
            if (mem.contains("BP")) return s.getSS() & 0xFFFF;
        }
        return s.getDS() & 0xFFFF;
    }

    public static Integer resolveEffectiveAddress(String text, CpuState s) {
        try {
            int open = text.indexOf('[');
            int close = text.indexOf(']');
            if (open != -1 && close > open) {
                String inner = text.substring(open + 1, close).trim().toUpperCase();
                if (inner.startsWith("WORD ") || inner.startsWith("BYTE ")) {
                    inner = inner.substring(5).trim();
                }
                int addr = 0;
                if (inner.contains("BX")) addr += s.getBX() & 0xFFFF;
                if (inner.contains("BP")) addr += s.getBP() & 0xFFFF;
                if (inner.contains("SI")) addr += s.getSI() & 0xFFFF;
                if (inner.contains("DI")) addr += s.getDI() & 0xFFFF;

                // Displacement
                if (inner.contains("+") || inner.contains("-")) {
                    String[] parts = inner.split("[+-]");
                    String last = parts[parts.length - 1].trim();
                    int disp = 0;
                    if (last.startsWith("0X")) {
                        disp = Integer.parseInt(last.substring(2), 16);
                    } else if (last.matches("\\d+")) {
                        disp = Integer.parseInt(last);
                    }
                    if (inner.contains("-" + last)) addr -= disp;
                    else addr += disp;
                } else if (inner.startsWith("0X")) {
                    addr = Integer.parseInt(inner.substring(2), 16);
                } else if (inner.matches("[0-9A-F]+H")) {
                    addr = Integer.parseInt(inner.substring(0, inner.length() - 1), 16);
                }
                return addr & 0xFFFF;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isConditionalJump(String text) {
        String m = text.split(" ")[0].toUpperCase();
        return m.equals("JZ") || m.equals("JE") || m.equals("JNZ") || m.equals("JNE") ||
               m.equals("JC") || m.equals("JB") || m.equals("JNC") || m.equals("JAE") ||
               m.equals("JS") || m.equals("JNS") || m.equals("JO") || m.equals("JNO") ||
               m.equals("JP") || m.equals("JPE") || m.equals("JNP") || m.equals("JPO") ||
               m.equals("JA") || m.equals("JBE") || m.equals("JG") || m.equals("JGE") ||
               m.equals("JL") || m.equals("JLE") || m.equals("JCXZ");
    }

    private static boolean evaluateJump(String text, CpuState s) {
        String mnemonic = text.split(" ")[0].toUpperCase();
        switch (mnemonic) {
            case "JZ": case "JE": return s.getZeroFlag();
            case "JNZ": case "JNE": return !s.getZeroFlag();
            case "JC": case "JB": return s.getCarryFlag();
            case "JNC": case "JAE": return !s.getCarryFlag();
            case "JS": return s.getSignFlag();
            case "JNS": return !s.getSignFlag();
            case "JO": return s.getOverflowFlag();
            case "JNO": return !s.getOverflowFlag();
            case "JP": case "JPE": return s.getParityFlag();
            case "JNP": case "JPO": return !s.getParityFlag();
            case "JA": return !s.getCarryFlag() && !s.getZeroFlag();
            case "JBE": return s.getCarryFlag() || s.getZeroFlag();
            case "JG": return !s.getZeroFlag() && (s.getSignFlag() == s.getOverflowFlag());
            case "JGE": return s.getSignFlag() == s.getOverflowFlag();
            case "JL": return s.getSignFlag() != s.getOverflowFlag();
            case "JLE": return s.getZeroFlag() || (s.getSignFlag() != s.getOverflowFlag());
            case "JCXZ": return s.getCX() == 0;
            default: return false;
        }
    }

    private static boolean evaluateLoop(String text, CpuState s) {
        String mnemonic = text.split(" ")[0].toUpperCase();
        int newCx = (s.getCX() - 1) & 0xFFFF;
        if (newCx == 0) return false;
        if (mnemonic.equals("LOOP")) return true;
        if (mnemonic.equals("LOOPZ") || mnemonic.equals("LOOPE")) return s.getZeroFlag();
        if (mnemonic.equals("LOOPNZ") || mnemonic.equals("LOOPNE")) return !s.getZeroFlag();
        return false;
    }

    private static int parseHexTarget(String text) {
        try {
            String u = text.toUpperCase();
            int idx = u.lastIndexOf("0X");
            if (idx != -1) {
                String sub = u.substring(idx + 2).trim();
                int space = sub.indexOf(' ');
                if (space != -1) sub = sub.substring(0, space);
                return Integer.parseInt(sub, 16);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}
