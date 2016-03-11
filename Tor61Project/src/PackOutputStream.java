import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 
 * 
 * DataOutputStream wrapper class
 * Writes to stream after unwrapping Tor Header
 *
 */
public class PackOutputStream extends OutputStream {
	private final byte RELAY_CMD = 3;
	private final byte RELAY_DATA_CMD = 2;
	
	private DataOutputStream stream;
	private short cid;
	private short stream_id;
	
	public PackOutputStream(DataOutputStream stream, short cid, short stream_id) {
		this.stream = stream;
		this.cid = cid;
		this.stream_id = stream_id;
	}
	
	@Override
	public void write(int b) throws IOException {
		stream.writeInt(b);
		
	}
	
	/**
	 * Writes to stream after packing HTTP with Tor Header
	 * @param b
	 * @throws IOException 
	 */
	public void write(byte[] b) throws IOException {
		int index = 0;
		while (0 < b.length) {
			byte[] data = Arrays.copyOfRange(b, index, Math.min(index + TorCellConverter.MAX_DATA_SIZE, b.length));
			byte[] header = getHeader((short) data.length);

			byte[] relayCell = new byte[data.length + header.length];
			System.arraycopy(header, 0, relayCell, 0, header.length);
			System.arraycopy(data, 0, relayCell, header.length, data.length);
			stream.write(relayCell);
			index += TorCellConverter.MAX_DATA_SIZE;
		}
	}
	
	private byte[] getHeader(short length) {
		ByteBuffer bb = ByteBuffer.allocate(TorCellConverter.CELL_HEADER_SIZE);
		bb.putShort(cid);
		bb.put(RELAY_CMD);
		bb.putShort(stream_id);
		bb.putShort((short) 0);			// 0x000 in header
		bb.putInt(0);					// digest
		bb.putShort((short) length);
		bb.put(RELAY_DATA_CMD);

		byte[] header = bb.array();
		bb.clear();
		return header;
	}
}