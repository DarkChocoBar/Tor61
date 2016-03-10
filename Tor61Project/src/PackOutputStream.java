import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

	// THINGS TO DO: 10
	/**
	 * Implement this
	 */
	
/**
 * 
 * 
 * DataOutputStream wrapper class
 * Writes to stream after unwrapping Tor Header
 *
 */
public class PackOutputStream extends OutputStream {
	
	DataOutputStream stream;
	
	public PackOutputStream(DataOutputStream stream) {
		this.stream = stream;
	}
	
	/**
	 * Writes to stream after packing HTTP with Tor Header
	 * @param b
	 */
	public void write(byte[] b) {
		
	}

	@Override
	public void write(int b) throws IOException {
		// TODO Auto-generated method stub
		
	}
}