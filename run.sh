INPUT=$1
OUTPUT=$2
ALLOCATOR=$3

mkdir build
find src -name "*.java" > sources.txt
javac -d build @sources.txt
java -cp ./build compilation.Compiler "$INPUT" "$OUTPUT" "$ALLOCATOR"