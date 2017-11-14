import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TypeCheck {

	public static void main(String[] args) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader("Data/Input.txt"));
		String s;
		while((s = br.readLine())!=null) {
			System.out.println(s);
		}
		br.close();
	}

}
