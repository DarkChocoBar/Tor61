import java.io.IOException;
import java.net.ServerSocket;


public class ATestTorRouter {
	public static void main(String[] args) throws IOException {
		ServerSocket socket = new ServerSocket(8080);
		int agent_id = 123123;
		TorRouter tr = new TorRouter(socket, agent_id);
		tr.start();
		
	}
}