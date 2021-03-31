package compilation;

import ir.IRInstruction;
import ir.operand.IROperand;

import java.util.ArrayList;
import java.util.HashSet;

public class Debug {
    public static void printInstruction(IRInstruction instruction) {
        if (instruction.opCode == IRInstruction.OpCode.LABEL) {
            System.out.println("\t\t" + instruction.operands[0].toString() + ":");
            return;
        }

        System.out.print("\t\t" + instruction.opCode.toString() + ", ");
        for (int i = 0; i < instruction.operands.length; i++) {
            IROperand operand = instruction.operands[i];
            System.out.print(operand.toString());
            if (i != instruction.operands.length - 1) System.out.print(", ");
        }
        System.out.println();
    }

    public static void printBasicBlock(BasicBlock block) {
        if (block == null) return;

        System.out.println("Block:");

        System.out.println("\tInstructions:");
        for (IRInstruction instruction: block.irInstructions) {
            printInstruction(instruction);
        }

        System.out.println("\tMIPS Instructions:");
        for (int i = 0; i < block.mipsInstructions.size(); i++) {
            String instruction = block.mipsInstructions.get(i);
            HashSet<String> liveIn = block.liveIn.get(i);
            HashSet<String> liveOut = block.liveOut.get(i);
            System.out.print("\t\t" + instruction);
            if (!liveIn.isEmpty()) System.out.print("; Live in: " + String.join(", ", liveIn));
            if (!liveOut.isEmpty()) System.out.print("; Live out: " + String.join(", ", liveOut));
            System.out.println();
        }

        if (!block.usesMap.isEmpty()) {
            System.out.println("\tUses:");
            for (String op : block.usesMap.keySet()) {
                System.out.println("\t\t" + op + ": " + block.usesMap.get(op));
            }
        }
    }
}
