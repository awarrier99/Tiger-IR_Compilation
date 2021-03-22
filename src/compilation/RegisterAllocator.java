package compilation;

import java.util.ArrayList;

public interface RegisterAllocator {
    ArrayList<String> allocate(ArrayList<String> instructions);
}
