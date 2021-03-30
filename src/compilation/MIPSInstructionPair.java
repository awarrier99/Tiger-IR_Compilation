package compilation;

import java.util.Objects;

public class MIPSInstructionPair {
    public final String instruction;
    public final int index;

    public MIPSInstructionPair(String instruction, int index) {
        this.instruction = instruction;
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MIPSInstructionPair pair = (MIPSInstructionPair) o;
        return index == pair.index && instruction.equals(pair.instruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instruction, index);
    }
}
