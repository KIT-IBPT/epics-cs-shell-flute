/**
 * 
 */
package org.scictrl.csshell.epics.server.jdoocs;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.application.AbstractApplication;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>PowerInterlockApplication class, NOT IN USE.</p>
 * 
 * @deprecated NOT IN USE, was part of specific LLRF1 routine, not in use since LRF1 does not exist any more.
 * 
 * @author igor@scictrl.com
 */
public class PowerInterlockApplication extends AbstractApplication {

	/** Constant <code>ENABLED="enabled"</code> */
	public static final String ENABLED = "enabled";
	/** Constant <code>INPUT="input"</code> */
	public static final String INPUT = "input";
	/** Constant <code>OUTPUT1="output1"</code> */
	public static final String OUTPUT1 = "output1";
	/** Constant <code>OUTPUT2="output2"</code> */
	public static final String OUTPUT2 = "output2";
	/** Constant <code>TRESHOLD="treshold"</code> */
	public static final String TRESHOLD = "treshold";
	/** Constant <code>INTERLOCK="interlock"</code> */
	public static final String INTERLOCK = "interlock";
	
	private double treshold;
	private double output1;
	private double output2;
	private String inputPV;
	private String outputPV1;
	private String outputPV2;
	private boolean enabled;

	/**
	 * <p>Constructor for PowerInterlockApplication.</p>
	 */
	public PowerInterlockApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		inputPV= config.getString("inputPV");
		outputPV1= config.getString("outputPV1");
		outputPV2= config.getString("outputPV2");
		
		if (inputPV==null || inputPV.length()==0) {
			log.error("Configuration for '"+name+"' has no inputPV parameter!");
		}
		if (outputPV1==null || outputPV1.length()==0) {
			log.error("Configuration for '"+name+"' has no outputPV1 parameter!");
		}
		if (outputPV2==null || outputPV2.length()==0) {
			log.error("Configuration for '"+name+"' has no outputPV2 parameter!");
		}
		
		
		output1=config.getDouble("output1", 0.0);
		output2=config.getDouble("output2", 0.0);
		
		this.enabled = config.getBoolean(ENABLED, false);

		addRecord(INPUT, LinkedValueProcessor.newProcessor(this.name+nameDelimiter+"Input", DBRType.DOUBLE, "Inpit value for the feedback.", inputPV).getRecord());
		addRecord(OUTPUT1, LinkedValueProcessor.newProcessor(this.name+nameDelimiter+"Output1", DBRType.DOUBLE, "Current output value.", outputPV1).getRecord());
		addRecord(OUTPUT2, LinkedValueProcessor.newProcessor(this.name+nameDelimiter+"Output2", DBRType.DOUBLE, "Current output value.", outputPV2).getRecord());
		addRecord(ENABLED, MemoryValueProcessor.newBooleanProcessor(this.name+nameDelimiter+"Enabled", "Feedback loop is enabled and active.", enabled,false, false).getRecord());
		addRecord(INTERLOCK, MemoryValueProcessor.newBooleanProcessor(this.name+nameDelimiter+"Interlock", "Interlock has been triggered.", false,false, false).getRecord());
		addRecord(TRESHOLD, MemoryValueProcessor.newDoubleProcessor(this.name+nameDelimiter+"Treshold", "Trigger treshold.",0.0,false).getRecord());
		
		getRecord(TRESHOLD).setPersistent(true);
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		if (name==INPUT) {
			if (getRecord(INPUT).isAlarmUndefined()) {
				return;
			}
			//log.debug("Input '"+inputPV+"' change "+getRecord(INPUT).getValueAsDouble());
			checkInterlock();
		} else if (name==ENABLED) {
			this.enabled = getRecord(ENABLED).getValueAsBoolean();
			log.debug("Enabled change "+enabled);
			if (enabled) {
				checkInterlock();
			}
		} else if (name==TRESHOLD) {
			this.treshold = getRecord(TRESHOLD).getValueAsDouble();
			log.debug("Treshold change "+treshold);
			checkInterlock();
		} 
	}

	private void checkInterlock() {
		
		if (!enabled) {
			return;
		}
		
		double input= getRecord(INPUT).getValueAsDouble();

		if (input>=treshold) {
			return;
		}
		
		try {
			((LinkedValueProcessor)getRecord(OUTPUT1).getProcessor()).setValue(new double[]{output1});
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
			log.info("Setting out '"+output1+"' to '"+outputPV1+"'.");
		} catch (Exception e) {
			log.error("Remote setting of '"+outputPV1+"' failed "+e.toString(), e);
			updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
			return;
		}
		try {
			((LinkedValueProcessor)getRecord(OUTPUT2).getProcessor()).setValue(new double[]{output2});
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
			log.info("Setting out '"+output2+"' to '"+outputPV2+"'.");
		} catch (Exception e) {
			log.error("Remote setting of '"+outputPV2+"' failed "+e.toString(), e);
			updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
			return;
		}
		
		getRecord(INTERLOCK).getProcessor().setValue(true);
	}

}
