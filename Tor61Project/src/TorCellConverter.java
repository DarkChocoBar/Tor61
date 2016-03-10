import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;


public class TorCellConverter {
	private static final int CELL_LENGTH = 512;
	private static final int MAX_DATA_SIZE = 498;
	
	private static final byte CREATE_CELL = 1;
	private static final byte CREATED_CELL = 2;
	private static final byte RELAY_CELL = 3;
	private static final byte DESTORY_CELL = 4;	
	private static final byte OPEN_CELL = 5;
	private static final byte OPENED_CELL = 6;
	private static final byte OPEN_FAIL_CELL = 7;
	private static final byte CREATE_FAIL_CELL = 8;

	private static final byte BEGIN_RELAY_CMD = 1;
	private static final byte DATA_RELAY_CMD = 2;
	private static final byte END_RELAY_CMD = 3;
	private static final byte CONNECTED_RELAY_CMD = 4;
	private static final byte EXTEND_RELAY_CMD = 6;
	private static final byte EXTENDED_RELAY_CMD = 7;
	private static final byte BEGIN_FAILED_RELAY_CMD = 11;
	private static final byte EXTEND_FAIL_RELAY_CMD = 12;
	
	private static ByteBuffer bb;
	
	public static byte[] getCreateCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATE_CELL);
	}
	
	public static byte[] getCreatedCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATED_CELL);
	}

	public static ArrayList<byte[]> getRelayCell(String cmd, short circuit_id, short stream_id, String data) {
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
				ret.add(simpleRelaySubcellHelper(circuit_id, stream_id, EXTEND_FAIL_RELAY_CMD));
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
		return OpenCellHelper(circuit_id, OPEN_CELL, opener, opener);
	}
	
	public static byte[] getOpenedCell(short circuit_id, int opener, int opened) {
		return OpenCellHelper(circuit_id, OPENED_CELL, opener, opener);
	}

	public static byte[] getOpenFailCell(short circuit_id, int opener, int opened) {
		return OpenCellHelper(circuit_id, OPEN_FAIL_CELL, opener, opener);
	}
	
	public static byte[] getCreateFailCell(short circuit_id) {
		return CreateDestoryCellHelper(circuit_id, CREATE_FAIL_CELL);
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
