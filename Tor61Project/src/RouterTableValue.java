import java.io.OutputStream;

/**
 * 
 * Inner class that is used as a value for the router table
 *
 */
public class RouterTableValue {
	
	private OutputStream stream;
	private int circuit_id;
	
	public RouterTableValue(OutputStream stream, int id) {
		this.stream = stream;
		circuit_id = id;
	}
	
	/**
	 * return the data output stream
	 * @return data output stream
	 */
	public OutputStream getStream() {
		return stream;
	}
	
	/**
	 * return the circuit id
	 * @return the circuit id
	 */
	public int getCID() {
		return circuit_id;
	}
	
	@Override
	public String toString() {
		return " Value Cid: " + circuit_id;
	}
}