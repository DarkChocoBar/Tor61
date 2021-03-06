import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;


public class TorCellConverter {
	public static final int CELL_LENGTH = 512;
	public static final int CELL_HEADER_SIZE = 14;
	public static final int MAX_DATA_SIZE = CELL_LENGTH - CELL_HEADER_SIZE;

	private static final int RELAY_TYPE_INDEX = 13;
	private static final int CELL_TYPE_INDEX = 2;
	
	private static final byte CREATE_CELL = 1;
	private static final byte CREATED_CELL = 2;
	private static final byte RELAY_CELL = 3;
	private static final byte DESTORY_CELL = 4;	
	private static final byte OPEN_CELL = 5;
	private static final byte OPENED_CELL = 6;
	private static final byte OPEN_FAILED_CELL = 7;
	private static final byte CREATE_FAILED_CELL = 8;

	private static final byte BEGIN_RELAY_CMD = 1;
	private static final byte DATA_RELAY_CMD = 2;
	private static final byte END_RELAY_CMD = 3;
	private static final byte CONNECTED_RELAY_CMD = 4;
	private static final byte EXTEND_RELAY_CMD = 6;
	private static final byte EXTENDED_RELAY_CMD = 7;
	private static final byte BEGIN_FAILED_RELAY_CMD = 11;
	private static final byte EXTEND_FAILED_RELAY_CMD = 12;
	
	private static ByteBuffer bb;
	
	public static byte[] getCreateCell(byte[] b) {
		bb = ByteBuffer.wrap(b);
		byte[] ret = CreateDestoryCellHelper(bb.getShort(0), CREATE_CELL);
		bb.clear();
		return ret;
	}

