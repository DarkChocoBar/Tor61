import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class P1PMessage {
	private static final int MAGIC_NUMBER = 50273;
	
	private static final int REGISTER_COMMAND = 1;
	private static final int REGISTERED_COMMAND = 2;
	private static final int FETCH_COMMAND = 3;
	private static final int FETCH_RESPONSE_COMMAND = 4;
	private static final int UNREGISTER_COMMAND = 5;
	private static final int PROBE_COMMAND = 6;
	private static final int ACK_COMMAND = 7;
	
	private static final int REGISTER_HEADER_SIZE = 15;
	private static final int UNREGISTER_HEADER_SIZE = 10;
	private static final int FETCH_HEADER_SIZE = 5;
	private static final int PROBE_HEADER_SIZE = 4;
	private static final int ACK_HEADER_SIZE = 4;
	
	private static ByteBuffer bb;
	
	/**
	 * constructing P1PMessage register message against 
	 * the user's input, sequence number, and user's IP
	 * @param input		user's stdin input
	 * @param seq		the current sequence number					
	 * @param hostIP	user's host IP address
	 * @throws Exception	if the user's inputs are invalid for creating a register message
	 * @return 	byte array of register P1PMessage that is ready to be sent to the server
	 * @return	null if port or servicedata are not integers
	 */
	public static byte[] getRegRequest(String[] input, int seq, InetAddress hostIP) {
		if (input.length != 4 || !input[0].equals("r"))
			throw new IllegalArgumentException("Call to "
					+ "getRegRequest(String[] input, int seq, InetAddress hostIP)"
					+ " with invalid user input");

		if (!isValidPort(input[1]))
			return null;

		int port;
		int serviceData;
		try {
			port = Integer.parseInt(input[1]);
			serviceData = Integer.parseInt(input[2]);
		} catch (NumberFormatException e) {
			return null;
		}

		byte[] serviceName = input[3].getBytes();
		byte[] address = hostIP.getAddress();

		bb = ByteBuffer.allocate(REGISTER_HEADER_SIZE + serviceName.length);
		bb.putShort((short) MAGIC_NUMBER);
		bb.put((byte) seq);
		bb.put((byte) REGISTER_COMMAND);
		bb.put(address);
		bb.putShort((short) port);
		bb.putInt(serviceData);
		bb.put((byte) serviceName.length);
		bb.put(serviceName);
		
		byte[] ret = bb.array();
		bb.clear();
		return ret;
	}

	/**
	 * constructing P1PMessage unregister message against 
	 * the user's input, sequence number, and user's IP
	 * @param input		user's stdin input
	 * @param seq		the current sequence number					
	 * @param hostIP	user's host IP address
	 * @throws Exception	if the user's inputs are invalid for creating an unregister message
	 * @return 	byte array of unregister P1PMessage that is ready to be sent to the server
	 * @return	null if portnum is not valid
	 */
	public static byte[] getURegRequest(String[] input, int seq, InetAddress hostIP) {
		if (input.length != 2 || !input[0].equals("u"))
			throw new IllegalArgumentException("Call to "
					+ "getURegRequest(String[] input, int seq, InetAddress hostIP)"
					+ " with invalid user input");
		if (!isValidPort(input[1]))
			return null;
		int port;
		try {
			port = Integer.parseInt(input[1]);
		} catch (NumberFormatException e) {
			return null;
		}
		byte[] address = hostIP.getAddress();

		bb = ByteBuffer.allocate(UNREGISTER_HEADER_SIZE);
		bb.putShort((short) MAGIC_NUMBER);
		bb.put((byte) seq);
		bb.put((byte) UNREGISTER_COMMAND);
		bb.put(address);
		bb.putShort((short) port);
		
		byte[] ret = bb.array();
		bb.clear();
		
		return ret;
	}
	
	/**
	 * constructing P1PMessage fetch request message against 
	 * the user's input, sequence number, and service name
	 * @param input		user's stdin input
	 * @param seq		the current sequence number					
	 * @throws Exception	if the user's inputs are invalid for creating a fetch request message
	 * @return 	byte array of fetch request P1PMessage that is ready to be sent to the server
	 */
	public static byte[] getFetchRequest(String[] input, int seq) {
		if ((input.length != 2 && input.length != 1) || !input[0].equals("f")) 
			throw new IllegalArgumentException("Call to getFetchRequest(String[] input, int seq)"
					+ " with invalid user input");

		byte[] name;
		if (input.length == 2)
			name = input[1].getBytes();
		else
			name = "".getBytes();

		bb = ByteBuffer.allocate(FETCH_HEADER_SIZE + name.length);
		bb.putShort((short) MAGIC_NUMBER);
		bb.put((byte) seq);
		bb.put((byte) FETCH_COMMAND);
		bb.put((byte) name.length);
		bb.put(name);

		byte[] ret = bb.array();
		bb.clear();
		
		return ret;
	}

	/**
	 * constructing P1PMessage probe request message against 
	 * the user's input, sequence number
	 * @param input		user's stdin input
	 * @param seq		the current sequence number
	 * @throws Exception	if the user's inputs are invalid for creating a probe request message
	 * @return 	byte array of probe request P1PMessage that is ready to be sent to the server
	 */
	public static byte[] getProbeRequest(String[] input, int seq) {
		if (input.length != 1 || !input[0].equals("p"))
			throw new IllegalArgumentException("Call to getProbeRequest(String[] input, int seq)"
					+ " with invalid user input");
		
		bb = ByteBuffer.allocate(PROBE_HEADER_SIZE);
		bb.putShort((short) MAGIC_NUMBER);
		bb.put((byte) seq);
		bb.put((byte) PROBE_COMMAND);
		
		byte[] ret = bb.array();
		bb.clear();
		
		return ret;
	}

	/**
	 * constructing P1PMessage acknowledge request message against 
	 * the user's input, sequence number
	 * @param input		user's stdin input
	 * @param seq		the current sequence number
	 * @throws Exception	if the user's inputs are invalid for creating an acknowledge request message
	 * @return 	byte array of acknowledge request P1PMessage that is ready to be sent to the server
	 */
	public static byte[] getAckResponse(int seq) {
		bb = ByteBuffer.allocate(ACK_HEADER_SIZE);
		bb.putShort((short) MAGIC_NUMBER);
		bb.put((byte) seq);
		bb.put((byte) ACK_COMMAND);

		byte[] ret = bb.array();
		bb.clear();

		return ret;
	}
	
	/**
	 * validating the register response from the server against the desired info
	 * and returning the lifetime for the current registration
	 * @param res	a byte array of server response
	 * @param desiredSeq	desired sequence number
	 * @return	-1 			if the server response is invalid
	 * 			lifetime 	if the server response is valid
	 */
	public static int valRegRes(byte[] res, int desiredSeq) {
		bb = ByteBuffer.allocate(res.length);
	    bb.put(res);
		bb.flip();

	    short magic = bb.getShort();
	    byte seq = bb.get();
	    byte command = bb.get();
	    int lifetime = (int) bb.getShort();
	    bb.clear();
	    
		if (!validHeader(magic, seq, command, (byte) desiredSeq, (byte) REGISTERED_COMMAND))
			return -1;
		return lifetime;
	}
	
	/**
	 * validating the u register response from the server against the desired info
	 * and returning the lifetime for the current registration
	 * @param res	a byte array of server response
	 * @param desiredSeq	desired sequence number
	 * @return	-1 			if the server response is invalid
	 * 			lifetime 	if the server response is valid
	 */
	public static int valURegRes(byte[] res, int desiredSeq) {
		bb = ByteBuffer.allocate(res.length);
	    bb.put(res);
		bb.flip();

	    short magic = bb.getShort();
	    byte seq = bb.get();
	    byte command = bb.get();
	    int lifetime = (int) bb.getShort();
	    bb.clear();
	    
		if (!validHeader(magic, seq, command, (byte) desiredSeq, (byte) ACK_COMMAND))
			return -1;
		return lifetime;
	}

	/**
	 * validating the acknowledge response from the server against the desired info
	 * and returning a boolean indicating the result
	 * @param res	a byte array of server response
	 * @param desiredSeq	desired sequence number
	 * @return	true if acknowledge response is valid, else false
	 */
	public static boolean valACK(byte[] res, int desiredSeq) {
		bb = ByteBuffer.allocate(res.length);
		bb.put(res);
		bb.flip();

		short magic = bb.getShort();
		byte seq = bb.get();
		byte command = bb.get();
		bb.clear();

		if (!validHeader(magic, seq, command, (byte) desiredSeq, (byte) ACK_COMMAND))
			return false;
		return true;
	}

	/**
	 * validating the fetch response from the server against the desired info
	 * and returning a list of a partial or all of the current registration
	 * @param res	a byte array of server response
	 * @param desiredSeq	desired sequence number
	 * @return	null if the fetch response from server is invalid
	 * 			a list of a partial or all of the running entries if response is valid
	 */
	public static List<Entry> valFetchRes(byte[] res, int desiredSeq) {
		bb = ByteBuffer.allocate(res.length);
		bb.put(res);
		bb.flip();

		short magic = bb.getShort();
		byte seq = bb.get();
		byte command = bb.get();
		int numEntries = (int) bb.get();
		
		if (!validHeader(magic, seq, command, (byte) desiredSeq, (byte) FETCH_RESPONSE_COMMAND)) {
			bb.clear();
			return null;
		}

		List<Entry> ret = new ArrayList<Entry>();
		for (int i = 0; i < numEntries; i++) {
			byte[] ipAddr = new byte[]{bb.get(), bb.get(), bb.get(), bb.get()};
			InetAddress addr;

			try {
				addr = InetAddress.getByAddress(ipAddr);
			} catch (UnknownHostException e) {
				return null;
			}

			int port = (int) bb.getShort();
			port = port >= 0 ? port : 0x10000 + port;

			int data = (int) bb.getInt();
			ret.add(new Entry(addr, port, data));
		}
		bb.clear();
		return ret;
	}

	/**
	 * validating the probe request from the server against the desired info
	 * and returning the current sequence number
	 * @param res	a byte array of server request
	 * @return	-1 			if the server response is invalid
	 * 			seq number 	if the server response is valid
	 */
	public static int valProbeRequest(byte[] req) {
		bb = ByteBuffer.allocate(req.length);
		bb.put(req);
		bb.flip();
		
		short magic = bb.getShort();
		byte seq = bb.get();
		byte command = bb.get();
		bb.clear();

		if (!validHeader(magic, seq, command, seq, (byte) PROBE_COMMAND))
			return -1;
		return seq;
	}

	// helper method to check if the header is valid according to the desired information
	private static boolean validHeader(short header, byte seq, byte command, 
			byte desiredSeq, byte desiredCommand) {
		int unsignedHeader = header & 0xffff;
		return (unsignedHeader == MAGIC_NUMBER) && (seq == desiredSeq) && (command == desiredCommand);
	}

	// helper method to check if the port given is valid
	private static boolean isValidPort(String s) {
		try {
			int port = Integer.parseInt(s);

			//if (port < 1024 || port > 49151)
			if (port < 1024 || port > 65535)
				return false;
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
}
