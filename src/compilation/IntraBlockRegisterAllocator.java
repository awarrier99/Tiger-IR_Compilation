package compilation;

import java.util.ArrayList;

public class IntraBlockRegisterAllocator implements RegisterAllocator {
    @Override
    public ArrayList<String> allocate(ArrayList<String> instructions) {
        return instructions;
    }
}
