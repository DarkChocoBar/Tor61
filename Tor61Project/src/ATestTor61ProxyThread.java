import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;


public class ATestTor61ProxyThread {
	
	public static Socket server = null;
	public static Socket tor = null;
	public static int port = 22222;
	public static int tor_port = 11111;
	public static boolean done = false;
	
	public static void main(String[] args) throws InterruptedException, UnknownHostException, IOException {
		/** TESTING StreamThread **/
//		DataOutputStream stream = new DataOutputStream(System.out);
//		PackOutputStream out = new PackOutputStream(stream, (short) 3, (short) 2);
//		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
//		StreamThread st = new StreamThread(in, out);
//		st.start();
		
		/** TESTING Tor61ProxyThread **/
		System.out.println("Main creating serveraccepter");
		ServerAccepter st = new ServerAccepter();
		st.start();
		System.out.println("Main ran serveraccepter");

		Socket client = new Socket(InetAddress.getLocalHost(),port);
		
		// Wait until the socket accepts
		System.out.println("Main waiting for acceptor to accept");

		while (server == null)
			continue;
		System.out.println("Main confirmed acceptor accepted");
		
		System.out.println("Main starting TorAccepter");
		TorAccepter ta = new TorAccepter();
		ta.start();
		
		// This is never used...
		Socket tor_thing = new Socket(InetAddress.getLocalHost(),tor_port);
		BufferedReader infromtor = null;
		try {
			infromtor = new BufferedReader(new InputStreamReader(tor_thing.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when creating new buffered reader in read thread");
		}
		
		
		System.out.println("Main started TorAccepter");
		System.out.println("Main waiting for TorAccepter to accept");

		while(tor == null)
			continue;
		System.out.println("Main confirmed TorAccepter accepted");

		
		PackOutputStream to_tor = new PackOutputStream(new DataOutputStream(tor.getOutputStream()), (short) 3, (short) 2);
		
		short cid = 4;
		short stream_id = 5;

		System.out.println("Main creating proxythread");

		Tor61ProxyThread tpt = new Tor61ProxyThread(server, to_tor, cid, stream_id);
		tpt.start();
		System.out.println("Main ran proxy thread");

		
		// This tor streamthread echos whatever it receives
		BufferedReader tor_in = null;
		try {
			tor_in = new BufferedReader(new InputStreamReader(tor.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when creating new buffered reader in read thread");
		}
		System.out.println("Main creating echo");

		Streamer echo = new Streamer(tor_in,server.getOutputStream());
		echo.start();
		System.out.println("Main running echo");

		
		// Things sent from out as HTTP should be received by tor_reader as TOR
		// TOR will echo content back at client
		// Anything sent from out will be read back in in
		DataOutputStream out = new DataOutputStream(client.getOutputStream());
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when creating new buffered reader in read thread");
		}

		System.out.println("Main creating spit");

		SpitItOut spit = new SpitItOut(infromtor);
		spit.start();
		System.out.println("Main started spit");
		System.out.println("Main going to wait for 1 seconds for stuff to run");

		Thread.sleep(1000);
		System.out.println("Main done waiting");

		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		Thread.sleep(1000);

		System.out.println("Trying again");
		out.flush();
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.write("GET / HTTP/1.1 \r\nHost: www.example.com\r\nConnection: close\r\n\r\n".getBytes());
		out.flush();

		System.out.println("Main sent http request");
		
		System.out.println("Main going to wait for 1 seconds just in case");

		Thread.sleep(5000);
		System.out.println("Main done waiting second time");

		done = true;
		Thread.sleep(5000);
		System.out.println("Yay we are done");

		System.exit(0);
		
	}
	
	
	
}

// This is the response the client gets from the server (web page info)
class SpitItOut extends Thread {
	public BufferedReader br;
	public SpitItOut(BufferedReader br) {
		this.br = br;
	}
	public void run() {
		System.out.println("Spititout running");
		while (!ATestTor61ProxyThread.done) {
			try {
				String line = br.readLine();
				if (line != null)
					System.out.println(br.readLine());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
			}
		}
		System.out.println("Spititout quitting");

	}
}

// This simulates the end server. For now, we are just echoing whatever we received
class Streamer extends Thread {
	private BufferedReader in;
	private OutputStream out;
	
	public Streamer(BufferedReader in, OutputStream out) {
		this.in = in;
		this.out = out;
	}
	
	public void run() {
		System.out.println("Running Stream thread");
		while (!ATestTor61ProxyThread.done) {
			try {
				String next;
				if ((next = in.readLine()) != null) {
					System.out.println("EndServer: " + next);
					out.write(next.getBytes());
				} 
			} catch (IOException e) {
				System.out.println("Some error in StreamThread");
			}
		}
		System.out.println("Done Stream thread");
	}
}

class ServerAccepter extends Thread {
	public ServerAccepter() {
		System.out.println("ServerAccepter constructor");
	}
	public void run() {
		System.out.println("ServerAccepter running");
		ServerSocket server = null;
		try {
			server = new ServerSocket(ATestTor61ProxyThread.port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("ServerAccepter created server socket");
		System.out.println("ServerAccepter waiting for accept");

		try {
			ATestTor61ProxyThread.server = server.accept();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("ServerAccepter accepted");


	}
}

class TorAccepter extends Thread {
	public TorAccepter() {
		System.out.println("TorAccepter constructor");
	}
	public void run() {
		System.out.println("TorAccepter running");
		ServerSocket server = null;
		try {
			server = new ServerSocket(ATestTor61ProxyThread.tor_port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("TorAccepter created server socket");
		System.out.println("TorAccepter waiting for accept");

		try {
			ATestTor61ProxyThread.tor = server.accept();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("TorAccepter accepted");


	}
}

