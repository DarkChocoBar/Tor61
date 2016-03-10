import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

// THINGS TO DO: 9
/**
 * Implement this
 */

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
	
	/**
	 * Writes to stream after upwrapping Tor Header
	 * @param b
	 */
	public void write(byte[] b) {
		
	}

	@Override
	public void write(int b) throws IOException {
		// TODO Auto-generated method stub
		
	}
}
