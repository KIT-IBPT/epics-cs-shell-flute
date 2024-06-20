/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>LaserApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class LaserApplication extends AbstractApplication {

	private static final String PULSE_PICKER_ENABLED = "PulsePicker:Enabled";
	private static final String PULSE_PICKER = "PulsePicker";
	
	private static final String PULSEGEN = "pulsegen";
	private static final String RESET = "reset";
	
	private String pulsegenPv;
	private int pulsegenValue1;
	private int pulsegenValue1k;
	private int pulsegenValueOff;
	private ValueLinks pulsegen;
	private boolean setting=false;
	private String resetPv;
	private int resetValue;
	private ValueLinks reset;

	/**
	 * <p>Constructor for LaserApplication.</p>
	 */
	public LaserApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		pulsegenPv= config.getString("pulsegenPv","F:TI:EVR:03:FPOut1:Map");
		pulsegenValue1= config.getInt("pulsegen1", 4);
		pulsegenValue1k= config.getInt("pulsegen1k", 2);
		pulsegenValueOff= config.getInt("pulsegenOff", 63);
		resetPv = config.getString("resetPv");
		resetValue= config.getInt("resetValue",0);
		
		
		pulsegen= connectLinks(PULSEGEN, pulsegenPv);
		
		if (resetPv!=null && resetPv.length()>0) {
			reset= connectLinks(RESET, resetPv);
		}
		
		addRecordOfMemoryValueProcessor(PULSE_PICKER, "Selects pulse picker", new String[]{"Trig","1000 Hz"}, (short)0);
		addRecordOfMemoryValueProcessor(PULSE_PICKER_ENABLED, "Switching pulse picker", DBRType.BYTE, 0);
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);

		if (name==PULSEGEN) {
		
			if (pulsegen.isReady() && !pulsegen.isInvalid() && !pulsegen.isLastSeverityInvalid()) {
				pulsegen2picker(pulsegen.consumeAsLongs()[0]);
			}
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==PULSE_PICKER || name==PULSE_PICKER_ENABLED) {
			int val= getRecord(PULSE_PICKER).getValueAsInt();
			boolean enabled= getRecord(PULSE_PICKER_ENABLED).getValueAsBoolean();
			picker2pulsegen(val, enabled);
		}
	}
	
	private void picker2pulsegen(int val, boolean enabled) {
		if (setting) {
			return;
		}
		setting=true;
		try {
			if (!enabled) {
				log4info("Picker enabled '"+enabled+"' mapped to '"+pulsegenValueOff+"'");
				pulsegen.setValue(pulsegenValueOff);
			} else if (val==0) {
				log4info("Picker value '"+val+"' mapped to '"+pulsegenValue1+"'");
				pulsegen.setValue(pulsegenValue1);
			} else if (val==1) {
				log4info("Picker value '"+val+"' mapped to '"+pulsegenValue1k+"'");
				pulsegen.setValue(pulsegenValue1k);
			} else {
				log4error("Picker value '"+val+"' not mapped.");
			}
		} catch (Exception e) {
			log.error("Pulsegen set failed: "+e.toString(), e);
		}
		reset(enabled);
		setting=false;
	}
	
	private void pulsegen2picker(long val) {
		setting=true;
		if (val==pulsegenValue1) {
			log4info("Pulsegen value '"+val+"' mapped to 1Hz+enabled");
			getRecord(PULSE_PICKER).setValue(0);
			getRecord(PULSE_PICKER_ENABLED).setValue(1);
		} else if (val==pulsegenValue1k) {
			log4info("Pulsegen value '"+val+"' mapped to 1kHz+enabled");
			getRecord(PULSE_PICKER).setValue(1);
			getRecord(PULSE_PICKER_ENABLED).setValue(1);
		} else {
			log4info("Pulsegen value '"+val+"' mapped to disabled");
			getRecord(PULSE_PICKER_ENABLED).setValue(0);
		}
		reset(getRecord(PULSE_PICKER_ENABLED).getValueAsBoolean());
		setting=false;
	}
	
	private void reset(boolean enabled) {
		
		if (!enabled || reset==null) {
			return;
		}
		
		try {
			reset.setValue(resetValue);
		} catch (Exception e) {
			log.error("Reset failed: "+e.toString(), e);
		}
		
	}
	
}
