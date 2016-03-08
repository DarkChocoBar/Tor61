import java.util.List;
import java.util.Scanner;

public class TorMain {

	private static int GROUP_NUMBER;
	private static int INSTANCE_NUMBER;
	private static int PROXY_PORT;
	private static int TOR_PORT = 13579; // THIS IS HARD CODED FOR NOW
	private static String ROUTER_STRING_NAME;
	private static RegistrationAgent AGENT;

	public static void main(String[] args) {
		verify(args);
		
		///////////////////////////// Create Tor Router////////////////////////////////////////

		///////////////////////////// Done Creating Tor Router/////////////////////////////////
				
		///////////////////////////// Initialize Agent ////////////////////////////////////////
		AGENT = new RegistrationAgent();
		///////////////////////////// Done Initializing Agent//////////////////////////////////

		///////////////////////////// Register Tor Router /////////////////////////////////////
		// Define service data to be: 32-bit value (xxxx << 16) | yyyy, i.e., the binary 
		// concatenation of the group and instance numbers.
		long serviceData = GROUP_NUMBER << 16 | INSTANCE_NUMBER;
		
		// Run agent, which registers our Tor Router with the well-known registration service
		System.out.println("About to register");

		boolean registered = AGENT.register("r " + TOR_PORT + " " + serviceData + " " + ROUTER_STRING_NAME);
		
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

		///////////////////////////// Finding Other Tor Routers ///////////////////////////////
		System.out.println("About to Fetch");

		List<Entry> entries = AGENT.fetch("f Tor61Router-" + GROUP_NUMBER);
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
		
		///////////////////////////// Create Tor Circuit ///////////////////////////////////////
		
		
		
		///////////////////////////// Done Creating Tor Circuit ////////////////////////////////

		///////////////////////////// Start Proxy Server ///////////////////////////////////////
		
		Tor61ProxyServer ps = new Tor61ProxyServer(PROXY_PORT, TOR_PORT);
		if (ps.start())
			System.out.println("Proxy Server Successfully Started");
		else
			System.out.println("Failed to start Proxy Server");
		
		///////////////////////////// Done Starting Proxy Server ///////////////////////////////

		
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
	
	// Notifies Client of Usage Conditions
	private static void usage() {
		System.out.println("Usage: <group number, range from 1 to 9999> <instance number, range from 1 to 9999> <HTTP Proxy port, ranges from 1024 to 49151>");
		System.exit(1);
	}
	
	private static void terminate() {
		System.out.println("Terminating. Goodbye");
		System.exit(1);
	}
}