/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;
import org.scictrl.csshell.epics.server.processor.RunningAverageValueProcessor.RunningAverageBufferCalculator;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * Filters and averages the BPM values. Values with Q below treshold ar thrown away.
 *
 * @author igor@scictrl.com
 */
public class BPMCalculatorApplication extends AbstractApplication {

	private static final String VALID = "valid";
	private static final String BPM = "bpm";
	private static final String POS_Q = "Q";
	private static final String POS_Y = "Y";
	private static final String POS_X = "X";
	private double treshold;
	private String bpmPV;
	private int count;
	private ValueLinks val;
	private RunningAverageBufferCalculator calcx;
	private RunningAverageBufferCalculator calcy;
	private RunningAverageBufferCalculator calcq;
	private long timeWindow;
	private String suffix;
	private String namePosX;
	private String namePosY;
	private String namePosQ;
	private ValueLinks valid;

	/**
	 * <p>Constructor for BPMCalculatorApplication.</p>
	 */
	public BPMCalculatorApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		treshold= config.getDouble("treshold", 1.1);
		count= config.getInt("count", 1);
		timeWindow= config.getLong("time_window", 500);
		suffix= config.getString("suffix",":xxx");

		int i= name.lastIndexOf(':');
		if (i>-1) {
			bpmPV= name.substring(0, i);
		}
		
		if (bpmPV==null) {
			throw new IllegalArgumentException("The application name '+name+' does not contain BPM PV");
		}
		
		namePosX= POS_X+suffix;
		namePosY= POS_Y+suffix;
		namePosQ= POS_Q+suffix;
		
		//addRecordOfMemoryValueProcessor(namePosX, "Filtered pos X", -100.0, 100.0, "mm", (short)5, 0.0);
		//addRecordOfMemoryValueProcessor(namePosY, "Filtered pos Y", -100.0, 100.0, "mm", (short)5, 0.0);
		//addRecordOfMemoryValueProcessor(namePosQ, "Filtered tune Q", -100.0, 100.0, "mm", (short)5, 0.0);
		//addRecordOfMemoryValueProcessor(namePosValid, "Position valid", DBRType.BYTE, (byte)1);
		
		addRecord(POS_X, MemoryValueProcessor.newProcessor(bpmPV+nameDelimiter+namePosX, DBRType.DOUBLE, 1, "Filtered pos X", 0.0, false, -100.0, 100.0, "mm", (short)5).getRecord());
		addRecord(POS_Y, MemoryValueProcessor.newProcessor(bpmPV+nameDelimiter+namePosY, DBRType.DOUBLE, 1, "Filtered pos X", 0.0, false, -100.0, 100.0, "mm", (short)5).getRecord());
		addRecord(POS_Q, MemoryValueProcessor.newProcessor(bpmPV+nameDelimiter+namePosQ, DBRType.DOUBLE, 1, "Filtered pos X", 0.0, false, -100.0, 100.0, "", (short)5).getRecord());

		
		val= connectLinks(BPM, bpmPV+":X", bpmPV+":Y", bpmPV+":Q");
		valid= connectLinks(VALID, bpmPV+":X-VALID", bpmPV+":Y-VALID", bpmPV+":Q-VALID");
		
		calcx= new RunningAverageBufferCalculator(count);
		calcy= new RunningAverageBufferCalculator(count);
		calcq= new RunningAverageBufferCalculator(count);
		
	}
	
	private boolean valid() {
		if (!valid.isInvalid() && valid.isReady() && !valid.isLastSeverityInvalid()) {
			return valid.consumeAsBooleanAnd();
		}
		return true;
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name == BPM) {
			if (!val.isReady()) {
				return;
			}
			
			if (val.isInvalid()) {
				updateAlarm(Severity.INVALID_ALARM, Status.UDF_ALARM);
				return;
			}
			
			Severity sev= val.getLastSeverity();
			Status sta= val.getLastStatus();
			
			ValueHolder[] vh= val.consume();
			
			//System.out.println("X "+vh[0].timestamp+" "+vh[0].doubleValue());
			//System.out.println("Y "+vh[1].timestamp+" "+vh[1].doubleValue());
			//System.out.println("Q "+vh[2].timestamp+" "+vh[2].doubleValue());
	
			if (
					Math.abs(vh[0].timestamp-vh[1].timestamp)>timeWindow || 
					Math.abs(vh[0].timestamp-vh[2].timestamp)>timeWindow || 
					Math.abs(vh[1].timestamp-vh[2].timestamp)>timeWindow) {
				//System.out.println("Out of time window "+timeWindow);
				return;
			}
			
			boolean valid= valid();
			
			if (!valid) {
				return;
			}
			
			double q= vh[2].doubleValue();
			if (q>treshold) {
				calcx.add(vh[0].doubleValue());
				calcy.add(vh[1].doubleValue());
				calcq.add(q);
				updateAlarm(sev, sta);
				getRecord(POS_X).setValue(calcx.avg);
				getRecord(POS_Y).setValue(calcy.avg);
				getRecord(POS_Q).setValue(calcq.avg);
			}
		}		
		
	}
	
	private void updateAlarm(Severity sev, Status sta) {
		updateErrorSum(sev, sta);
		getRecord(POS_X).updateAlarm(sev, sta);
		getRecord(POS_Y).updateAlarm(sev, sta);
		getRecord(POS_Q).updateAlarm(sev, sta);
	}
	
	

}
