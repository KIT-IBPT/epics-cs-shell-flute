/**
 * 
 */
package org.scictrl.csshell.epics.server.jdoocs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>PowerRatioValueProcessor , calculates logarithmic power ratio LLRF1 power PV. A workaround for LLRF1.</p>
 *
 * @deprecated NOT IN USE, was part of specific LLRF1 routine, not in use since LRF1 does not exist any more.
 *
 * @author igor@scictrl.com
 */
public class PowerRatioValueProcessor extends MemoryValueProcessor implements PropertyChangeListener {

	private double treshold;
	private String nomPV;
	private String denomPV;
	private ValueLinks inputs;

	/**
	 * <p>Constructor for PowerRatioValueProcessor.</p>
	 */
	public PowerRatioValueProcessor() {
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		treshold= config.getDouble("treshold", 0.00001);
		
		nomPV= config.getString("nomPV");
		denomPV= config.getString("denomPV");
		
		if (nomPV==null || nomPV.length()==0) {
			log.error("Configuration for '"+record.getName()+"' has no nomPV parameter!");
		}
		
		if (denomPV==null || denomPV.length()==0) {
			log.error("Configuration for '"+record.getName()+"' has no denomPV parameter!");
		}

		inputs= new ValueLinks(record.getName(), new String[]{nomPV,denomPV}, this, Record.PROPERTY_VALUE);

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
			
			double[] val= inputs.consumeAsDoubles();
			
			
			if (val==null || val.length!=2) { 
				return;
			}
			
			if (Math.abs(val[0])>treshold && Math.abs(val[1])>treshold) {
				
				double d= 10.0 * Math.log10(val[0]/val[1]);
				_setValue(d,Severity.NO_ALARM,Status.NO_ALARM,true);
			}
			
		}

	}
	
}
