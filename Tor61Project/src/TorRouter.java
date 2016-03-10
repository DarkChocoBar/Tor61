import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
	
	private boolean LISTENING;									// Class constant used to kill all threads
	private Map<RouterTableKey,RouterTableValue> ROUTER_TABLE; 	// Tells us where to forward TOR packets
	private Map<Socket,Opener> OPENER;			// Stores opener, openee relationship of a socket
	private Map<Integer,Socket> CONNECTIONS; 	// Maps Router ID to socket. Only 1 socket per router
	private static final int PACKAGE_SIZE = 512;

	public TorRouter(ServerSocket socket) {
		SOCKET = socket;
		ROUTER = null;
		LISTENING = false;
		ROUTER_TABLE = new HashMap<RouterTableKey,RouterTableValue>();
		OPENER = new HashMap<Socket,Opener>();
		CONNECTIONS = new HashMap<Integer,Socket>();
	}
	
	/**
	 * Starts the tor router if it is not already started
	 * @return returns true if successfully started, and false otherwise
	 */
	public boolean start() {
		if (!LISTENING && ROUTER == null) {
			LISTENING = true;
			ROUTER = new TorRouterThread(SOCKET);
			ROUTER.start();
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
		private ServerSocket ROUTER_SOCKET;
		
		public TorRouterThread(ServerSocket socket) {
			this.ROUTER_SOCKET = socket;	
		}
		
		public void run() {
			while (LISTENING) {
				// Set timeout to be 20 seconds
				try {
					ROUTER_SOCKET.setSoTimeout(20000);
					
					Socket s = ROUTER_SOCKET.accept();
					ROUTER_SOCKET.setSoTimeout(0); // Kill the timer
					
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
				ROUTER_SOCKET.close();
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
		
		private Socket READ_SOCKET;
		private BufferedReader in;
		
		public ReadThread(Socket s) {
			this.READ_SOCKET = s;
			try {
				this.in = new BufferedReader(new InputStreamReader(READ_SOCKET.getInputStream()));
			} catch (IOException e) {
				System.out.println("Error Creating Buffered Reader when constructing new ReadThread");
				System.exit(1);
			}
		}
		
		public void run() {
			while (LISTENING) {
				
				BufferedReader in = null;
				char[] next_cell = new char[PACKAGE_SIZE];
				try {
					in = new BufferedReader(new InputStreamReader(READ_SOCKET.getInputStream()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				// Read the next 512 bytes (one tor cell)
				int read = 0;
				while (read < PACKAGE_SIZE) {
					try {
						in.read(next_cell,read,PACKAGE_SIZE - read);
					} catch (IOException e) {
						System.out.println("Error when reading from buffered");
					}
				}
				
				// pass next_cell into TorCellConverter and find out what the command was
				byte[] bytes = next_cell.toString().getBytes();
				
				assert(bytes.length == PACKAGE_SIZE); // MAKE SURE CONVERSION KEEPS IT AT PACKAGE_SIZE
				
				String command = TorCellConverter.getCellType(bytes);
				int cid = TorCellConverter.getCircuitId(bytes);
				
				// Do something depending on the command
				switch (command) {
					case "open":
					case "create":
					case "relay":
						new WriteThread(command, READ_SOCKET, cid, bytes).start();
					case "destroy":
						destroyConnection(cid);
						break;
					default:
						break;
				}
			}
			
			// Being here means that we are no longer LISTENING, and we want to quit
			prepareToQuit();
			
			try {
				READ_SOCKET.close();
			} catch (IOException e) {
				System.out.println("IOException: ReadThread no longer listening, but failed to close socket");
			}
		}
		
		/**
		 * Remove this circuit from routing table
		 * @param cid
		 */
		private void destroyConnection(int cid) {
			RouterTableKey key = new RouterTableKey(READ_SOCKET,cid);
			RouterTableValue value = ROUTER_TABLE.get(key);
			try {
				value.getStream().close();
			} catch (IOException e) {
				System.out.println("Error when trying to close Stream when we received a destroy cell");
			}
			ROUTER_TABLE.remove(key);
		}
		
		private void prepareToQuit() {
			// Send Destroy messages to everyone
			for (RouterTableKey key: ROUTER_TABLE.keySet()) {
				OutputStream s = ROUTER_TABLE.get(key).getStream();
				if (s != null) {
					try {
						s.write(TorCellConverter.getDestoryCell((short)key.circuit_id));
						s.flush();
						s.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			// Close all sockets
			for (Integer key: CONNECTIONS.keySet()) {
				try {
					CONNECTIONS.get(key).close();
				} catch (IOException e) {
					System.out.println("Failed to close socket when preparing to quit read thread");
				}
			}
		}
	}
	
	/**
	 * 
	 * @author Tyler
	 * 
	 * Writes appropriate messages to a designated Streams
	 *
	 */
	private class WriteThread extends Thread {
		
		private Socket socket;
		private DataOutputStream out; // Stream to whoever sent us this command
		private String command;
		private int cid;
		private byte[] bytes;

		public WriteThread(String command, Socket s, int cid, byte[] bytes) {
			this.socket = s;
			try {
				out = new DataOutputStream(s.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.command = command;
			this.cid = cid;
			this.bytes = bytes;
		}
		
		public void run() {
			switch (command) {
				case "open":
					try {
						out.write(TorCellConverter.getOpenedCell(bytes));
					} catch (IOException e) {
						System.out.println("Error when sending opened reply in write thread");
					}
					break;
				case "create":
					try {
						out.write(TorCellConverter.getCreatedCell((short)cid));
					} catch (IOException e) {
						System.out.println("Error when sending created reply in write thread");
					}
					ROUTER_TABLE.put(new RouterTableKey(socket,cid),null);
					break;
				case "relay":
					
					break;
				default:
					break;
			}
		}
	}
}
