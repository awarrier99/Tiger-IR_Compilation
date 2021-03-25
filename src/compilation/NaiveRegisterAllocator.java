package compilation;

import java.util.ArrayList;
import java.util.HashMap;
public class NaiveRegisterAllocator implements RegisterAllocator {
    
    private HashMap<String, Integer> vRegisters = new HashMap<>();
    private ArrayList<String> allocInst = new ArrayList<>();

    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
    	//add init for stack variables
        for (String instruction: instructions)
        {
        	String operation = getOperand(instruction, 0);
        	switch(operation)
        	{
        		case "add":
        		case "sub":
        		case "mul":
        		case "div":
        		case "and":
        		case "or":
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
        		case "sw"
        	}
        }
        return instructions;
    }

   private String[] allocateBinary(String instruction) {

   		String operation = getOperand(instruction, 0);
       	String lvalue = getOperand(instruction, 1);
       	String op1 = getOperand(instruction, 2);
       	String op2 = getOperand(instruction, 3);

		instruction = String.format("%s %s, %s, %s", operation, lvalue, op1, op2);
		
		if (op2.contains("$"))
		{
		   String[] prefix = new String[2];
		   String[] suffix = new String[2];
		   generateNaivePrefix(new String[] {lvalue, op1});
		   allocInst.add(instruction);
		   generateNaiveSuffix(new String[] {lvalue, op1});
		} else {
		   String[] prefix = new String[3];
		   String[] suffix = new String[3];
		   generateNaivePrefix(new String[] {lvalue, op1, op2});
		   allocInst.add(instruction);
		   generateNaiveSuffix(new String[] {lvalue, op1, op2});
		}

		/*String[] newInst = new String[prefix.length + 1 + suffix.length];
		return concatInstructions(new String[][] {prefix, New String[] {assembly}, suffix});*/
       //return new String[] {assembly};
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

   private void generateNaivePrefix(String[] registers)
   {
       String[] tregisters = new String[] {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"};
       int regnum = 0;
       //ArrayList<String> prefix = new ArrayList<>();
       for (String register: registers)
       {
           allocInst.add(String.format("lw %s, %d($fp)", tregisters[regnum], vRegisters.get(register)));
           regnum++;
       }
       //String[] rInstructions = new String[prefix.size()];
       //rInstructions = prefix.toArray(rInstructions);
       //return rInstructions;
   }

   private String[] generateNaiveSuffix(String[] registers) {
       String[] tregisters = new String[] {
           "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"
       } ;
       int regnum = 0;
       //ArrayList<String> suffix = new ArrayList<>;
       for (String register : registers) {
           allocInst.add(String.format("sw %s, %d($sp)", tregisters[regnum], vRegisters.get(register)));
           regnum++;
       }
       /*String[] rInstructions = new String[suffix.size()];
       rInstructions = suffix.toArray(rInstructions);
       return rInstructions;*/
   }
}
