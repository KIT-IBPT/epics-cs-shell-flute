/**
 * 
 */
package org.scictrl.csshell.python;

import java.io.File;

/**
 * <p>Calculates emittance with Python script, written by Thiemo Schmelzer.</p>
 *
 * @author igor@scictrl.org
 */
public class EmittanceCalculator extends PythonRunner {

	
	
	private double[] quad_currents;
	private double[] beam_widths;
	private double[] beam_widths_std;
	private double lastEmittance;
	private double lastEmittanceStd;
	private double energy=5.81;

	/**
	 * <p>Constructor for EmittanceCalculator.</p>
	 */
	public EmittanceCalculator() {
	}
	
	/**
	 * <p>init.</p>
	 *
	 * @param dir a directory as {@link java.io.File} where scripts are Python located
	 * @param script a name of Python script to be used
	 */
	public void init(File dir, String script) {
		setDirectory(dir);
		setScript(script);
	}
	
	/**
	 * <p>Setter for the field <code>energy</code>.</p>
	 *
	 * @param energy a double
	 */
	public void setEnergy(double energy) {
		this.energy = energy;
	}
	
	/**
	 * <p>Getter for the field <code>energy</code>.</p>
	 *
	 * @return a double
	 */
	public double getEnergy() {
		return energy;
	}
	
	/**
	 * <p>inputs.</p>
	 *
	 * @param quad_currents an array of {@link double} objects
	 * @param beam_widths an array of {@link double} objects
	 * @param beam_widths_std an array of {@link double} objects
	 */
	public void inputs(double[] quad_currents, double[] beam_widths, double[] beam_widths_std) {
		
		if (quad_currents==null || beam_widths==null || beam_widths_std==null) {
			throw new NullPointerException("Input parameters contain null!");
		}
		
		if ((quad_currents.length != beam_widths.length) || (quad_currents.length != beam_widths_std.length)) {
			throw new IllegalArgumentException("Input arrays are not of same legngth");
		}
		
		this.quad_currents=quad_currents;
		this.beam_widths=beam_widths;
		this.beam_widths_std=beam_widths_std;
		
		
	}
	
	/**
	 * <p>calculateEmittance.</p>
	 *
	 * @return a Result object
	 */
	public Result calculateEmittance() {
		
		if (beam_widths==null) {
			throw new NullPointerException("beam_widths");
		}
		if (beam_widths_std==null) {
			throw new NullPointerException("beam_widths_std");
		}
		if (quad_currents==null) {
			throw new NullPointerException("quad_currents");
		}
		
		double[] inp= new double[1+quad_currents.length*3];
		
		inp[0]=energy;
		
		for (int i = 0; i < quad_currents.length; i++) {
			inp[1+i]=quad_currents[i];
			inp[1+quad_currents.length+i]=beam_widths[i];
			inp[1+quad_currents.length*2+i]=beam_widths_std[i];
		}
		
		Result r= executeArrayTransaction(inp);
		
		if (r.hasData()) {
			if(r.data.length>0) {
				lastEmittance=r.data[0];
			}
			if(r.data.length>1) {
				lastEmittanceStd=r.data[1];
			}
		} else {
			lastEmittance=0.0;
			lastEmittanceStd=0.0;
		}
		
		return r;
	}
	
	/**
	 * <p>Getter for the field <code>lastEmittance</code>.</p>
	 *
	 * @return a double
	 */
	public double getLastEmittance() {
		return lastEmittance;
	}
	
	/**
	 * <p>Getter for the field <code>lastEmittanceStd</code>.</p>
	 *
	 * @return a double
	 */
	public double getLastEmittanceStd() {
		return lastEmittanceStd;
	}

}
