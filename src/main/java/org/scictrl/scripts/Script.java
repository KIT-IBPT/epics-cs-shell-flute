package org.scictrl.scripts;

import java.text.DecimalFormat;

/**
 * Script generates points for array configuration.
 */
public class Script {

	/**
	 * Start
	 * @param args ignored
	 */
	public static void main(String[] args) {
		
		DecimalFormat df= new DecimalFormat("0.000"); 
		
		for (int i = 0; i < 2048; i++) {
			System.out.print(df.format(((double)(i*8))/1000.0));
			System.out.print(",");
		}
		
		
	}
	
	private Script() {
	}

}
