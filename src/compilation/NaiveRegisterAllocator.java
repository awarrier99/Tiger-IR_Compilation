package compilation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

public class NaiveRegisterAllocator implements RegisterAllocator {

    private HashMap<String, Integer> vRegisters = new HashMap<>();
    private ArrayList<String> allocInst = new ArrayList<>();
    private final String[] realReg = {"$sp", "$fp", "$zero", "$ra", "$v0", "$a0", "a1", "a2", "a3"};
    private final ArrayList<String> real = new ArrayList<>(Arrays.asList(realReg));

    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
        //add init for stack variables
        for (String instruction : instructions) {
            String operation = getOperand(instruction, 0);
            if (operation.equals("li") || operation.equals("move")) {
                String dest = getOperand(instruction, 1);
                if (!vRegisters.containsKey(dest) && !real.contains(dest))
                    vRegisters.put(dest, (vRegisters.size() + 1) * -4);
            }
        }
        instructions.add(instructions.size() - 2,String.format("addi $sp, $sp, %d", (vRegisters.size() + 1) * 4));
        for (String instruction : instructions) {
            String operation = getOperand(instruction, 0);
            switch (operation) {
                case "add":
                case "addi":
                case "sub":
                case "mul":
                case "div":
                case "and":
                case "andi":
                case "or":
                case "ori":
                    allocateBinary(instruction);
                    break;
                case "beq":
                case "bge":
                case "bgt":
                case "ble":
                case "blt":
                case "bne":
                    allocateBranch(instruction);
                    break;
                case "li":
                case "move":
                    allocateAssign(instruction);
                    break;
                case "lw":
                case "sw":
                    allocateMemory(instruction);
                    break;
                default:

                    this.allocInst.add(instruction);
                    if (instruction.equals("main:"))
                    {
                        this.allocInst.add("move $fp, $sp");
                        this.allocInst.add(String.format("addi $sp, $sp, %d", (vRegisters.size() + 1) * -4));
                    }
                    break;
            }
        }

