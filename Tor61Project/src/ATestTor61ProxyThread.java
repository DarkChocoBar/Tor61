import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;


public class ATestTor61ProxyThread {
	public static void main(String[] args) throws InterruptedException, UnknownHostException, IOException {
		/** TESTING StreamThread **/
//		DataOutputStream stream = new DataOutputStream(System.out);
//		PackOutputStream out = new PackOutputStream(stream, (short) 3, (short) 2);
//		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
//		StreamThread st = new StreamThread(in, out);
//		st.start();
		
		/** TESTING Tor61ProxyThread **/
		int proxy_port = 8080;
		PackOutputStream out = new PackOutputStream(new DataOutputStream(System.out), (short) 3, (short) 2);
		short cid = 4;
		short stream_id = 5;
		Socket s = new Socket("www.google.com", 8080);
		Tor61ProxyThread tpt = new Tor61ProxyThread(s, out, cid, stream_id);
		tpt.start();
	}
	
	public static class StreamThread extends Thread {
		private BufferedReader in;
		private PackOutputStream out;
		
		public StreamThread(BufferedReader in, PackOutputStream out) {
			this.in = in;
			this.out = out;
		}
		
		public void run() {
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
