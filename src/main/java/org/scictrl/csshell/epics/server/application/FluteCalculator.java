package org.scictrl.csshell.epics.server.application;

/**
 * <p>FluteCalculator class.</p>
 *
 * @author igor@scictrl.com
 */
public final class FluteCalculator {
	
	
	
	
	private static FluteCalculator instance;
	
	/**
	 * <p>instance.</p>
	 *
	 * @return a {@link org.scictrl.csshell.epics.server.application.FluteCalculator} object
	 */
	public static FluteCalculator instance() {
		if (instance!=null) {
			return instance;
		}

		instance= new FluteCalculator();
		return instance;
	}
	

	private FluteCalculator() {
	}

	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main(String[] args) {

	}
	
	
	
	

}
