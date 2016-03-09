import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
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
	
	private boolean LISTENING;
	private Map<RouterTableKey,DataOutputStream> ROUTER_TABLE;

	public TorRouter(ServerSocket socket) {
		SOCKET = socket;
		ROUTER = null;
		LISTENING = false;
		ROUTER_TABLE = new HashMap<RouterTableKey,DataOutputStream>();
	}
	
	/**
	 * Starts the tor router if it is not already started
	 * @return returns true if successfully started, and false otherwise
	 */
	public boolean start() {
		if (!LISTENING && ROUTER == null) {
			LISTENING = true;
			ROUTER = new TorRouterThread(SOCKET);
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
    	System.out.println("Tor Router is Terminating. Please note that this operation can take up to 20 seconds");
    	if (ROUTER == null) {
    		System.out.println("ROUTER was null. Router never started");
    		return false;
    	}
    	if (!LISTENING) {
    		System.out.println("LISTENING was false. Router never started");
    		return false;
    	}
    	LISTENING = false;
    	
    	try {
    		System.out.println("Attemping to Join Router...");
    		ROUTER.join();
    		System.out.println("Join Router Success!");
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
	private class TorRouterThread extends Thread {
		private ServerSocket SOCKET;
		
		public TorRouterThread(ServerSocket socket) {
			this.SOCKET = socket;	
		}
		
		public void run() {
			while (LISTENING) {
				// Set timeout to be 20 seconds
				try {
					SOCKET.setSoTimeout(20000);
					
					Socket s = SOCKET.accept();
					SOCKET.setSoTimeout(0); // Kill the timer
					
					// Create new thread to handle receiving messages
					Thread read_thread = new ReadThread(s);
					read_thread.start();
					
				} catch (SocketException e) {
					System.out.println("SocketException when Tor Router is trying to create a bew tcp connection");
					System.exit(1);
				} catch (IOException e) {
					// Socket Timeout Exceptions are caught here
					// This is used to allow the thread to check if we are still LISTENING
					continue;
				}
			}
			// Being here means that we are no longer LISTENING, and we want to quit
			try {
				SOCKET.close();
			} catch (IOException e) {
				System.out.println("IOException: Tor Router no longer listening, but failed to close socket");
			}
		}
	}
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * ReadThread reads incoming Tor Messages and decides how to handle each message
	 * ReadThread continuously reads until either an end message it received, or we stop Listening
	 *
	 */
	private class ReadThread extends Thread {
		
		private Socket SOCKET;
		private BufferedReader in;
		
		public ReadThread(Socket s) {
			this.SOCKET = s;
			try {
				this.in = new BufferedReader(new InputStreamReader(SOCKET.getInputStream()));
			} catch (IOException e) {
				System.out.println("Error Creating Buffered Reader when constructing new ReadThread");
				System.exit(1);
			}
		}
		
		public void run() {
			while (LISTENING) {
				// THINGS TO DO: 1
				// Read first 3 bytes in buffer (in)
				/* 1. 0x0000 0x05 means open
				 * 		Read 8 more bytes and confirm our name
				 * 		Return an opened/open failed reply
				 * 2. Circ ID 0x01 means create new circuit
				 * 		add Socket,CircID pair to RouterTable
				 * 		return Created Message
				 * 3. Circ ID 0x04 means destroy this circuit
				 * 		remove this circuit from RouterTable
				 * 4. Circ ID 0x03 means relay
				 * 		Check RouterTable and relay, or if no entry, read rest of message
				 * 
				 * MAKE SURE TO NOT DO ANY BLOCKING PROCEDURES
				 * MAKE MORE THREADS TO HANDLE JOBS AS NESSISARY
				 */
			}
			
			// THINGS TO DO: 2
			// Send Destroy/End Messages to everyone
			
			// Being here means that we are no longer LISTENING, and we want to quit
			try {
				SOCKET.close();
			} catch (IOException e) {
				System.out.println("IOException: ReadThread no longer listening, but failed to close socket");
			}
		}
	}
	
	// THINGS TO DO: 3
	// Implement WriteThread to handle all blocking procedures we don't want to do in ReadThread
	// WriteThread will most likely take more input arguments or something to determine what to write
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * Writes appropriate messages to a designated Streams
	 *
	 */
	private class WriteThread extends Thread {
		
		private DataOutputStream out;

		public WriteThread(RouterTableKey key) {
			out = ROUTER_TABLE.get(key);
		}
		
		public void run() {
			
		}
	}
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * Inner class that is used as a key for the router table
	 *
	 */
	private class RouterTableKey {
		
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