/**
 *
 * Object that encapsulates the tcp connection opener, openee relationship
 */
public class Opener {
	private int opener;
	private int openee;
	
	public Opener(int opener, int openee) {
		this.opener = opener;
		this.openee = openee;
	}
	
	/**
	 * Return the opener's id
	 * @return the opener's id
	 */
	public int getOpener() {
		return opener;
	}
	
	/**
	 * Return the openee's id
	 * @return the openee's id
	 */
	public int getOpenee() {
		return openee;
	}
	
	/**
	 * Returns wither the user was an opener or not
	 * @param opener opener id
	 * @return true if the opener id matches the opener and false otherwise
	 */
	public boolean isOpener(int opener) {
		return this.opener == opener;
	}
	
	@Override
	public String toString() {
		return "Opener: " + opener + " Openee: " + openee;
	}
}