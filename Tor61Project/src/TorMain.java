import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class TorMain {

	private static int GROUP_NUMBER;
	private static int INSTANCE_NUMBER;
	private static int PROXY_PORT;
	private static int TOR_PORT;
	private static InetAddress TOR_ADDRESS;
	private static String ROUTER_STRING_NAME;
	private static RegistrationAgent AGENT;
	private static int CIRCUIT_SIZE = 4;

	public static void main(String[] args) {
		verify(args);
		
		///////////////////////////// Start Tor Router/////////////////////////////////////////
		
		ServerSocket tor_socket = null; // Socket the tor router will be using
		try {
			tor_socket = new ServerSocket(0);
		} catch (IOException e1) {
			System.out.println("Failed to create a server socket for tor router");
			System.exit(1);
		}
		TOR_PORT = tor_socket.getLocalPort();
		TOR_ADDRESS = tor_socket.getInetAddress();
		
		TorRouter tor_router = new TorRouter(tor_socket);
		
		if (!tor_router.start()) {
			System.out.println("Tor Router Failed to start");
			System.exit(1);
		} 
		System.out.println("Tor Router is Listening on port: " + TOR_PORT);
		///////////////////////////// Done Starting Tor Router/////////////////////////////////
				
		///////////////////////////// Initialize Agent ////////////////////////////////////////
		AGENT = new RegistrationAgent();
		///////////////////////////// Done Initializing Agent//////////////////////////////////

		///////////////////////////// Register Tor Router /////////////////////////////////////
		// Define service data to be: 32-bit value (xxxx << 16) | yyyy, i.e., the binary 
		// concatenation of the group and instance numbers.
		int serviceData = GROUP_NUMBER << 16 | INSTANCE_NUMBER;
		
		// Run agent, which registers our Tor Router with the well-known registration service
		System.out.println("About to register");

		boolean registered = AGENT.register(TOR_PORT, serviceData, ROUTER_STRING_NAME);
		
		if (!registered) {
			System.out.println("Failed to register Tor61 Router with Registration Service");
			System.exit(1);
		}
		System.out.println("Finished Register");
		///////////////////////////// Done Registering Tor Router ///////////////////////////// 

		// Wait for 1 second to allow other Tor Routers to finish registering
		try {
			Thread.sleep(1000);
		} catch (Exception e) {
			System.out.println("Exception when trying to wait for other Tor Routers to register");
		}

		///////////////////////////// Find Other Tor Routers ///////////////////////////////
		System.out.println("About to Fetch");

		List<Entry> entries = AGENT.fetch("Tor61Router-" + GROUP_NUMBER);
		System.out.println("Done Fetching");
		System.out.println("About to Print Contents of entires");
		if (entries == null)
			System.out.println("Entries was null.....");
		else {
			if (entries.isEmpty())
				System.out.println("There were no Tor Routers with designated prefix");
			else
				for (Entry e: entries)
					System.out.println(e.ip.getHostAddress() + " " + e.port + " " + e.serviceData);
		}
		System.out.println("Done");
		
		///////////////////////////// Done Finding Other Tor Routers ///////////////////////////

		///////////////////////////// Start Proxy Server ///////////////////////////////////////
		
		Tor61ProxyServer ps = new Tor61ProxyServer(PROXY_PORT, TOR_PORT, TOR_ADDRESS, serviceData);
		if (ps.start())
			System.out.println("Proxy Server Successfully Started");
		else
			System.out.println("Failed to start Proxy Server");
		
		///////////////////////////// Done Starting Proxy Server ///////////////////////////////

		///////////////////////////// Create Tor Circuit ///////////////////////////////////////
				
		// Must choose 4 random Routers from the list of found routers, and extend to them
		Random r = new Random();
		
		// Extend Circuit CIRCUIT_SIZE times
		int current_circuit_size = 0;
		while (current_circuit_size < CIRCUIT_SIZE) {
			System.out.println("Attempting to Extend Circuit...");
			// If number of entries = 3, choose a random index 0,1,2
			Entry e = entries.get(r.nextInt(entries.size()));
			
			// If we failed to extend, try again with another random router
			try {
				if (!ps.extend(e)) {
					System.out.println("We failed to extend circuit to entry: " + e);
				} else {
					System.out.println("Successfully Extended Circuit to: " + e);
					current_circuit_size++;
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			System.out.println("Current Circuit Size: " + current_circuit_size);
		}
		///////////////////////////// Done Creating Tor Circuit ////////////////////////////////
		
		// Keep running until user types 'q'
		Scanner scanner = new Scanner(System.in);
		String next = "";
		while (!next.equals("q")) {
			System.out.println("Please type 'q' to terminate");
			next = scanner.next();
			System.out.println("Your typed: " + next);
		}
			
		System.out.println("Received Terminate Command: Terminating Processes");
		
		System.out.println("About to terminate Agent");
		if (AGENT.quit())
			System.out.println("Agent Terminated");
			
		System.out.println("About to terminate Proxy Server");
		if (ps.quit())
			System.out.println("Proxy Server Terminated");
		
		scanner.close();
		System.out.println("Everything successfully Terminated");
		System.out.println("Good bye!");
		System.exit(0);
	}
	
	// Verifies Input Arguments and Sets Global Variables
	private static void verify(String[] args) {
		if (args.length != 3) {
			terminate();
		} else {
			try {
				GROUP_NUMBER = Integer.parseInt(args[0]);
				INSTANCE_NUMBER = Integer.parseInt(args[1]);
				PROXY_PORT = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				terminate();
			}
				
			if (GROUP_NUMBER <= 0 || GROUP_NUMBER > 9999)
				terminate();
				
			if (INSTANCE_NUMBER <= 0 || INSTANCE_NUMBER > 9999)
				terminate();
				
			if (PROXY_PORT < 1024 || PROXY_PORT > 49151)
				terminate();
			
			ROUTER_STRING_NAME = "Tor61Router-" + String.format("%04d",GROUP_NUMBER) + "-" +
			String.format("%04d",INSTANCE_NUMBER);
		}
	}

	private static void terminate() {
		System.out.println("Terminating. Goodbye");
		System.exit(1);
	}
}