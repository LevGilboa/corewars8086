package il.co.codeguru.corewars8086.cpu;

import il.co.codeguru.corewars8086.memory.MemoryException;

/**
 * 8086 shift and rotate instructions operating on the current indirect operand.
 *
 * All 14 instructions (7 operations x 8/16 bits) share one implementation,
 * {@link #shift}. The few per-width differences in which flags get refreshed
 * are kept as-is (see {@link FlagUpdate}).
 */
final class ShiftOps {

    private enum Kind { ROL, ROR, RCL, RCR, SHL, SHR, SAR }

    /** Which of the Sign/Zero/Parity flags are refreshed after each step. */
    private enum FlagUpdate {
        /** Only Carry and Overflow change. */
        NONE,
        /** Sign, Zero and Parity are refreshed too. */
        SZP,
        /** Only Zero is refreshed. */
        ZERO_ONLY
    }

    private final CpuState m_state;
    private final IndirectAddressingDecoder m_indirect;
    private final Alu m_alu;

    ShiftOps(CpuState state, IndirectAddressingDecoder indirect, Alu alu) {
        m_state = state;
        m_indirect = indirect;
        m_alu = alu;
    }

    void rol8(int count) throws MemoryException { shift(Kind.ROL, 8, count, FlagUpdate.NONE); }
    void ror8(int count) throws MemoryException { shift(Kind.ROR, 8, count, FlagUpdate.NONE); }
    void rcl8(int count) throws MemoryException { shift(Kind.RCL, 8, count, FlagUpdate.NONE); }
    void rcr8(int count) throws MemoryException { shift(Kind.RCR, 8, count, FlagUpdate.NONE); }
    void shl8(int count) throws MemoryException { shift(Kind.SHL, 8, count, FlagUpdate.SZP); }
    void shr8(int count) throws MemoryException { shift(Kind.SHR, 8, count, FlagUpdate.SZP); }
    void sar8(int count) throws MemoryException { shift(Kind.SAR, 8, count, FlagUpdate.SZP); }

    void rol16(int count) throws MemoryException { shift(Kind.ROL, 16, count, FlagUpdate.SZP); }
    void ror16(int count) throws MemoryException { shift(Kind.ROR, 16, count, FlagUpdate.SZP); }
    void rcl16(int count) throws MemoryException { shift(Kind.RCL, 16, count, FlagUpdate.SZP); }
    void rcr16(int count) throws MemoryException { shift(Kind.RCR, 16, count, FlagUpdate.NONE); }
    void shl16(int count) throws MemoryException { shift(Kind.SHL, 16, count, FlagUpdate.ZERO_ONLY); }
    void shr16(int count) throws MemoryException { shift(Kind.SHR, 16, count, FlagUpdate.ZERO_ONLY); }
    void sar16(int count) throws MemoryException { shift(Kind.SAR, 16, count, FlagUpdate.ZERO_ONLY); }

    /**
     * Shifts/rotates the current indirect operand one bit at a time.
     *
     * @param kind   operation to perform.
     * @param bits   operand width: 8 or 16.
     * @param count  repeat count (only the low 5 bits are used, like the 80186+).
     * @param flags  which extra flags are refreshed after every step.
     */
    private void shift(Kind kind, int bits, int count, FlagUpdate flags) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31
        final int top = bits - 1;
        final int mask = (1 << bits) - 1;

        for (int i = 0; i < count; ++i) {
            int val = (bits == 8) ? m_indirect.getMem8() : m_indirect.getMem16();
            int lsb = val & 0x01;
            int msb = (val >> top) & 0x01;
            int cf = m_state.getCarryFlag() ? 1 : 0;

            int raw;
            boolean carryOut;
            boolean overflowFromOld;
            switch (kind) {
                case ROL: raw = (val << 1) | msb;  carryOut = msb != 0; overflowFromOld = true;  break;
                case RCL: raw = (val << 1) | cf;   carryOut = msb != 0; overflowFromOld = true;  break;
                case SHL: raw = val << 1;          carryOut = msb != 0; overflowFromOld = true;  break;
                case ROR: raw = ((val & mask) >>> 1) | (lsb << top); carryOut = lsb != 0; overflowFromOld = false; break;
                case RCR: raw = ((val & mask) >>> 1) | (cf << top);  carryOut = lsb != 0; overflowFromOld = false; break;
                case SHR: raw = (val & mask) >>> 1;                  carryOut = lsb != 0; overflowFromOld = false; break;
                case SAR: raw = val >> 1;                            carryOut = lsb != 0; overflowFromOld = false; break;
                default: throw new IllegalStateException();
            }

            int result;
            if (bits == 8) {
                result = (byte) raw;
                m_indirect.setMem8((byte) result);
            } else {
                result = (short) raw;
                m_indirect.setMem16((short) result);
            }

            int ref = overflowFromOld ? val : result;
            int msb1 = (ref >> top) & 0x01;
            int msb2 = (ref >> (top - 1)) & 0x01;
            m_state.setCarryFlag(carryOut);
            m_state.setOverflowFlag(msb1 != msb2);

            switch (flags) {
                case SZP:
                    if (bits == 8) {
                        m_alu.updateFlagsNoCarryOverflow8((byte) result);
                    } else {
                        m_alu.updateFlagsNoCarryOverflow16((short) result);
                    }
                    break;
                case ZERO_ONLY:
                    m_state.setZeroFlag(result == 0);
                    break;
                default:
                    break;
            }
        }
    }
}
