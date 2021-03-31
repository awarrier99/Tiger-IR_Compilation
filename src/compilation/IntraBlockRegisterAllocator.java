package compilation;

import java.util.*;
import java.util.stream.Collectors;

public class IntraBlockRegisterAllocator implements RegisterAllocator {
    private final HashMap<MIPSInstructionPair, BasicBlock> mipsLeaderBlockMap;
    public final HashMap<String, HashMap<String, Integer>> functionOffsetMaps;
    private HashMap<String, Integer> offsetMap = new HashMap<>();

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

    public IntraBlockRegisterAllocator(HashMap<MIPSInstructionPair, BasicBlock> mipsLeaderBlockMap, HashMap<String, HashMap<String, Integer>> functionOffsetMaps) {
        this.mipsLeaderBlockMap = mipsLeaderBlockMap;
        this.functionOffsetMaps = functionOffsetMaps;
    }

    private String getOperation(String instruction) {
        if (instruction.equals("syscall")) return null;
        int spacePos = instruction.indexOf(' ');
        if (spacePos == -1) return null;
        return instruction.substring(0, spacePos);
    }

    private ArrayList<String> getOperands(String instruction, ArrayList<Integer> offsets) {
        int spacePos = instruction.indexOf(' ');
        if (spacePos == -1) return new ArrayList<>();

        return Arrays.stream(instruction.substring(spacePos + 1).split(","))
                .map(i -> {
                    Integer offset = null;
                    i = i.strip();
                    int pos = i.indexOf('(');
                    if (pos != -1) {
                        offset = Integer.parseInt(i.substring(0, pos).strip());
                        i = i.substring(pos + 1, i.indexOf(')'));
                    }

                    offsets.add(offset);
                    return i;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private HashSet<String> upwardsExposedVariables(BasicBlock block, int index) {
        HashSet<String> vars = new HashSet<>();
        if (index == 0) return vars;

        String instruction = block.mipsInstructions.get(index);
        String op = this.getOperation(instruction);
        ArrayList<String> operands = this.getOperands(instruction, new ArrayList<>());
        if (defInstructions.contains(op)) operands.remove(0);

        for (int i = 0; i < index; i++) {
            String curr = block.mipsInstructions.get(i);
            String currOp = this.getOperation(curr);
            if (currOp == null) continue;

            String defOperand = this.getOperands(curr, new ArrayList<>()).get(0);
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

            ArrayList<String> operands = this.getOperands(instruction, new ArrayList<>());
            String defOperand = operands.get(0);
            if (ignoreRegisterList.contains(defOperand)) continue;
            if (counted.contains(defOperand)) continue;
            counted.add(defOperand);

            for (int j = i + 1; j < block.mipsInstructions.size(); j++) {
                String curr = block.mipsInstructions.get(j);
                String currOp = this.getOperation(curr);
                ArrayList<String> currOperands = this.getOperands(curr, new ArrayList<>());
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
                varKill.add(this.getOperands(mipsInstructionPair.instruction, new ArrayList<>()).get(0));
            HashSet<String> temp = new HashSet<>(instructionLiveOut);
            temp.removeAll(varKill);
            temp.addAll(ueVars);
            instructionLiveIn = new HashSet<>(temp);
            block.liveIn.set(mipsInstructionPair.index, instructionLiveIn);

            HashSet<String> prevInstructionLiveIn = block.liveIn.get(mipsInstructionPair.index);
            if (!prevInstructionLiveIn.equals(instructionLiveIn)) worklist.add(mipsInstructionPair);
        }

        this.countUses(block);
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

    private String replaceOperand(String instruction, ArrayList<Integer> offsets, int opIndex, String replacement) {
        ArrayList<String> components = new ArrayList<>(Arrays.asList(instruction.split(", ")));
        String[] split = components.get(0).split(" ");
        String operation = split[0];
        components.set(0, split[1]);

        Integer offset = offsets.get(opIndex);
        if (offset != null) replacement = String.format("%d(%s)", offset, replacement);

        components.set(opIndex, replacement);

        return String.format("%s %s", operation, String.join(", ", components));
    }

    private ArrayList<String> allocateBlock(BasicBlock block, int index) {
        ArrayList<String> instructions = new ArrayList<>();
        int firstAvailable = 0;
        HashMap<String, Integer> usesMap = new HashMap<>(block.usesMap);
        HashMap<String, String> localRegisterMap = new HashMap<>();
        boolean endOnBranchOrJump = false;
        boolean functionEnd = false;

        while (!usesMap.isEmpty()) {
            String maxUsedDef = Collections.max(usesMap.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey();
            firstAvailable = this.allocateRegister(maxUsedDef, localRegisterMap, firstAvailable);
            usesMap.remove(maxUsedDef);
        }

        HashSet<String> locallyInitialized = new HashSet<>();
        boolean deleteRestores = false;
        boolean matchedFirst = false;
        boolean functionBlock = block.mipsInstructions.contains("sw $ra, 0($sp)");
        boolean deleteStores = false;
        boolean deleted = false;
        int prev = 0;

        for (int i = 0; i < block.mipsInstructions.size(); i++) {
            String instruction = block.mipsInstructions.get(i);
            System.out.println("\n" + instruction + ":");
            if (instruction.contains(":") && this.functionOffsetMaps.containsKey(instruction))
                this.offsetMap = functionOffsetMaps.get(instruction);

            if (!deleteStores && !deleted && functionBlock) {
                boolean isAdd = instruction.matches("addi \\$sp, \\$sp, -?\\d+");
                boolean nextIsStore = i != block.mipsInstructions.size() - 1 &&
                        block.mipsInstructions.get(i + 1).matches("sw \\$.+, \\d+\\(\\$.+\\)");
                if (isAdd && nextIsStore){
                    deleteStores = true;
                    continue;
                }
            }
            if (deleteStores && instruction.matches("sw \\$.+, \\d+\\(\\$.+\\)")) continue;
            else if (deleteStores) {
                deleteStores = false;
                deleted = true;
            }

            if (deleteRestores) {
                if (instruction.matches("lw \\$.+, \\d+\\(\\$.+\\)") && !instruction.contains("$ra")) {
                    matchedFirst = true;
                    continue;
                } else if (matchedFirst && instruction.matches("addi \\$sp, \\$sp, -?\\d+")) {
                    deleteRestores = false;
                    matchedFirst = false;
                    continue;
                }
            }

            ArrayList<String> suffix = new ArrayList<>();
            String operation = this.getOperation(instruction);
            ArrayList<Integer> offsets = new ArrayList<>();
            ArrayList<String> operands = this.getOperands(instruction, offsets);
            boolean t8Used = false;
            HashSet<String> tempLocal = new HashSet<>();

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
                            instruction = this.replaceOperand(instruction, offsets, j, "$t8");
                            suffix.add(String.format("sw $t8, %d($sp)", this.offsetMap.get(op) * 4));
                        }
                        tempLocal.add(op);
                    } else if (!localRegisterMap.containsKey(op)) {
                        String register;
                        if (!t8Used) {
                            register = "$t8";
                            t8Used = true;
                        }
                        else register = "$t9";
                        instructions.add(String.format("lw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
                        instruction = this.replaceOperand(instruction, offsets, j, register);
                    } else instructions.add(String.format("lw %s, %d($sp)", localRegisterMap.get(op), this.offsetMap.get(op) * 4));
                } else if (!localRegisterMap.containsKey(op)) {
                    String register = "$t8";
                    if (j == 0 && defInstructions.contains(operation))
                        suffix.add(String.format("sw $t8, %d($sp)", this.offsetMap.get(op) * 4));
                    else {
                        if (!t8Used) {
                            register = "$t8";
                            t8Used = true;
                        }
                        else register = "$t9";
                        instructions.add(String.format("lw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
                    }
                    instruction = this.replaceOperand(instruction, offsets, j, register);
                }
                if (localRegisterMap.containsKey(op))
                    instruction = this.replaceOperand(instruction, offsets, j, localRegisterMap.get(op));
            }
            instructions.add(instruction);
            instructions.addAll(suffix);

            if (branchInstructions.contains(operation)) {
                endOnBranchOrJump = true;
                instructions.addAll(instructions.size() - 1, this.generateStoreVariables(localRegisterMap));
            }
            if (jumpInstructions.contains(operation)) {
                endOnBranchOrJump = true;
                int insertOffset = 1;
                if ("jal".equals(operation)) {
                    deleteRestores = true;
                    insertOffset = 3;
                }
                instructions.addAll(instructions.size() - insertOffset, this.generateStoreVariables(localRegisterMap)); // before $ra stack allocation
            }

            if (instruction.equals("jr $ra")) {
                functionEnd = true;
                instructions.add(instructions.size() - 1, String.format("addi $sp, $sp, %d", this.offsetMap.size() * 4));
            }

            if (!tempLocal.isEmpty()) locallyInitialized.addAll(tempLocal);

            index++;
            for (String s: instructions.subList(prev, instructions.size())) System.out.println(s);
            prev = instructions.size();
        }

        if (!functionEnd && !endOnBranchOrJump) instructions.addAll(this.generateStoreVariables(localRegisterMap));

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
            }
        }

        return allocatedInstructions;
    }
}
