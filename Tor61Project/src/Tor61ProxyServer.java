import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
			TOR_OUT_STREAM = new DataOutputStream(TOR_SOCKET.getOutputStream());
		} catch (IOException e) {
			System.out.println("Failed Creating a Socket with Tor Router at ip: " + TOR_ADDRESS + " and port: " + TOR_PORT);
			System.exit(1);
		}
		sendOpenAndCreateMessage();
		STREAMS = new HashMap<Short,UnpackOutputStream>();
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
            // Set Timer To 10 Minutes
            // If header is not processed within 5 seconds, assume client is dead
			TOR_SOCKET.setSoTimeout(5 * 1000);

			// Wait for the data get pushed into the BufferedReader, timeout after 5 secs
			long startTime = System.currentTimeMillis();
			while (!in.ready() && System.currentTimeMillis()-startTime <= 5 * 1000)
				TimeUnit.MICROSECONDS.sleep(100);
			
			ByteBuffer bb = ByteBuffer.allocate(TorCellConverter.CELL_LENGTH);
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				bb.put(line.getBytes());
			}
			byte[] data = bb.array();

			if (TorCellConverter.getCellType(data).equals("opened")) {
				out.write(TorCellConverter.getCreateCell(data));
				
				startTime = System.currentTimeMillis();
				// Wait for the data get pushed into the BufferedReader, timeout after 5 secs
				while (!in.ready() && System.currentTimeMillis()-startTime <= 5 * 1000)
					TimeUnit.MICROSECONDS.sleep(100);
				
				bb = ByteBuffer.allocate(TorCellConverter.CELL_LENGTH);
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					bb.put(line.getBytes());
				}
				data = bb.array();
				if (!TorCellConverter.getCellType(data).equals("created")) {
					throw new Exception("Tor61ProxyServer:sendOpenAndCreateMessage - "
							+ "Didn't receive created cell message");
				}	
			} else {
				throw new Exception("Tor61ProxyServer:sendOpenAndCreateMessage - "
						+ "Didn't receive opened cell message");
			}
		} catch (SocketException e) {
			System.out.println("Timed out waiting while sending open and create messages to Tor Router");
		} catch (IOException e) {
			System.out.println("Error when sending open and create messages to Tor Router");
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
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
    // Ideally, the Server should terminate within the next 20 seconds
    public boolean quit() {
    	LISTENING = false;
    	System.out.println("Proxy Server is Terminating. Please note that this operation can take up to 20 seconds");
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

        ByteBuffer bb = ByteBuffer.allocate(TorCellConverter.MAX_DATA_SIZE);
        bb.put(e.ip.getAddress());
        bb.put(":".getBytes());
        bb.putInt(e.port);
        bb.put((byte) 0);
        bb.putInt(e.serviceData);
        String extendData = bb.toString();
        bb.clear();

        ArrayList<byte[]> relayCells = TorCellConverter.getRelayCells("extend", CID, (short) 0, extendData);
        if (relayCells.size() != 1)
        	throw new Exception("Tor61ProxyServer:extend failed with wrong Relay cells created");
        out.write(relayCells.get(0));
		TOR_SOCKET.setSoTimeout(5 * 1000);

		// Wait for the data get pushed into the BufferedReader, timeout after 5 secs
		long startTime = System.currentTimeMillis();
		while (!in.ready() && System.currentTimeMillis()-startTime <= 5 * 1000)
			TimeUnit.MICROSECONDS.sleep(100);
		
		bb = ByteBuffer.allocate(TorCellConverter.CELL_LENGTH);
		for (String line = in.readLine(); line != null; line = in.readLine()) {
			bb.put(line.getBytes());
		}
		byte[] data = bb.array();

		if (TorCellConverter.getRelaySubcellType(data).equals("extended"))
			return true;
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
				System.out.println("Proxy listening on " + InetAddress.getLocalHost().getHostAddress() + ":" + PROXY_PORT);
			} catch (IOException e) {
				System.err.println("Could not listen on port: " + PROXY_PORT);
				System.exit(1);
			}
			
			// TODO
			/**
			 * Need to create a thread that listens to Tor_socket input stream
			 * Depending on what the stream id is on the incoming packet,
			 * write the the correct client socket in STREAMS
			 */
		
			while (LISTENING) {
				// Set timeout to be 20 seconds
				try {
					serverSocket.setSoTimeout(20000);
					Socket newClient = serverSocket.accept();
					short new_stream_id = getStreamID();
					
					// Each new thread listens to client, and sends all packets to tor router
					new Tor61ProxyThread(newClient, PROXY_PORT, new PackOutputStream(TOR_OUT_STREAM,CID,new_stream_id),CID,new_stream_id).start();
					
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
}