        return this.allocInst;
    }

    private void allocateBinary(String instruction) {

        String operation = getOperand(instruction, 0);
        String lvalue = getOperand(instruction, 1);
        String op1 = getOperand(instruction, 2);
        String op2 = getOperand(instruction, 3);
        //this.allocInst.add("Binary");
        String reg1, reg2, reg3;
        //instruction = String.format("%s %s, %s, %s", operation, lvalue, op1, op2);

        if (!op2.contains("$")) {
            reg1 = (real.contains(lvalue)) ? lvalue : "$t0";
            reg2 = (real.contains(lvalue)) ? lvalue : "$t1";
            generateNaivePrefix(new String[]{lvalue, op1});
            this.allocInst.add(String.format("%s %s, %s, %s", operation, reg1, reg2, op2));
            generateNaiveSuffix(new String[]{lvalue, op1});
        } else {
            reg1 = (real.contains(lvalue)) ? lvalue : "$t0";
            reg2 = (real.contains(lvalue)) ? lvalue : "$t1";
            reg3 = (real.contains(lvalue)) ? lvalue : "$t2";
            generateNaivePrefix(new String[]{lvalue, op1, op2});
            this.allocInst.add(String.format("%s %s, %s, %s", operation, reg1, reg2, reg3));
            generateNaiveSuffix(new String[]{lvalue, op1, op2});
        }
    }

    private String getOperand(String instruction, int operand) {
        instruction = instruction.replace(",", "");
        String[] operands = instruction.split(" ");
        return operands[operand];
    }

    private void allocateAssign(String instruction) {
        String operation = getOperand(instruction, 0);
        //this.allocInst.add("Assign");
        if (operation.equals("li")) {

            String dest = getOperand(instruction, 1);
            if (real.contains(dest))
            {
                this.allocInst.add(instruction);
            }
            else {
                String imm = getOperand(instruction, 2);
                this.allocInst.add(String.format("addi $t0, $zero, %s", imm));
                this.allocInst.add(String.format("sw $t0, %d($fp)", vRegisters.get(dest)));
            }
        } else if (operation.equals("move")) {
            String dest = getOperand(instruction, 1);
            String src = getOperand(instruction, 2);

            if (!real.contains(src)) {
                this.allocInst.add(String.format("lw $t0, %d($fp)", vRegisters.get(src)));
            } else if(!real.contains(dest)) {
                this.allocInst.add(String.format("move $t0, %s", src));
                this.allocInst.add(String.format("sw $t0, %d($fp)", vRegisters.get(dest)));
            } else {
                this.allocInst.add(String.format("move %s, %s", dest, src));
            }
            if (!real.contains(src) && !real.contains(dest))
            {
                this.allocInst.add(String.format("sw $t0, %d($fp)", vRegisters.get(dest)));
            }
        }
    }

    private void allocateBranch(String instruction) {
        String operation = getOperand(instruction, 0);
        String first = getOperand(instruction, 1);
        String second = getOperand(instruction, 2);
        String function = getOperand(instruction, 3);
        //this.allocInst.add("Branch");
        if (first.contains("$") && second.contains("$")) {
            generateNaivePrefix(new String[]{first, second});
            String reg1 = (real.contains(first)) ? first : "$t0";
            String reg2 = (real.contains(second)) ? second : "$t1";
            this.allocInst.add(String.format("%s %s, %s, %s", operation, reg1, reg2, function));
            generateNaiveSuffix(new String[]{first, second});
        } else if (first.contains("$")) {
            generateNaivePrefix(new String[]{first});
            String reg1 = (real.contains(first)) ? first : "$t0";
            this.allocInst.add(String.format("%s %s, %s, %s", operation, reg1, second, function));
            generateNaiveSuffix(new String[]{first});
        }
    }

    private void allocateMemory(String instruction) {
        String operation = getOperand(instruction, 0);
        String first = getOperand(instruction, 1);
        String second = getOperand(instruction, 2);
        String reg1, reg2;
        String offsetReg = second.substring(second.indexOf("(") + 1, second.indexOf(")"));
        String offset = second.substring(0, second.indexOf("("));
        //this.allocInst.add(String.format("Memory %s, offsetReg: %s, offset: %s, instruction: %s", operation, offsetReg, offset, instruction));
        if (operation.equals("lw")) {
            reg1 = (real.contains(first)) ? first : "$t0";
            reg2 = (real.contains(offsetReg)) ? offsetReg : "$t0";
            generateNaivePrefix(new String[]{offsetReg});
            this.allocInst.add(String.format("lw %s, %s(%s)", reg1, offset, reg2));
            generateNaiveSuffix(new String[]{first});

        } else if (operation.equals("sw")) {
            reg1 = (real.contains(first)) ? first : "$t0";
            reg2 = (real.contains(offsetReg)) ? offsetReg : "$t1";
            generateNaivePrefix(new String[]{first, offsetReg});
            this.allocInst.add(String.format("sw %s, %s(%s)", reg1, offset, reg2));
        }
    }

   /*private String[] concatInstructions(String[][] instructions)
   {
       int length = 0;
       int instNum = 0;
       for (String[] grouping: instructions)
       {
           length += grouping.length;
       }

       String[] rInstructions = new String[length];

       for (String[] grouping: instructions)
       {
           for (String inst: grouping)
           {
               rInstructions[instNum] = inst;
               instNum++;
           }
       }

       return rInstructions;
   }*/

    private void generateNaivePrefix(String[] registers) {
        String[] tregisters = new String[]{"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"};
        int regnum = 0;
        //ArrayList<String> prefix = new ArrayList<>();
        for (String register : registers) {
            if (!this.real.contains(register)) {
                this.allocInst.add(String.format("lw %s, %d($fp)", tregisters[regnum], vRegisters.get(register)));
                regnum++;
            }
        }
        //String[] rInstructions = new String[prefix.size()];
        //rInstructions = prefix.toArray(rInstructions);
        //return rInstructions;
    }

    private void generateNaiveSuffix(String[] registers) {
        String[] tregisters = new String[]{
                "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"
        };
        int regnum = 0;
        //ArrayList<String> suffix = new ArrayList<>;
        for (String register : registers) {
            if (!this.real.contains(register)) {
                this.allocInst.add(String.format("sw %s, %d($fp)", tregisters[regnum], vRegisters.get(register)));
                regnum++;
            }

        }
       /*String[] rInstructions = new String[suffix.size()];
       rInstructions = suffix.toArray(rInstructions);
       return rInstructions;*/
    }
}
