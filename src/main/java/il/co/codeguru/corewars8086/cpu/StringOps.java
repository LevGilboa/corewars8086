package il.co.codeguru.corewars8086.cpu;

import il.co.codeguru.corewars8086.memory.MemoryException;
import il.co.codeguru.corewars8086.memory.RealModeAddress;
import il.co.codeguru.corewars8086.memory.RealModeMemory;

/**
 * 8086 string instructions (MOVS, CMPS, STOS, LODS, SCAS).
 */
final class StringOps {

    private final CpuState m_state;
    private final RealModeMemory m_memory;
    private final Alu m_alu;

    StringOps(CpuState state, RealModeMemory memory, Alu alu) {
        m_state = state;
        m_memory = memory;
        m_alu = alu;
    }
    /**
     * Implements the 'movsb' opcode.
     * @throws MemoryException
     */
    void movsb() throws MemoryException {
        RealModeAddress src =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        RealModeAddress dst =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_memory.writeByte(dst, m_memory.readByte(src));

        byte diff = delta(1);
        m_state.setSI((short)(m_state.getSI() + diff));
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'movsw' opcode.
     * @throws MemoryException
     */
    void movsw() throws MemoryException {
        RealModeAddress src =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        RealModeAddress dst =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_memory.writeWord(dst, m_memory.readWord(src));

        byte diff = delta(2);
        m_state.setSI((short)(m_state.getSI() + diff));
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'cmpsb' opcode.
     * @throws MemoryException
     */
    void cmpsb() throws MemoryException {
        RealModeAddress address1 =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        RealModeAddress address2 =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_alu.sub8(m_memory.readByte(address1), m_memory.readByte(address2));

        byte diff = delta(1);
        m_state.setSI((short)(m_state.getSI() + diff));
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'cmpsw' opcode.
     * @throws MemoryException
     */
    void cmpsw() throws MemoryException {
        RealModeAddress address1 =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        RealModeAddress address2 =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_alu.sub16(m_memory.readWord(address1), m_memory.readWord(address2));

        byte diff = delta(2);
        m_state.setSI((short)(m_state.getSI() + diff));
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'stosb' opcode.
     * @throws MemoryException
     */
    void stosb() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_memory.writeByte(address, m_state.getAL());
        byte diff = delta(1);
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'stosw' opcode.
     * @throws MemoryException
     */
    void stosw() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_memory.writeWord(address, m_state.getAX());
        byte diff = delta(2);
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the virtual 'stosdw' opcode.
     * @throws MemoryException
     */
    void stosdw() throws MemoryException {
        RealModeAddress address1 =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_memory.writeWord(address1, m_state.getAX());

        RealModeAddress address2 =
            new RealModeAddress(m_state.getES(), (short)(m_state.getDI() + 2));
        m_memory.writeWord(address2, m_state.getDX());

        byte diff = delta(4);
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'lodsb' opcode.
     * @throws MemoryException
     */
    void lodsb() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        m_state.setAL(m_memory.readByte(address));
        byte diff = delta(1);
        m_state.setSI((short)(m_state.getSI() + diff));
    }

    /**
     * Implements the 'lodsw' opcode.
     * @throws MemoryException
     */
    void lodsw() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getDS(), m_state.getSI());
        m_state.setAX(m_memory.readWord(address));
        byte diff = delta(2);
        m_state.setSI((short)(m_state.getSI() + diff));
    }

    /**
     * Implements the 'scasb' opcode.
     * @throws MemoryException
     */
    void scasb() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_alu.sub8(m_state.getAL(), m_memory.readByte(address));
        byte diff = delta(1);
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /**
     * Implements the 'scasw' opcode.
     * @throws MemoryException
     */
    void scasw() throws MemoryException {
        RealModeAddress address =
            new RealModeAddress(m_state.getES(), m_state.getDI());
        m_alu.sub16(m_state.getAX(), m_memory.readWord(address));
        byte diff = delta(2);
        m_state.setDI((short)(m_state.getDI() + diff));
    }

    /** Step for SI/DI after a string op of {@code size} bytes (direction-flag aware). */
    private byte delta(int size) {
        return (byte)(m_state.getDirectionFlag() ? -size : size);
    }
}
