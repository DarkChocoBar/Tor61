import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;



/**

	How to Use RegistrationAgent
	
	1. Create an object. 
		It is assumed that we will connect to "cse461.cs.washington.edu" at port 46101, since
		this is a well-known registration service
	2. Call on the object's run() method
			run() takes a space-separated String
			Commands are: r(egister), u(nregister), f(etch), p(robe), or q(uit)
			example: agentObject.run("r 22222 12345678 Tor61-4321-0001");


**/

public class RegistrationAgent {

	private static InetAddress SERVICE_HOST;
	private static InetAddress THIS_HOST;
	private static Map<Integer,Integer> REGISTERED_PORTS = new HashMap<Integer,Integer>();
	private static int SERVICE_PORT;
	private static boolean TIME_TO_DIE;
	private static int DATA_BUFFER_SIZE = 1048;
	private static int SEQUENCE = 0;
	private static String WELL_KNOWN_HOST = "cse461.cs.washington.edu";
	private static int WELL_KNOWN_PORT = 46101;
	
	protected static DatagramSocket main;
	protected static DatagramSocket sub;
	protected static Thread subhandler;
	
	public RegistrationAgent() {
		// Validates user input
		try {
			SERVICE_HOST = InetAddress.getByName(WELL_KNOWN_HOST);
			SERVICE_PORT = WELL_KNOWN_PORT;
			THIS_HOST    = InetAddress.getLocalHost();
		} catch (UnknownHostException e){
			System.out.println("Unknown Host: " + WELL_KNOWN_HOST);
			usage();
		}
		System.out.println("regServerIp = " + SERVICE_HOST.getHostAddress());
		System.out.println("thisHostIP  = " + THIS_HOST.getHostAddress());
		
		TIME_TO_DIE = false;
		setupSockets();
	}
	
	private void setupSockets() {
		try {
			// Connects to Service Port
			main = new DatagramSocket();
			// Second port to listen to server probing
			sub  = new DatagramSocket(main.getLocalPort() + 1);

			// Create a thread to listen to service host probes
			TIME_TO_DIE = false; // Flag to notify subhandler to terminate
			subhandler = new Thread(new ThreadServerHandler(sub));
			subhandler.start();
		} catch (SocketException e) {
			System.out.println("Sockets Failed To Bind to Ports. Please Try Again");
			usage();
		}
	}
	
