package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>FluteCyclingApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class FluteCyclingApplication extends org.scictrl.csshell.epics.server.application.cycling.CyclingApplication {

	private static final String SET_PV = "SET_PV";
	private static final String GET_PV = "GET_PV";
	
	/** Constant <code>STATUS_NOT_CYCLED="Status:NotCycled"</code> */
	public static final String STATUS_NOT_CYCLED = "Status:NotCycled";

	private boolean armed=false;
	private double levelLow=0.01;
	private double levelHigh=0.1;
	
	/**
	 * <p>Constructor for FluteCyclingApplication.</p>
	 */
	public FluteCyclingApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		levelLow= config.getDouble("usedLow", 0.01);
		levelHigh= config.getDouble("usedHigh", 0.1);

		addRecordOfMemoryValueProcessor(STATUS_NOT_CYCLED, "Bend has not been cycle since last switch off", DBRType.INT, 1);

		connectLinks(SET_PV, setPV);
		connectLinks(GET_PV, getPV);
	}
	
	private void updateNotCycled(boolean b) {
		if (getRecord(STATUS).getValueAsInt() == Status.CYCLING.ordinal()) {
			armed=false;
			getRecord(STATUS_NOT_CYCLED).setValue(false);
		} else {
			getRecord(STATUS_NOT_CYCLED).setValue(b);
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected void updateLastTimeCycled() {
		super.updateLastTimeCycled();
		armed=false;
		updateNotCycled(false);
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==GET_PV) {
			
			ValueLinks vl= getLinks(GET_PV);
			
			if (vl.isReady() && !vl.isInvalid() && !vl.isLastSeverityInvalid()) {
	
				double d= Math.abs(vl.consumeAsDoubles()[0]);
				
				if (d>levelHigh) {
					armed=true;
				} else if (d<levelLow && armed) {
					updateNotCycled(true);
				}
			}
		} 
	}

}
