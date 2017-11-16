import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class TypeCheck {
	boolean[][] structuralEquivalenceMatrix;
	ArrayList<String[]> nameEquivalence = new ArrayList<>(), internalNameEquivalence = new ArrayList<>();
	/* nameEquivalence
	 * [a,b,c]
	 * [aa,bb]
	 */	
	
	LinkedHashMap<String,String> vars = new LinkedHashMap<>();
	/* All variables or functions in input file with their types
	 * Key     Val
	 * 
	 * a       int
	 * s       string
	 * pt1     ptr_int i.e. pointer to int
	 * pt2     ptr_struct foo
	 * pt3     ptr_ptr_int i.e. pointer to pointer to int
	 * aa      struct foo i.e. aa is a var of type foo 
	 * 
	 * ARRAY TRAMSFORMATION: array:<dimentions seperated by _>:<type>
	 * q       array:int:4 i.e. array of type int, 1 dimention which has size 4
	 * aOfPtr  array:5:ptr_int i.e. array of pointer to int of size 5
	 * a2      array:4_9:struct foo i.e. array of type struct foo, 2 dimentions which have sizes 4 & 9 respectively 
	 * 
	 * FUNTIOND TRANSFORMATION: func|<returnType>|<arguments seperated by !>
	 * f1      func|void| i.e. function with return type = void and no args
	 * square  func|float|struct foo!int i.e. function with return type = float, 2 args which are 'struct foo' and 'int' respectively
	 * func2   func|ptr_ptr_int|struct foo!float i.e. function with return type = ptr to ptr to int, 2 args which are 'struct foo' and 'float' respectively
	 */
	
	LinkedHashMap<String,String> structs = new LinkedHashMap<>();
	/*
	 * Key=name     Val=struct definition
	 * 
	 * STRUCT TRANSFORMATION: <types of variables seperated bty !>
	 * foo          int!float
	 * bar          array:4:int!int!struct foo!ptr_int i.e. "struct bar{int a[4],t ; struct foo b; int* c;};"
	 */
	
	public TypeCheck(String inputFile) throws IOException {
		parseFile(inputFile);
		findStructuralEquivalence();
	}
	
	// getVarType("int* *a,b[6][8];") returns (a,ptr_ptr_int) and (b,array:int:6_8)
	private LinkedHashMap<String,String> getVarType(String s, boolean findInternalNameEquivalences) {
		LinkedHashMap<String,String> tempVars = new LinkedHashMap<>();
		String type;
		int space;
		if(s.startsWith("struct")) // space is 2nd space
			space = s.indexOf( " " , s.indexOf(" ")+1 );
		else
			space = s.indexOf(" ");
		
		int firstStar = s.indexOf("*"); // -1 if not found
		if(firstStar==-1 || firstStar > space) { // s = "struct foo * a;" OR s = "int *a,b;" OR s = "int a,b;"
			type = s.substring(0, space);
			s = s.substring(space+1);
		}else { // s = "struct foo* a;" OR s = "int* a;" 
			type = s.substring(0, firstStar);
			s = s.substring(firstStar);
		}
		
		/*
		 * array:5:int     [a,c]
		 * array:10:int    [b]
		 * ptr_int         [x,y]
		 * So, a and c have internal name equivalence
		 *     x and y have internal name equivalence
		 */
		LinkedHashMap<String,ArrayList<String>> arraysAndPtrsForInternalNameEquivalence = new LinkedHashMap<>();
		String[] variables = s.replace(" ","").replace(";","").split(",");
		for(String v : variables) {
			String hashMapValue = "";
			if(v.contains("[")) { // array
				hashMapValue+="array:";
				String dims = v.substring(v.indexOf("["),v.lastIndexOf("]")+1);
				for(String d : dims.replace("[","").split("]")) {
					hashMapValue+=d+"_";
				}
				hashMapValue = hashMapValue.substring(0,hashMapValue.length()-1)+":";//remove last _ and append :
				v = v.substring(0,v.indexOf("[")); // **x[20][50] --> **x
			}
			
			if(v.contains("*")) {
				String copy = new String(v);
				int ptrs = copy.length() - copy.replace("*", "").length();
				for(int i = 0; i < ptrs; i++)	hashMapValue+="ptr_";
				v = v.substring(v.lastIndexOf("*")+1);
			}
			
			hashMapValue += type;
			tempVars.put(v, hashMapValue);
			
			if(hashMapValue.startsWith("array") || hashMapValue.startsWith("ptr")) {
				if(!arraysAndPtrsForInternalNameEquivalence.containsKey(hashMapValue))
					arraysAndPtrsForInternalNameEquivalence.put(hashMapValue, new ArrayList<>());
				arraysAndPtrsForInternalNameEquivalence.get(hashMapValue).add(v);
			}
		}
		
		if(findInternalNameEquivalences)
			for(ArrayList<String> INEArrays : arraysAndPtrsForInternalNameEquivalence.values()) {
				if(INEArrays.size()==1)	continue;
				internalNameEquivalence.add( (String[])INEArrays.toArray(new String[INEArrays.size()]) );
			}
		
		return tempVars;
	}
	
	// s = "struct foo{int a[10]; int* b; struct bar br;};"
	private void addStructDef(String s) {
		LinkedHashMap<String, String> tempVars;

		String structName = s.substring(0,s.indexOf("{")).trim(), structType="";
		s = s.substring(s.indexOf("{")+1,s.indexOf("}")).trim();
		
		String[] arguments = s.split(";");
		for(String arg : arguments) {
			tempVars = getVarType(arg, false);
			for(String v : tempVars.keySet()) {
				structType+=tempVars.get(v)+"!";
			}
		}
		structType = structType.substring(0,structType.length()-1);
		structs.put(structName, structType);
	}
	
	// s = "int square(int x, int y);" --> func:int:int_int
	private void addFuncType(String s) {
		LinkedHashMap<String, String> tempVars;
		s = s.replace(";","");
		String returnType="", funcName="";
		
		tempVars = getVarType(s.substring(0,s.indexOf("(")), false);
		if(tempVars.size()!=1) System.err.println("ERROR: FUNCTION RETURN TYPE FINDING");
		for(String v : tempVars.keySet()) {
			funcName = v;
			returnType = tempVars.get(v);
		}

		String[] arguments = s.substring(s.indexOf("(")+1,s.indexOf(")")).split(",");
		
		String funcType = "func|"+returnType+"|";
		for(int i = 0; i < arguments.length; i++) {
			tempVars = getVarType(arguments[i], false);
			if(tempVars.size()!=1)	System.err.println("ERROR: FUNCTION ARGUMENT TYPE FINDING");
			for(String v : tempVars.keySet()) {
				funcType+=tempVars.get(v)+"!";
			}
		}
		if(arguments.length>0)	funcType = funcType.substring(0, funcType.length()-1); // to remove last !
		vars.put(funcName, funcType);
	}
	
	private void print() {
		System.out.println("Structs:");
		for(String tmp : structs.keySet()) {
			System.out.println(tmp+"-->"+structs.get(tmp));
		}
		
		System.out.println("\nName Equivalence:");
		for(String[] sarr : nameEquivalence) {
			System.out.println(Arrays.toString(sarr));
		}
		
		System.out.println("\nInternal Name Equivalence:");
		for(String[] sarr : internalNameEquivalence) {
			System.out.println(Arrays.toString(sarr));
		}
		
		System.out.println("\nVariables:");
		int index = 0;
		for(String tmp : vars.keySet()) {
			System.out.printf("%-3d: %-20s --> %s\n",index++, tmp, vars.get(tmp));
		}
		
		System.out.println("\nStructural Equivalence Matrix:");
		for(int i = -1; i < index; i++)		System.out.printf("%-3d|",i);
		System.out.println();
		for(int i = 0; i < index; i++) {
			System.out.printf("%-3d|",i);	
			for (int j = 0; j < index; j++)		System.out.print(structuralEquivalenceMatrix[i][j]?" T |":"   |");
			System.out.println();
		}
	}
	
	private void parseFile(String inputFile) throws IOException {
		//Input file should be syntactically correct
		BufferedReader br = new BufferedReader(new FileReader(inputFile));
		
		String s;
		while((s = br.readLine())!=null) {
			// "  struct  foo a, b , c,d ;  " --> "struct foo a,b,c,d;"
			s = s.trim(); // remove starting and trailing spaces
			s = s.replaceAll(" +", " "); // replace multiple spaces by a single space
			s = s.replace(", ", ",").replace(" ,",",").replace("; ", ";").replace(" ;",";"); 
			s = s.replace(" (","(").replace("( ", "(").replace(" )",")").replace(") ", ")").trim(); 
			if(s.length()==0)	continue;
			
			if(s.contains("(")) { // function prototype
				addFuncType(s);
			}else if(s.contains("struct") && (s.contains("{") || !s.contains(";"))) { // struct definition, can be single lined or multi lined
				if(!s.contains("}")) {
					do {
						String next = br.readLine().trim();
						s+=next;
					}while(!s.contains("}"));
				}
				addStructDef(s);
			}else { // var type i.e. int a[10], *b, c; OR struct foo a,b[100];
				LinkedHashMap<String,String> tempVars = getVarType(s, true);
				for(String v : tempVars.keySet())	vars.put(v, tempVars.get(v));
			}
		}
		br.close();
		
		HashSet<String> types = new HashSet<>(); // contains all types except arrays, ptrs and functions
		for(String t : vars.values()) {
			if(types.contains(t))	continue;
			if(t.startsWith("array") || t.startsWith("func") || t.startsWith("ptr"))	continue;
			types.add(t);
		}
		for(String t : types) {
			ArrayList<String> al = new ArrayList<>();
			for(String key : vars.keySet()) {
				if(vars.get(key).equals(t))	al.add(key);
			}
			String[] sarr = (String[]) al.toArray(new String[al.size()]);
			nameEquivalence.add(sarr);
			internalNameEquivalence.add(sarr);
		}
		
	}
	
	private void findStructuralEquivalence(){
		LinkedHashMap<Integer,String> intToVar = new LinkedHashMap<>();
		int index = 0;	
		for(String type : vars.values())	intToVar.put(index++, type);
		
		structuralEquivalenceMatrix = new boolean[index][index];
		for(int i = 0; i < index; i++)	Arrays.fill(structuralEquivalenceMatrix[i],true);
		
		for(int i = 0; i < index; i++) {
			for(int j = i+1; j < index; j++) {
				structuralEquivalenceMatrix[i][j] = intToVar.get(i).equals(intToVar.get(j));
				structuralEquivalenceMatrix[j][i] = structuralEquivalenceMatrix[i][j];
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		TypeCheck tc = new TypeCheck("Data/Input.txt");
		tc.print();
	}

}