	// This thread handles all user input
	public boolean register(int tor_port, long service_data, String name) {
		String[] input = new String[3];
		input[0] = "r";
		input[1] = "" + tor_port;
		input[2] = "" + service_data;
		input[3] = name;
		
		// Create new thread to handle each user command
		Thread t = new Thread(new ThreadUserRequestHandler(main, input));
		t.start();
		try {
			t.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		int port = -1;
		try {
			port = Integer.parseInt(input[1]);
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return false;
		}
		return REGISTERED_PORTS.containsKey(port);
	}
	
	// Returns all IP address and Port numbers of Routers with specified Prefix
	// Returns null if there was some sort of error fetching
	public List<Entry> fetch(String prefix) {
		String[] input = new String[2];
		input[0] = "f";
		input[1] = prefix;
		
		// Object to store result set
		List<Entry> entries = new ArrayList<Entry>();
		
		// Create new thread to handle each user command
		Thread t = new Thread(new ThreadUserRequestHandler(main, input, entries));
		
		t.start();
		try {
			t.join(); // Wait for fetch to finish
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		return entries;
	}
	
	// Terminate Registration Agent
	// Returns true when successfully terminates
	public boolean quit() {
		return terminate();
	}
	
	// Notifies user how to use this program
	private static void usage() {
		System.out.println("Usage: java agent <registration service host name> <service port>");
		System.exit(1);
	}
	
	// Terminate process
	// Returns true when finished
	private boolean terminate() {
		// If there are currently registered ports, unregister them
		if (!REGISTERED_PORTS.isEmpty()) {
			System.out.print("Preparing to Quit: Unregistering Active Ports...");
			// Create new thread for each new unregister request per active port
			for (Integer i: REGISTERED_PORTS.keySet()) {
				// Create new thread to ask server to unregister port
				Thread t = new Thread(new ThreadUserRequestHandler(main, i));
				t.start();
			}
		
			// We need to wait until all registered ports are unregistered
			while (!REGISTERED_PORTS.isEmpty()) {
				try {
					Thread.sleep(500);
				} catch(InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			System.out.println("Successfully Unregistered All Active Ports!");
		}
		
		// Kill all threads
		TIME_TO_DIE = true;
		System.out.println("Goodbye!");
			
		main.close();
		sub.close();
		return true;
	}
	
	// Thread to handle receiving input from server
	public static class ThreadServerHandler implements Runnable {
		private DatagramSocket socket;		// the socket used for the thread

		private static final int DATA_BUFFER_SIZE = 1024;

		public ThreadServerHandler(DatagramSocket socket) {
			this.socket = socket;
		}
		
		public void run() {
			byte[] buf = new byte[DATA_BUFFER_SIZE];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
	
			// Loop until it is time to die
        	while(true){
        		if (TIME_TO_DIE)
        			return;
				try {
					socket.setSoTimeout(5000);	// set the timeout in millisecounds.
					socket.receive(dp);
					socket.setSoTimeout(0);		// Stop timer
							
					// Only request coming from Server should be a probe request
					int seq = P1PMessage.valProbeRequest(dp.getData());
					if (seq != -1) {
						byte[] response = P1PMessage.getAckResponse(seq);
						DatagramPacket rsp = new DatagramPacket(response, response.length,dp.getAddress(), dp.getPort());
						socket.send(rsp);
						System.out.println("I've been probed!");
					}
				}
				catch (SocketTimeoutException e) {
					continue;
				} catch (SocketException e) {
					return;
				} catch (IOException e) {
					return;
				}
        	}
		}
	}
	
	// Thread to handle sending request to server from user
	public static class ThreadUserRequestHandler implements Runnable {
		private DatagramSocket socket;		// the socket used for the thread
		private String[] data;				// client input data
		private String[] redata;
		private List<Entry> entries;
		
		public ThreadUserRequestHandler(DatagramSocket socket, String[] data) {
			this.socket = socket;
			this.data = data;
			this.redata = null;
			this.entries = null;
		}
		
		public ThreadUserRequestHandler(DatagramSocket socket, String[] data, String[] redata) {
			this.socket = socket;
			this.data = data;
			this.redata = redata;
			this.entries = null;
		}
		
		public ThreadUserRequestHandler(DatagramSocket socket, String[] data, List<Entry> e) {
			this.socket = socket;
			this.data = data;
			this.redata = null;
			this.entries = e;
		}
		
		// Creates a new thread to unregister ports (only used in terminate())
		// This constructor was made solely to not include /n characters when mass unregistering
		public ThreadUserRequestHandler(DatagramSocket socket, int unregisterPort) {
			this.socket = socket;
			this.data = new String[2];
			this.data[0] = "u";
			this.data[1] = "" + unregisterPort;
			this.redata = null;
		}
		
		public void run() {			
			switch(data[0]) {
				case "r":
					handleRegister();
					break;
				case "u":
					handleUnregister();
					break;
				case "f":
					handleFetch();
					break;
				case "p":
					handleProbe();
					break;
				case "rr":
					handleReregister();
					break;
				default:
					invalidInput();
					break;
			}
		}
		
		// Notify user of invalid input
		private void invalidInput() {
			System.out.println("Invalid Command: " + data[0]);
		}
		
		// Handle registering with Registration Server
		// Send message, set timer, then wait for server "registered" message
		// If timer goes off, try again up to 3 times
		// If fail 3 times, notify user registration failed
		// Also, if registration succeeds, updates "REGISTERED_PORTS" and add port number
		private void handleRegister() {
			if (data.length != 4) {
				System.out.println("Invalid Number of Arguments for command 'r'"
						+ "Usage: r <portnum> <data> <serviceName>");
			} else {
				String methodName = "REGISTER";
				int number_of_tries = 1;
				// Exit condition is in the catch statement
				// Exits when fails to receive confirmation 3 times
				while (true) {
					try {
						int curr_sequence = SEQUENCE;
						byte[] send = P1PMessage.getRegRequest(data,curr_sequence,SERVICE_HOST);
						byte[] receive = new byte[DATA_BUFFER_SIZE];
						
						// Invalid inputs
						if (send == null) {
							System.out.println("<portnum> and <data> must respectively be 2 and 4 byte integers ");
							System.out.println("Registration Failed");
							return;
						}
						
						DatagramPacket dpsend = new DatagramPacket(send, send.length, SERVICE_HOST, SERVICE_PORT);
						DatagramPacket dpreceive = new DatagramPacket(receive, receive.length);

						socket.send(dpsend);
						socket.setSoTimeout(5000);

						socket.receive(dpreceive);
						socket.setSoTimeout(0);		// Stop timer

						int lifetime = P1PMessage.valRegRes(dpreceive.getData(),curr_sequence);
						if (lifetime != -1) {
							int port = Integer.parseInt(data[1]);
							System.out.println("Succeeded Registering Port: " + port + " as " + data[3] + " with lifetime: " + lifetime);
							
							// Keeps track of registered ports
							REGISTERED_PORTS.put(port,curr_sequence);
							SEQUENCE++;
							
							Thread t = new Thread(new ThreadUserRequestHandler(socket, new String[]{"rr",data[1],data[2],data[3]}, new String[]{""+port,""+curr_sequence,""+lifetime}));
							t.start();
							
							return;
						} else {
							continue;
						}
					} catch (SocketTimeoutException e) {
						if (number_of_tries >= 3) {
							timeoutMessage(methodName);
							failMessage(methodName);
							return;
						} else {
							number_of_tries++;
							timeoutMessage(methodName);
						}
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		private void handleReregister() {
			int port = -1;
			int curr_sequence = -1;
			int lifetime = -1;
			try {
				port = Integer.parseInt(redata[0]);
				curr_sequence = Integer.parseInt(redata[1]);
				lifetime = Integer.parseInt(redata[2]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return;
			}
			
			// While assigned port is not unregistered, or same port is not re-registered by user, reregister
			while (REGISTERED_PORTS.containsKey(port) && REGISTERED_PORTS.get(port) == curr_sequence && !TIME_TO_DIE) {
				// Should re-register 15 seconds before the end of lifetime
				int waitperiod = (lifetime - 15) * 1000;
				
				// Wait before re-registering
				try {
					Thread.sleep(waitperiod);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				
				int number_of_tries = 1;
				String methodName = "REREGISTER";
				System.out.println("Port: " + port + " is about to expire. Attempting to re-register");
				// Exit condition is in the try catch statement or done
				// Does not reregister if fails to receive reregister-confirmation 3 times
				boolean done = false;
				while (!done) {
					try {
						curr_sequence = SEQUENCE;
						byte[] send = P1PMessage.getRegRequest(new String[] {"r",data[1],data[2],data[3]},curr_sequence,SERVICE_HOST);
						byte[] receive = new byte[DATA_BUFFER_SIZE];
						DatagramPacket dpsend = new DatagramPacket(send, send.length,SERVICE_HOST ,SERVICE_PORT);
						DatagramPacket dpreceive = new DatagramPacket(receive, receive.length);
		
						socket.send(dpsend);
						socket.setSoTimeout(5000);

						socket.receive(dpreceive);
						socket.setSoTimeout(0);
		
						lifetime = P1PMessage.valRegRes(dpreceive.getData(),curr_sequence);
						if (lifetime != -1) {
							System.out.println("Succeeded Re-registering Port: " + port + " as " + data[3] + " with lifetime: " + lifetime);
							REGISTERED_PORTS.put(port,curr_sequence);
							SEQUENCE++;
						
							// Successfully reregistered
							done = true;
						} else {
							continue;
						}		
					
					}  catch (SocketTimeoutException e){
						if (number_of_tries >= 3) {
							timeoutMessage(methodName);
							failMessage(methodName);
							return;
						} else {
							number_of_tries++;
							timeoutMessage(methodName);
						}
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Handle unregistering with Registration Server
		private void handleUnregister() {
			if (data.length != 2) {
				System.out.println("Invalid Number of Arguments for command 'u'"
						+ "Usage: u <portnum>");
			//} else if (!REGISTERED_PORTS.contains(Integer.parseInt(data[1]))) {
			//	System.out.println("Port Number " + data[1] + " is not registered");
			} else {
				String methodName = "UNREGISTER";
				int number_of_tries = 1;
				while (true) {
					try {
						int curr_sequence = SEQUENCE;
						byte[] send = P1PMessage.getURegRequest(data,curr_sequence,SERVICE_HOST);
						if (send == null) {
							System.out.println("<portnum> must respectively be a 2 byte integer ");
							System.out.println("Unregistering Failed");
							return;
						}
						
						byte[] receive = new byte[DATA_BUFFER_SIZE];
						DatagramPacket dpsend = new DatagramPacket(send, send.length, SERVICE_HOST, SERVICE_PORT);
						DatagramPacket dpreceive = new DatagramPacket(receive, receive.length);
					
						socket.send(dpsend);
						socket.setSoTimeout(5000);
					
						socket.receive(dpreceive);
						socket.setSoTimeout(0);		// Stop timer
						
						// Validate server response
						if (P1PMessage.valURegRes(dpreceive.getData(),curr_sequence) != -1) {
							System.out.println("Succeeded Unregistering Port: " + data[1]);
							
							// Remove confirmed unregistered port from memory
							REGISTERED_PORTS.remove(Integer.parseInt(data[1]));
							SEQUENCE++;
							
							return;
							
						// Received some sort of data from server, but it was invalid. Try again
						} else {
							continue;
						}
					} catch (SocketTimeoutException e) {
						if (number_of_tries >= 3) {
							timeoutMessage(methodName);
							failMessage(methodName);
							return;
						} else {
							number_of_tries++;
							timeoutMessage(methodName);
						}
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		// Handle fetching service information from Registration Server
		private void handleFetch() {
			if (data.length != 2 && data.length != 1) {
				System.out.println("Invalid Number of Arguments for command 'f'"
						+ "Usage: f, or f <name prefix>");
			} else {
				String methodName = "FETCH";
				int number_of_tries = 1;
				while (true) {
					try {
						int curr_sequence = SEQUENCE;
						byte[] send = P1PMessage.getFetchRequest(data,curr_sequence);
						byte[] receive = new byte[DATA_BUFFER_SIZE];
						DatagramPacket dpsend = new DatagramPacket(send, send.length, SERVICE_HOST, SERVICE_PORT);
						DatagramPacket dpreceive = new DatagramPacket(receive, receive.length);
					
						socket.send(dpsend);
						socket.setSoTimeout(5000);
					
						socket.receive(dpreceive);
						socket.setSoTimeout(0);		// Stop timer
						
						List<Entry> list = P1PMessage.valFetchRes(dpreceive.getData(),curr_sequence);
						
						// Validate server response
						if (list != null) {
							System.out.println("Succeeded Fetching Data from Server");
							if (list.isEmpty() && data.length == 2) {
								System.out.println("There were no ports associated the prefix: " + data[1]);
							} else if (list.isEmpty() && data.length == 1) {
								System.out.println("There were no registered ports on the server");
							} else {
								int counter = 1;
								for (Entry e: list) {
									entries.add(e);
									System.out.println("[" + counter + "] " + e.ip.getHostAddress() + " " + e.port + " " + e.serviceData);
									counter ++;
								}
							}
							SEQUENCE++;
							return;
						} else {
							continue;
						}
					} catch (SocketTimeoutException e) {
						if (number_of_tries >= 3) {
							timeoutMessage(methodName);
							failMessage(methodName);
							entries = null;
							return;
						} else {
							number_of_tries++;
							timeoutMessage(methodName);
						}
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Handle probing the Registration Server
		private void handleProbe() {
			if (data.length != 1) {
				System.out.println("Invalid Number of Arguments for command 'p'"
						+ "Usage: p");
			} else {
				String methodName = "PROBE";
				int number_of_tries = 1;
				while (true) {
					try {
						int curr_sequence = SEQUENCE;
						byte[] send = P1PMessage.getProbeRequest(data,curr_sequence);
						byte[] receive = new byte[DATA_BUFFER_SIZE];
						DatagramPacket dpsend = new DatagramPacket(send, send.length, SERVICE_HOST, SERVICE_PORT);
						DatagramPacket dpreceive = new DatagramPacket(receive, receive.length);
					
						socket.send(dpsend);
						socket.setSoTimeout(5000);
					
						socket.receive(dpreceive);
						socket.setSoTimeout(0);		// Stop timer
						
						// Validate server response
						if (P1PMessage.valACK(dpreceive.getData(),curr_sequence)) {
							System.out.println("Succeeded Probing Server");
							SEQUENCE++;
							return;
						} else {
							continue;
						}
					} catch (SocketTimeoutException e) {
						if (number_of_tries >= 3) {
							timeoutMessage(methodName);
							failMessage(methodName);
							return;
						} else {
							number_of_tries++;
							timeoutMessage(methodName);
						}
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Notifies user that we timed out waiting for a server response
		private void timeoutMessage(String method) {
			System.out.println("Timed out waiting for reply to " + method + " message");
		}
		
		private void failMessage(String method) {
			System.out.println("Sent 3 " + method + " messages but got no reply.");
			System.out.println("Failed to " + method);
		}
	}
}
