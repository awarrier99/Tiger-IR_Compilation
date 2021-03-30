package compilation;

import ir.IRInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BasicBlock {
    public final ArrayList<IRInstruction> irInstructions;
    public final ArrayList<String> mipsInstructions = new ArrayList<>();
    public final HashMap<String, Integer> usesMap = new HashMap<>();
    public ArrayList<HashSet<String>> liveIn;
    public ArrayList<HashSet<String>> liveOut;

    public BasicBlock(ArrayList<IRInstruction> irInstructions) {
        this.irInstructions = irInstructions;
    }
}
