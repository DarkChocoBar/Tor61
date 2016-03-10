import java.io.DataOutputStream;


public class TestUnpackOutputStream {
	public static void main(String [] args) {
		try {
			DataOutputStream s = new DataOutputStream(System.out);
			s.writeByte(5);
			s.close();
		} catch (Exception e) {
			System.err.println("ooooops");
		}

//		UnpackOutputStream outputStream = new UnpackOutputStream(s);
//		
//		byte[] b = new byte[512];
//		b[14] = 1;
//		b[15] = 1;
//		b[16] = 1;
//		b[17] = 1;
//		b[18] = 1;
////		outputStream.write(b);
////		outputStream.flush();
////		outputStream.close();
//		s.writeInt(5);
//		s.flush();
//		s.close();
	}
}
