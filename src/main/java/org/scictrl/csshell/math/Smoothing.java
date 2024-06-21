/**
 * 
 */
package org.scictrl.csshell.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Smoothing out measurement curve.
 */
public final class Smoothing {

	/**
	 * Sums elements sub-array for give data array
	 * @param data the data array 
	 * @param start start index of sub-array
	 * @param count the count of elements in sub-array
	 * @return sum of elements of sub-array
	 */
	public static double sum(final double[] data, final int start, final int count) {
		
		if (data==null) {
			throw new NullPointerException("Data array is null!");
		}
		
		if (start+count > data.length) {
			throw new IndexOutOfBoundsException("End of start '"+start+"' + count '"+count+"' is over '"+data.length+"'!");
		}
		
		double s= 0.0;

		for (int i = 0; i < count; i++) {
			s+=data[start+i];
		}
		
		return s;
	}
	
	/**
	 * Averages elements in sub-array for given data array
	 * @param data the data array 
	 * @param start start index of sub-array
	 * @param count the count of elements in sub-array
	 * @return average of elements of sub-array
	 */
	public static double avg(final double[] data, final int start, final int count) {
		double a= sum(data,start,count);
		return a/count;
	}
	
	/**
	 * Runs average over data, producing new average point for each sample count of data points. 
	 * Averaged data points is in the center of sub-array of   
	 * Averages at start and end of data array are made with less samples due to sample region reaches over array dimension.
	 * @param data the data array
	 * @param sample number of samples over which average is calculate for data points
	 * @return array with averaged points 
	 */
	public static final double[] smoothAvg(final double[] data, final int sample) {
		
		final int start= (int)(sample/2.0);
		final int end= sample-start;
		final double[] avg= new double[data.length];
		
		for (int i = 0; i < start; i++) {
			avg[i]= avg(data,0,end+i);
		}
		
		for (int i = start; i < avg.length-end; i++) {
			avg[i]= avg(data,i-start,sample);
		}
		
		for (int i = avg.length-end; i < avg.length; i++) {
			avg[i]= avg(data,i-start,avg.length-i+start);
		}
		
		return avg;
	}
	
	/**
	 * Collapses measurement data points where for same x value there is several y values by calculating average and collapse measurement to a single x, y(avg) point.
	 * @param x measurement on x side
	 * @param y corresponding measurement on y side
	 * @return combined two arrays [collapsed x array][averaged y array]
	 */
	public static final double[][] collapseSame(double[] x, double[] y) {
		List<Double> ax= new ArrayList<Double>(x.length);
		List<Double> ay= new ArrayList<Double>(y.length);

		double px=Double.NaN;
		int c=1;
		int k=-1;
		
		for (int i = 0; i < x.length && i < y.length; i++) {
			double vx= x[i];
			double vy= y[i];
			
			if (vx==0.0 && vy==0.0) {
				continue;
			}
			
			if (vx==px) {
				ay.set(k, ay.get(k)+vy);
				c++;
			} else {
				if (c>1) {
					ay.set(k, ay.get(k)/(double)c);
					c=1;
				}
				px=vx;
				ax.add(vx);
				ay.add(vy);
				k=ax.size()-1;
			}
		}
		if (c>1) {
			ay.set(k, ay.get(k)/(double)c);
		}
		
		double[][] r= new double[2][ax.size()];
		
		for (int i = 0; i < r[0].length; i++) {
			r[0][i]=ax.get(i);
			r[1][i]=ay.get(i);
		}
		
		return r;
	}
	
	private Smoothing() {
	}

}
