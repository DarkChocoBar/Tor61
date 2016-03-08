public class TestRegistrationAgentObject {

	public static void main(String[] args) {
		RegistrationAgent agent = new RegistrationAgent();
		System.out.println("About to test run");
		agent.register("r 44444 12345678 TestAgent");
		System.out.println("Finished testing run");
		System.out.println("About to sleep for 240 seconds");
		try {
			Thread.sleep(250000);
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		System.out.println("About to test quit on agent");
		agent.quit();
		System.out.println("About to sleep for 20 seconds");
		try {
			Thread.sleep(20000);
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		System.out.println("Goodbye!");
	}
}