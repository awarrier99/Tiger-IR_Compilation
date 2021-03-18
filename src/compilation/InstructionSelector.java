package compilation;

import ir.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class InstructionSelector {
    private final IRProgram program;
    private IRInstruction instruction;

    public InstructionSelector(String filename) throws FileNotFoundException, IRException {
        IRReader irReader = new IRReader();
        this.program = irReader.parseIRFile(filename);
    }

    public void generate(String filename) throws FileNotFoundException {
//        FileOutputStream outputStream = new FileOutputStream(this.filename);
        for (IRFunction function: program.functions) {
            for (int i = 0; i < function.instructions.size(); i++) {
                if (i == 0) {
                    String[] stackSetup = this.generateStackSetup();
                    for (String line: stackSetup) {
                        System.out.println(line);
                    }
                }
                this.instruction = function.instructions.get(i);
                for (String assembly: this.map()) {
                    System.out.println(assembly);
                }
            }
        }
    }

    private String[] generateStackSetup() {
        String allocation = "addi $sp, $sp, -8";
        return new String[] {allocation};
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

    private String[] mapBranch(String condition) {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        String op1 = this.getOperand(1);
        String op2 = this.getOperand(2);
        String assembly = String.format("%s %s, %s, %s", condition, op1, op2, label);

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
            String operation;
            if (op2.contains("$")) operation = "move";
            else operation = "li";
            assembly = String.format("%s %s, %s", operation, op1, op2);
        }


        return new String[] {assembly};
    }

    private String[] mapGoto() {
        String label = this.getOperand(0).substring(1); // strip $ symbol
        String assembly = String.format("b %s",label);

        return new String[] {assembly};
    }

    private String[] mapFunction(boolean ret) {
        if (ret) {

        } else {

        }

        return new String[] {};
    }

    private String[] map() {
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
                return mapBranch("beq");
            case BRGEQ:
                return mapBranch("bge");
            case BRGT:
                return mapBranch("bgt");
            case BRLEQ:
                return mapBranch("ble");
            case BRLT:
                return mapBranch("blt");
            case BRNEQ:
                return mapBranch("bne");
            case ASSIGN:
                return mapAssign();
            case GOTO:
                return mapGoto();
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
