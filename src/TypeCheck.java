import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class TypeCheck {
	ArrayList<String[]> nameEquivalence = new ArrayList<>(), internalNameEquivalence = new ArrayList<>();
	/* nameEquivalence
	 * [a,b,c]
	 * [aa,bb]
	 */	
	
	HashMap<String,String> vars = new HashMap<>();
	/* All variables or functions in input file with their types
	 * Key   Val
	 * 
	 * a       int
	 * s       string
	 * pt1     ptr_int i.e. pointer to int
	 * pt2     ptr_struct foo
	 * pt3     ptr_ptr_int i.e. pointer to pointer to int
	 * q       array:int:4 i.e. array of type int, 1 dimention which has size 4
	 * aOfPtr  array:5:ptr_int i.e. array of pointer to int of size 5
	 * a2      array:4_9:struct foo i.e. array of type struct foo, 2 dimentions which have sizes 4 & 9 respectively 
	 * aa      struct foo i.e. aa is a var of type foo 
	 * f1      func:void: i.e. function with return type = void and no args
	 * square  func:float:struct foo_int i.e. function with return type = float, 2 args which are 'struct foo' and 'int' respectively
	 * func2   func:ptr_ptr_int:struct foo_float i.e. function with return type = ptr to ptr to int, 2 args which are 'struct foo' and 'float' respectively
	 */
	
	HashMap<String,String> structs = new HashMap<>();
	/*
	 * Key     Val
	 * foo     int:float
	 * bar     array_int_4:struct foo:ptr_int i.e. "struct bar{int a[4] ; struct foo b; int* c;};"
	 */
	
	public TypeCheck(String inputFile) throws IOException {
		parseFile(inputFile);
		findStructuralEquivalence();
	}
	
	// addVarType("int* *a,b[6][8];") --> add (a,ptr_ptr_int) and (b,array:int:6_8) to vars
	private void addVarType(String s) {
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
		 * 
		 * So, a and c have internal name equivalence
		 */
		HashMap<String,ArrayList<String>> arraysForInternalNameEquivalence = new HashMap<>();
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
			vars.put(v, hashMapValue);
			
			if(hashMapValue.startsWith("array")) {
				if(!arraysForInternalNameEquivalence.containsKey(hashMapValue))
					arraysForInternalNameEquivalence.put(hashMapValue, new ArrayList<>());
				arraysForInternalNameEquivalence.get(hashMapValue).add(v);
			}
		}
		for(ArrayList<String> INEArrays : arraysForInternalNameEquivalence.values()) {
			if(INEArrays.size()==1)	continue;
			internalNameEquivalence.add( (String[])INEArrays.toArray(new String[INEArrays.size()]) );
		}
	}
	
	// s = "struct foo{int a[10], int* b, struct bar br;};"
	private void addStructDef(String s) {

	}
	
	// s = "int square(int x, int y);"
	private void addFuncType(String s) {
		String returnType = "int";
		String funcName = "Square";
		String[] arguments = {"int","struct foo"};
		
		///////////////
		// REMAINING
		///////////////
		
		String funcType = "func:"+returnType+":";
		for(int i = 0; i < arguments.length; i++) {
			funcType+=arguments[i]+"_";
		}
		if(arguments.length>1)	funcType = funcType.substring(0, funcType.length()-1); // to remove last _
		vars.put(funcName, funcType);
	}
	
	private void printEquivalences() {
		System.out.println("Name Equivalence:");
		for(String[] sarr : nameEquivalence) {
			System.out.println(Arrays.toString(sarr));
		}
		
		System.out.println("\nInternal Name Equivalence:");
		for(String[] sarr : internalNameEquivalence) {
			System.out.println(Arrays.toString(sarr));
		}

		System.out.println("\nVariables");
		for(String tmp : vars.keySet()) {
			System.out.println(tmp+"-->"+vars.get(tmp));
		}
	}
	
	private void parseFile(String inputFile) throws IOException {
		//Input file should be syntactically correct
		BufferedReader br = new BufferedReader(new FileReader(inputFile));
		
		String s;
		while((s = br.readLine())!=null) {
			// "  struct  foo a, b , c,d ;  " --> "struct foo a,b,c,d;"
			s = s.trim(); // remove starting and trailing spaces
			s = s.replace(" +", " "); // replace multiple spaces by a single space
			s = s.replace(", ", ",").replace(" ,",",").replace("; ", ";").replace(" ;",";"); 
			if(s.length()==0)	continue;
			
			if(s.contains("(")) { // function prototype
				addFuncType(s);
			}else if(s.contains("struct") && (s.contains("{") || !s.contains(";"))) { // struct definition, can be single lined or multi lined
				//REMAINING for multi line struct definition
				addStructDef(s);
			}else { // var type i.e. int a[10], *b, c; OR struct foo a,b[100];
				addVarType(s);
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
		
	}
	
	public static void main(String[] args) throws IOException {
		TypeCheck tc = new TypeCheck("Data/Input.txt");
		tc.printEquivalences();
	}

}