	public static byte[] getCreateCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATE_CELL);
	}
	
	public static byte[] getCreatedCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATED_CELL);
	}

	public static ArrayList<byte[]> getRelayCells(String cmd, short circuit_id, short stream_id, String data) {
		cmd = cmd.toLowerCase();
		ArrayList<byte[]> ret = new ArrayList<byte[]>();
		byte[] data_arr = data.getBytes(Charset.forName("UTF-8"));

		switch(cmd) {
			case "begin":
				ret.add(withDataSubcellHelper(circuit_id, stream_id, BEGIN_RELAY_CMD, data_arr));
				break;
			case "data":
				int start = 0;
				while (start < data_arr.length) {
					byte[] temp = Arrays.copyOfRange(data_arr, start, Math.min(start + MAX_DATA_SIZE, data_arr.length));
					ret.add(withDataSubcellHelper(circuit_id, stream_id, DATA_RELAY_CMD, temp));
					start += MAX_DATA_SIZE;
				}
				break;
			case "end":
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, END_RELAY_CMD));
				break;
			case "connected":
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, CONNECTED_RELAY_CMD));
				break;
			case "extend":
				ret.add(withDataSubcellHelper(circuit_id, stream_id, EXTEND_RELAY_CMD, data_arr));
				break;
			case "extended":
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, EXTENDED_RELAY_CMD));
				break;
			case "begin failed":
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, BEGIN_FAILED_RELAY_CMD));
				break;
			case "extend failed":
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, EXTEND_FAILED_RELAY_CMD));
				break;
			default:
				throw new IllegalArgumentException("Invalid relay_cmd parameter passed into getRelayCell");
		}
		return ret;
	}

	public static byte[] getDestoryCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, DESTORY_CELL);
	}
	
	public static byte[] getOpenCell(short circuit_id, int opener, int opened) {
		return OpenCellHelper((short)0, OPEN_CELL, opener, opened);
	}
	
	public static byte[] getOpenedCell(byte[] b) {
		bb = ByteBuffer.wrap(b);
		byte[] ret = OpenCellHelper((short)0, OPENED_CELL, bb.getInt(3), bb.getInt(7));
		bb.clear();
		return ret;
	}

	public static byte[] getOpenFailCell(byte[] b) {
		bb = ByteBuffer.wrap(b);
		byte[] ret = OpenCellHelper(bb.getShort(0), OPEN_FAILED_CELL, bb.getInt(3), bb.getInt(7));
		bb.clear();
		return ret;
	}
	
	public static byte[] getCreateFailCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATE_FAILED_CELL);
	}
	
	public static short getCircuitId(byte[] b) {
		assert(b.length >= 2);
		ByteBuffer bb = ByteBuffer.wrap(b);
		short s = bb.getShort();
		return (short) (s >= 0 ? s : 0x10000 + s); 
	}
	
	public static String getCellType(byte[] b) {
		assert(b.length >= CELL_TYPE_INDEX);
		ByteBuffer bb = ByteBuffer.wrap(b);
		byte tempByte = bb.get(CELL_TYPE_INDEX);
		bb.clear();
		switch(tempByte) {
			case CREATE_CELL: return "create";
			case CREATED_CELL: return "created";
			case RELAY_CELL: return "relay";
			case DESTORY_CELL: return "destory";
			case OPEN_CELL: return "open";
			case OPENED_CELL: return "opened";
			case OPEN_FAILED_CELL: return "open failed";
			case CREATE_FAILED_CELL: return "create failed";
			default: 
				System.out.println("Cell Type: " + tempByte);
				throw new IllegalArgumentException("Invalid cell type");
		}
	}

	public static String getRelaySubcellType(byte[] b) {
		ByteBuffer bb = ByteBuffer.wrap(b);	
		assert(bb.get(2) == RELAY_CELL);
		assert(b.length >= RELAY_TYPE_INDEX);
		byte tempByte = bb.get(RELAY_TYPE_INDEX);
	    bb.clear();
		switch(tempByte) {
			case BEGIN_RELAY_CMD: return "begin";
			case DATA_RELAY_CMD: return "data";
			case END_RELAY_CMD: return "end";
			case CONNECTED_RELAY_CMD: return "connected";
			case EXTEND_RELAY_CMD: return "extend";
			case EXTENDED_RELAY_CMD: return "extended";
			case BEGIN_FAILED_RELAY_CMD: return "begin failed";
			case EXTEND_FAILED_RELAY_CMD: return "extend failed";
			default: throw new IllegalArgumentException("Invalid cell type");
		}
	}

	public static InetSocketAddress getExtendDestination(byte[] b) {
		ByteBuffer bb = ByteBuffer.wrap(b);	
		assert(bb.get(2) == RELAY_CELL);
		assert(b.length >= TorCellConverter.CELL_HEADER_SIZE);
		assert(bb.get(13) == BEGIN_RELAY_CMD);
		bb.clear();

		byte[] httpReqArrAgentID = Arrays.copyOfRange(b, TorCellConverter.CELL_HEADER_SIZE, TorCellConverter.CELL_LENGTH);
		String httpReq = new String(httpReqArrAgentID).split("\0")[0];
		String host = httpReq.split(":")[0];
		host = host.replace("/", "");
		int port = Integer.parseInt(httpReq.split(":")[1]);
		return new InetSocketAddress(host, port);
	}

	public static int getExtendAgent(byte[] b) {
		bb = ByteBuffer.wrap(b);	
		assert(bb.get(2) == RELAY_CELL);
		assert(b.length >= TorCellConverter.CELL_HEADER_SIZE);
		assert(bb.get(13) == EXTEND_RELAY_CMD);
		bb.clear();

		byte[] httpReqArrAgentID = Arrays.copyOfRange(b, TorCellConverter.CELL_HEADER_SIZE, TorCellConverter.CELL_LENGTH);
		String agent = new String(httpReqArrAgentID).split("\0")[1];
		int agentID = Integer.parseInt(agent);
		return agentID;
	}

	public static short getStreamID(byte[] b) {
		ByteBuffer bb = ByteBuffer.wrap(b);
		assert(b.length >= TorCellConverter.CELL_HEADER_SIZE);
		assert(bb.getShort(3) == RELAY_CELL);
		short ret = (short) ((bb.getShort(3) >> 8) & 0xFF);
	
		bb.clear();
		return ret;		// deal with unsigned short
	}
	
	public static byte[] updateCID(byte[] b, int newCID) {
		bb = ByteBuffer.allocate(CELL_LENGTH);
		bb.putShort((short) newCID);
		bb.put(Arrays.copyOfRange(b, 2, b.length));
		b = bb.array();
		bb.clear();
		return b;
	}
	
	public static int getOpener(byte[] b) {
		bb = ByteBuffer.wrap(b);
		
		// check if it is one of the open commands
		assert((bb.get(2) == (byte) 5) || (bb.get(2) == (byte) 6) || (bb.get(2) == (byte) 7));
		assert(b.length >= TorCellConverter.CELL_HEADER_SIZE);
		
		int opener = bb.getInt(3);
		bb.clear();
		return opener;
	}
	
	public static int getOpenee(byte[] b) {
		bb = ByteBuffer.wrap(b);
		
		// check if it is one of the open commands
		assert((bb.get(2) == (byte) 5) || (bb.get(2) == (byte) 6) || (bb.get(2) == (byte) 7));
		assert(b.length >= TorCellConverter.CELL_HEADER_SIZE);

		int opener = bb.getInt(7);
		bb.clear();
		return opener;
	}
	
	private static byte[] CreateDestoryCellHelper(short circuit_id, byte cell_num) {
		bb = ByteBuffer.allocate(CELL_LENGTH);
		bb.putShort(circuit_id);
		bb.put(cell_num);
		byte[] ret = bb.array();
		bb.clear();
		return ret;
	}
	
	private static byte[] OpenCellHelper(short circuit_id, byte cell_num, int opener_id, int opened_id) {
		bb = ByteBuffer.allocate(CELL_LENGTH);
		bb.putShort(circuit_id);
		bb.put(cell_num);
		bb.putInt(opener_id);
		bb.putInt(opened_id);
		byte[] ret = bb.array();
		bb.clear();
		return ret;
	}

	private static byte[] simpleRelaySubcellHelper(short circuit_id, short stream_id, byte relay_cmd) {
		bb = ByteBuffer.allocate(CELL_LENGTH);
		bb.putShort(circuit_id);		// circuit id
		bb.put(RELAY_CELL);				// cell cmd
		bb.putShort(stream_id);			// stream id
		bb.putShort((short) 0);			// 0x0000
		bb.putInt(0);					// digest
		bb.putShort((short) 0);			// body length = 0
		bb.put(relay_cmd);
		
		byte[] ret = bb.array();
		bb.clear();
		return ret;
	}

	private static byte[] withDataSubcellHelper(short circuit_id, short stream_id, byte relay_cmd, byte[] data) {
		if (data.length > MAX_DATA_SIZE)
			throw new IllegalArgumentException("Invalid size of data passed in");

		bb = ByteBuffer.allocate(CELL_LENGTH);
		bb.putShort(circuit_id);				// circuit id
		bb.put(RELAY_CELL);						// cell cmd
		bb.putShort(stream_id);					// stream id
		bb.putShort((short) 0);					// 0x0000
		bb.putInt(0);							// digest
		bb.putShort((short) data.length);		// body length = 0
		bb.put(relay_cmd);
		bb.put(data);

		byte[] ret = bb.array();
		bb.clear();
		return ret;
	}
}
