import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Scanner;

public class TypeCheck {
	boolean[][] structuralEquivalenceMatrix, structEqMatrix;
	LinkedHashMap<String, Integer> varToInt = new LinkedHashMap<>(); // for index in structuralEquivalenceMatrix
	
	LinkedHashMap<String, String> typeDefReplacements = new LinkedHashMap<>();
	/*
	 * To replace Key with Value in vars for structural name equivalence
	 * Key      Value
	 * PtrInt   ptr_int
	 * cm		int
	 * Node		ptr_struct node
	 */
	
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
	 * q       array:4:int i.e. array of type int, 1 dimention which has size 4
	 * aOfPtr  array:5:ptr_int i.e. array of pointer to int of size 5
	 * a2      array:4_9:struct foo i.e. array of type struct foo, 2 dimentions which have sizes 4 & 9 respectively 
	 * 
	 * FUNCTION TRANSFORMATION: func|<returnType>|<arguments seperated by !>
	 * f1      func|void| i.e. function with return type = void and no args
	 * square  func|float|struct foo!int i.e. function with return type = float, 2 args which are 'struct foo' and 'int' respectively
	 * func2   func|ptr_ptr_int|struct foo!float i.e. function with return type = ptr to ptr to int, 2 args which are 'struct foo' and 'float' respectively
	 */
	LinkedHashMap<String, String > modifiedVars = new LinkedHashMap<>(); //After replacing typedefs and equivalent stucts by same struct name
	LinkedHashMap<String,String> structs = new LinkedHashMap<>();
	/*
	 * Key=name     Val=struct definition
	 * 
	 * STRUCT TRANSFORMATION: <types of variables seperated bty !>
	 * struct foo          int!float
	 * struct bar          array:4:int!int!struct foo!ptr_int i.e. "struct bar{int a[4],t ; struct foo b; int* c;};"
	 */
	
	// CONSTRUCTOR
	public TypeCheck(String inputFile) throws IOException {
		parseFile(inputFile);
		findStructuralEquivalence();
	}
	
	// getVarType("int* *a,b[6][8];") returns (a,ptr_ptr_int) and (b,array:6_8:int)
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
	
