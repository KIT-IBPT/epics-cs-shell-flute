/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.time.Duration;
import java.time.Instant;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>BeamScanApplication scans the beam with four control values: power, phase, solenoid magnet and bending magnet.
 * It records beam position and shape and filters and averages the beam values.
 * Values with Q below configured threshold are discarded.</p>
 *
 * <p>Four parameters, which are beeing scannes: Power, Phase, Solenoid, Bend, have own set of scan properties and control PVs, which are being provided by {@link org.scictrl.csshell.epics.server.application.ScanApplication}, which is embedded in this application.</p>
 *
 * <p>Supported XML parameters</p>
 *
 * <ul>
 * <li>Power.precision, Phase.precision, Solenoid.precision, Bend.precision - precision for 4 scan modules, determines when scan value is close enough to target value. Default is 0.000001.</li>
 * <li>Power.setpointPV, Phase.setpointPV, Solenoid.setpointPV, Bend.setpointPV - PV name for corresponding record that is being scanned. Required.</li>
 * <li>Power.setpointCmdPV, Phase.setpointCmdPV, Solenoid.setpointCmdPV, Bend.setpointCmdPV - PV name for record that is necessary to be set after scan value has been set. Some devices need to apply value after has been set, optional.</li>
 * <li>beamSpotPv - PV for beam spot measurement device, expected device is {@link org.scictrl.csshell.epics.server.application.BeamSpotApplication}. Default value F:GL:BeamSpot:01.</li>
 * <li>measurementWait - wait time between beam measurements, in ms. Default value 10000.</li>
 * </ul>
 *
 * <p>Application provides PVs with following suffixes:</p>
 *
 * <ul>
 * <li>Access to embedded scanning tools PVs is provided with PV prefixes: Power, Phase, Solenoid, Bend. See {@link org.scictrl.csshell.epics.server.application.ScanApplication}.</li>
 * <li>Cmd:Start - Starts scanning task. Setting 1 triggers command.</li>
 * <li>Cmd:Stop - Stops scanning task. Setting 1 triggers command.</li>
 * <li>Meas:Last - Last beam measurement. Array with measurements.</li>
 * <li>Meas:Peak - Beam measurement at peak beam, that is largest beam area. Array with measurements.</li>
 * <li>Status - Scanning status. Enum with states: READY, SCANNING, ERROR</li>
 * <li>Status:Progress - Scanning progress, in %.</li>
 * <li>Status:Remaining - Estimated remaining time of scan. String with human friendly time format.</li>
 * <li>Status:Scanning - Boolean flag indicating scanning in progress. 0,1 as byte.</li>
 * <li>Wait - wait time between beam measurements. Float from 0 to 1000 s.</li>
 * </ul>
 *
 * @author igor@scictrl.com
 */
public class BeamScanApplication extends AbstractApplication {

	private static final String POS_H = ":Pos:H";
	private static final String POS_V = ":Pos:V";
	private static final String SIZE_H = ":Size:H";
	private static final String SIZE_V = ":Size:V";
	private static final String AREA = ":Area";
	
	private static final String MEASUREMENT_LAST = "Meas:Last";
	private static final String MEASUREMENT_PEAK = "Meas:Peak";
	private static final String CMD_STOP = "Cmd:Stop";
	private static final String CMD_START = "Cmd:Start";
	private static final String STATUS_SCANNING = "Status:Scanning";
	private static final String STATUS = "Status";
	private static final String STATUS_PROGRESS = "Status:Progress";
	private static final String STATUS_REMAINING = "Status:Remaining";
	private static final String WAIT = "Wait";

	/**
	 * <p>Measurement stores beam measurement data is na array. Values in array are in this order: posH, posV, sizeH, sizeV, area, power, phase, solenoid, bend. Class itself and fields are final, therefore immutable.</p>
	 */
	public final class Measurement {
		/**
		 * Horizontal beam position.
		 */
		public final double posH;
		/**
		 * Vertical beam position.
		 */
		public final double posV;
		/**
		 * Horizontal beam size.
		 */
		public final double sizeH;
		/**
		 * Vertical beam size.
		 */
		public final double sizeV;
		/**
		 * Beam spot area.
		 */
		public final double area;
		/**
		 * Klystron power.
		 */
		public final double power;
		/**
		 * Klystron phase.
		 */
		public final double phase;
		/**
		 * Solenoid magnet electrical current.
		 */
		public final double solenoid;
		/**
		 * Bending magnet electrical current.
		 */
		public final double bend;

