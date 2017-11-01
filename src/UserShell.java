import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Scanner;

public class UserShell {
	public static void main(String[] args){
		IO_System io = new IO_System();
		FileSystem fs = new FileSystem(io);
		PrintWriter pw = null;
		Scanner sc = null;
		boolean inited = false;

		try {
			pw = new PrintWriter("D:/76679882.txt");
			sc = new Scanner(new FileReader("D:/input.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		String input = null, command = null, arg1 = null;
		String [] parsed_input;
		byte [] tempBuffer;
		int OFTindex = -1, count = -1, status = -1;
		
		while (sc.hasNextLine() && (input = sc.nextLine()) != null){
			parsed_input = input.trim	().split("\\s+");
			command = parsed_input[0];
			try {
				switch (command) {
				case "cr":  //create
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					arg1 = parsed_input[1];
					status = fs.create(arg1);
					if (status > 0){
						System.out.println(arg1 + " created");
						pw.println(arg1 + " created");
					} else {
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "de":  //destroy
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					arg1 = parsed_input[1];
					status = fs.destroy(arg1);
					if (status > 0){
						System.out.println(arg1 + " destroyed");
						pw.format(arg1 + " destroyed");
					}
					else{
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "op":  //open
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					arg1 = parsed_input[1];
					status = fs.open(arg1);
					if (status > 0){
						System.out.println(arg1 + " opened " + status);
						pw.println(arg1 + " opened " + status);
					}
					else {
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "cl":  //close -- need to test
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					OFTindex = Integer.parseInt(parsed_input[1]);
					status = fs.close(OFTindex);
					if (status > 0){
						System.out.println(status + " closed");
						pw.println(status + " closed");
					}
					else {
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "rd":  //read
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					OFTindex = Integer.parseInt(parsed_input[1]);
					count = Integer.parseInt(parsed_input[2]);
					tempBuffer = new byte[count]; 
					status = fs.read(OFTindex, tempBuffer, count);
					String printout = new String(tempBuffer);
					if (status > 0){
						System.out.println(printout);
						pw.println(printout);
					}
					else{
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "wr":  //write
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					OFTindex = Integer.parseInt(parsed_input[1]);
					tempBuffer = parsed_input[2].getBytes();
					count = Integer.parseInt(parsed_input[3]); 
					status = fs.write(OFTindex, tempBuffer, count);
					if (status > 0){
						System.out.println(status + " bytes written");
						pw.println(status + " bytes written");
					}
					else {
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				case "sk":  //lseek
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					OFTindex = Integer.parseInt(parsed_input[1]);
					int newPos = Integer.parseInt(parsed_input[2]);
					status = fs.lseek(OFTindex, newPos);
					if (status > 0){
						System.out.println("position is " + newPos);
						pw.println("position is " + newPos);
					}
					else {
						System.out.println("Error");
						pw.println("Error");
					}
					break;
				//special outputs
				case "dr":  //directory -- need to test
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					for (String s: fs.directory()){
						System.out.format("%s\t", s);
						pw.format("%s\t", s);
					}
					System.out.println();
					pw.println();
					break;
				case "in":  //init
					if (parsed_input.length == 2){
						fs.init(parsed_input[1]); //init at fileName
						System.out.println("disk restored");
						pw.println("disk restored");
					} else if (parsed_input.length == 1){
						fs.init("");
						System.out.println("disk initialized");
						pw.println("disk initialized");
					}
					inited = true;
					break;
				case "sv":  //save
					if (!inited){
						System.out.println("Error");
						pw.println("Error");
						break;
					}
					fs.save(parsed_input[1]);
					System.out.println("disk saved");
					pw.println("disk saved");
					break;
				default: 
					System.out.println();
					pw.println();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		sc.close();
		pw.close();
	}
}
