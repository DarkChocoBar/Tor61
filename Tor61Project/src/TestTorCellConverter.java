import java.util.ArrayList;

public class TestTorCellConverter {
	public static void main(String[] args) {
		short s = 2;
		//(String cmd, short circuit_id, short stream_id, String data)

		String str = "Create a stream (on the circuit named in the header). A TCP "
				+ "connection to the [host identifier]:[port] given by the body data "
				+ "should be opened and associated with the stream.Create a stream (on "
				+ "the circuit named in the header). A TCP connection to the [host ide"
				+ "ntifier]:[port] given by the body data should be opened and associated "
				+ "with the stream.Create a stream (on the circuit named in the header). "
				+ "A TCP connection to the [host identifier]:[port] given by the body data "
				+ "should be opened and associated with the stream.Create a stream (on the "
				+ "circuit named in the header). A TCP connection to the [host identifier]:"
				+ "[port] given by the body data should be opened and associated with the "
				+ "stream.Create a stream (on the circuit named in the header). A TCP connection "
				+ "to the [host identifier]:[port] given by the body data should be opened "
				+ "and associated with the stream.";
		ArrayList<byte[]> ret = TorCellConverter.getRelayCell("data", s, s, str);		
		System.out.println(ret.size());

		for (int i = 0; i < ret.get(0).length; i++) { 
			System.out.println(i + ":\t" + ret.get(0)[i]);
		}
	}
}
