/**
 * 
 */
package org.scictrl.csshell.epics.server.processor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>SWRValueProcessor calculates SWR ratio between forward and reflected power.</p>
 *
 * <p>Configured by following parameters:</p>
 *
 * <ul>
 * <li>fwdPV - PV for record with forward power</li>
 * <li>refPV - PV for record with reflected power</li>
 * <li>minValue - minimal value, when power is below this value, then 0 is returned. Default value 0.01</li>
 * </ul>
 *
 * @author igor@scictrl.com
 */
public class SWRValueProcessor extends MemoryValueProcessor implements PropertyChangeListener {

	private static final String REF = "REF";
	private static final String FWD = "FWD";
	private String fwdPV;
	private String refPV;
	private ValueLinks ref;
	private ValueLinks fwd;
	private double minValue;
	private long lastValid= 0L;
	private double zeroValue;
	private boolean power;
	
	private Logger log= LogManager.getLogger(SWRValueProcessor.class);

	/**
	 * <p>Constructor for SWRValueProcessor.</p>
	 */
	public SWRValueProcessor() {
		
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		power= config.getBoolean("power",true);
		fwdPV= config.getString("fwdPV");
		refPV= config.getString("refPV");
		minValue= config.getDouble("minValue",0.1);
		zeroValue= config.getDouble("zeroValue",0.0001);
		
		log.info("Options: {} {} {} {} {} {}", record.getName(), power, minValue, zeroValue, refPV, fwdPV);
		
		if (fwdPV==null || fwdPV.length()==0) {
			throw new IllegalArgumentException("Configuration for 'fwdPV' not provided.");
		}
		if (refPV==null || refPV.length()==0) {
			throw new IllegalArgumentException("Configuration for 'refPV' not provided.");
		}
		
		fwd= new ValueLinks(FWD, fwdPV, this, Record.PROPERTY_VALUE);
		ref= new ValueLinks(REF, refPV, this, Record.PROPERTY_VALUE);
		
		_setValue(0.0, Severity.INVALID_ALARM, Status.UDF_ALARM, false, true);

	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		fwd.activate(getRecord().getDatabase());
		ref.activate(getRecord().getDatabase());
	}
	
	/** {@inheritDoc} */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		
		if (fwd.isReady() && 
				!fwd.isInvalid() && 
				!fwd.isLastSeverityInvalid() && 
				ref.isReady() && 
				!ref.isInvalid() && 
				!ref.isLastSeverityInvalid()) {
			
			double f= fwd.consumeAsDoubles()[0];
			double r= ref.consumeAsDoubles()[0];
			
			double d= 0.0;
			Severity se0= Severity.NO_ALARM;
			Status st0= Status.NO_ALARM;

			Severity se= fwd.getLastSeverity();
			Status st= fwd.getLastStatus();
			
			if (se!=null && st!=null && se.isGreaterThan(se0)) {
				se0=se;
				st0=st;
			}
			
			se= ref.getLastSeverity();
			st= ref.getLastStatus();

			if (se!=null && st!=null && se.isGreaterThan(se0)) {
				se0=se;
				st0=st;
			}
			
			if ( f>minValue && r>minValue ) {
 
				double dd= r/f;

				if (power) {
					dd= Math.sqrt(dd);
				}
				
				if ( Math.abs(dd)>zeroValue && Math.abs(1.0-dd)>zeroValue ) {

					d= (1.0+dd)/(1.0-dd);
					
					lastValid= System.currentTimeMillis();

				} else {
					
					if (System.currentTimeMillis()-lastValid> 10000) {
						lastValid=System.currentTimeMillis();
						log.debug("[{}] Calc invalid f:{} r:{} zero:{}",getName(),f,r,zeroValue);
						record.updateAlarm(Severity.INVALID_ALARM, Status.CALC_ALARM);
					}
					return;
				}
				
			}
			
			_setValue(d, se0, st0, true);
			
			
		} else {
			record.updateAlarm(Severity.INVALID_ALARM, Status.UDF_ALARM);
		}
		
	}

}
