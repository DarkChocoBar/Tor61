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
	
	private DataOutputStream stream;
	private short cid;
	private short stream_id;
	
	public PackOutputStream(DataOutputStream stream, short cid, short stream_id) {
		this.stream = stream;
		this.cid = cid;
		this.stream_id = stream_id;
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