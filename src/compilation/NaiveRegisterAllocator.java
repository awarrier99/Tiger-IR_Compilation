package compilation;

import java.util.ArrayList;

public class NaiveRegisterAllocator implements RegisterAllocator {
    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
        return instructions;
    }

//    private String[] mapArithmetic(String operation) {
//        String lvalue = this.getOperand(0);
//        String op1 = this.getOperand(1);
//        String op2 = this.getOperand(2);
//
//        String assembly = String.format("%s %s, %s, %s", operation, lvalue, op1, op2);
//        if (this.isNaive)
//        {
//            if (op2.contains("$"))
//            {
//                String[] prefix = new String[2];
//                String[] suffix = new String[2];
//                prefix = generateNaivePrefix(new String[] {lvalue, op1});
//                suffix = generateNaiveSuffix(new String[] {lvalue, op1});
//            } else {
//                String[] prefix = new String[3];
//                String[] suffix = new String[3];
//                prefix = generateNaivePrefix(new String[] {lvalue, op1, op2});
//                suffix = generateNaiveSuffix(new String[] {lvalue, op1, op2});
//            }
//
//        }
//        String[] newInst = new String[prefix.length + 1 + suffix.length];
//        return concatInstructions(new String[][] {prefix, New String[] {assembly}, suffix});
//        //return new String[] {assembly};
//    }
//
//    private String[] concatInstructions(String[][] instructions)
//    {
//        int length = 0;
//        int instNum = 0;
//        for (String[] grouping: instructions)
//        {
//            length += grouping.length;
//        }
//
//        String[] rInstructions = new String[length];
//
//        for (String[] grouping: instructions)
//        {
//            for (String inst: grouping)
//            {
//                rInstructions[instNum] = inst;
//                instNum++;
//            }
//        }
//
//        return rInstructions;
//    }
//
//    private String[] generateNaivePrefix(String[] registers)
//    {
//        String[] tregisters = new String[] {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"};
//        int regnum = 0;
//        ArrayList<String> prefix = new ArrayList<>();
//        for (String register: registers)
//        {
//            prefix.add(String.format("lw %s, %d($sp)", tregisters[regnum], getOffset(register)));
//            regnum++;
//        }
//        String[] rInstructions = new String[prefix.size()];
//        rInstructions = prefix.toArray(rInstructions);
//        return rInstructions;
//    }
//
//    private String[] generateNaiveSuffix(String[] registers) {
//        String[] tregisters = new String {
//            "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"
//        } ;
//        int regnum = 0;
//        ArrayList<String> suffix = new ArrayList<>;
//        for (String register : registers) {
//            suffix.add(String.format("sw %s, %d($sp)", tregisters[regnum], getOffset(register)));
//            regnum++;
//        }
//        String[] rInstructions = new String[suffix.size()];
//        rInstructions = suffix.toArray(rInstructions);
//        return rInstructions;
//    }
}
