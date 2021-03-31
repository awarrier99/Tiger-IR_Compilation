package compilation;

import ir.IRException;
import ir.IRProgram;
import ir.IRReader;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

public class Compiler {
    public static void main(String[] args) throws FileNotFoundException, IRException {
        String inputFilename = args[0];
        String outputFilename = args[1];

        boolean naiveAllocation = true;
        try {
            String allocationMode = args[2];
            if (allocationMode.equals("--intrablock")) naiveAllocation = false;
        } catch (IndexOutOfBoundsException e) {}

        IRReader irReader = new IRReader();
        IRProgram program = irReader.parseIRFile(inputFilename);
        InstructionSelector selector = new InstructionSelector(program);
        ArrayList<String> instructions = selector.generate();

        RegisterAllocator allocator;
        if (naiveAllocation) allocator = new NaiveRegisterAllocator();
        else allocator = new IntraBlockRegisterAllocator(selector.mipsLeaderBlockMap, selector.functionOffsetMaps);
        instructions = allocator.allocate(instructions);

        FileOutputStream outputFile = new FileOutputStream(outputFilename);
        PrintStream printStream = new PrintStream(outputFile);
        for (String instruction: instructions) {
            printStream.println(instruction);
        }
    }
}
