/**
 * 
 */
package org.scictrl.csshell.math;

/**
 * Searches pattern in array of data.
 */
public final class PatternSearch {

	private PatternSearch() {
	}
	
	/**
	 * Finds breakpoints, which are in this order: High1, Low1, High2; where High2 is higher than High1.
	 * @param data array with measurements to be searched
	 * @param threshold measurement data values below this threshold are ignored
	 * @return array with three indexes for breakpoints (High1, Min1, High2), if value is -1 then breakpoint has not been found
	 */
	public static final int[] findBreakpointsHiLoHHi(double[] data, double threshold) {
		
		double max1=Double.MIN_VALUE; // first max point, lover than max2
		@SuppressWarnings("unused")
		double min1=Double.MAX_VALUE; // first min point, between max1 and max2
		double max2=Double.MIN_VALUE; // second max point, higher than max1


		int max1n=-1; // first max point, lover than max2
		int min1n=-1; // first min point, between max1 and max2
		int max2n=-1; // second max point, higher than max1

		// first find total max
		for (int i = 0; i < data.length ; i++) {
			double d = data[i];
			if (d>threshold && d>max2) {
				max2n=i;
				max2=d;
			}
		}

		double min1S=max2; // first min point, between max1 and max2, forward seeking value  
		int min1nS=-1; // first min point, between max1 and max2, forward seeking value

		// now search before max
		for (int i = max2n-1; i >=0 ; i--) {
			double d = data[i];
			if (d>threshold && d<min1S) {
				min1S=d;
				min1nS=i;
				max1=0.0;
			} else if (min1nS>-1 && d>threshold && d>max1) {
				max1n=i;
				max1=d;
				min1=min1S; // remember last good min
				min1n=min1nS;
			}
		}
		
		return new int[]{max1n,min1n,max2n};
	}


}
