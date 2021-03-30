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
    		if (operation.equals("li") || operation.equals("move"))
    		{
    			String dest = getOperand(instruction, 1);
    			if (!vRegisters.containsKey(dest))
    				vRegisters.put(dest, (vRegisters.size() + 1)*4);
    		}
    	}
    	this.allocInst.add(String.format("addi $sp, $sp, %d", (vRegisters.size() + 1)*4));
        for (String instruction: instructions)
        {
        	String operation = getOperand(instruction, 0);
        	switch(operation)
        	{
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

		//instruction = String.format("%s %s, %s, %s", operation, lvalue, op1, op2);
		
		if (!op2.contains("$"))
		{
		   generateNaivePrefix(new String[] {lvalue, op1});
		   this.allocInst.add(String.format("%s $t0, $t1, %s", operation, op2));
		   generateNaiveSuffix(new String[] {lvalue, op1});
		} else {
		   generateNaivePrefix(new String[] {lvalue, op1, op2});
		   this.allocInst.add(String.format("%s $t0, $t1, $t2", operation));
		   generateNaiveSuffix(new String[] {lvalue, op1, op2});
		}
    }

    private String getOperand(String instruction, int operand)
    {
        instruction = instruction.replace(",","");
        String[] operands = instruction.split(" ");
        return operands[operand];
    }

    private void allocateAssign(String instruction)
    {
    	String operation = getOperand(instruction, 0);

    	if (operation.equals("li"))
    	{
    		String dest = getOperand(instruction, 1);
    		String imm = getOperand(instruction, 2);
    		this.allocInst.add(String.format("addi $t0, $zero, %s", imm));
    		this.allocInst.add(String.format("sw $t0, %d($fp)", vRegisters.get(dest)));
    	} else if (operation.equals("move"))
    	{
    		String dest = getOperand(instruction, 1);
    		String src = getOperand(instruction, 2);
    		this.allocInst.add(String.format("lw $t0, %d($fp)", vRegisters.get(src)));
    		this.allocInst.add(String.format("sw $t0, %d($fp)", vRegisters.get(dest)));
    	}
    }

    private void allocateBranch(String instruction) 
    {
    	String operation = getOperand(instruction, 0);
    	String first = getOperand(instruction, 1);
    	String second = getOperand(instruction, 2);
    	String function = getOperand(instruction, 3);

    	if (first.contains("$") && second.contains("$"))
    	{
    		generateNaivePrefix(new String[] {first, second});
    		this.allocInst.add(String.format("%s $t0, $t1, %s", operation, function));
    		generateNaiveSuffix(new String[] {first, second});
    	} else if (first.contains("$"))
    	{
    		generateNaivePrefix(new String[] {first});
    		this.allocInst.add(String.format("%s $t0, %s, %s", operation, second, function));
    		generateNaiveSuffix(new String[] {first});
    	}
    }

    private void allocateMemory(String instruction) 
    {
        String operation = getOperand(instruction, 0);
        String first = getOperand(instruction, 1);
        String second = getOperand(instruction, 2);

        String offsetReg = second.substring(second.indexOf("(")+1, second.indexOf(")"));
        String offset = second.substring(0, second.indexOf("("));
    	if (operation.equals("lw"))
        {
            generateNaivePrefix(new String[] {offsetReg});
            this.allocInst.add(String.format("lw $t0, %s($t0)", offset));
            generateNaiveSuffix(new String[] {first});

        } else if(operation.equals("sw"))
        {
            generateNaivePrefix(new String[] {first, offsetReg});
            this.allocInst.add(String.format("sw $t0, %s($t1)", offset));
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

   private void generateNaivePrefix(String[] registers)
   {
       String[] tregisters = new String[] {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"};
       int regnum = 0;
       //ArrayList<String> prefix = new ArrayList<>();
       for (String register: registers)
       {
           this.allocInst.add(String.format("lw %s, %d($fp)", tregisters[regnum], vRegisters.get(register)));
           regnum++;
       }
       //String[] rInstructions = new String[prefix.size()];
       //rInstructions = prefix.toArray(rInstructions);
       //return rInstructions;
   }

   private void generateNaiveSuffix(String[] registers) {
       String[] tregisters = new String[] {
           "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"
       } ;
       int regnum = 0;
       //ArrayList<String> suffix = new ArrayList<>;
       for (String register : registers) {
           this.allocInst.add(String.format("sw %s, %d($fp)", tregisters[regnum], vRegisters.get(register)));
           regnum++;
       }
       /*String[] rInstructions = new String[suffix.size()];
       rInstructions = suffix.toArray(rInstructions);
       return rInstructions;*/
   }
}
