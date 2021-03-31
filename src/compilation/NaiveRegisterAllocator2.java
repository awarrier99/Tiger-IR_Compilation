package compilation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public class NaiveRegisterAllocator2 implements RegisterAllocator {
    public final HashMap<String, HashMap<String, Integer>> functionOffsetMaps;
    private HashMap<String, Integer> offsetMap = new HashMap<>();

    private static final HashSet<String> defInstructions = new HashSet<>();
    private static final HashSet<String> ignoreRegisterList = new HashSet<>();

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
    }

    public NaiveRegisterAllocator2(HashMap<String, HashMap<String, Integer>> functionOffsetMaps) {
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

    private void adjustOffsets() {
        for (String op: this.offsetMap.keySet()) {
            int offset = this.offsetMap.get(op);
            this.offsetMap.put(op, offset + 1);
        }
    }

    private ArrayList<String> allocateInstruction(String instruction) {
        ArrayList<String> instructions = new ArrayList<>();
        ArrayList<Integer> offsets = new ArrayList<>();
        String operation = this.getOperation(instruction);
        ArrayList<String> operands = this.getOperands(instruction, offsets);
        ArrayList<String> suffix = new ArrayList<>();
        boolean t8Used = false;

        for (int i = 0; i < operands.size(); i++) {
            String op = operands.get(i);
            if (!op.contains("$") || ignoreRegisterList.contains(op)) continue;

            if (!this.offsetMap.containsKey(op)) {
                this.adjustOffsets();
                this.offsetMap.put(op, 0);
                instructions.add("addi $sp, $sp, -4");
            }

            String register;
            if (i == 0 && defInstructions.contains(operation)) {
                register = "$t8";
                suffix.add(String.format("sw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
            }
            else if (!t8Used) {
                t8Used = true;
                register = "$t8";
                instructions.add(String.format("lw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
            }
            else {
                register = "$t9";
                instructions.add(String.format("lw %s, %d($sp)", register, this.offsetMap.get(op) * 4));
            }

            instruction = this.replaceOperand(instruction, offsets, i, register);
        }

        if (instruction.equals("jr $ra"))
            instructions.add(String.format("addi $sp, $sp, %d", this.offsetMap.size() * 4));

        instructions.add(instruction);
        instructions.addAll(suffix);

        return instructions;
    }

    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
        ArrayList<String> allocatedInstructions = new ArrayList<>();
        boolean deleteStores = false;
        boolean deleteRestores = false;
        boolean matchedFirst = false;

        for (int i = 0; i < instructions.size(); i++) {
            String instruction = instructions.get(i);
            System.out.println("\n" + instruction + ":");

            if (!deleteStores) {
                boolean isAdd = instruction.matches("addi \\$sp, \\$sp, -?\\d+");
                boolean nextIsStore = i != instructions.size() - 1 &&
                       instructions.get(i + 1).matches("sw \\$.+, \\d+\\(\\$.+\\)") && !instructions.get(i + 1).contains("$ra");
                if (isAdd && nextIsStore){
                    deleteStores = true;
                    continue;
                }
            }
            if (deleteStores && instruction.matches("sw \\$.+, \\d+\\(\\$.+\\)")) continue;
            else if (deleteStores) deleteStores = false;

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

            if (instruction.contains(":") && this.functionOffsetMaps.containsKey(instruction))
                this.offsetMap = functionOffsetMaps.get(instruction);

            ArrayList<String> temp = this.allocateInstruction(instruction);
            allocatedInstructions.addAll(temp);
            for (String t: temp) System.out.println(t);

            if (instruction.contains("jal")) deleteRestores = true;
        }

        return allocatedInstructions;
    }
}
