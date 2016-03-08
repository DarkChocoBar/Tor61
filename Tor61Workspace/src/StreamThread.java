import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;

public class StreamThread extends Thread {
	private BufferedReader in;
	private DataOutputStream out;
	private Boolean done;
	
	public StreamThread(BufferedReader in, DataOutputStream out, Boolean done) {
		this.in = in;
		this.out = out;
		this.done = done;
	}
	
	public void run() {
		while (!done) {
			try {
				String next;
				if ((next = in.readLine()) != null)
					out.write(next.getBytes());
				else
					done = true;
				out.flush();
			} catch (IOException e) {
				done = true;
			}
		}
	}
}