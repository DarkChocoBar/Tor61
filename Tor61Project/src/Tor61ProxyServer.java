import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Tor61ProxyServer {
	private int PROXY_PORT;
	private int TOR_PORT;
	private InetAddress TOR_ADDRESS;
	private boolean LISTENING;
	private ProxyServerThread SERVER;
	private Socket TOR_SOCKET;
	private DataOutputStream TOR_OUT_STREAM;
	private int TOR_SERVICE_DATA;
	private short CID;
	public static Map<Short,UnpackOutputStream> STREAMS;
	
	// Set proxy and tor ports
	public Tor61ProxyServer(int proxy_port, int tor_port, InetAddress address, int service_data) {
		this.PROXY_PORT = proxy_port;
		this.TOR_PORT = tor_port;
		this.TOR_ADDRESS = address;
		if (PROXY_PORT < 1024 || PROXY_PORT > 49151)
			terminate();
		SERVER = null;
		TOR_SERVICE_DATA = service_data;

		try {
			TOR_SOCKET = new Socket(TOR_ADDRESS, TOR_PORT);
			System.out.println("Tor Port at: " + TOR_PORT);
			System.out.println("Proxy Socket at: " + TOR_SOCKET.getLocalPort() + " connected to: " + TOR_SOCKET.getPort());
			TOR_OUT_STREAM = new DataOutputStream(TOR_SOCKET.getOutputStream());
		} catch (IOException e) {
			System.out.println("Failed Creating a Socket with Tor Router at ip: " + TOR_ADDRESS + " and port: " + TOR_PORT);
			System.exit(1);
		}
		sendOpenAndCreateMessage();
		STREAMS = new HashMap<Short,UnpackOutputStream>();
		System.out.println("FINISHED PROXY SERVER CONSTRUCTOR");
	}
	
	/**
	 * Send a open and create message to the new connection
	 */
	private void sendOpenAndCreateMessage() {
		Random r = new Random();
		CID = (short) r.nextInt(Short.MAX_VALUE);
		if (CID % 2 == 0)
			CID++;
      

        try {
        	DataOutputStream out = new DataOutputStream(TOR_SOCKET.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(TOR_SOCKET.getInputStream()));
            
            out.write(TorCellConverter.getOpenCell(CID, 0, TOR_SERVICE_DATA));
            out.flush();
            // Set Timer To 10 Minutes
            // If header is not processed within 5 seconds, assume client is dead
			TOR_SOCKET.setSoTimeout(5 * 1000);

			// Wait for the data get pushed into the BufferedReader, timeout after 5 secs
			long startTime = System.currentTimeMillis();
						
			// Read the next 512 bytes (one tor cell)
    		char[] next_cell = new char[512];

			int read = 0;
			boolean none = false;
			while (read < 512 && read != -1 && !none) {
				try {
					read = in.read(next_cell,read,512 - read);
					
					// just in case if this is eof
					int wait = 0;
					while (!in.ready() && wait < 5) {
						Thread.sleep(10);
						wait++;
					}
					none = wait==5 ? true : false;
				} catch (IOException e) {
					System.out.println("Error when reading from buffered");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// pass next_cell into TorCellConverter and find out what the command was
			byte[] data = new byte[TorCellConverter.CELL_LENGTH];
			try {
				data = new String(next_cell).getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}			
			
			TOR_SOCKET.setSoTimeout(0);
			if (TorCellConverter.getCellType(data).equals("opened")) {
				out.write(TorCellConverter.getCreateCell(CID));

				startTime = System.currentTimeMillis();
				// Wait for the data get pushed into the BufferedReader, timeout after 5 secs

				TOR_SOCKET.setSoTimeout(5000);

				while (!in.ready() && System.currentTimeMillis()-startTime <= 5 * 1000)
					TimeUnit.MICROSECONDS.sleep(100);

				//Read the next 512 bytes (one tor cell)
	    		next_cell = new char[512];

				read = 0;
				none = false;
				while (read < 512 && read != -1 && !none) {
					try {
						read = in.read(next_cell,read,512 - read);
						
						// just in case if this is eof
						int wait = 0;
						while (!in.ready() && wait < 5) {
							Thread.sleep(10);
							wait++;
						}
						none = wait==5 ? true : false;
					} catch (IOException e) {
						System.out.println("Error when reading from buffered");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				// pass next_cell into TorCellConverter and find out what the command was
				try {
					data = new String(next_cell).getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				TOR_SOCKET.setSoTimeout(0);


				if (!TorCellConverter.getCellType(data).equals("created")) {
					throw new Exception("Tor61ProxyServer:sendOpenAndCreateMessage - "
							+ "Didn't receive created cell message");
				}	
			} else if (TorCellConverter.getCellType(data).equals("open failed")){
				throw new Exception("Tor61ProxyServer:sendOpenAndCreateMessage - "
						+ "Received open failed");
			} else {
				throw new Exception("Tor61ProxyServer:sendOpenAndCreateMessage - "
						+ "Didn't receive opened cell message");
			}
		} catch (SocketException e) {
			System.out.println("Timed out waiting while sending open and create messages to Tor Router");
		} catch (IOException e) {
			System.out.println("Error when sending open and create messages to Tor Router");
			System.exit(1);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (Exception e) {
			System.out.println("Error in Tor61Proxy Server");
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Start up proxy service on designated port
	// Return true if successfully started, and false otherwise
    public boolean start() {
		if (!LISTENING && SERVER == null) {
			LISTENING = true;
			SERVER = new ProxyServerThread();
			SERVER.start();
			return true;
		} else {
			System.out.println("PROXY SERVER IS ALREADY RUNNING ON PORT: " + PROXY_PORT);
			return false;
		}
    }

    // Returns true when Server successfully terminates and false otherwise
    // Ideally, the Server should terminate within the next 10 seconds
    public boolean quit() {
    	LISTENING = false;
    	System.out.println("Proxy Server is Terminating. Please note that this operation can take up to 10 seconds");
    	try {
    		SERVER.join();
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		System.out.println("Interrupted when trying to quit in Proxy Server");
    		return false;
    	}
    	return true;
    }

    /**
     * Sends Tor Router Replay Extend Message to the address stored in Entry
     * @param e Entry object that stores the address of the next hop router
     * @return true if successful, false otherwise
     * @throws Exception 
     */
    public boolean extend(Entry e) throws Exception {    
    	DataOutputStream out = new DataOutputStream(TOR_SOCKET.getOutputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(TOR_SOCKET.getInputStream()));

        //TorCellConverter.getRelayCells expends an actual string like '199.123....:111\01234'
        /*
        ByteBuffer bb = ByteBuffer.allocate(TorCellConverter.MAX_DATA_SIZE);
        bb.put(e.ip.getAddress());
        bb.put(":".getBytes());
        bb.putInt(e.port);
        bb.put((byte) 0);
        bb.putInt(e.serviceData);
        String extendData = bb.toString();
        bb.clear();
        */

        String extendData = e.ip + ":" + e.port + '\0' + TOR_SERVICE_DATA;

        ArrayList<byte[]> relayCells = TorCellConverter.getRelayCells("extend", CID, (short) 0, extendData);
        if (relayCells.size() != 1)
        	throw new Exception("Tor61ProxyServer:extend failed with wrong Relay cells created");
        
        //System.out.println(TorCellConverter.getCellType(relayCells.get(0)));
        //System.out.println(TorCellConverter.getRelaySubcellType(relayCells.get(0)));

        out.write(relayCells.get(0));
        out.flush();

		TOR_SOCKET.setSoTimeout(5 * 1000);

		// Read the next 512 bytes (one tor cell)
		char[] next_cell = new char[512];
	System.out.println("about to read");
		int read = 0;
		boolean none = false;
		while (read < 512 && read != -1 && !none) {
			try {
				read = in.read(next_cell,read,512 - read);
				
				// just in case if this is eof
				int wait = 0;
				while (!in.ready() && wait < 5) {
					Thread.sleep(10);
					wait++;
				}
				none = wait==5 ? true : false;
			} catch (IOException e2) {
				System.out.println("Error when reading from buffered");
			} catch (InterruptedException e2) {
				e2.printStackTrace();
			}
		}
		System.out.println("done reading");

		// pass next_cell into TorCellConverter and find out what the command was
		byte[] data = new byte[TorCellConverter.CELL_LENGTH];
		try {
			data = new String(next_cell).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}
		System.out.println("checking what we read");

		if (TorCellConverter.getRelaySubcellType(data).equals("extended")) {
			return true;
		}
    	return false;
    }

	private void terminate() {
		System.err.println("Usage: <port number, ranges from 1024 to 49151>");
		System.err.println(PROXY_PORT + " is an invalid Port Number");
		System.exit(1);
	}
	
	public class ProxyServerThread extends Thread {
		public ProxyServerThread() {
		}
		
		public void run() {
			ServerSocket serverSocket = null;

			try {
				serverSocket = new ServerSocket(PROXY_PORT);
				System.out.println("Proxy listening on " + PROXY_PORT);
			} catch (IOException e) {
				System.err.println("Could not listen on port: " + PROXY_PORT);
				System.exit(1);
			}
			while (LISTENING) {
				// Set timeout to be 10 seconds
				try {
					serverSocket.setSoTimeout(10000);
					Socket newClient = serverSocket.accept();

					short new_stream_id = getStreamID();
					UnpackOutputStream output_stream = new UnpackOutputStream(new DataOutputStream(TOR_SOCKET.getOutputStream()));
					new TorInputThread(new_stream_id, output_stream).start();

					// Each new thread listens to client, and sends all packets to tor router
					new Tor61ProxyThread(newClient, new PackOutputStream(TOR_OUT_STREAM,CID,new_stream_id),CID,new_stream_id).start();

				} catch (SocketException e) {
					System.out.println("SocketException when trying to listen to Proxy Server");
					System.exit(1);
				} catch (IOException e) {
					continue;
				}
			}

			try {
				serverSocket.close();
			} catch (IOException e) {
				System.out.println("IOException: Proxy No Longer Listening, but failed to close serverSocket");
				System.exit(1);
			}

		}
	}
	
	// Finds an unused Stream ID
	private short getStreamID() {
		Random r = new Random();
		short stream_id = (short) (r.nextInt(Short.MAX_VALUE) + 1);
		while (STREAMS.containsKey(stream_id)) {
			stream_id = (short) (r.nextInt(Short.MAX_VALUE) + 1);
		}
		return stream_id;
	}
	
	public class TorInputThread extends Thread {
		private short stream_id;
		private UnpackOutputStream stream;

		public TorInputThread(short stream_id, UnpackOutputStream stream) {
			this.stream_id = stream_id;
			this.stream = stream;
		}
		
		@Override
		public void run() {			
			if (!STREAMS.containsKey(stream_id)) {
				STREAMS.put(stream_id, stream);
			}
		}
	}
}