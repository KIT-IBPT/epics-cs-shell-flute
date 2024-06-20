/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>PowerSplitterApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class PowerSplitterApplication extends AbstractApplication {

	private static final String GUN_PART = "Gun:Part";
	private static final String LINAC_PART = "Linac:Part";
	private static final String GUN = "Gun";
	private static final String LINAC = "Linac";
	private static final String POSITION= "Position";
	private double linacPos;
	private double gunPos;
	private String inputPV;

	/**
	 * <p>Constructor for PowerSplitterApplication.</p>
	 */
	public PowerSplitterApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		gunPos=   config.getDouble("gunPos", 0.0);
		linacPos= config.getDouble("linacPos", 0.0);
		
		inputPV= config.getString("link");
		
		if (inputPV==null || inputPV.length()==0) {
			throw new IllegalArgumentException("Configured input PV is not set!");
		}
		
		addRecordOfOnLinkValueProcessor(POSITION, "BCM input", DBRType.DOUBLE, inputPV);

		addRecordOfMemoryValueProcessor(GUN_PART, "Gun power procentage", 0.0, 100.0, "%", (short)1, 0.0);
		addRecordOfMemoryValueProcessor(LINAC_PART, "Linac power procentage", 0.0, 100.0, "%", (short)1, 0.0);
		
		addRecordOfMemoryValueProcessor(GUN, "Gun gets 90% power", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(LINAC, "Linac gets 90% power", DBRType.BYTE, false);
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==POSITION) {
			
			Record b= getRecord(POSITION);
			
			double pos= b.getValueAsDouble();
			
			double d= linacPos-gunPos;
			
			double g= (1.0 - Math.abs(gunPos-pos)/d) * 100.0;
			double l= (1.0 - Math.abs(linacPos-pos)/d) * 100.0;
			
			if (g<0.0) {
				g= 0.0;
			}
			if (g>100.0) {
				g=100.0;
			}
			
			if (l<0.0) {
				l= 0.0;
			}
			if (l>100.0) {
				l=100.0;
			}
			
			getRecord(GUN_PART).setValue(g);
			getRecord(LINAC_PART).setValue(l);
			
			getRecord(GUN).setValue(g>90.0);
			getRecord(LINAC).setValue(l>90.0);

			getRecord(GUN_PART).updateAlarm(b.getAlarmSeverity(), b.getAlarmStatus());
			getRecord(LINAC_PART).updateAlarm(b.getAlarmSeverity(), b.getAlarmStatus());
			getRecord(GUN).updateAlarm(b.getAlarmSeverity(), b.getAlarmStatus());
			getRecord(LINAC).updateAlarm(b.getAlarmSeverity(), b.getAlarmStatus());

		}
	}

}
