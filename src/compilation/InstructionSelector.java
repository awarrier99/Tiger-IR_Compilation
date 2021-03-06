package compilation;

import ir.*;
import ir.datatype.IRArrayType;
import ir.operand.IRFunctionOperand;
import ir.operand.IRVariableOperand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class InstructionSelector {
    private final HashMap<IRInstruction, BasicBlock> leaderBlockMap = new HashMap<>();
    public final HashMap<MIPSInstructionPair, BasicBlock> mipsLeaderBlockMap = new HashMap<>();
    public final HashMap<String, HashMap<String, Integer>> functionOffsetMaps = new HashMap<>();
    private final IRProgram program;
    private IRInstruction instruction;

    private static final HashMap<String, Integer> intrinsicFunctions = new HashMap<>();
    private static final HashSet<IRInstruction.OpCode> branchCodes = new HashSet<>();

    static {
        intrinsicFunctions.put("geti", 5);
        intrinsicFunctions.put("getc", 12);
        intrinsicFunctions.put("puti", 1);
        intrinsicFunctions.put("putc", 11);

        branchCodes.add(IRInstruction.OpCode.BREQ);
        branchCodes.add(IRInstruction.OpCode.BRGEQ);
        branchCodes.add(IRInstruction.OpCode.BRGT);
        branchCodes.add(IRInstruction.OpCode.BRLT);
        branchCodes.add(IRInstruction.OpCode.BRLEQ);
        branchCodes.add(IRInstruction.OpCode.BRNEQ);
    }

    public InstructionSelector(IRProgram program) {
        this.program = program;
    }

    private HashSet<IRInstruction> getLeaders() {
        boolean branchSuccessor = false;
        boolean funcSuccessor = false;
        HashSet<IRInstruction> leaders = new HashSet<>();
        for (IRFunction function: this.program.functions) {
            for (int i = 0; i < function.instructions.size(); i++) {
                IRInstruction instruction = function.instructions.get(i);
                if (instruction.opCode == IRInstruction.OpCode.LABEL) {
                    leaders.add(instruction);
                }
                else if (i == 0) leaders.add(instruction);
                else if (branchSuccessor || funcSuccessor) leaders.add(instruction);

                if (branchSuccessor) branchSuccessor = false;
                if (funcSuccessor) funcSuccessor = false;
                if (branchCodes.contains(instruction.opCode)) branchSuccessor = true;
                if (instruction.opCode == IRInstruction.OpCode.CALL || instruction.opCode == IRInstruction.OpCode.CALLR) {
                    int pos = instruction.opCode == IRInstruction.OpCode.CALL ? 0 : 1;
                    if (!intrinsicFunctions.containsKey(((IRFunctionOperand) instruction.operands[pos]).getName())) funcSuccessor = true;
                }
            }
        }

        return leaders;
    }

    private void generateBasicBlocks() {
        HashSet<IRInstruction> leaders = this.getLeaders();

        for (IRInstruction leader: leaders) {
            ArrayList<IRInstruction> instructions = new ArrayList<>();
            instructions.add(leader);
            this.leaderBlockMap.put(leader, new BasicBlock(instructions));
        }

        BasicBlock block = this.leaderBlockMap.get(this.program.functions.get(0).instructions.get(0));
        for (IRFunction function: this.program.functions) {
            for (IRInstruction instruction: function.instructions) {
                if (leaders.contains(instruction)) block = this.leaderBlockMap.get(instruction);
                else block.irInstructions.add(instruction);
            }
        }

//        for (IRInstruction leader: leaders) Debug.printBasicBlock(this.leaderBlockMap.get(leader));
    }

    public ArrayList<String> generate() {
        this.generateBasicBlocks();
        ArrayList<String> instructions = new ArrayList<>();

        instructions.add(".text");
        instructions.add("beq $zero, $zero, main"); // TODO: remove for SPIM
        for (IRFunction function: program.functions) {
            BasicBlock block = this.leaderBlockMap.get(function.instructions.get(0));
            block.mipsInstructions.add(function.name + ":");
            this.functionOffsetMaps.put(function.name + ":", new HashMap<>());
            block.mipsInstructions.addAll(this.generateArguments(function));
            block.mipsInstructions.addAll(this.generateVariableInitialization(function));
            instructions.addAll(block.mipsInstructions);
            MIPSInstructionPair pair = new MIPSInstructionPair(block.mipsInstructions.get(0), instructions.size() - block.mipsInstructions.size());
            this.mipsLeaderBlockMap.put(pair, block);

            for (int i = 0; i < function.instructions.size(); i++) {
                this.instruction = function.instructions.get(i);
                ArrayList<String> assembly = this.map(function);
                instructions.addAll(assembly);
                if (i != 0 && leaderBlockMap.containsKey(this.instruction)) {
                    block = this.leaderBlockMap.get(this.instruction);
                    pair = new MIPSInstructionPair(assembly.get(0), instructions.size() - assembly.size());
                    this.mipsLeaderBlockMap.put(pair, block);
                }
                block.mipsInstructions.addAll(assembly);
            }

            if (function.name.equals("main")) {
                String load = "li $v0, 10";
                String syscall = "syscall";
                instructions.add(load);
                instructions.add(syscall);
                block.mipsInstructions.add(load);
                block.mipsInstructions.add(syscall);
            } else if (function.returnType == null) {
                String ret = "jr $ra";
                instructions.add(ret);
                block.mipsInstructions.add(ret);
            }
        }

        return instructions;
    }

    private ArrayList<String> assignArray(String array, int size, String value) {
        ArrayList<String> instructions = new ArrayList<>();
        String operation = "li";
        if (value.contains("$")) operation = "move";

        if (size > 0) instructions.add(String.format("%s $temp, %s", operation, value));
        for (int i = 0; i < size; i++) instructions.add(String.format("sw $temp, %d($%s)", i * 4, array));

        return instructions;
    }

    private ArrayList<String> generateVariableInitialization(IRFunction function) {
        ArrayList<String> instructions = new ArrayList<>();
        for (IRVariableOperand op: function.variables) {
            if (!function.parameters.contains(op)) {
                String name = op.getName();
                if (op.type instanceof IRArrayType) {
                    IRArrayType type = (IRArrayType) op.type;
                    instructions.add("li $v0, 9");
                    instructions.add(String.format("li $a0, %d", type.getSize() * 4));
                    instructions.add("syscall");
                    instructions.add(String.format("move $%s, $v0", name));
                    instructions.addAll(this.assignArray(name, type.getSize(), "0"));
                } else {
                    instructions.add(String.format("li $%s, 0", name));
                }
            }
        }

        instructions.add("li $temp, 0");
        instructions.add("li $temp2, 0");

        return instructions;
    }

    private ArrayList<String> generateArguments(IRFunction function) {
        ArrayList<String> instructions = new ArrayList<>();

        int numArgs = function.parameters.size();
        for (int i = 0; i < numArgs; i++) {
            String argument = function.parameters.get(i).getName();
            if (i < 4) {
                instructions.add(String.format("move $%s, $a%d", argument, i));
            } else {
                instructions.add(String.format("lw $%s, %d($sp)", argument, (numArgs - i - 1) * 4));
            }
        }

        return instructions;
    }

    private ArrayList<String> generateCallInitialization(IRFunction function, String functionLabel, boolean ret) {
        ArrayList<String> instructions = new ArrayList<>();
        int numVariables = function.variables.size();

        instructions.add(String.format("addi $sp, $sp, %d", numVariables * -4));
        for (int i = 0; i < numVariables; i++) {
            IRVariableOperand op = function.variables.get(i);
            instructions.add(String.format("sw $%s, %d($sp)", op.getName(), (numVariables - i - 1) * 4));
        }

        int start = 1;
        if (ret) start = 2;
        int length = this.instruction.operands.length;
        int numArgs = length - start;
        int j = 0;

        if (numArgs > 4) instructions.add(String.format("addi $sp, $sp, %d", (numArgs - 4) * -4));
        for (int i = start; i < length; i++) {
            String op = this.getOperand(i);
            String operation = "li";
            if (op.contains("$")) operation = "move";

            if (j < 4) {
                instructions.add(String.format("%s $a%d, %s", operation, j, op));
            } else {
                instructions.add(String.format("%s $temp, %s", operation, op));
                instructions.add(String.format("sw $temp, %d($sp)", (numArgs - j - 1) * 4));
            }
            j++;
        }

        instructions.add("addi $sp, $sp, -4");
        instructions.add("sw $ra, 0($sp)");
        instructions.add(String.format("jal %s", functionLabel));
        instructions.add("lw $ra, 0($sp)");
        instructions.add("addi $sp, $sp, 4");
        if (numArgs > 4) instructions.add(String.format("addi $sp, $sp, %d", (numArgs - 4) * 4));

        for (int i = 0; i < numVariables; i++) {
            IRVariableOperand op = function.variables.get(i);
            instructions.add(String.format("lw $%s, %d($sp)", op.getName(), (numVariables - i - 1) * 4));
        }
        instructions.add(String.format("addi $sp, $sp, %d", numVariables * 4));

        if (ret) {
            String op = this.getOperand(0);
            instructions.add(String.format("move %s, $v0", op));
        }

        return instructions;
    }

    private ArrayList<String> mapIntrinsicFunction(String function) {
        ArrayList<String> instructions = new ArrayList<>();
        int callCode = intrinsicFunctions.get(function);
        instructions.add(String.format("li $v0, %d", callCode));
        boolean read = true;
        if (function.contains("put")) read = false;

        if (!read) {
            String op = this.getOperand(1);
            String operation = "li";
            if (op.contains("$")) operation = "move";
            instructions.add(String.format("%s $a0, %s", operation, op));
        }

        instructions.add("syscall");

        if (read) {
            String op = this.getOperand(0);
            String register = "$v0";
            if (callCode == 12) register = "$a0";
            instructions.add(String.format("move %s, %s", op, register));
        }

        return instructions;
    }

    private String getOperand(int i) {
        try {
            Integer.parseInt(this.instruction.operands[i].toString());
            return this.instruction.operands[i].toString();
        } catch (NumberFormatException e) {
            return "$" + this.instruction.operands[i].toString();
        }
    }

    private ArrayList<String> mapBinary(String operation) {
        String x = this.getOperand(0);
        String y = this.getOperand(1);
        String z = this.getOperand(2);
        ArrayList<String> instructions = new ArrayList<>();
        String first = y, second = z;
        boolean hasImmediateForm = operation.equals("add") || operation.equals("and") || operation.equals("or");

        if (!y.contains("$") && !z.contains("$")) {
            instructions.add(String.format("li $temp, %s", y));
            instructions.add(String.format("li $temp2, %s", z));
            first = "$temp";
            second = "$temp2";
        } else if (!y.contains("$")) {
            if (hasImmediateForm) {
                operation += "i";
                first = z;
                second = y;
            } else {
                instructions.add(String.format("li $temp, %s", y));
                first = "$temp";
            }
        } else if (!z.contains("$")) {
            if (hasImmediateForm) operation += "i";
            else {
                instructions.add(String.format("li $temp2, %s", z));
                second = "$temp2";
            }
        }

        instructions.add(String.format("%s %s, %s, %s", operation, x, first, second));

        return instructions;
    }

    private ArrayList<String> mapBranch(String functionName, String condition) {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        String y = this.getOperand(1);
        String z = this.getOperand(2);
        ArrayList<String> instructions = new ArrayList<>();

        String first = y, second = z;
        if (!y.contains("$")) {
            instructions.add(String.format("li $temp, %s", y));
            first = "$temp";
        }
        if (!z.contains("$")) {
            instructions.add(String.format("li $temp2, %s", z));
            second = "$temp2";
        }

        instructions.add(String.format("%s %s, %s, %s_%s", condition, first, second, functionName, label));

        return instructions;
    }

    private ArrayList<String> mapAssign() {
        String x = this.getOperand(0);
        String op2 = this.getOperand(1);
        ArrayList<String> instructions = new ArrayList<>();

        if (this.instruction.operands.length == 3) {
            String value = this.getOperand(2);
            return this.assignArray(x, Integer.parseInt(op2), value);
        }

        String operation = "li";
        if (op2.contains("$")) operation = "move";
        instructions.add(String.format("%s %s, %s", operation, x, op2));

        return instructions;
    }

    private ArrayList<String> mapGoto(String functionName) {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        ArrayList<String> instructions = new ArrayList<>();

        instructions.add(String.format("beq $zero, $zero, %s_%s", functionName, label)); // TODO: change to b on SPIM

        return instructions;
    }

    private ArrayList<String> mapFunction(IRFunction function, boolean ret) {
        String functionLabel = this.getOperand(0);
        if (ret) functionLabel = this.getOperand(1);
        functionLabel = functionLabel.substring(1); // strip $ symbol

        if (intrinsicFunctions.containsKey(functionLabel)) return this.mapIntrinsicFunction(functionLabel);

        return this.generateCallInitialization(function, functionLabel, ret);
    }

    private ArrayList<String> mapReturn() {
        String x = this.getOperand(0);
        ArrayList<String> instructions = new ArrayList<>();
        String operation = "li";
        if (x.contains("$")) operation = "move";

        instructions.add(String.format("%s $v0, %s", operation, x));
        instructions.add("jr $ra");

        return instructions;
    }

    private ArrayList<String> mapLabel(String functionName) {
        ArrayList<String> instructions = new ArrayList<>();

        instructions.add(String.format("%s_%s:", functionName, this.instruction.operands[0].toString()));

        return instructions;
    }

    private ArrayList<String> mapArrayLoad() {
        String x = this.getOperand(0);
        String array = this.getOperand(1);
        String offset = this.getOperand(2);
        ArrayList<String> instructions = new ArrayList<>();

        if (offset.contains("$")) {
            instructions.add("li $temp, 4");
            instructions.add(String.format("mul $temp, %s, $temp", offset));
            instructions.add(String.format("add $temp, %s, $temp", array));
            offset = "0";
            array = "$temp";
        }

        instructions.add(String.format("lw %s, %d(%s)", x, Integer.parseInt(offset) * 4, array));

        return instructions;
    }

    private ArrayList<String> mapArrayStore() {
        String x = this.getOperand(0);
        String array = this.getOperand(1);
        String offset = this.getOperand(2);
        ArrayList<String> instructions = new ArrayList<>();

        if (offset.contains("$")) {
            instructions.add("li $temp, 4");
            instructions.add(String.format("mul $temp, %s, $temp", offset));
            instructions.add(String.format("add $temp, %s, $temp", array));
            offset = "0";
            array = "$temp";
        }

        if (!x.contains("$")) {
            instructions.add(String.format("li $temp2, %s", x));
            x = "$temp2";
        }

        instructions.add(String.format("sw %s, %d(%s)", x, Integer.parseInt(offset) * 4, array));

        return instructions;
    }

    private ArrayList<String> map(IRFunction function) {
        switch (this.instruction.opCode) {
            case ADD:
                return mapBinary("add");
            case SUB:
                return mapBinary("sub");
            case MULT:
                return mapBinary("mul");
            case DIV:
                return mapBinary("div");
            case AND:
                return mapBinary("and");
            case OR:
                return mapBinary("or");
            case BREQ:
                return mapBranch(function.name, "beq");
            case BRGEQ:
                return mapBranch(function.name, "bge");
            case BRGT:
                return mapBranch(function.name, "bgt");
            case BRLEQ:
                return mapBranch(function.name, "ble");
            case BRLT:
                return mapBranch(function.name, "blt");
            case BRNEQ:
                return mapBranch(function.name, "bne");
            case ASSIGN:
                return mapAssign();
            case GOTO:
                return mapGoto(function.name);
            case CALL:
                return mapFunction(function, false);
            case CALLR:
                return mapFunction(function, true);
            case RETURN:
                return mapReturn();
            case LABEL:
                return mapLabel(function.name);
            case ARRAY_LOAD:
                return mapArrayLoad();
            case ARRAY_STORE:
                return mapArrayStore();
            default:
                return new ArrayList<>();
        }
    }
}
