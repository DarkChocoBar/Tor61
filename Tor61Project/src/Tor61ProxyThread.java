import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;


public class Tor61ProxyThread extends Thread {
	private PackOutputStream TOR_OUT_STREAM;
    private Socket SOCKET = null;
    private short CID;
    private short STREAM_ID;
    
    // Set socket and tor_port number
    public Tor61ProxyThread(Socket socket, int proxy_port, PackOutputStream stream, short cid, short stream_id) {
        this.SOCKET = socket;
        this.TOR_OUT_STREAM = stream;
        this.CID = cid;
        this.STREAM_ID = stream_id;
    }

    public void run() {

        try {
            DataOutputStream client_out = new DataOutputStream(SOCKET.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(SOCKET.getInputStream()));

            // Set Timer To 10 Minutes
            // If header is not processed within 10 minutes, assume client is dead
            SOCKET.setSoTimeout(10 * 1000);
            
            ArrayList<String> header = processRequestHeader(in);
            
            if (header.isEmpty()) {
            	SOCKET.close();
            }
            
            // Kill Timer
            SOCKET.setSoTimeout(0);
         			
            String get = header.get(0).trim();            
            String request = get.split("\\s+")[0];
                        
            System.out.println(get);
            
            String host_ip = getHostLine(header);
            int port = getPort(get, host_ip);
            String host = getHost(host_ip);
            
            // We need to send begin cell
			// If begin success, write ok. else send bad gateway
			String data = host + ":" + port + '\0';
			for (byte[] bs: TorCellConverter.getRelayCells("begin", CID, STREAM_ID, data))
				TOR_OUT_STREAM.write(bs);
			
			// Wait for connected reply up to 10 seconds
			SOCKET.setSoTimeout(10000);
		
			while (!Tor61ProxyServer.STREAMS.containsKey(STREAM_ID)) {
				continue;
			}
			SOCKET.setSoTimeout(0); // Kill the timer
			
			// Successfully received connected reply

            try {
				if (request.toLowerCase().equals("connect")) {
					// This is when we send a begin request if successful, send ok. else send bad gateway
					try {

						client_out.write("HTTP/1.0 200 OK\r\n\r\n".getBytes());
						client_out.flush();
					// TODO We can never get to this part of the code. 
					} catch (ConnectException e){
						client_out.write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes());
						client_out.flush();
						return;
					}
					
					stream(in, TOR_OUT_STREAM);
				} else {
					// Write HTTP request to tor router
					
					// THIS PART MAY BE EXTREMELY SLOW BECAUSE 512 BYTES PER HTTP REQUEST LINE
					// TODO
					/**
					 * Maybe concat header into 1 byte[] then write whole byte to out stream
					 */
					for (String s: header) {
						TOR_OUT_STREAM.write((s + "\r\n").getBytes());
					}
					TOR_OUT_STREAM.write("\r\n".getBytes());
					TOR_OUT_STREAM.flush();


					// We are no longer reading. This is someone else's job
					/*
					InputStream is = server.getInputStream();
					
					// Set timer to 10 minutes
					// If no input is read in 10 minutes, assume connection is dead
					server.setSoTimeout(10 * 1000);
					
					// Send Server Response to Client
					byte b[] = new byte[BUFFER_SIZE];
					int read = is.read(b, 0, BUFFER_SIZE);
					while (read != -1) {
					  // If no input is read in 10 minutes, assume connection is dead
					  server.setSoTimeout(10 * 1000);
					  
					  out.write(b, 0, read);
					  read = is.read(b, 0, BUFFER_SIZE);
					  out.flush();
					}
					
					// Kill timer
					server.setSoTimeout(0);
					*/
				}          
            } catch (Exception e) {
				// Satisfy Client Request by terminating
            	client_out.writeBytes("");
            }
            
            // Send tor router relay end cell
            for (byte[] bs: TorCellConverter.getRelayCells("end", CID, STREAM_ID, ""))
            	TOR_OUT_STREAM.write(bs);
            
            // Close stream
            assert(Tor61ProxyServer.STREAMS.containsKey(STREAM_ID));

            Tor61ProxyServer.STREAMS.remove(STREAM_ID);
            
            if (client_out != null) {
            	client_out.close();
			}
			if (in != null) {
				in.close();
			}
			if (SOCKET != null) {
				SOCKET.close();
			}

        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    // Stream client and server input and output
    private void stream(BufferedReader cin, PackOutputStream cout) {
    	try {        	
        	Thread cs = new StreamThread(cin, cout);
        	
        	cs.run();
        	
        	cs.join();
        	
        } catch (Exception e) {
        	System.out.println("Some error in stream method in proxy thread");
        }
    }
    
    private static ArrayList<String> processRequestHeader(BufferedReader br) throws IOException {
		ArrayList<String> header = new ArrayList<String>();
		String temp;

		while ((temp = br.readLine()) != null && !temp.equals("") && !temp.equals("\n")) {
			header.add(temp);
		}

		header = updateConnectionFieldandHttpVersion(header);
				
		return header;
	}

	private static ArrayList<String> updateConnectionFieldandHttpVersion(ArrayList<String> list) {
		for(int i = 0; i < list.size(); i++) {
			String s = list.get(i);
			String s1 = "Connection: ";
			String s2 = "Proxy-connection: ";
			String s3 = "close";
			if (s.length() >= s1.length() && s.substring(0, s1.length()).toLowerCase().equals(s1.toLowerCase())) {
				list.set(i,s1 + s3);
			} else if (s.length() >= s2.length() && s.substring(0, s2.length()).toLowerCase().equals(s2.toLowerCase())) {
				list.set(i,s2 + s3);
			} else if (s.contains("HTTP/1.1")) {
				list.set(i,s.replaceAll("HTTP/1.1","HTTP/1.0"));
			}
		}
		return list;
	}
	
	private static int getPort(String get, String host) {
		int port = get.contains("https://") ? 443 : 80;
		if (host.contains(":")) {
			try {
				port = Integer.parseInt(host.split(":")[1]);
			} catch (Exception e) {
				return -1;
			}
		}
		return port;
	}
	
	private static String getHost(String host) {
		if (host.contains(":")) {
			return host.split(":")[0].trim();
		}
		return host;
	}
	
	private static String getHostLine(ArrayList<String> list) {
		String ret = "";
		for (String s : list) {
			if (s.length() >= 6 && s.substring(0, 6).toLowerCase().equals("host: ")) {
				ret = s.substring(6, s.length()).trim();
				break;
			}
		}
		return ret;
	}
	
	public class StreamThread extends Thread {
		private BufferedReader in;
		private PackOutputStream out;
		
		public StreamThread(BufferedReader in, PackOutputStream out) {
			this.in = in;
			this.out = out;
		}
		
		public void run() {
			while (!Tor61ProxyServer.STREAMS.containsKey(STREAM_ID)) {
				try {
					String next;
					if ((next = in.readLine()) != null)
						out.write(next.getBytes());
					else {
						out.flush();
						return;
					}
					out.flush();
				} catch (IOException e) {
					System.out.println("Some error in StreamThread");
				}
			}
		}
	}
}