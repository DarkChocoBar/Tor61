public class TestTorCellConverter {
	public static void main(String[] args) {
		short s = 2;
		byte[] b = TorCellConverter.getOpenCell(s, 5, 5);
		for (int i = 0; i < b.length; i++) { 
			System.out.println(i + ":\t" + b[i]);
		}
	}
}
