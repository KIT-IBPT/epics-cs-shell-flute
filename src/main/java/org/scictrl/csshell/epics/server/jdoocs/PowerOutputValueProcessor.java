/**
 * 
 */
package org.scictrl.csshell.epics.server.jdoocs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>PowerOutputValueProcessor, calculates output power from attenuation and LLRF1 power PV. A workaround for LLRF1.</p>
 *
 * @deprecated NOT IN USE, was part of specific LLRF1 routine, not in use since LRF1 does not exist any more.
 * 
 * @author igor@scictrl.com
 */
public class PowerOutputValueProcessor extends MemoryValueProcessor implements PropertyChangeListener {

	static final private String POWER_PV="powerPV"; 
	static final private String ATTEN_PV="attenPV"; 
	
	private String powerPV;
	private String attenPV;
	private ValueLinks inputs;
	private double powerFactor=10.0;
	private double unitFactor=1000000.0;

	/**
	 * <p>Constructor for PowerOutputValueProcessor.</p>
	 */
	public PowerOutputValueProcessor() {
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		powerPV= config.getString(POWER_PV);
		attenPV= config.getString(ATTEN_PV);
		
		if (powerPV==null || powerPV.length()==0) {
			log.error("Configuration for '"+record.getName()+"' has no powerPV parameter!");
		}
		
		if (attenPV==null || attenPV.length()==0) {
			log.error("Configuration for '"+record.getName()+"' has no attenPV parameter!");
		}

		inputs= new ValueLinks(record.getName(), new String[]{powerPV,attenPV}, this, Record.PROPERTY_VALUE);

		powerFactor= config.getDouble("powerFactor", 10.0);
		unitFactor= config.getDouble("unitFactor", 1000000.0);
	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		inputs.activate(record.getDatabase());
	}
	
	/** {@inheritDoc} */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName()==Record.PROPERTY_VALUE) {

			if (inputs==null) {
				return;
			}
			if (inputs.isInvalid()) {
				record.updateAlarm(Severity.INVALID_ALARM,Status.LINK_ALARM,true);
				return;
			}
			if (!inputs.isReady()) {
				return;
			}
			
			ValueHolder[] val= inputs.consume();
			
			if (val==null || val.length!=2) { 
				return;
			}
			
			double[] pow= val[0].doubleArrayValue();
			double atten= val[1].doubleValue();
			
			double att= Math.pow(10.0, atten / powerFactor);

			double[] out= new double[pow.length];
			
			if (record.getCount()==1) {
				record.setCount(out.length);
			}

			for (int i = 0; i < out.length; i++) {
				out[i]= pow[i] * att / unitFactor;
			}
			
			_setValue(out,Severity.NO_ALARM,Status.NO_ALARM,true);
		}

	}
	
}
