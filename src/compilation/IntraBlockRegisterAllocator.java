package compilation;

import java.util.*;
import java.util.stream.Collectors;

public class IntraBlockRegisterAllocator implements RegisterAllocator {
    private final HashMap<MIPSInstructionPair, BasicBlock> mipsLeaderBlockMap;
    private final HashMap<String, Integer> offsetMap = new HashMap<>();
    public final HashSet<MIPSInstructionPair> functionEndMap;

    private static final HashSet<String> defInstructions = new HashSet<>();
    private static final HashSet<String> ignoreRegisterList = new HashSet<>();
    private static final HashSet<String> branchInstructions = new HashSet<>();
    private static final HashSet<String> jumpInstructions = new HashSet<>();

    static {
        defInstructions.add("add");
        defInstructions.add("addi");
        defInstructions.add("sub");
        defInstructions.add("mul");
        defInstructions.add("div");
        defInstructions.add("and");
        defInstructions.add("andi");
        defInstructions.add("or");
        defInstructions.add("ori");
        defInstructions.add("li");
        defInstructions.add("lw");
        defInstructions.add("move");

        ignoreRegisterList.add("$zero");
        ignoreRegisterList.add("$v0");
        ignoreRegisterList.add("$a0");
        ignoreRegisterList.add("$a1");
        ignoreRegisterList.add("$a2");
        ignoreRegisterList.add("$a3");
        ignoreRegisterList.add("$sp");
        ignoreRegisterList.add("$ra");

        branchInstructions.add("beq");
        branchInstructions.add("bge");
        branchInstructions.add("bgt");
        branchInstructions.add("ble");
        branchInstructions.add("blt");
        branchInstructions.add("bne");

        jumpInstructions.add("jal");
        jumpInstructions.add("jr");
    }

    public IntraBlockRegisterAllocator(HashMap<MIPSInstructionPair, BasicBlock> mipsLeaderBlockMap, HashSet<MIPSInstructionPair> functionEndMap) {
        this.mipsLeaderBlockMap = mipsLeaderBlockMap;
        this.functionEndMap = functionEndMap;
    }

    private String getOperation(String instruction) {
        if (instruction.equals("syscall")) return null;
        int spacePos = instruction.indexOf(' ');
        if (spacePos == -1) return null;
        return instruction.substring(0, spacePos);
    }

