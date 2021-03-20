package compilation;

import ir.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

public class InstructionSelector {
    private final IRProgram program;
    private IRInstruction instruction;

    private static final HashMap<String, Integer> intrinsicFunctions;

    static {
        intrinsicFunctions = new HashMap<>();
        intrinsicFunctions.put("geti", 5);
        intrinsicFunctions.put("getc", 12);
        intrinsicFunctions.put("puti", 1);
        intrinsicFunctions.put("putc", 11);
    }

    public InstructionSelector(String filename) throws FileNotFoundException, IRException {
        IRReader irReader = new IRReader();
        this.program = irReader.parseIRFile(filename);
    }

    public void generate(String filename) throws FileNotFoundException {
        FileOutputStream outputStream = new FileOutputStream(filename);
        PrintStream printStream = new PrintStream(outputStream);

        printStream.println(".text");
        printStream.println("beq $zero, $zero, main\n");
        for (IRFunction function: program.functions) {
            printStream.printf("%s:%n", function.name); // TODO: initialize function variables
            for (String assembly: this.generateArguments(function)) printStream.println(assembly);
            for (int i = 0; i < function.instructions.size(); i++) {
                this.instruction = function.instructions.get(i);
                for (String assembly: this.map(function.name)) printStream.println(assembly);
            }

            if (function.name.equals("main")) {
                printStream.println("li $v0, 10");
                printStream.println("syscall");
            } else {
                printStream.println(); // empty line after function for readability
            }
        }
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

    private ArrayList<String> generateCallInitialization(String functionLabel, boolean ret) {
        ArrayList<String> instructions = new ArrayList<>();
//        instructions.add("addi $sp, $sp, -40");
//
//        for (int i = 0; i < 10; i++) {
//            instructions.add(String.format("sw $t%d, %d($sp)", i, (9 - i) * 4));
//        }
//
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
                instructions.add(String.format("%s $t0, %s", operation, op));
                instructions.add(String.format("sw $t0, %d($sp)", (numArgs - j - 1) * 4));
            }
            j++;
        }
//
//        instructions.add("addi $sp, $sp, -4");
//        instructions.add("sw $ra, 0($sp)");
        instructions.add(String.format("jal %s", functionLabel));
//        instructions.add("lw $ra, 0($sp)");
//        instructions.add("addi $sp, $sp, 4");
//        if (numArgs > 4) instructions.add(String.format("addi $sp, $sp, %d", (numArgs - 4) * 4));
//
//        for (int i = 0; i < 10; i++) {
//            instructions.add(String.format("lw $t%d, %d($sp)", i, (9 - i) * 4));
//        }
//        instructions.add("addi $sp, $sp, 40");
//
        if (ret) {
            String op = this.getOperand(0);
            instructions.add(String.format("move %s, $v0", op));
        }

        return instructions;
    }

    private String[] mapIntrinsicFunction(String function) {
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

        String[] instructionsArray = new String[instructions.size()];

        return instructions.toArray(instructionsArray);
    }

    private String getOperand(int i) {
        try {
            Integer.parseInt(this.instruction.operands[i].toString());
            return this.instruction.operands[i].toString();
        } catch (NumberFormatException e) {
            return "$" + this.instruction.operands[i].toString();
        }
    }

    private String[] mapArithmetic(String operation) {
        String lvalue = this.getOperand(0);
        String op1 = this.getOperand(1);
        String op2 = this.getOperand(2);
        String assembly = String.format("%s %s, %s, %s", operation, lvalue, op1, op2);

        return new String[] {assembly};
    }

    private String[] mapBranch(String functionName, String condition) {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        String op1 = this.getOperand(1);
        String op2 = this.getOperand(2);
        if (!op2.contains("$")) {
            String load = String.format("li $branch, %s", op2);
            String assembly = String.format("%s %s, $branch, %s_%s", condition, op1, functionName, label);

            return new String[] {load, assembly};
        }

        String assembly = String.format("%s %s, %s, %s_%s", condition, op1, op2, functionName, label);


        return new String[] {assembly};
    }

    private String[] mapAssign() {
        String op1 = this.getOperand(0);
        String op2 = this.getOperand(1);
        String assembly;

        if (this.instruction.operands.length == 3) {
            String op3 = this.getOperand(3);
            assembly = "";
        } else {
            String operation = "li";
            if (op2.contains("$")) operation = "move";
            assembly = String.format("%s %s, %s", operation, op1, op2);
        }


        return new String[] {assembly};
    }

    private String[] mapGoto(String functionName) {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        String assembly = String.format("beq $zero, $zero, %s_%s", functionName, label);

        return new String[] {assembly};
    }

    private String[] mapFunction(boolean ret) {
        String functionLabel = this.getOperand(0);
        if (ret) functionLabel = this.getOperand(1);
        functionLabel = functionLabel.substring(1); // strip $ symbol

        if (intrinsicFunctions.containsKey(functionLabel)) return this.mapIntrinsicFunction(functionLabel);

        ArrayList<String> callInitialization = this.generateCallInitialization(functionLabel, ret);
        String[] instructions = new String[callInitialization.size()];

        return callInitialization.toArray(instructions);
    }

    private String[] mapReturn() {
        String op = this.getOperand(0);
        String operation = "li";
        if (op.contains("$")) operation = "move";
        String move = String.format("%s $v0, %s", operation, op);
        String ret = "jr $ra";

        return new String[] {move, ret};
    }

    private String[] mapLabel(String functionName) {
        String assembly = String.format("%s_%s:", functionName, this.instruction.operands[0].toString());

        return new String[] {"", assembly}; // empty line before label for readability
    }

    private String[] map(String functionName) {
        switch (this.instruction.opCode) {
            case ADD:
                return mapArithmetic("add");
            case SUB:
                return mapArithmetic("sub");
            case MULT:
                return mapArithmetic("mul");
            case DIV:
                return mapArithmetic("div");
            case AND:
                return mapArithmetic("and");
            case OR:
                return mapArithmetic("or");
            case BREQ:
                return mapBranch(functionName, "beq");
            case BRGEQ:
                return mapBranch(functionName, "bge");
            case BRGT:
                return mapBranch(functionName, "bgt");
            case BRLEQ:
                return mapBranch(functionName, "ble");
            case BRLT:
                return mapBranch(functionName, "blt");
            case BRNEQ:
                return mapBranch(functionName, "bne");
            case ASSIGN:
                return mapAssign();
            case GOTO:
                return mapGoto(functionName);
            case CALL:
                return mapFunction(false);
            case CALLR:
                return mapFunction(true);
            case RETURN:
                return mapReturn();
            case LABEL:
                return mapLabel(functionName);
            default:
                return new String[0];
        }
    }

    public static void main(String[] args) throws FileNotFoundException, IRException {
        String inputFilename = args[0];
        String outputFilename = args[1];
        InstructionSelector selector = new InstructionSelector(inputFilename);
        selector.generate(outputFilename);
    }
}
