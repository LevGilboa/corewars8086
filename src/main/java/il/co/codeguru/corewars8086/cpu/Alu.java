package il.co.codeguru.corewars8086.cpu;

import il.co.codeguru.corewars8086.utils.Unsigned;

/**
 * Arithmetic/logic unit of the 8086: performs ALU operations and keeps
 * the CPU flags up to date.
 */
final class Alu {

    private final CpuState m_state;

    Alu(CpuState state) {
        m_state = state;
    }

    /**
     * Updates the CPU flags register after an 8bit operation.
     * @param value  Result of an 8bit operation.
     */
    void updateFlags8(short value) {
        m_state.setCarryFlag((value & 0xFF00) != 0);
        // TODO: update overflow flag
        updateFlagsNoCarryOverflow8((byte)value);
    }

    /**
     * Updates the CPU flags register after a 16bit operation.
     * @param value  Result of a 16bit operation.
     */
    void updateFlags16(int value) {
        m_state.setCarryFlag((value & 0xFFFF0000) != 0);
        // TODO: update overflow flag
        updateFlagsNoCarryOverflow16((short)value);
    }

    /**
     * Updates the CPU flags register after an 8bit operation, except for the
     * Carry and Overflow flags.
     * @param value  Result of an 8bit operation.
     */
    void updateFlagsNoCarryOverflow8(byte value) {
        // TODO: update aux flag		
        m_state.setParityFlag(getParity(value));
        m_state.setSignFlag((value & 0x80) != 0);
        m_state.setZeroFlag(value == 0);		
    }

    /**
     * Updates the CPU flags register after a 16bit operation, except for the
     * Carry and Overflow flags.
     * @param value  Result of a 16bit operation.
     */
    void updateFlagsNoCarryOverflow16(short value) {
        byte byteValue = (byte)value;
        // TODO: update aux flag		
        m_state.setParityFlag(getParity(byteValue));
        m_state.setSignFlag((value & 0x8000) != 0);
        m_state.setZeroFlag(value == 0);
    }	

    /**
     * Adds two 8bit values, updates the flags and returns the result.
     */
    byte add8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) + Unsigned.unsignedByte(value2));
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * Adds two 16bit values, updates the flags and returns the result.
     */
    short add16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) + Unsigned.unsignedShort(value2);
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }

    /**
     * Increments an 8bit value, updates the flags and returns the result.
     * Note: does not modify the carry flag.
     */
    byte inc8(byte value) {
        boolean oldCarry = m_state.getCarryFlag();
        byte result = add8(value, (byte)1);
        m_state.setCarryFlag(oldCarry);
        return result;		
    }

    /**
     * Increments a 16bit value, updates the flags and returns the result.
     * Note: does not modify the carry flag.
     */
    short inc16(short value) {
        boolean oldCarry = m_state.getCarryFlag();
        short result = add16(value, (short)1);
        m_state.setCarryFlag(oldCarry);
        return result;		
    }

    /**
     * Decrements an 8bit value, updates the flags and returns the result.
     * Note: does not modify the carry flag.
     */
    byte dec8(byte value) {
        boolean oldCarry = m_state.getCarryFlag();
        byte result = sub8(value, (byte)1);
        m_state.setCarryFlag(oldCarry);
        return result;		
    }

    /**
     * Decrements a 16bit value, updates the flags and returns the result.
     * Note: does not modify the carry flag.
     */
    short dec16(short value) {
        boolean oldCarry = m_state.getCarryFlag();
        short result = sub16(value, (short)1);
        m_state.setCarryFlag(oldCarry);
        return result;		
    }

    /**
     * ORs two 8bit values, updates the flags and returns the result.
     */
    byte or8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) | Unsigned.unsignedByte(value2));
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * ORs two 16bit values, updates the flags and returns the result.
     */
    short or16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) | Unsigned.unsignedShort(value2);
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }

    /**
     * Adds two 8bit values with the carry flag, updates the flags and returns
     * the result.
     */
    byte adc8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) + Unsigned.unsignedByte(value2));
        if (m_state.getCarryFlag()) {
            ++result16;
        }
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * Adds two 16bit values with the carry flag, updates the flags and returns
     * the result.
     */
    short adc16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) + Unsigned.unsignedShort(value2);
        if (m_state.getCarryFlag()) {
            ++result32;
        }
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }	

    /**
     * Subtracts a given 8bit value with the sum of a second 8bit value and the
     * carry flag, updates the flags and returns the result.
     */
    byte sbb8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) - Unsigned.unsignedByte(value2));
        if (m_state.getCarryFlag()) {
            --result16;
        }
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * Subtracts a given 16bit value with the sum of a second 16bit value and the
     * carry flag, updates the flags and returns the result.
     */
    short sbb16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) - Unsigned.unsignedShort(value2);
        if (m_state.getCarryFlag()) {
            --result32;
        }
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }

    /**
     * ANDs two 8bit values, updates the flags and returns the result.
     */
    byte and8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) & Unsigned.unsignedByte(value2));
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * ANDs two 16bit values, updates the flags and returns the result.
     */
    short and16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) & Unsigned.unsignedShort(value2);
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }	

    /**
     * Subtracts two 8bit values, updates the flags and returns the result.
     */
    byte sub8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) - Unsigned.unsignedByte(value2));
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * Subtracts two 16bit values, updates the flags and returns the result.
     */
    short sub16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) - Unsigned.unsignedShort(value2);
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }

    /**
     * XORs two 8bit values, updates the flags and returns the result.
     */
    byte xor8(byte value1, byte value2) {
        short result16 = 
            (short)(Unsigned.unsignedByte(value1) ^ Unsigned.unsignedByte(value2));
        byte result8 = (byte)result16;	
        updateFlags8(result16);
        return result8;
    }

    /**
     * XORs two 16bit values, updates the flags and returns the result.
     */
    short xor16(short value1, short value2) {
        int result32 =
            Unsigned.unsignedShort(value1) ^ Unsigned.unsignedShort(value2);
        short result16 = (short)result32;		
        updateFlags16(result32);
        return result16;
    }


    /**
     * Returns true iff the given byte's bit parity is EVEN.
     * @param value  Value for which bit parity will be tested.
     * @return true iff the given byte's bit parity is EVEN.
     */
    boolean getParity(byte value) {
        return PARITY_TABLE[Unsigned.unsignedByte(value)];
    }

    /**
     * Parity table implementation.
     * An array memeber of 'true' means the bit parity for the given index is EVEN.
     */
    private static final boolean PARITY_TABLE[] = {
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true,
        true, false, false, true, false, true, true, false,
        true, false, false, true, false, true, true, false,
        false, true, true, false, true, false, false, true
    };
}
