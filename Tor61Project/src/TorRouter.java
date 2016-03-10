import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
	private Map<RouterTableKey,OutputStream> STREAMS; // <Socket, StreamID> map toa stream
	private int AGENT_ID;

	public TorRouter(ServerSocket socket, int agent_id) {
		SOCKET = socket;
		ROUTER = null;
		LISTENING = false;
		ROUTER_TABLE = new HashMap<RouterTableKey,RouterTableValue>();
		OPENER = new HashMap<Socket,Opener>();
		CONNECTIONS = new HashMap<Integer,Socket>();
		STREAMS = new HashMap<RouterTableKey,OutputStream>();
		AGENT_ID = agent_id;
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
		
		public ReadThread(Socket s) {
			this.READ_SOCKET = s;
		}
		
		public void run() {
			while (LISTENING) {
				
				BufferedReader in = null;
				char[] next_cell = new char[PACKAGE_SIZE];
				try {
					in = new BufferedReader(new InputStreamReader(READ_SOCKET.getInputStream()));
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when creating new buffered reader in read thread");
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
		private short cid;
		private short stream_id;
		private byte[] bytes;
		private RouterTableKey routing_key;
		private RouterTableKey stream_key;
		private int agent_id;

		public WriteThread(String command, Socket s, int cid, byte[] bytes) {
			this.socket = s;
			try {
				out = new DataOutputStream(s.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.command = command;
			this.cid = (short)cid;
			this.stream_id = TorCellConverter.getStreamID(bytes);
			this.bytes = bytes;
			this.routing_key = new RouterTableKey(socket, cid);
			this.stream_key = new RouterTableKey(socket, stream_id);
			this.agent_id = TorCellConverter.getExtendAgent(bytes);

		}
		
		public void run() {
			// If we are not the end of the circuit, forward to the next tor router
			if (ROUTER_TABLE.containsKey(routing_key) && ROUTER_TABLE.get(routing_key) != null) {
				RouterTableValue value = ROUTER_TABLE.get(routing_key);
				OutputStream next = value.getStream();
				int nextCID = value.getCID();
				try {
					next.write(TorCellConverter.updateCID(bytes,nextCID));
					next.flush();
				} catch (IOException e) {
					System.out.println("Error when 'forwarding' packets to next router in writethread");
				}
			// If we are the end of the circuit
			} else {
				switch (command) {
					case "open":
						try {
							if (TorCellConverter.getOpenee(bytes) == AGENT_ID) {
								OPENER.put(socket, new Opener(TorCellConverter.getOpener(bytes), AGENT_ID));
								out.write(TorCellConverter.getOpenedCell(bytes));
							} else {
								out.write(TorCellConverter.getOpenFailCell(bytes));
							}
						} catch (IOException e) {
							try {
								out.write(TorCellConverter.getOpenFailCell(bytes));
							} catch (IOException e2) {
								System.out.println("Error whenn sending open failed reply in write thread");
							}
							System.out.println("Error when sending opened reply in write thread");
						}
						break;
					// TODO
					case "opened":
						break;
					case "create":
						RouterTableKey key = new RouterTableKey(socket,cid);
						// If this cid is being used, reply with Create Cell Failed
						if (ROUTER_TABLE.containsKey(key)) {
							try {
								out.write(TorCellConverter.getCreateFailCell((short)cid));
							} catch (IOException e) {
								System.out.println("Error when sending create fail reply in write thread");
							}
						// Proceed to add the circuit to our router table
						} else {
							ROUTER_TABLE.put(new RouterTableKey(socket,cid),null);
							try {
								out.write(TorCellConverter.getCreatedCell((short)cid));
							} catch (IOException e) {
								System.out.println("Error when sending created reply in write thread");
							}
						}
						break;
					// TODO
					case "created":
						break;
					case "relay":
						handleRelayCase();
						break;
					default:
						throw new IllegalArgumentException("Invalid command in write thread: " + command);
				}
			}
		}
		
		// Handles the case where we receive a relay tor packet
		private void handleRelayCase() {
			String relay_type = TorCellConverter.getCellType(bytes);
			switch (relay_type) {
				case "begin":
					relayBegin();
					break;
				case "data":
					relayData();
				case "end":
					if (STREAMS.containsKey(stream_key))
						STREAMS.remove(stream_key);
					break;
				case "extend":
					relayExtend();
				default:
					throw new IllegalArgumentException("Invalid Relay Subcase in handleRelayCase: " + relay_type);
			}
		}
		
		// Handles creating a new TCP connection with destination
		private void relayBegin() {
			InetSocketAddress address = TorCellConverter.getDestination(bytes);
			Socket toDestination = null;
			try {
				toDestination = new Socket(address.getAddress(), address.getPort());
			} catch (IOException e) {
				List<byte[]> bytes_list = TorCellConverter.getRelayCells("begin failed", cid, stream_id, "");
				for (byte[] bs: bytes_list) {
					try {
						out.write(bs);
						out.flush();
					} catch (IOException e1) {
						e1.printStackTrace();
						System.out.println("Error when sending 'begin failed' in relayBegin in write thread");
					}
				}
			}
						
			// We should only be doing this if we are at the end and there is no previous stream
			assert(ROUTER_TABLE.containsKey(routing_key));
			assert(ROUTER_TABLE.get(routing_key) == null);
			assert(!STREAMS.containsKey(stream_key));
			
			// Insert into stream table source -> destination
			try {
				STREAMS.put(stream_key, new UnpackOutputStream(new DataOutputStream(toDestination.getOutputStream())));
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error when trying to add destination stream to router table in write thread");
			}
			
			RouterTableKey destToSourceKey = new RouterTableKey(toDestination, cid);
			
			if (!ROUTER_TABLE.containsKey(destToSourceKey)) {
				RouterTableValue destToSourceValue = new RouterTableValue(out, cid);
				
				// Insert into router table destination -> source
				ROUTER_TABLE.put(destToSourceKey, destToSourceValue);
			}
			
			// Reply with connected message
			List<byte[]> bytes_list = TorCellConverter.getRelayCells("connected", cid, stream_id, "");
			for (byte[] bs: bytes_list) {
				try {
					out.write(bs);
					out.flush();
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when sending 'connected' message back to source in write thread");
				}
			}
			
			// Since this thread is supposed to terminate anyways, we will instead use it to forever read
			// from this newly created socket and direct it to the begin source
			
			PackOutputStream packStream = new PackOutputStream(out, cid, stream_id);
            try {
				BufferedReader in = new BufferedReader(new InputStreamReader(toDestination.getInputStream()));
				while (STREAMS.containsKey(stream_key) && LISTENING) {
					packStream.write(in.read());
					packStream.flush();
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error when packing stream from destination, back to source in write thread");
			}  
            
            // Close streams
            try {
				packStream.close();
			} catch (IOException e) {
				System.out.println("Error when trying to close packStream in write thread");
			}
		}
		
		// Handles relaying data to existing stream to an existing destination
		private void relayData() {
			// If there already exists a stream with the designated stream id, send it there
			if (STREAMS.containsKey(stream_key)) {
				OutputStream toDestination = STREAMS.get(stream_key);
				try {
					toDestination.write(bytes);
					toDestination.flush();
				} catch (IOException e) {
					System.out.println("Error when trying to forward packets to destination in write thread");
				}
			} else {
				throw new IllegalArgumentException("Someone is trying to relay data to a stream that doesn't exist");
			}
		}
		
		// Handles dealing with a relayExtend command
		private void relayExtend() {
			assert(ROUTER_TABLE.containsKey(routing_key));
			assert(ROUTER_TABLE.get(routing_key) == null);
			InetSocketAddress address = TorCellConverter.getExtendDestination(bytes);
			Socket dest_socket = null;
			DataOutputStream dest_stream = null;
			short newCid = getNewCid(dest_socket);
			
			// If we already have a tcp connection to destination, use it
			if (CONNECTIONS.containsKey(agent_id)) {
				// Retreive existing socket
				dest_socket = CONNECTIONS.get(agent_id);
				try {
					dest_stream = new DataOutputStream(dest_socket.getOutputStream());
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when creating new DataOutputStream to an existing socket in relay extend in write thread");
				}

			// Otherwise, create a new tcp connection and do open protocol
			} else {
				// create a new socket
				try {
					dest_socket = new Socket(address.getAddress(), address.getPort());
				} catch (IOException e) {
					List<byte[]> bytes_list = TorCellConverter.getRelayCells("extend failed", cid, stream_id, "");
					for (byte[] bs: bytes_list) {
						try {
							out.write(bs);
							out.flush();
						} catch (IOException e1) {
							e1.printStackTrace();
							System.out.println("Error when sending 'extend failed' in relayExtend in write thread");
						}
					}
				}
				// send open packet
				try {
					dest_stream.write(TorCellConverter.getOpenCell(newCid, AGENT_ID, agent_id));
				} catch (IOException e) {
					try {
						for (byte[] bs: TorCellConverter.getRelayCells("extend failed", cid, stream_id, ""))
							out.write(bs);
					} catch (IOException e1) {
						System.out.println("Error when sending extend failed because exception in send open cell relay extend in write thread");
					}
				}
				
				// wait until we receive a opened packet
				// we will know if CONNECTIONS has a new entry: Agent_id, socket
				// we will wait up to 10 seconds
				try {
					dest_socket.setSoTimeout(10000);
					while (!CONNECTIONS.containsKey(agent_id)) {
						continue;
					}
					dest_socket.setSoTimeout(0); // Kill timer
					
					// Being here means that we successfully received a opened cell
				} catch (SocketException e) {
					// Failed to receive opened cell
					try {
						for (byte[] bs: TorCellConverter.getRelayCells("extend failed", cid, stream_id, ""))
							out.write(bs);
					} catch (IOException e1) {
						System.out.println("Timedout when waiting for opened cell in relay extend in write thread");
					}
				}
			}
			
			RouterTableKey newKey = new RouterTableKey(dest_socket,newCid);
			
			// Send existing tor router a create cell to make extend new circuit
			try {
				dest_stream.write(TorCellConverter.getCreateCell(newCid));
			} catch (IOException e) {
				System.out.println("Error sending a create cell in relayExtend in write thread");
				e.printStackTrace();
			}
			
			// If receive created, new routing table entry: Dest -> null should be added
			// Wait up to 10 seconds. If we don't get a created key, send create failed
			try {
				dest_socket.setSoTimeout(10000);
				while (!ROUTER_TABLE.containsKey(newKey)) {
					continue;
				}
				dest_socket.setSoTimeout(0); // Kill timer
				// Being here means we successfully received a created cell
				
				// update Dest -> null to Dest -> client
				assert(ROUTER_TABLE.get(newKey) == null);
				RouterTableValue newValueToClient = new RouterTableValue(out,cid);
				ROUTER_TABLE.put(newKey, newValueToClient);
				
				// update client -> null to client -> Dest
				assert(ROUTER_TABLE.get(routing_key) == null);
				RouterTableValue newValueToDest = new RouterTableValue(dest_stream,newCid);
				ROUTER_TABLE.put(routing_key, newValueToDest);
				
				// Send extended cell to client
				for (byte[] bs: TorCellConverter.getRelayCells("extended", cid, stream_id, "")) {
					try {
						out.write(bs);
						out.flush();
					} catch (IOException e) {
						System.out.println("Error when sending client extended cell in relayExtend in write thread");
					}
				}
			} catch (SocketException e) {
				// This means we timed out. Return create failed cell
				try {
					out.write(TorCellConverter.getCreateFailCell(cid));
				} catch (IOException e1) {
					System.out.println("Error when trying to send create failed cell in relayExtend in write thread");
				}
			}
		}
		
		// Finds a new cid not used between a specific socket
		private short getNewCid(Socket dest_socket) {
			// Must choose a new unique CID between existing tor router
			
			// If there is no existing connection, we are the opener
			boolean isOpener = true;
			// If there is an existing connection, check to see if we were the opener
			if (OPENER.containsKey(dest_socket))
				isOpener = OPENER.get(dest_socket).isOpener(agent_id);
			
			Random r = new Random();
			short newCid = (short)r.nextInt(Short.MAX_VALUE);
			if (isOpener && newCid % 2 == 0)
				newCid++;
			else if (!isOpener && newCid % 2 == 1)
				newCid++;
			RouterTableKey newKey = new RouterTableKey(dest_socket,newCid);
			while (ROUTER_TABLE.containsKey(newKey)) {
				newCid = (short)r.nextInt(Short.MAX_VALUE);
				if (isOpener && newCid % 2 == 0)
					newCid++;
				else if (!isOpener && newCid % 2 == 1)
					newCid++;
				newKey = new RouterTableKey(dest_socket,newCid);
			}
			return newCid;
		}
	}
}
