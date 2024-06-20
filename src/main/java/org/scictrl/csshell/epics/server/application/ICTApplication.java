/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>ICTApplication converts charge readout from ADC (BCM - Beam Charge Monitor pickup) to beam charge value.</p>
 *
 * @author igor@scictrl.com
 */
public class ICTApplication extends AbstractApplication {

	private static final String Q = "Q";
	private static final String BCM = "Bcm";
	private double qcal;
	private double ucal;
	private String inputPV;

	/**
	 * <p>Constructor for ICTApplication.</p>
	 */
	public ICTApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		qcal= config.getDouble("qcal", 0.0);
		ucal= config.getDouble("ucal", 0.0);
		
		inputPV= config.getString("input");
		
		if (inputPV==null || inputPV.length()==0) {
			throw new IllegalArgumentException("Configured input PV is not set!");
		}
		
		addRecordOfOnLinkValueProcessor(BCM, "BCM input", DBRType.DOUBLE, inputPV);
		addRecordOfMemoryValueProcessor(Q, "Beam charge", 0.0, 10000.0, "pC", (short)3, 0.0);
		
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==BCM) {
			
			Record b= getRecord(BCM);
			
			double bcm= b.getValueAsDouble();
			double q= qcal*Math.pow(10.0, bcm/ucal);
			
			Record r= getRecord(Q);
			
			r.setValue(q);
			r.updateAlarm(b.getAlarmSeverity(), b.getAlarmStatus());
		}
	}

}
