import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * 
 * DataOutputStream wrapper class
 * Writes to stream after unwrapping Tor Header
 *
 */
public class UnpackOutputStream extends OutputStream{
	
	DataOutputStream stream;
	
	public UnpackOutputStream(DataOutputStream stream) {
		this.stream = stream;
	}

	@Override
	public void write(int b) throws IOException {
		stream.writeInt(b);
	}
	
	public void write(byte[] b) throws IOException {
		byte[] httpReq = Arrays.copyOfRange(b, TorCellConverter.CELL_HEADER_SIZE, TorCellConverter.CELL_LENGTH);
		System.out.println(httpReq.toString());
		stream.write(httpReq);
	}
}
