package il.co.codeguru.corewars8086.cpu;

import il.co.codeguru.corewars8086.memory.MemoryException;

/**
 * 8086 shift and rotate instructions operating on the current indirect operand.
 */
final class ShiftOps {

    private final CpuState m_state;
    private final IndirectAddressingDecoder m_indirect;
    private final Alu m_alu;

    ShiftOps(CpuState state, IndirectAddressingDecoder indirect, Alu alu) {
        m_state = state;
        m_indirect = indirect;
        m_alu = alu;
    }
    void rol8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            val = (byte)((val << 1) | msb1);
            m_indirect.setMem8(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
        }
    }

    void ror8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte lsb = (byte)(val & 0x01);

            val = (byte)(((val & 0xFF) >>> 1) | (lsb << 7));
            m_indirect.setMem8(val);

            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
        }
    }

    void rcl8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);
            byte cf = (byte)(m_state.getCarryFlag() ? 1 : 0);

            val = (byte)((val << 1) | cf);
            m_indirect.setMem8(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
        }
    }

    void rcr8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte lsb = (byte)(val & 0x01);
            byte cf = (byte)(m_state.getCarryFlag() ? 1 : 0);

            val = (byte)(((val & 0xFF) >>> 1) | (cf << 7));
            m_indirect.setMem8(val);

            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
        }
    }

    void shl8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            val = (byte)(val << 1);
            m_indirect.setMem8(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow8(val);
        }
    }

    void shr8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte lsb = (byte)(val & 0x01);

            val = (byte)((val & 0xFF) >>> 1);
            m_indirect.setMem8(val);

            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow8(val);
        }
    }

    void sar8(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            byte val = m_indirect.getMem8();
            byte lsb = (byte)(val & 0x01);

            val = (byte)(val >> 1);
            m_indirect.setMem8(val);

            byte msb1 = (byte)((val >> 7) & 0x01);
            byte msb2 = (byte)((val >> 6) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow8(val);
        }
    }

    void rol16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            val = (short)((val << 1) | msb1);
            m_indirect.setMem16(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow16(val);
        }
    }

    void ror16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte lsb = (byte)(val & 0x01);

            val = (short)(((val & 0xFFFF) >>> 1) | (lsb << 15));
            m_indirect.setMem16(val);

            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow16(val);
        }
    }

    void rcl16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);
            byte cf = (byte)(m_state.getCarryFlag() ? 1 : 0);

            val = (short)((val << 1) | cf);
            m_indirect.setMem16(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_alu.updateFlagsNoCarryOverflow16(val);
        }
    }

    void rcr16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte lsb = (byte)(val & 0x01);
            byte cf = (byte)(m_state.getCarryFlag() ? 1 : 0);

            val = (short)(((val & 0xFFFF) >>> 1) | (cf << 15));
            m_indirect.setMem16(val);

            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
        }
    }

    void shl16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            val = (short)(val << 1);
            m_indirect.setMem16(val);

            m_state.setCarryFlag(msb1 != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_state.setZeroFlag(val == 0);
        }
    }

    void shr16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte lsb = (byte)(val & 0x01);

            val = (short) ((val & 0xFFFF) >>> 1);
            m_indirect.setMem16(val);

            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_state.setZeroFlag(val == 0);
        }
    }

    void sar16(int count) throws MemoryException {
        count &= 0x1F; // restrict count to 0-31

        for (int i = 0; i < count; ++i) {
            short val = m_indirect.getMem16();
            byte lsb = (byte)(val & 0x01);

            val = (short)(val >> 1);
            m_indirect.setMem16(val);

            byte msb1 = (byte)((val >> 15) & 0x01);
            byte msb2 = (byte)((val >> 14) & 0x01);

            m_state.setCarryFlag(lsb != 0);
            m_state.setOverflowFlag(msb1 != msb2);
            m_state.setZeroFlag(val == 0);
        }
    }
}
