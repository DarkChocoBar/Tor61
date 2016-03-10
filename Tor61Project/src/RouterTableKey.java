import java.net.ServerSocket;

/**
 * 
 * @author Tyler
 * 
 * Inner class that is used as a key for the router table
 *
 */
public class RouterTableKey {
	
	public ServerSocket socket;
	public int circuit_id;
	
	public RouterTableKey(ServerSocket socket, int id) {
		this.socket = socket;
		circuit_id = id;
	}
	
	@Override
    public int hashCode() {
        return circuit_id + 31 * socket.hashCode();
    }
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof RouterTableKey) {
			RouterTableKey key = (RouterTableKey)o;
			return key.socket.equals(socket) && key.circuit_id == circuit_id;
		}
		return false;
	}
}