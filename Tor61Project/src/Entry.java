import java.net.InetAddress;

public class Entry {
	public InetAddress ip;
	public int port;
	public int serviceData;
	
	public Entry(InetAddress ip, int port, int serviceData) {
		this.ip = ip;
		this.port = port;
		this.serviceData = serviceData;
	}
	
	@Override
	public String toString() {
		return ip + ":" + port + " " + serviceData;
	}
}