		/**
		 * Created new measurement objects, fields are final, therefore immutable. 
		 * @param posH Horizontal beam position.
		 * @param posV Vertical beam position.
		 * @param sizeH Horizontal beam size.
		 * @param sizeV Vertical beam size.
		 * @param area Beam spot area.
		 * @param power Klystron power.
		 * @param phase Klystron phase.
		 * @param solenoid Solenoid magnet electrical current.
		 * @param bend Bending magnet electrical current.
		 */
		public Measurement(double posH, double posV, double sizeH, double sizeV, double area, double power, double phase, double solenoid, double bend) {
			this.posH=posH;
			this.posV=posV;
			this.sizeH=sizeH;
			this.sizeV=sizeV;
			this.area=area;
			this.power=power;
			this.phase=phase;
			this.solenoid=solenoid;
			this.bend=bend;
		}
		
		/**
		 * Creates new empty measurement with 0.0 values.
		 */
		public Measurement() {
			this(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0);
		}
		
		/**
		 * Returns values in an array.
		 * @return values in an array
		 */
		public double[] toArray() {
			return new double[]{posH,posV,sizeH,sizeV,area,power,phase,solenoid,bend};
		}
		
		/**
		 * Returns values in a String.
		 * @return values in a String
		 */
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append("ph=");
			sb.append(posH);
			sb.append(" pv=");
			sb.append(posV);
			sb.append(" sh=");
			sb.append(sizeH);
			sb.append(" sv=");
			sb.append(sizeV);
			sb.append(" ar=");
			sb.append(area);
			sb.append(" po=");
			sb.append(power);
			sb.append(" ph=");
			sb.append(phase);
			sb.append(" so=");
			sb.append(solenoid);
			sb.append(" be=");
			sb.append(bend);
			
