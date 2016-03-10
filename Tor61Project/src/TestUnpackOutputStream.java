import java.io.DataOutputStream;


public class TestUnpackOutputStream {
	public static void main(String [] args) {
		byte[] b = getBytes();
		System.out.println(b.length);
		try {
			DataOutputStream s = new DataOutputStream(System.out);
			UnpackOutputStream outputStream = new UnpackOutputStream(s);
			outputStream.write(b);
			outputStream.close();
		} catch (Exception e) {
			System.err.println("ooooops");
		}
	}
	
	private static byte[] getBytes() {
		byte[] header = new byte[TorCellConverter.CELL_HEADER_SIZE];
		byte[] body = "hello world".getBytes();		
		byte[] combined = new byte[header.length + body.length];

		System.arraycopy(header,0,combined,0         ,header.length);
		System.arraycopy(body,0,combined,TorCellConverter.CELL_HEADER_SIZE,body.length);
		return combined; 
	}
}
