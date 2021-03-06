import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
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
	public static int readers = 1;
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
	
	public void printTables() {
		System.out.println("ROUTER_TABLE");
		for (RouterTableKey key: ROUTER_TABLE.keySet()) {
			System.out.println("\t"+key + " " + ROUTER_TABLE.get(key));
		}
		System.out.println("CONNECTIONS");
		for (Integer i: CONNECTIONS.keySet()) {
			System.out.println("\t"+i + " " + CONNECTIONS.get(i));
		}
		System.out.println("OPENER");
		for (Socket s: OPENER.keySet()) {
			System.out.println("\t"+OPENER.get(s));
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
					
					System.out.println("Tor Accepted New Connection at: " + s.getLocalPort() + " connected to: "+s.getPort());
					
					// Create new thread to handle receiving messages
					Thread read_thread = new ReadThread(s);
					read_thread.start();
					
				} catch (SocketException e) {
					System.out.println("SocketException when Tor Router is trying to create a new tcp connection");
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
			InputStream in = null;
			byte[] bytes = new byte[TorCellConverter.CELL_LENGTH];
			try {
				in = READ_SOCKET.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error when creating new buffered reader in read thread");
			}
			int readid = readers;
			readers++;
			while (LISTENING) {
				System.out.println("Tor " + readid + " Reader waiting for new command at: " + READ_SOCKET.getLocalPort());
				// Read the next 512 bytes (one tor cell)
				int total_read = 0;
				int read = 0;
				boolean none = false;
				while (total_read < PACKAGE_SIZE && read != -1 && !none) {
					try {
						read = in.read(bytes);
						System.out.println("Tor " + readid + " Reading");
						total_read += read;
					} catch (IOException e) {
						System.out.println("Error when reading from buffered 3");
					}
				}
				System.out.println("Tor " + readid + " Finished Reading");


				// pass next_cell into TorCellConverter and find out what the command was

				assert(bytes.length <= PACKAGE_SIZE); // MAKE SURE CONVERSION KEEPS IT AT PACKAGE_SIZE

				/* ************** For class only debugging******************* */
//				ByteBuffer bb = ByteBuffer.allocate(TorCellConverter.CELL_HEADER_SIZE);
//				bb.putShort((short) 10);
//				bb.put((byte) 3);
//				bb.putShort((short) 1);
//				bb.putShort((short) 0);			// 0x000 in header
//				bb.putInt(0);					// digest
//				bb.putShort((short) bytes.length);
//				bb.put((byte) 6);
//				byte[] header = bb.array();
//				bb.clear();
//				byte[] temp = bytes;
//				
//		        bytes = new byte[TorCellConverter.CELL_LENGTH];
//		        System.arraycopy(header, 0, bytes, 0, header.length);
//		        System.arraycopy(temp, 0, bytes, header.length, TorCellConverter.MAX_DATA_SIZE);
				/* ********************************* */
		        
				String command = TorCellConverter.getCellType(bytes);
				int cid = TorCellConverter.getCircuitId(bytes);
				System.out.println("Tor " + readid + " Received Command: "+command + " cid: " + cid);
				// Do something depending on the command
				switch (command) {
					case "open":
					case "create":
					case "relay":
						new WriteThread(command, READ_SOCKET, cid, bytes,readid).start();
						break;
					case "opened":
						int openee_id = TorCellConverter.getOpenee(bytes);
						OPENER.put(READ_SOCKET,new Opener(AGENT_ID,openee_id));
						assert(!CONNECTIONS.containsKey(openee_id));
						System.out.println("Tor " + readid + " Received Valid Opened Command");
	
						// Add new entry to CONNECTIONS with null value
						CONNECTIONS.put(TorCellConverter.getOpenee(bytes), null);
						printTables();
						break;
						// Add new entry to ROUTER_TABLE with null value
					case "created":
						ROUTER_TABLE.put(new RouterTableKey(READ_SOCKET,cid), null);
						System.out.println("Tor " + readid + " Received Valid Created Command");
						printTables();
						break;
					case "destroy":
						destroyConnection(cid);
						break;
					default:
						System.out.println("Command Was not recognized");
						System.exit(1);
						break;
				}
			}
			System.out.println("Preparing to quit read thread");
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
		//private int agent_id;
		
		public int readid; // used for debugging

		public WriteThread(String command, Socket s, int cid, byte[] bytes,int readid) {
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
			/*
			try {
				this.agent_id = TorCellConverter.getExtendAgent(bytes);
			} catch (ArrayIndexOutOfBoundsException e) {
			}*/
			this.readid=readid;
		}
		
		public void run() {
			// If we are not the end of the circuit, forward to the next tor router
			if (ROUTER_TABLE.containsKey(routing_key) && ROUTER_TABLE.get(routing_key) != null) {
//TODO
				System.out.println("Yay we're forwarding");
				System.out.println("Key: " + routing_key);
				RouterTableValue value = ROUTER_TABLE.get(routing_key);
				OutputStream next = value.getStream();
				int nextCID = value.getCID();
				try {
					//System.out.println(TorCellConverter.getCellType(bytes));
					//System.out.println(TorCellConverter.getRelaySubcellType(bytes));
					byte[] bs = TorCellConverter.updateCID(bytes,nextCID);
					//System.out.println(TorCellConverter.getCellType(bytes));
					//System.out.println(TorCellConverter.getRelaySubcellType(bytes));
					next.write(bs);
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
								System.out.println("Tor " + readid + " Received Valid Open Command");
								// Add new connection to CONNECTIONS
								OPENER.put(socket, new Opener(TorCellConverter.getOpener(bytes), AGENT_ID));
								CONNECTIONS.put(TorCellConverter.getOpener(bytes), socket);
								System.out.println("Tor " + readid + " Sending Opened Command to: " + socket.getPort());
								printTables();
								byte[] bs = TorCellConverter.getOpenedCell(bytes);
								//System.out.println(TorCellConverter.getCellType(bs));
								out.write(bs);
								out.flush();
								System.out.println("Tor " + readid + " Sent Opened Command");

							} else {
								System.out.println("AGENT_ID DID NOT MATCH IN OPEN COMMAND. OPEN FAILED");
								System.out.println("Agent: " + TorCellConverter.getOpenee(bytes));
								out.write(TorCellConverter.getOpenFailCell(bytes));
							}
						} catch (IOException e) {
							try {
								System.out.println("SOME KIND OF ERROR OCCURED WHEN PROCESSING OPEN COMMAND. OPEN FAILED");
								out.write(TorCellConverter.getOpenFailCell(bytes));
							} catch (IOException e2) {
								System.out.println("Error whenn sending open failed reply in write thread");
							}
							System.out.println("Error when sending opened reply in write thread");
						}
						break;
					case "create":
						RouterTableKey key = new RouterTableKey(socket,cid);
						// If this cid is being used, reply with Create Cell Failed
						if (ROUTER_TABLE.containsKey(key)) {
							try {
								System.out.println("Tor " + readid + " Received Invalid Create Command");

								out.write(TorCellConverter.getCreateFailCell((short)cid));
								System.out.println("Tor " + readid + " Sending Create Failed Command");

							} catch (IOException e) {
								System.out.println("Error when sending create fail reply in write thread");
							}
						// Proceed to add the circuit to our router table
						} else {
							System.out.println("Tor " + readid + " Received Create Command " + cid);
							System.out.println(TorCellConverter.getCircuitId(bytes));

							// Should add to router table and map to null. this indicates we're at the end of the circuit
							// Open should add things to CONNECTIONS
							ROUTER_TABLE.put(new RouterTableKey(socket,cid),null);
							printTables();
							try {
								byte[] bs = TorCellConverter.getCreatedCell((short)cid); 
								System.out.println(TorCellConverter.getCircuitId(bs));
								out.write(bs);
								System.out.println("Tor " + readid + " Sending Created Command to: " + socket.getPort() + " " + cid);
								System.out.println(TorCellConverter.getCircuitId(bytes));
							} catch (IOException e) {
								System.out.println("Error when sending created reply in write thread");
							}
						}
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
			String relay_type = TorCellConverter.getRelaySubcellType(bytes);
			System.out.println("Tor " + readid + " Received Relay " + relay_type);

			switch (relay_type) {
				case "begin":
					relayBegin();
					break;
				case "data":
					relayData();
					break;
				case "end":
					if (STREAMS.containsKey(stream_key))
						STREAMS.remove(stream_key);
					break;
				case "extend":
					relayExtend();
					break;
				default:
					throw new IllegalArgumentException("Invalid Relay Subcase in handleRelayCase: " + relay_type);
			}
		}
		
		// Handles creating a new TCP connection with destination
		private void relayBegin() {
			InetSocketAddress address = TorCellConverter.getExtendDestination(bytes);
			Socket toDestination = null;
			System.out.println("Tor " + readid + " trying to establish connection");
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
			
			System.out.println("Tor " + readid + " successfully established connection");

			RouterTableKey destToSourceKey = new RouterTableKey(toDestination, cid);
			
			if (!ROUTER_TABLE.containsKey(destToSourceKey)) {
				RouterTableValue destToSourceValue = new RouterTableValue(out, cid);
				
				// Insert into router table destination -> source
				ROUTER_TABLE.put(destToSourceKey, destToSourceValue);
				printTables();
			}
						
			System.out.println("Tor " + readid + " sending connected message");

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
			
			System.out.println("Tor " + readid + " sent connected message");

			
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
			int agent_id = TorCellConverter.getExtendAgent(bytes);

			printTables();

			Socket dest_socket = null;
			DataOutputStream dest_stream = null;
			short newCid = -1;

			System.out.println("Tor " + readid + " Checking to see if connection exists");
			// If we already have a tcp connection to destination, use it
			if (CONNECTIONS.containsKey(agent_id)) {
				System.out.println("Tor " + readid + " Found Existing connection");

				// Retreive existing socket
				dest_socket = CONNECTIONS.get(agent_id);
				try {
					dest_stream = new DataOutputStream(dest_socket.getOutputStream());
					newCid = getNewCid(dest_socket);
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when creating new DataOutputStream to an existing socket in relay extend in write thread");
				}

			// Otherwise, create a new tcp connection and do open protocol
			} else {
				System.out.println("Tor " + readid + " did not find existing connection");
				System.out.println("Tor " + readid + " creating new connection");


				// create a new socket
				try {
					dest_socket = new Socket();
					System.out.println("Tor trying to connect to address: " + address);
					dest_socket.connect(address);
					System.out.println("Tor connected to address: " + address + " from: " + dest_socket.getLocalPort());

					dest_stream = new DataOutputStream(dest_socket.getOutputStream());
					newCid = getNewCid(dest_socket);
				} catch (IOException e) {
					e.printStackTrace();
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
				System.out.println("Tor " + readid + " Created new connection");
				
				System.out.println("Tor " + readid + " Sending Open Packet To: " + dest_socket.getPort());

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
				System.out.println("Tor " + readid + " Waiting for opened packet from Another Tor");
				
				// wait until we receive a opened packet
				try {
					dest_socket.setSoTimeout(5000); // Set timer 5 seconds

					InputStream in = null;
					byte[] bytes = new byte[TorCellConverter.CELL_LENGTH];
					try {
						in = dest_socket.getInputStream();
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println("Error when creating new buffered reader in read thread");
					}
					int total_read = 0;
					int read = 0;
					boolean none = false;
					while (total_read < PACKAGE_SIZE && read != -1 && !none) {
						try {
							read = in.read(bytes);
							total_read += read;
						} catch (IOException e) {
							System.out.println("Error when reading from buffered 1");
						}
					}
					dest_socket.setSoTimeout(0); // Kill timer
					System.out.println("Tor " + readid + " Read something");
					
					if (!TorCellConverter.getCellType(bytes).equals("opened")) {
						System.out.println("Tor " + readid + " expected opened cell but got something else");
						System.out.println("Tor " + readid + " sending exted failed to" + socket.getPort());
						
						// Send extend failed cell to client
						for (byte[] bs: TorCellConverter.getRelayCells("extend failed", cid, stream_id, "")) {
							try {
								out.write(bs);
								out.flush();
							} catch (IOException e) {
								System.out.println("Error when sending client extend failed cell in relayExtend in write thread");
							}
						}
						return;
					} else {
						System.out.println("Tor " + readid + " received opened cell");
					}

					// Update connections dest_agent_id -> null to dest_agent_id -> dest_socket
					CONNECTIONS.put(agent_id,dest_socket);
					printTables();
					
				} catch (SocketException e) {
					// Failed to receive opened cell
					try {
						for (byte[] bs: TorCellConverter.getRelayCells("extend failed", cid, stream_id, ""))
							out.write(bs);
					} catch (IOException e1) {
						System.out.println("Timedout when waiting for opened cell in relay extend in write thread");
					}
					return;
				}
			}
						
			RouterTableKey newKey = new RouterTableKey(dest_socket,newCid);
			System.out.println("Tor " + readid + " Sending Create Packet to: " + dest_socket.getPort() + " " + newCid);

			// Send existing tor router a create cell to make extend new circuit
			try {
				byte[] bs = TorCellConverter.getCreateCell(newCid);
				System.out.println(TorCellConverter.getCircuitId(bs));
				dest_stream.write(bs);
			} catch (IOException e) {
				System.out.println("Error sending a create cell in relayExtend in write thread");
				e.printStackTrace();
			}
			System.out.println("Tor " + readid + " Waiting for Created Packet");

			// If receive created, new routing table entry: Dest -> null should be added
			// Wait up to 10 seconds. If we don't get a created key, send create failed
			try {
				dest_socket.setSoTimeout(5000); // Set timer 5 seconds

				InputStream in = null;
				byte[] bytes = new byte[TorCellConverter.CELL_LENGTH];
				try {
					in = dest_socket.getInputStream();
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when creating new buffered reader in read thread");
				}
				int total_read = 0;
				int read = 0;
				boolean none = false;
				while (total_read < PACKAGE_SIZE && read != -1 && !none) {
					try {
						read = in.read(bytes);
						total_read += read;
					} catch (IOException e) {
						System.out.println("Error when reading from buffered 2");
					}
				}
				System.out.println("Tor " + readid + " read something");
				System.out.println("Tor " + readid + " checking to see if recevied created cell");

				if (!TorCellConverter.getCellType(bytes).equals("created")) {
					System.out.println("Tor " + readid + " expected created cell but got something else");
					System.out.println("Tor " + readid + " sending exted failed to" + socket.getPort());
					return;
				} else {
					System.out.println("Tor " + readid + " received created cell " + TorCellConverter.getCircuitId(bytes));
				}
								
				dest_socket.setSoTimeout(0); // Kill timer
				System.out.println("newcid: " + newCid);
				System.out.println("cid: " + cid);
				System.out.println("created cid: " + TorCellConverter.getCircuitId(bytes));
				RouterTableValue newValueToClient = new RouterTableValue(out,cid);
				RouterTableValue newValueToDest = new RouterTableValue(dest_stream,newCid);

				// update client -> null to client -> Dest
				assert(ROUTER_TABLE.get(routing_key) == null);
				
				ROUTER_TABLE.put(newKey, newValueToClient);
				ROUTER_TABLE.put(routing_key, newValueToDest);
				
				printTables();
				
				System.out.println("Sending extended cell to: local: " + socket.getLocalPort() +" remote: "+socket.getPort() + " cid: " + cid);
				System.out.println("cid" + cid);
				// Send extended cell to client
				for (byte[] bs: TorCellConverter.getRelayCells("extended", cid, stream_id, "")) {
					try {
						out.write(bs);
						out.flush();
					} catch (IOException e) {
						System.out.println("Error when sending client extended cell in relayExtend in write thread");
					}
				}
				System.out.println("Sent extended cell");

			} catch (SocketException e) {
				// This means we timed out. Return create failed cell
				try {
					out.write(TorCellConverter.getCreateFailCell(cid));
				} catch (IOException e1) {
					System.out.println("Error when trying to send create failed cell in relayExtend in write thread");
				}
			}
			printTables();
			System.out.println("yay finished extend");
		}
		
		// Finds a new cid not used between a specific socket
		private short getNewCid(Socket dest_socket) {
			int agent_id = TorCellConverter.getExtendAgent(bytes);

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