			return sb.toString();
		}

	}

	private class ScanningTask implements Runnable {
		
		private boolean aborted=false;
		//double time=-1;

		public ScanningTask() {
		}
		
		public synchronized void abort() {
			aborted=true;
			notify();
		}
		
		public boolean isAborted() {
			return aborted;
		}
		
		@Override
		public synchronized void run() {
			
			log.debug("Scanning initiated");
			
			try {

				scanStart(this);
				
			} catch (Exception e) {
				log4error("Scanning failed "+e, e);
			}
			log.debug("Aborted");
			aborted=true;
			
			scanStop(this);
			
		}
	}
	
	
	private ScanApplication powerScan;
	private ScanApplication phaseScan;
	private ScanApplication solenoidScan;
	private ScanApplication bendScan;
	private String beamSpotPV;
	
	private long measurementWait=10000;
	private ScanningTask task;
	private Instant start;

	/**
	 * <p>Constructor for BeamScanApplication.</p>
	 */
	public BeamScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		powerScan = configureScan(name,"Power", config);
		phaseScan = configureScan(name,"Phase", config);
		solenoidScan = configureScan(name,"Solenoid", config);
		bendScan = configureScan(name,"Bend", config);
		
		beamSpotPV= config.getString("beamSpotPv", "F:GL:BeamSpot:01");
		measurementWait= config.getLong("measurementWait", 10000);
		
		connectLinks("beam", beamSpotPV+POS_H, beamSpotPV+POS_V, beamSpotPV+SIZE_H, beamSpotPV+SIZE_V, beamSpotPV+AREA);
		
		addRecordOfMemoryValueProcessor(MEASUREMENT_LAST, "Last beam measurement", -1000.0, 1000.0, "", (short)3, new Measurement().toArray());
		addRecordOfMemoryValueProcessor(MEASUREMENT_PEAK, "Peak beam measurement", -1000.0, 1000.0, "", (short)3, new Measurement().toArray());
		
		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{"READY","SCANNING","ERROR"}, (short)0);
		addRecordOfMemoryValueProcessor(CMD_STOP, "Stops scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(WAIT, "Wait for measurement", 0, 1000, "s", 0);
		
		Record r= getRecord(WAIT);
		r.setPersistent(true);
		
		if (r.getValueAsInt()==0) {
			r.setValue((int)(measurementWait/1000.0));
		} else {
			measurementWait=r.getValueAsInt()*1000;
		}
		

		if (getRecord(ERROR_SUM).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
		}
		if (getRecord(LINK_ERROR).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateLinkError(false, "");
		}

	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		database.schedule(new Runnable() {
			
			@Override
			public void run() {
				
				if (start!=null) {
					Duration d= Duration.between(start, Instant.now());
					
					double p= getRecord(STATUS_PROGRESS).getValueAsDouble();
					
					double t= d.toMillis()*(100.0/p-1.0);
					
					d= Duration.ofMillis((long)t);
					
					StringBuilder sb= new StringBuilder(128);
					long a= d.toHours();
					sb.append(a);
					sb.append("h ");
					d=d.minusHours(a);
					a=d.toMinutes();
					sb.append(a);
					sb.append("min ");
					d=d.minusMinutes(a);
					a=d.getSeconds();
					sb.append(a);
					sb.append("s");
					
					getRecord(STATUS_REMAINING).setValue(sb.toString());
				} else {
					getRecord(STATUS_REMAINING).setValue("N/A");
				}
				
			}
		}, 1000, 1000);
		
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void initialize(Database database) {
		super.initialize(database);
		
		database.addAll(powerScan.getRecords());
		database.addAll(phaseScan.getRecords());
		database.addAll(solenoidScan.getRecords());
		database.addAll(bendScan.getRecords());
	}
	
	private ScanApplication configureScan(String parent, String name, HierarchicalConfiguration config) {
		
		ScanApplication sa= new ScanApplication();
		HierarchicalConfiguration c= config.configurationAt(name);
		sa.configure(parent+":"+name, c);
		return sa;
		
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==WAIT) {
			Record r= getRecord(WAIT);
			measurementWait=r.getValueAsInt()*1000;
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (name==CMD_START) {
			if (task!=null) {
				log.debug("Scan request denied, scan in progress");
				return;
			}
			
			getRecord(STATUS).setValue(1);
			getRecord(STATUS_SCANNING).setValue(1);
			task= new ScanningTask();
			database.schedule(task, 0);
			
		} else if (name==CMD_STOP) {
			getRecord(STATUS).setValue(0);
			getRecord(STATUS_SCANNING).setValue(0);
			ScanningTask t=task;
			if (t!=null) {
				t.abort();
				scanStop(t);
			}
			task=null;
		}
	}

	private boolean canRun(ScanningTask t) {
		if (t==null || task==null || task!=t) {
			return false;
		}
		
		return !t.isAborted();
	}

	private void scanStop(ScanningTask t) {
		
		if (t!=task) {
			return;
		}
		
		start=null;

		powerScan.stopManualScan();
		phaseScan.stopManualScan();
		solenoidScan.stopManualScan();
		bendScan.stopManualScan();
		
		getRecord(STATUS).setValue(0);
		getRecord(STATUS_SCANNING).setValue(0);
		task=null;
	}

	private void scanStart(ScanningTask t) {
		
		start= Instant.now();
		
		double steps= powerScan.getStepCount() *  phaseScan.getStepCount() * solenoidScan.getStepCount() * bendScan.getStepCount();
		double step=0.0;
		
		Measurement peak=null;
		
		Record p= getRecord(STATUS_PROGRESS);
		
		p.setValue(0.0);
		
		powerScan.startManualScan();
		
		while(powerScan.isManualScanActive()) {
			
			phaseScan.startManualScan();

			while(phaseScan.isManualScanActive()) {
				
				solenoidScan.startManualScan();
			
				while(solenoidScan.isManualScanActive()) {

					bendScan.startManualScan();

					while(bendScan.isManualScanActive()) {
						
						if (!canRun(t)) {
							return;
						}
						
						synchronized (t) {
							try {
								t.wait(measurementWait);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						
						if (!canRun(t)) {
							return;
						}

						ValueLinks vl= getLinks("beam");
						
						if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
							
							double[] d= vl.consumeAsDoubles();
							
							Measurement m= new Measurement(d[0], d[1], d[2], d[3], d[4], powerScan.getSetpointValue(), phaseScan.getSetpointValue(), solenoidScan.getSetpointValue(), bendScan.getSetpointValue());
							
							getRecord(MEASUREMENT_LAST).setValue(m.toArray());
							
							log.debug("Measurement "+m.toString());
							
							if (peak==null || peak.area>m.area) {
								peak=m;
								getRecord(MEASUREMENT_PEAK).setValue(m.toArray());
								log.debug("Peak Measur "+m.toString());
							}
						}
						
						if (!canRun(t)) {
							return;
						}

						step++;
						p.setValue(step/steps*100.0);

						
						if (!canRun(t)) {
							return;
						}
						bendScan.stepManualScan();
						
					}
					
					if (!canRun(t)) {
						return;
					}
					solenoidScan.stepManualScan();
				}
				
				if (!canRun(t)) {
					return;
				}
				phaseScan.stepManualScan();
			}			
			
			if (!canRun(t)) {
				return;
			}
			powerScan.stepManualScan();
		}
		
		if (!canRun(t)) {
			return;
		}
		p.setValue(100.0);
	}
}
