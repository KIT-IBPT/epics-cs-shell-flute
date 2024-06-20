/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>Waveform summary calculator with conversion from amplitude to power.</p>
 * 
 * <p>Formula:</p>
 * 
 * <p>POWER = (AMPLITUDE/(CAL_SCA/0.8)*BIT_SCALING)^2 / POWER.SCALING</p>
 * 
 * <p>Where: </p>
 * 
 *
 * 
 * @author igor@scictrl.com
 */
public class LLRFWaveformSumApplication extends WaveformSumApplication {
	
	private static final String AMPL_SCALING=	"amplScalingPv";
	private static final String BIT_SCALING=	"bitScalingPv";
	private static final String POWER_SCALING=	"powerScalingPv";
	
	private static final String BUFFER=	"Buffer";
	
	
	private String amplScalingPv;
	private String bitScalingPv;
	private String powerScalingPv;
	
	private double factor=1.0;
	private double amplSc=1.0;
	private double bitSc=1.0;
	private double powerSc=1.0;

	private ValueLinks amplScaling;
	private ValueLinks bitScaling;
	private ValueLinks powerScaling;
	private Record buffer;


	/**
	 * Constructor. 
	 */
	public LLRFWaveformSumApplication() {
	}
	
	
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		factor=config.getDouble("factor", 0.8);

		amplScalingPv=config.getString(AMPL_SCALING, "F:RF:LLRF:02:Gun:Calibration:AmplScaling");
		bitScalingPv=config.getString(BIT_SCALING, "F:RF:LLRF:02:Gun:Calibration:BitScaling");
		powerScalingPv=config.getString(POWER_SCALING, "F:RF:LLRF:02:Gun:Calibration:PowerScaling");

		amplScaling= connectLinks(AMPL_SCALING,amplScalingPv);
		bitScaling= connectLinks(BIT_SCALING,bitScalingPv);
		powerScaling= connectLinks(POWER_SCALING,powerScalingPv);
		
		int size= config.getInt("size", 2048);
		
		buffer= addRecordOfMemoryValueProcessor(BUFFER, "Bufffer", size, DBRType.DOUBLE);
		
	}
	
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==AMPL_SCALING) {
			if (amplScaling.isReady() && !amplScaling.isInvalid()) {
				amplSc=amplScaling.consumeAsDoubles()[0];
				update();
			}
		} else if (name==BIT_SCALING) {
			if (bitScaling.isReady() && !bitScaling.isInvalid()) {
				bitSc=bitScaling.consumeAsDoubles()[0];
				update();
			}
		} else if (name==POWER_SCALING) {
			if (powerScaling.isReady() && !powerScaling.isInvalid()) {
				powerSc=powerScaling.consumeAsDoubles()[0];
				update();
			}
		}
	}
	
	@Override
	protected double[] extract(Record input) {
		double[] d= super.extract(input);
		
		/* POWER = (AMPLITUDE/(CAL_SCA/0.8)*BIT_SCALING)^2 / POWER.SCALING */

		double fac=  bitSc*factor/amplSc;
		fac=fac*fac/powerSc;
		
		for (int i = 0; i < d.length; i++) {
			double dd= d[i];
			d[i]= dd*dd*fac;
		}
		
		buffer.setValue(d);
		
		return d;
	}
	
	
	

}