	// s = "struct foo{int a[10]; int* b,c; struct bar br;};"
	private void addStructDef(String s) {
		LinkedHashMap<String, String> tempVars;

		String structName = s.substring(0,s.indexOf("{")).trim(), structType="";
		s = s.substring(s.indexOf("{")+1,s.indexOf("}")).trim();
		
		String[] arguments = s.split(";");
		for(String arg : arguments) {
			tempVars = getVarType(arg, false);
			for(String v : tempVars.keySet()) {
				structType+=getModifiedType(tempVars.get(v))+"!";
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
		String modifiedReturnType="";
		
		tempVars = getVarType(s.substring(0,s.indexOf("(")), false);
		if(tempVars.size()!=1) System.err.println("ERROR: FUNCTION RETURN TYPE FINDING");
		for(String v : tempVars.keySet()) {
			funcName = v;
			returnType = tempVars.get(v);
			modifiedReturnType = getModifiedType(returnType);
		}
		String funcType = "func|"+returnType+"|";
		String modifiedFuncType = "func|"+modifiedReturnType+"|";
		String argStr = s.substring(s.indexOf("(")+1,s.indexOf(")"));
		if(argStr.length()>0) {
			String[] arguments = argStr.split(",");
			for(int i = 0; i < arguments.length; i++) {
				tempVars = getVarType(arguments[i], false);
				if(tempVars.size()!=1)	System.err.println("ERROR: FUNCTION ARGUMENT TYPE FINDING");
				for(String v : tempVars.keySet()) {
					funcType+=tempVars.get(v)+"!";
					modifiedFuncType+=getModifiedType(tempVars.get(v))+"!";
				}
			}
			if(arguments.length>0) {
				funcType = funcType.substring(0, funcType.length()-1); // to remove last !
				modifiedFuncType = modifiedFuncType.substring(0, modifiedFuncType.length()-1); // to remove last !
			}
		}
		vars.put(funcName, funcType);
		modifiedVars.put(funcName, modifiedFuncType);
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

			if(s.contains("typedef")) { // s = "typedef int* PtrInt" --> substring from index 8 and replace PtrInt by ptr_int in vars
				s = s.substring(8);
				//int* PtrInt --> will return (PtrInt, ptr_int)
				LinkedHashMap<String, String> typedef = getVarType(s, false);
				if(typedef.size()!=1) {
					System.err.println("ERROR PARSING TYPEDEF");
					System.exit(1);
				}
				String key="", val="";
				for(String k : typedef.keySet()) {
					key = k;
					val = typedef.get(k);
				}
				/*
				 * typedef struct node N;
				 * typedef N* N2; 
				 * Therefore store (N2,ptr_struct node) instead of (N2,ptr_N) in typedefReplacements
				 */
				for(String k : typeDefReplacements.keySet()) {
					if(val.endsWith(k)) {
						val = val.substring(0,val.length()-k.length())+typeDefReplacements.get(k);
					}
				}
				typeDefReplacements.put(key,val);
			}else if(s.contains("(")) { // function prototype
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
				for(String v : tempVars.keySet()) {
					vars.put(v, tempVars.get(v));
					modifiedVars.put(v, getModifiedType(tempVars.get(v)));
				}
			}
		}
		br.close();
		
		HashSet<String> types = new HashSet<>(); // contains all types except arrays, ptrs and functions
		for(String t : modifiedVars.values()) {
			if(types.contains(t))	continue;
			if(t.startsWith("array") || t.startsWith("func") || t.startsWith("ptr"))	continue;
			types.add(t);
		}
		for(String t : types) {
			ArrayList<String> al = new ArrayList<>();
			for(String key : modifiedVars.keySet()) {
				if(modifiedVars.get(key).equals(t))	al.add(key);
			}
			String[] sarr = (String[]) al.toArray(new String[al.size()]);
			if(sarr.length == 1)	continue;
			nameEquivalence.add(sarr);
			internalNameEquivalence.add(sarr);
		}		
	}
	
	// String after replacing typedefs
	private String getModifiedType(String s) {
		for(String key : typeDefReplacements.keySet()) {
			if(s.endsWith(key))	return s.substring(0, s.length()-key.length())+typeDefReplacements.get(key);
		}
		return s;
	}
	
	private void findStructuralEquivalence(){			
		// STEP 1: FIND STRUCTURALLY EQUIVALENT STRUCTS AND REPLACE THEM WITH ONE STRUCT
		LinkedHashMap<String,Integer> typeToInt = new LinkedHashMap<>();
		LinkedHashMap<Integer,String> intToType = new LinkedHashMap<>();
		int totalStructs = 0;
		for(String struct : structs.keySet()) {
			typeToInt.put(struct, totalStructs);
			intToType.put(totalStructs, struct);
			totalStructs++;
		}
		
		structEqMatrix = new boolean[totalStructs][totalStructs];
		for(int i = 0; i < totalStructs; i++)	Arrays.fill(structEqMatrix[i], true);
		boolean changes = true;
		while(changes) {
			changes = false;
			for (int i = 0; i < totalStructs; i++) {
				for (int j = i+1; j < totalStructs; j++) {
					if(structEqMatrix[i][j] == false)	continue;
					String s1 = structs.get(intToType.get(i)), s2 = structs.get(intToType.get(j));
					String[] tokens1 = s1.split("!"), tokens2 = s2.split("!");
					if(tokens1.length != tokens2.length) {
						structEqMatrix[i][j] = false;
						changes = true;
						continue;
					}
					for(int k = 0; k < tokens1.length; k++) {
						String t1 = tokens1[k], t2 = tokens2[k];
						if(t1.equals(t2))	continue;
						if(t1.contains("struct") && t2.contains("struct")) {
							/*
							 * CASE 1: "array:6:ptr_struct foo" and "array:4:ptr_struct cat"
							 * CASE 2: "array:4:ptr_struct foo" and "array:4:ptr_struct cat"
							 */		
							int idx1 = t1.indexOf("struct"), idx2 = t2.indexOf("struct");
							if(!t1.substring(0, idx1).equals(t2.substring(0, idx2))) { // CASE 1
								structEqMatrix[i][j] = false;
								changes = true;
								break;
							}else { // CASE 2
								t1 = t1.substring(idx1); 
								t2 = t2.substring(idx2);
								// t1 = struct foo, t2 = struct cat
								if(structEqMatrix[typeToInt.get(t1)][typeToInt.get(t2)] == false) {
									structEqMatrix[i][j] = false;
									changes = true;
									break;
								}
							}
						}else {
							structEqMatrix[i][j] = false;
							changes = true;
							break;
						}
					}
				}
			}
		}
		for(int i = 0; i < totalStructs; i++)
			for(int j = 0; j < i; j++)	structEqMatrix[i][j] = structEqMatrix[j][i];
		
		boolean[] marked = new boolean[totalStructs];
		for(int i = 0; i < totalStructs; i++) {
			if(marked[i])	continue;
			marked[i] = true;
			String s1 = intToType.get(i); 
			for(int j = i+1; j < totalStructs; j++) {
				if(marked[j])	continue;
				if(structEqMatrix[i][j]) {
					marked[j] = true;
					String s2 = intToType.get(j);
					for(String v : modifiedVars.keySet()) {
						modifiedVars.put(v,modifiedVars.get(v).replace(s2, s1));
					}
				}
			}
		}
		
		//STEP 2: FIND STRUCTURAL EQUIVALENCE MATRIX
		intToType = new LinkedHashMap<>();
		int totalVars = 0;	
		for(String type : modifiedVars.values())	intToType.put(totalVars++, type);
		
		totalVars = 0;	
		for(String v : modifiedVars.keySet())	varToInt.put(v,totalVars++);
		
		structuralEquivalenceMatrix = new boolean[totalVars][totalVars];
		for(int i = 0; i < totalVars; i++)	Arrays.fill(structuralEquivalenceMatrix[i],true);
		
		for(int i = 0; i < totalVars; i++) {
			for(int j = i+1; j < totalVars; j++) {
				structuralEquivalenceMatrix[i][j] = intToType.get(i).equals(intToType.get(j));
				structuralEquivalenceMatrix[j][i] = structuralEquivalenceMatrix[i][j];
			}
		}
	}
	
	private void print() {
		System.out.println("Typedefs:");
		int totalTypedefs = 0;
		for(String tmp : typeDefReplacements.keySet()) {
			System.out.printf("%-3d: %-20s --> %s\n",totalTypedefs++, tmp, typeDefReplacements.get(tmp));
		}
		
		System.out.println("\nStructs:");
		int totalStructs = 0;
		for(String tmp : structs.keySet()) {
			System.out.printf("%-3d: %-20s --> %s\n",totalStructs++, tmp, structs.get(tmp));
		}
		
		for(int i = -1; i < totalStructs; i++)		System.out.printf("%-3d|",i);
		System.out.println();
		for(int i = 0; i < totalStructs; i++) {
			System.out.printf("%-3d|",i);	
			for (int j = 0; j < totalStructs; j++)		System.out.print(structEqMatrix[i][j]?" T |":"   |");
			System.out.println();
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
		int totalVars = 0;
		for(String tmp : vars.keySet()) {
			System.out.printf("%-3d: %-20s --> %s\n",totalVars++, tmp, vars.get(tmp));
		}
		
		System.out.println("\nStructural Equivalence Matrix:");
		for(int i = -1; i < totalVars; i++)		System.out.printf("%-3d|",i);
		System.out.println();
		for(int i = 0; i < totalVars; i++) {
			System.out.printf("%-3d|",i);	
			for (int j = 0; j < totalVars; j++)		System.out.print(structuralEquivalenceMatrix[i][j]?" T |":"   |");
			System.out.println();
		}
		
	}
	
	//array, func, ptr, struct or basic
	private String getType(String t) {
		if(t.startsWith("array"))	return "array";
		if(t.startsWith("func"))	return "func";
		if(t.contains("ptr"))		return "ptr";
		if(t.contains("struct"))	return "struct";
		return "basic";
	}
	
	/*
	 * Two variables are given and check if v1 = v2 assignment statement is valid or not
	 * Rules:
	 * 1. Name equivalence is used for basic data types.
	 * 2. Structural equivalence is used for structures.
	 * 3. Name equivalence is used for arrays.
	 */
	private void checkTypeEquivalences(String v1, String v2) { 
		if(!modifiedVars.containsKey(v1) || !modifiedVars.containsKey(v2)) {
			System.out.println("Undefined Variables");
			return;
		}
		String t1 = getType(modifiedVars.get(v1)), t2 = getType(modifiedVars.get(v2));
		System.out.print(t1+" "+t2+": ");
		if(t1.equals("func") || t2.equals("func")) {
			System.out.println("INVALID");
			return;
		}
		
		if(!t1.equals(t2)) 	System.out.println("INVALID");
		else {
			if(t1.equals("struct")) { // STRUCTURAL EQUIVALENCE
				System.out.println(structuralEquivalenceMatrix[varToInt.get(v1)][varToInt.get(v2)]?"VALID":"INVALID");
			}else { // NAME EQUIVALENCE
				for(String[] nameEq : nameEquivalence) { // check if both in same name equivalence class
					boolean found1 = false, found2 = false;
					for(int i = 0; i < nameEq.length; i++) {
						if(nameEq[i].equals(v1))	found1 = true;
						else if(nameEq[i].equals(v2))	found2 = true;
					}
					if(found1 || found2) {
						System.out.println(found1 && found2 ? "VALID" : "INVALID");
						return;
					}
				}
				System.out.println("INVALID");
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		TypeCheck tc = new TypeCheck("Data/Input.txt");
		tc.print();
		
		System.out.println("\nEnter two variables seperated by ',' to check their equivalence. Enter 'DONE' when over");
		Scanner sc = new Scanner(System.in);
		while(true) { // Loop until "DONE" found
			String v = sc.nextLine(); // Input 2 variables seperated by ',' Example: a,b
			if(v.equals("DONE"))	break;
			v = v.replace(" ","");
			String[] variables = v.split(",");
			if(variables.length!=2)	break;
			tc.checkTypeEquivalences(variables[0],variables[1]);
		}
		sc.close();
	}
}
