import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author Tyler
 * 
 * User interface for Tor Router. Allows user to start and stop the Router
 *
 */
public class TorRouter {
	private ServerSocket SOCKET;
	private TorRouterThread ROUTER;
	
	protected boolean LISTENING;
	protected Map<RouterTableKey,Integer> router_table;

	public TorRouter(ServerSocket socket) {
		SOCKET = socket;
		LISTENING = false;
		router_table = new HashMap<RouterTableKey,Integer>();
	}
	
	/**
	 * Starts the tor router if it is not already started
	 * @return returns true if successfully started, and false otherwise
	 */
	public boolean start() {
		if (!LISTENING) {
			LISTENING = true;
			return true;
		} else {
			System.out.println("Tor Router is already listening");
			return false;
		}
	}
	
	/**
	 * Closes the Tor Router
	 * @return true if successfully close the application, and false otherwise
	 */
	public boolean quit() {
    	LISTENING = false;
    	System.out.println("Tor Router is Terminating. Please note that this operation can take up to 20 seconds");
    	try {
    		ROUTER.join();
    		return true;
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		System.out.println("Interrupted when trying to quit in Tor Router");
    		return false;
    	}
    }
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * TorRouterThread Listens to incoming tcp connections, and handles it accordingly
	 *
	 */
	public class TorRouterThread extends Thread {
		private ServerSocket SOCKET;
		
		public TorRouterThread(ServerSocket socket) {
			this.SOCKET = socket;	
		}
		
		public void run() {
			while (LISTENING) {
				// Set timeout to be 20 seconds
				try {
					SOCKET.setSoTimeout(20000);
					// do something here
				} catch (SocketException e) {
					System.out.println("SocketException when Tor Router is trying to create a bew tcp connection");
					System.exit(1);
				} catch (IOException e) {
					continue;
				}
			}
			try {
				SOCKET.close();
			} catch (IOException e) {
				System.out.println("IOException: Tor Router no longer listening, but failed to close socket");
				System.exit(1);
			}
		}
	}
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * Inner class that creates a key for the router table
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
}