    private ArrayList<String> getOperands(String instruction) {
        int spacePos = instruction.indexOf(' ');
        if (spacePos == -1) return new ArrayList<>();

        return Arrays.stream(instruction.substring(spacePos + 1).split(","))
                .map(i -> {
                    i = i.strip();
                    int pos = i.indexOf('(');
                    if (pos != -1) {
                        i = i.substring(pos + 1, i.indexOf(')'));
                    }

                    return i;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private HashSet<String> upwardsExposedVariables(BasicBlock block, int index) {
        HashSet<String> vars = new HashSet<>();
        if (index == 0) return vars;

        String instruction = block.mipsInstructions.get(index);
        String op = this.getOperation(instruction);
        ArrayList<String> operands = this.getOperands(instruction);
        if (defInstructions.contains(op)) operands.remove(0);

        for (int i = 0; i < index; i++) {
            String curr = block.mipsInstructions.get(i);
            String currOp = this.getOperation(curr);
            if (currOp == null) continue;

            String defOperand = this.getOperands(curr).get(0);
            if (!ignoreRegisterList.contains(defOperand) && defInstructions.contains(currOp) && operands.contains(defOperand))
                vars.add(defOperand);
        }

        return vars;
    }

    private void countUses(BasicBlock block) {
        HashSet<String> counted = new HashSet<>();

        for (int i = 0; i < block.mipsInstructions.size() - 1; i++) {
            String instruction = block.mipsInstructions.get(i);
            String op = this.getOperation(instruction);
            if (!defInstructions.contains(op)) continue;;

            ArrayList<String> operands = this.getOperands(instruction);
            String defOperand = operands.get(0);
            if (ignoreRegisterList.contains(defOperand)) continue;
            if (counted.contains(defOperand)) continue;
            counted.add(defOperand);

            for (int j = i + 1; j < block.mipsInstructions.size(); j++) {
                String curr = block.mipsInstructions.get(j);
                String currOp = this.getOperation(curr);
                ArrayList<String> currOperands = this.getOperands(curr);
                if (defInstructions.contains(currOp)) currOperands.remove(0);

                if (currOperands.contains(defOperand)) {
                    int count = 0;
                    if (block.usesMap.containsKey(defOperand)) count = block.usesMap.get(defOperand);
                    block.usesMap.put(defOperand, count + 1);
                }
            }
        }
    }

    private void computeLiveSets(BasicBlock block) {
        ArrayList<MIPSInstructionPair> worklist = new ArrayList<>();
        for (int i = 0; i < block.mipsInstructions.size(); i++) worklist.add(new MIPSInstructionPair(block.mipsInstructions.get(i), i));
        block.liveIn = block.mipsInstructions.stream()
                .map(s -> new HashSet<String>())
                .collect(Collectors.toCollection(ArrayList::new));
        block.liveOut = block.mipsInstructions.stream()
                .map(s -> new HashSet<String>())
                .collect(Collectors.toCollection(ArrayList::new));

        while (!worklist.isEmpty()) {
            MIPSInstructionPair mipsInstructionPair = worklist.remove(0);
            HashSet<String> instructionLiveIn;
            HashSet<String> instructionLiveOut;

            if (mipsInstructionPair.index == block.mipsInstructions.size() - 1) instructionLiveOut = new HashSet<>();
            else instructionLiveOut = new HashSet<>(block.liveIn.get(mipsInstructionPair.index + 1));
            block.liveOut.set(mipsInstructionPair.index, instructionLiveOut);

            HashSet<String> ueVars = this.upwardsExposedVariables(block, mipsInstructionPair.index);
            HashSet<String> varKill = new HashSet<>();
            if (defInstructions.contains(this.getOperation(mipsInstructionPair.instruction)))
                varKill.add(this.getOperands(mipsInstructionPair.instruction).get(0));
            HashSet<String> temp = new HashSet<>(instructionLiveOut);
            temp.removeAll(varKill);
            temp.addAll(ueVars);
            instructionLiveIn = new HashSet<>(temp);
            block.liveIn.set(mipsInstructionPair.index, instructionLiveIn);

            HashSet<String> prevInstructionLiveIn = block.liveIn.get(mipsInstructionPair.index);
            if (!prevInstructionLiveIn.equals(instructionLiveIn)) worklist.add(mipsInstructionPair);
        }

        this.countUses(block);

        Debug.printBasicBlock(block);
    }

    private int allocateRegister(String op, HashMap<String, String> localRegisterMap, int firstAvailable) {
        if (firstAvailable < 8) {
            localRegisterMap.put(op, "$t" + firstAvailable);
            firstAvailable++;
        }

        return firstAvailable;
    }

    private void adjustOffsets() {
        for (String op: this.offsetMap.keySet()) {
            int offset = this.offsetMap.get(op);
            this.offsetMap.put(op, offset + 1);
        }
    }

    private ArrayList<String> generateStoreVariables(HashMap<String, String> localRegisterMap) {
        ArrayList<String> storeVariables = new ArrayList<>();
        for (String op: localRegisterMap.keySet()) {
            storeVariables.add(String.format("sw %s, %d($sp)", localRegisterMap.get(op), this.offsetMap.get(op) * 4));
        }

        return storeVariables;
    }

    private ArrayList<String> allocateBlock(BasicBlock block, int index) {
        ArrayList<String> instructions = new ArrayList<>();
        int firstAvailable = 0;
        HashMap<String, Integer> usesMap = new HashMap<>(block.usesMap);
        HashMap<String, String> localRegisterMap = new HashMap<>();
        boolean endOnBranchOrJump = false;

        while (!usesMap.isEmpty()) {
            String maxUsedDef = Collections.max(usesMap.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey();
            firstAvailable = this.allocateRegister(maxUsedDef, localRegisterMap, firstAvailable);
            usesMap.remove(maxUsedDef);
        }

        HashSet<String> locallyInitialized = new HashSet<>();
        boolean deleteRestores = false;
        for (int i = 0; i < block.mipsInstructions.size(); i++) {
            String instruction = block.mipsInstructions.get(i);

            if (deleteRestores && !instruction.matches("addi \\$sp, \\$sp, -?\\d+")) continue;
            else if (deleteRestores) {
                deleteRestores = false;
                continue;
            }

            ArrayList<String> suffix = new ArrayList<>();
            String operation = this.getOperation(instruction);
            ArrayList<String> operands = this.getOperands(instruction);

            for (int j = 0; j < operands.size(); j++) {
                String op = operands.get(j);
                if (!op.contains("$") || ignoreRegisterList.contains(op)) continue;

                if (!locallyInitialized.contains(op)) {
                    if (j == 0 && defInstructions.contains(operation)) {
                        if (!this.offsetMap.containsKey(op)) {
                            this.adjustOffsets();
                            this.offsetMap.put(op, 0);
                            instructions.add("addi $sp, $sp, -4");
                        }
                        if (!localRegisterMap.containsKey(op)) {
                            instruction = instruction.replace(op, "$t8");
                            suffix.add(String.format("sw $t8, %d($sp)", this.offsetMap.get(op) * 4));
                        }
                        locallyInitialized.add(op);
                    } else if (!localRegisterMap.containsKey(op)) {
                        String register;
                        if (j == 1) register = "$t8";
                        else register = "$t9";
                        instructions.add(String.format("lw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
                        instruction = instruction.replace(op, register);
                    }
                } else if (!localRegisterMap.containsKey(op)) {
                    instruction = instruction.replace(op, "$t8");
                    if (j == 0 && defInstructions.contains(op))
                        suffix.add(String.format("sw $t8, %d($sp)", this.offsetMap.get(op) * 4));
                }
                if (localRegisterMap.containsKey(op)) instruction = instruction.replace(op, localRegisterMap.get(op));
            }
            instructions.add(instruction);
            instructions.addAll(suffix);

            if (branchInstructions.contains(operation)) {
                endOnBranchOrJump = true;
                instructions.addAll(instructions.size() - 1, this.generateStoreVariables(localRegisterMap));
            }
            if (jumpInstructions.contains(operation)) {
                endOnBranchOrJump = true;

                if ("jal".equals(operation)) {
                    deleteRestores = true;
                    boolean matchedRAStackAllocation = false;
                    int startDelete = instructions.size() - 1;
                    int endDelete = startDelete;
                    for (int j = instructions.size() - 1; j >= 0; j--) {
                        String curr = instructions.get(j);
                        if (curr.matches("addi \\$sp, \\$sp, -?\\d+")) {
                            if (matchedRAStackAllocation) startDelete = j;
                            else {
                                matchedRAStackAllocation = true; // skip past $ra stack allocation
                                endDelete = j;
                            }
                        }
                    }
                    ArrayList<String> deleted = new ArrayList<>(instructions.subList(0, startDelete));
                    deleted.addAll(instructions.subList(endDelete, instructions.size()));
                    instructions = deleted;
                    instructions.addAll(instructions.size() - 3, this.generateStoreVariables(localRegisterMap)); // before $ra stack allocation
                }
            }

            if (this.functionEndMap.contains(new MIPSInstructionPair(instruction, index))) {

            }

            index++;
        }

        if (!endOnBranchOrJump) instructions.addAll(this.generateStoreVariables(localRegisterMap));

        return instructions;
    }

    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
        ArrayList<String> allocatedInstructions = new ArrayList<>(instructions.subList(0, 2));
        BasicBlock block;
        for (int i = 2; i < instructions.size(); i++) {
            String instruction = instructions.get(i);
            MIPSInstructionPair pair = new MIPSInstructionPair(instruction, i);
            if (this.mipsLeaderBlockMap.containsKey(pair)) {
                block = this.mipsLeaderBlockMap.get(pair);
                this.computeLiveSets(block);
                ArrayList<String> allocatedBlock = this.allocateBlock(block, i);
                allocatedInstructions.addAll(allocatedBlock);
                for (String s: allocatedBlock) {
                    System.out.println(s);
                }
            }
        }

        return instructions;
    }
}
