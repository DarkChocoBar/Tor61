import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class ATestTor61ProxyServer extends Thread{

	public static ServerSocket server = null;
	public static Socket tor_socket = null;
	public static Tor61ProxyServer ps = null;
	public static int anyport = 0;
	
	public static void main (String[] args) {
		ATestTor61ProxyServer t = new ATestTor61ProxyServer();
		try {
			server = new ServerSocket(0);
			anyport = server.getLocalPort();
			ServerThread st = new ServerThread(server);
			st.start();
			System.out.println("Started ServerThread");
			
			t.start();
			System.out.println("Started ProxyServerThread");

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run() {
		try {
			ps = new Tor61ProxyServer(10000,anyport,InetAddress.getLocalHost(),123456);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Back in test run");
		System.out.println("Testing Proxy start()");
		if (!ps.start())
			System.out.println("SHIT IT SHOULDNT BE FALSE BUT IT IS");
		System.out.println("Testing Proxy start() second time");

		if (ps.start()) {
			System.out.println("SHIT IT SHOULDNT BE TRUE BUT IT IS");
		}
		
		Entry e = null;
		try {
			e = new Entry(InetAddress.getLocalHost(),12345,88888888);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Testing extend");
		System.out.println("Starting Extend Thread");

		try {
			if (!ps.extend(e)) {
				System.out.println("Failed to extend");
			} else
				System.out.println("Test done extending");
		} catch (Exception e1) { 
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		System.out.println("Testing Proxy quit()");

		ps.quit();
		System.out.println("Done with tests yay!");
	}	
}

class ServerThread extends Thread {
	ServerSocket server = null;
	public ServerThread(ServerSocket s) {
		server = s;
	}
	public void run() {
		Socket s = null;
		System.out.println("Test about to accept");
		try {
			s = server.accept();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Test accepted");
		
		BufferedReader in = null;
		char[] next_cell = new char[512];
		try {
			in = new BufferedReader(new InputStreamReader(s.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when creating new buffered reader in read thread");
		}
		System.out.println("Test ready to read");

		// Read the next 512 bytes (one tor cell)
		int read = 0;
		while (read < 512) {
			try {
				read += in.read(next_cell,read,512 - read);
			} catch (IOException e) {
				System.out.println("Error when reading from buffered");
			}
		}
		System.out.println("Test finshed reading");

		// pass next_cell into TorCellConverter and find out what the command was
		byte[] data = new byte[TorCellConverter.CELL_LENGTH];
		try {
			data = new String(next_cell).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}

		System.out.println("Test checking to see if cell was open");

		// Confirm data is a create cell
		if (!TorCellConverter.getCellType(data).equals("open")) {
			System.out.println("WAS NOT A OPEN CELL");
			System.exit(1);
		}

		DataOutputStream stream = null;
		try {
			stream = new DataOutputStream(s.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Test about to send opened");
		try {
			stream.write(TorCellConverter.getOpenedCell(data));
			stream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Test waiting for created cell");

		// Read the next 512 bytes (one tor cell)
		read = 0;
		System.out.println("Test about to read something in test");

		while (read < 512) {
			try {
				read += in.read(next_cell,read,512 - read);
			} catch (IOException e) {
				System.out.println("Error when reading from buffered");
			}
		}
		System.out.println("Test finshed reading");

		// pass next_cell into TorCellConverter and find out what the command was
		data = new byte[TorCellConverter.CELL_LENGTH];
		try {
			data = new String(next_cell).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}

		// Confirm data is a create cell
		if (!TorCellConverter.getCellType(data).equals("create")) {
			System.out.println("WAS NOT A CREATE CELL");
			System.exit(1);
		}
			
		System.out.println("Test about to send back created cell");
		try {
			stream.write(TorCellConverter.getCreatedCell(TorCellConverter.getCircuitId(data)));
			stream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Test sent created cell");
		
		
		
		System.out.println("Testing Extend");
		
		
		System.out.println("Test about to read something in test");
		read = 0;
		next_cell = new char[512];
		while (read < 512) {
			try {
				read += in.read(next_cell,read,512 - read);
			} catch (IOException e) {
				System.out.println("Error when reading from buffered");
			}
		}
		System.out.println("Test finshed reading");

		// pass next_cell into TorCellConverter and find out what the command was
		data = new byte[TorCellConverter.CELL_LENGTH];
		try {
			data = new String(next_cell).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}

		System.out.println("Test checking if cell was a relay");
		// Confirm data is a relay
		if (!TorCellConverter.getCellType(data).equals("relay")) {
			System.out.println("WAS NOT A RELAY CELL");
			System.exit(1);
		}
		
		System.out.println("Test checking if cell was an extend");
		if (!TorCellConverter.getRelaySubcellType(data).equals("extend")) {
			System.out.println("WAS NOT A EXTEND CELL");
			System.exit(1);
		}
		
		System.out.println("Test sending relay extended");
		// Return relay extended
		
		for (byte[] bs: TorCellConverter.getRelayCells("extended", TorCellConverter.getCircuitId(data), TorCellConverter.getStreamID(data), ""))
			try {
				stream.write(bs);
				stream.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		System.out.println("Test finished sending extended");
		
		System.out.println("Test finished");
	}
}

