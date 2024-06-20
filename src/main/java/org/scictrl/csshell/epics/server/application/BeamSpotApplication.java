/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.processor.RunningAverageValueProcessor.RunningAverageBufferCalculator;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>BeamSpotApplication connects server with direct beam parameters, then filters and averages them and provides summary data. Measurements with Q below threshold are discarded as unreliable.</p>
 *
 * <p>Supported XML parameters</p>
 *
 * <ul>
 * <li>count - number of measurements to be taken into averaging. Default 1.</li>
 * <li>time_window - only calculate averages, if timestamps of received values are within this time window. </li>
 * <li>inputPvs.size_h - input PV for size in H</li>
 * <li>inputPvs.size_v - input PV for size in V</li>
 * <li>inputPvs.pos_h - input PV for position in H</li>
 * <li>inputPvs.pos_v - input PV for position in V</li>
 * <li>inputPvs.chi2_h - input PV for chi square in H</li>
 * <li>inputPvs.chi2_v - input PV for chi square in V</li>
 * <li>inputPvs.fwhm_h - input PV for FWHM in H</li>
 * <li>inputPvs.fwhm_v - input PV for FWHM in V</li>
 * <li>range.h_min - lower limit for acceptable position range in H, default -8.0</li>
 * <li>range.h_max - upper limit for acceptable position range in H, default 8.0</li>
 * <li>range.v_min - lower limit for acceptable position range in V, default -8.0</li>
 * <li>range.v_max - upper limit for acceptable position range in V, default 8.0</li>
 * <li>range.center - limit for acceptable position range center, default 0.1</li>
 * <li>range.deviation - limit for acceptable averaging deviation, default 0.1</li>
 * <li>range.area - limit for acceptable beam spot area size, default 0.1</li>
 * <li>range.chi2_min - lower limit for acceptable Chi square, default 0.0</li>
 * <li>range.chi2_max - upper limit for acceptable Chi square, default 1.0</li>
 * <li>laserSwitchPv - PV for switching on/off the laser, default F:LAS:Timing:01:PulsePicker:Enabled</li>
 * <li>findBeamPv - PV for automatic beam recognition by frame grabber, default F:GL:Python:01:DiagCam:Do_ROI</li>
 * <li>takeBackgroundPv - PV for taking fresh screen background fro background substractions, default F:GL:Python:01:DiagCam:Take_New_BG</li>
 * <li>takeBackgroundBusyPv - PV for signal notifying frame grabber is bussy with backgroudn measurement, default F:GL:Python:01:DiagCam:BG_proc</li>
 * </ul>
 *
 *
 * <p>Application provides PVs with following suffixes:</p>
 *
 * <ul>
 * <li>Pos:H - Averaged position in horizontal, in mm as float.</li>
 * <li>Pos:H:Std - STD for horizontal position, in mm as float.</li>
 * <li>Pos:V - Averaged position vertical, in mm as float.</li>
 * <li>Pos:V:Std - STD for vertical position, in mm as float.</li>
 * <li>Size:H - Averaged size in horizontal, in mm as float.</li>
 * <li>Size:H:Std - STD for horizontal size, in mm as float.</li>
 * <li>Size:V - Averaged size in vertical, in mm as float.</li>
 * <li>Size:V:Std - STD for vertical size, in mm as float.</li>
 * <li>Chi2:H - Maximal Chi square in horizontal, float.</li>
 * <li>Chi2:V - Maximal Chi square in vertical, float.</li>
 * <li>Fwhm:H - Averaged FWHM square in horizontal, float.</li>
 * <li>Fwhm:V - Averaged FWHM square in vertical, float.</li>
 * <li>Area - Averaged beam spot area, mm^2 as float.</li>
 * <li>Area:Std - STD for beam spot area, mm^2 as float.</li>
 * <li>Area:Min - minimal beam spot area out of averaged, mm^2 as float.</li>
 * <li>Deviation - Deviation of averages, float.</li>
 * <li>Count - Averaging count, float.</li>
 * <li>Data - Array with combined essential data, see {@link org.scictrl.csshell.epics.server.application.BeamSpotApplication.BeamSpotData} for field descriptions.</li>
 * <li>Status:InRange - Beam position within range window, status bit as byte.</li>
 * <li>Status:Stable - Beam position reading stable, status bit as byte.</li>
 * <li>Status:FitGood - Beam gauss fit chi square below threshold, status bit as byte.</li>
 * <li>Status:Centred - Beam position centred on 0, status bit as byte.</li>
 * <li>Status:Small - Beam area below threshold, status bit as byte.</li>
 * <li>Status:Valid - Beam position valid, status bit as byte.</li>
 * <li>Range:Area - limit for acceptable beam spot area size, mm^2 as float.</li>
 * <li>Range:Center - limit for acceptable position range center, mm as float.</li>
 * <li>Range:Deviation - limit for acceptable averaging deviation, float.</li>
 * <li>Range:Hor:Min - lower limit for acceptable position range in H, mm as float.</li>
 * <li>Range:Hor:Max - upper limit for acceptable position range in H, mm as float.</li>
 * <li>Range:Ver:Min - lower limit for acceptable position range in V, mm as float.</li>
 * <li>Range:Ver:Max - upper limit for acceptable position range in V, mm as float.</li>
 * <li>Range:Chi2:Min - lower limit for acceptable Chi square, float.</li>
 * <li>Range:Chi2:Max - upper limit for acceptable Chi square, float.</li>
 * <li>Status:BgBusy - Busy taking background measurement, status bit as byte.</li>
 * <li>Cmd:BgTake - Command to take beam background measurement, 1 triggers command.</li>
 * </ul>
 *
 * @author igor@scictrl.com
 */
public class BeamSpotApplication extends AbstractApplication {
	/**
	 * Enumeration defining name and position of parameters in data array with combined measurements returned trough {@link BeamSpotApplication#DATA} PV.
	 */
	public static enum BeamSpotData {
		/**
		 * Horizontal beam position.
		 */
		POS_H,
		/**
		 * Vertical beam position.
		 */
		POS_V,
		/**
		 * Horizontal beam size.
		 */
		SIZE_H,
		/**
		 * Vertical beam size.
		 */
		SIZE_V,
		/**
		 * Beam spot area.
		 */
		AREA,
		/**
		 * Chi square horizontal.
		 */
		CHI2_H,
		/**
		 * Chi square vertical.
		 */
		CHI2_V,
		/**
		 * FWHM square horizontal.
		 */
		FWHM_H,
		/**
		 * FWHM square vertical.
		 */
		FWHM_V,
		/**
		 * Deviation of averages.
		 */
		DEV,
		/**
		 * Beam position within range window
		 */
		ST_RANGE,
		/**
		 * Beam position reading stable
		 */
		ST_STABLE,
		/**
		 * Beam gauss fit chi square below treshold
		 */
		ST_FIT,
		/**
		 * Beam position centred on 0
		 */
		ST_CENTER,
		/**
		 * Beam area below treshold
		 */
		ST_SMALL,
		/**
		 * Beam position valid
		 */
		ST_VALID,
		/**
		 * Horizontal beam size average STD.
		 */
		SIZE_H_STD,
		/**
		 * Vertical beam size average STD.
		 */
		SIZE_V_STD
	}

	private static final String LINK_LASER_ENABLED = "LaserSwitch";
	private static final String LINK_BACKGROUND_TAKE = "BackgroundTake";
	private static final String LINK_BACKGROUND_BUSY = "BackgroundBusy";
	private static final String LINK_FIND_BEAM_ENABLED = "FindBeamEnabled";

	private static final long T_WAIT_FOR_LASER = 2000;
	private static final long T_WAIT_FOR_BG = 1000;

	private static final String POS_H = 		"Pos:H";
	private static final String POS_H_STD = 	"Pos:H:Std";
	private static final String POS_V = 		"Pos:V";
	private static final String POS_V_STD = 	"Pos:V:Std";
	private static final String SIZE_H = 		"Size:H";
	private static final String SIZE_H_STD = 	"Size:H:Std";
	private static final String SIZE_V = 		"Size:V";
	private static final String SIZE_V_STD = 	"Size:V:Std";
	private static final String CHI2_H =  		"Chi2:H";
	private static final String CHI2_V =  		"Chi2:V";
	private static final String FWHM_H =  		"Fwhm:H";
	private static final String FWHM_V =  		"Fwhm:V";
	private static final String AREA = 			"Area";
	private static final String AREA_STD = 		"Area:Std";
	private static final String AREA_MIN = 		"Area:Min";
	private static final String DEVIATION = 	"Deviation";
	private static final String COUNT = 		"Count";
	
	/**
	 * Suffix for PV name which provides array with combined data.
	 */
	public static final String DATA = 			"Data";
	
	private static final String STATUS_IN_RANGE = 	"Status:InRange";
	private static final String STATUS_STABLE = 	"Status:Stable";
	private static final String STATUS_FIT_GOOD = 	"Status:FitGood";
	private static final String STATUS_CENTRED = 	"Status:Centred";
	private static final String STATUS_SMALL = 		"Status:Small";
	private static final String STATUS_VALID = 		"Status:Valid";
	
	private static final String RANGE_AREA =	 "Range:Area";
	private static final String RANGE_CENTER =	 "Range:Center";
	private static final String RANGE_DEVIATION= "Range:Deviation";
	private static final String RANGE_HOR_MIN =	 "Range:Hor:Min";
	private static final String RANGE_HOR_MAX =	 "Range:Hor:Max";
	private static final String RANGE_VER_MIN =	 "Range:Ver:Min";
	private static final String RANGE_VER_MAX =	 "Range:Ver:Max";
	private static final String RANGE_CHI2_MIN = "Range:Chi2:Min";
	private static final String RANGE_CHI2_MAX = "Range:Chi2:Max";

	// busy signal when beam screen background measurement is running
	private static final String STATUS_BG_BUSY= 	"Status:BgBusy";

	private static final String CMD_TAKE_BG= 	"Cmd:BgTake";
	
	class Task implements Runnable {

		boolean bgBusy=false;
		boolean bgDone=false;

		public Task() {
		}
		
		@Override
		public synchronized void run() {
			
			log4debug("Background taking started");
			
			try {
				takeNewBackground(this);
			} catch (Exception e) {
				log4error("Background taking failed "+e, e);
			} finally {
				if (task==this) {
					task=null;
				}
			}
			log4debug("Done");
		}
		
		/**
		 * Waits the calling thread for t milliseconds. Returns false if main loop should abort.
		 * @param t
		 * @return false if main loop should abort
		 */
		public synchronized void delay(long t) {
			try {
				this.wait(t);
			} catch (InterruptedException e) {
				log4error("Wait interupted: "+e.toString(), e);
			}
		}
		
		synchronized void bgBusy() {
			bgBusy=true;
			notify();
		}
		
		synchronized void bgDone() {
			bgDone=true;
			notify();
		}
		
		
	}

	
	private int count;
	private long timeWindow;
	private ValueLinks val;
	private Task task;

	private RunningAverageBufferCalculator calcPosH;
	private RunningAverageBufferCalculator calcPosV;
	private RunningAverageBufferCalculator calcChi2H;
	private RunningAverageBufferCalculator calcChi2V;
	private RunningAverageBufferCalculator calcSizeH;
	private RunningAverageBufferCalculator calcSizeV;
	
	
	private String pvSizeHor;
	private String pvSizeVer;
	private String pvPosHor;
	private String pvPosVer;
	private double rangeHmin;
	private double rangeHmax;
	private double rangeVmin;
	private double rangeVmax;
	private double rangeCenter;
	private double rangeDeviation;
	private double rangeArea;
	private String pvChi2Hor;
	private String pvChi2Ver;
	private double rangeChi2Min;
	private double rangeChi2Max;
	private String pvFwhmHor;
	private String pvFwhmVer;
	private RunningAverageBufferCalculator calcFwhmH;
	private RunningAverageBufferCalculator calcFwhmV;
	private String pvLaserSwitch;
	private String pvFindBeam;
	private String pvBackgroundTake;
	private String pvBackgroundBusy;

	/**
	 * <p>Constructor for BeamSpotApplication.</p>
	 */
	public BeamSpotApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		count= config.getInt("count", 1);
		timeWindow= config.getLong("time_window", 500);

		pvSizeHor= config.getString("inputPvs.size_h");
		pvSizeVer= config.getString("inputPvs.size_v");
		pvPosHor= config.getString("inputPvs.pos_h");
		pvPosVer= config.getString("inputPvs.pos_v");
		pvChi2Hor= config.getString("inputPvs.chi2_h");
		pvChi2Ver= config.getString("inputPvs.chi2_v");
		pvFwhmHor= config.getString("inputPvs.fwhm_h");
		pvFwhmVer= config.getString("inputPvs.fwhm_v");
		
		rangeHmin= config.getDouble("range.h_min",-8.0);
		rangeHmax= config.getDouble("range.h_max",8.0);
		rangeVmin= config.getDouble("range.v_min",-8.0);
		rangeVmax= config.getDouble("range.v_max",8.0);
		rangeCenter= config.getDouble("range.center",0.1);
		rangeDeviation= config.getDouble("range.deviation",0.1);
		rangeArea= config.getDouble("range.area",0.1);
		rangeChi2Min= config.getDouble("range.chi2_min",0.0);
		rangeChi2Max= config.getDouble("range.chi2_max",1.0);
		
		pvLaserSwitch= config.getString("laserSwitchPv", "F:LAS:Timing:01:PulsePicker:Enabled");
		pvFindBeam= config.getString("findBeamPv", "F:GL:Python:01:DiagCam:Do_ROI");
		pvBackgroundTake= config.getString("takeBackgroundPv", "F:GL:Python:01:DiagCam:Take_New_BG");
		pvBackgroundBusy= config.getString("takeBackgroundBusyPv", "F:GL:Python:01:DiagCam:BG_proc");

		connectLinks(LINK_LASER_ENABLED, pvLaserSwitch);
		connectLinks(LINK_FIND_BEAM_ENABLED, pvFindBeam);
		connectLinks(LINK_BACKGROUND_TAKE, pvBackgroundTake);
		connectLinks(LINK_BACKGROUND_BUSY, pvBackgroundBusy);
		
		addRecordOfMemoryValueProcessor(POS_H, "Averaged position Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(POS_V, "Averaged position Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(SIZE_H, "Averaged size Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(SIZE_V, "Averaged size Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(AREA, "Averaged spot area", -10000.0, 10000.0, "mm^2", (short)3, 0.0);
		addRecordOfMemoryValueProcessor(CHI2_H, "Maximal Chi square Hor", -100.0, 100.0, "", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(CHI2_V, "Maximal Chi square Ver", -100.0, 100.0, "", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(FWHM_H, "Averaged FWHM square Hor", -100.0, 100.0, "", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(FWHM_V, "Averaged FWHM square Ver", -100.0, 100.0, "", (short)4, 0.0);

		addRecordOfMemoryValueProcessor(POS_H_STD, "STD for Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(POS_V_STD, "STD for Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(SIZE_H_STD, "STD for Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(SIZE_V_STD, "STD for Hor", -1000.0, 1000.0, "mm", (short)4, 0.0);
		addRecordOfMemoryValueProcessor(AREA_STD, "STD for spot area", -10000.0, 10000.0, "mm^2", (short)3, 0.0);

		addRecordOfMemoryValueProcessor(AREA_MIN, "Min spot area from averaged", -10000.0, 10000.0, "mm^2", (short)3, 0.0);
		addRecordOfMemoryValueProcessor(DEVIATION, "Deviation of averages", -10000.0, 10000.0, "", (short)3, 0.0);
		addRecordOfMemoryValueProcessor(COUNT, "Averaging count", 0, 10000, "", 0);

		addRecordOfMemoryValueProcessor(DATA, "Array with combined essential data", -10000.0, 10000.0, "", (short)4, new double[BeamSpotData.values().length]);

		addRecordOfCommandProcessor(CMD_TAKE_BG, "Take beam background measurement", 10000);
		
		addRecordOfMemoryValueProcessor(STATUS_IN_RANGE, "Beam position within range window", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_CENTRED, "Beam position centred on 0", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_STABLE, "Beam position reading stable", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_VALID, "Beam position valid", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_SMALL, "Beam area below treshold", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_FIT_GOOD, "Beam gauss fit chi square below treshold", DBRType.BYTE, false);
		addRecordOfMemoryValueProcessor(STATUS_BG_BUSY, "Busy taking background measurement", DBRType.BYTE, false);
		
		addRecordOfMemoryValueProcessor(RANGE_AREA, "Treshold for small spot area", -10000.0, 10000.0, "mm^2", (short)2, rangeArea);
		addRecordOfMemoryValueProcessor(RANGE_CENTER, "Treshold for centerpoint", -100.0, 100.0, "mm", (short)4, rangeCenter);
		addRecordOfMemoryValueProcessor(RANGE_DEVIATION, "Treshold for stability deviation", 0.0, 100.0, "", (short)3, rangeDeviation);
		addRecordOfMemoryValueProcessor(RANGE_HOR_MIN, "Treshold for position", -100.0, 100.0, "mm", (short)4, rangeHmin);
		addRecordOfMemoryValueProcessor(RANGE_HOR_MAX, "Treshold for position", -100.0, 100.0, "mm", (short)4, rangeHmax);
		addRecordOfMemoryValueProcessor(RANGE_VER_MIN, "Treshold for position", -100.0, 100.0, "mm", (short)4, rangeVmin);
		addRecordOfMemoryValueProcessor(RANGE_VER_MAX, "Treshold for position", -100.0, 100.0, "mm", (short)4, rangeVmax);
		addRecordOfMemoryValueProcessor(RANGE_CHI2_MIN, "Treshold for Chi2", -1E100, 1E100, "", (short)2, rangeChi2Min);
		addRecordOfMemoryValueProcessor(RANGE_CHI2_MAX, "Treshold for Chi2", -1E100, 1E100, "", (short)2, rangeChi2Max);

		Record r= getRecord(COUNT); 
		r.setPersistent(true);
		
		if (r.getValueAsInt()==0) {
			r.setValue(count);
		} else {
			count= r.getValueAsInt();
		}
		
		r= getRecord(RANGE_AREA); 
		r.setPersistent(true);
		r= getRecord(RANGE_CENTER); 
		r.setPersistent(true);
		r= getRecord(RANGE_DEVIATION); 
		r.setPersistent(true);
		r= getRecord(RANGE_HOR_MIN); 
		r.setPersistent(true);
		r= getRecord(RANGE_HOR_MAX); 
		r.setPersistent(true);
		r= getRecord(RANGE_VER_MIN); 
		r.setPersistent(true);
		r= getRecord(RANGE_VER_MAX); 
		r.setPersistent(true);
		r= getRecord(RANGE_CHI2_MIN); 
		r.setPersistent(true);
		r= getRecord(RANGE_CHI2_MAX); 
		r.setPersistent(true);
		
		if (r.getValueAsDouble()==0.0) {
			r.setValue(rangeArea);
		} else {
			rangeArea= r.getValueAsDouble();
		}

		val= connectLinks("beam", pvPosHor, pvPosVer, pvSizeHor, pvSizeVer, pvChi2Hor, pvChi2Ver, pvFwhmHor, pvFwhmVer);
		
		calcPosH= new RunningAverageBufferCalculator(count);
		calcPosV= new RunningAverageBufferCalculator(count);
		calcSizeH= new RunningAverageBufferCalculator(count);
		calcSizeV= new RunningAverageBufferCalculator(count);
		calcChi2H= new RunningAverageBufferCalculator(count);
		calcChi2V= new RunningAverageBufferCalculator(count);
		calcFwhmH= new RunningAverageBufferCalculator(count);
		calcFwhmV= new RunningAverageBufferCalculator(count);
		
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (name==CMD_TAKE_BG) {
			if (getRecord(CMD_TAKE_BG).getValueAsDouble()>0.0) {
				if (task!=null) {
					return;
				}
				getRecord(STATUS_BG_BUSY).setValue(true);
				task= new Task();
				database.schedule(task, 0);
			}
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==COUNT) {
			count= getRecord(COUNT).getValueAsInt();
			calcPosH= new RunningAverageBufferCalculator(count);
			calcPosV= new RunningAverageBufferCalculator(count);
			calcSizeH= new RunningAverageBufferCalculator(count);
			calcSizeV= new RunningAverageBufferCalculator(count);
			calcChi2H= new RunningAverageBufferCalculator(count);
			calcChi2V= new RunningAverageBufferCalculator(count);
		} else if (name==RANGE_AREA) {
			rangeArea= getRecord(RANGE_AREA).getValueAsDouble();
		} else if (name==RANGE_CENTER) {
			rangeCenter= getRecord(RANGE_CENTER).getValueAsDouble();
		} else if (name==RANGE_DEVIATION) {
			rangeDeviation= getRecord(RANGE_DEVIATION).getValueAsDouble();
		} else if (name==RANGE_HOR_MIN) {
			rangeHmin= getRecord(RANGE_HOR_MIN).getValueAsDouble();
		} else if (name==RANGE_HOR_MAX) {
			rangeHmax= getRecord(RANGE_HOR_MAX).getValueAsDouble();
		} else if (name==RANGE_VER_MIN) {
			rangeVmin= getRecord(RANGE_VER_MIN).getValueAsDouble();
		} else if (name==RANGE_VER_MAX) {
			rangeVmax= getRecord(RANGE_VER_MAX).getValueAsDouble();
		} else if (name==RANGE_CHI2_MIN) {
			rangeChi2Min= getRecord(RANGE_CHI2_MIN).getValueAsDouble();
		} else if (name==RANGE_CHI2_MAX) {
			rangeChi2Max= getRecord(RANGE_CHI2_MAX).getValueAsDouble();
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==LINK_BACKGROUND_BUSY) {
			ValueLinks vl= getLinks(LINK_BACKGROUND_BUSY);
			if (!vl.isReady() || vl.isInvalid() || vl.isLastSeverityInvalid()) {
				return;
			}
			
			boolean b= vl.consumeAsBooleanAnd();
			
			Task t= task;
			if (t!=null) {
				if (b) {
					t.bgBusy();
				} else {
					t.bgDone();
				}
			}
		}
		
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
				Math.abs(vh[0].timestamp-vh[3].timestamp)>timeWindow ||
				Math.abs(vh[1].timestamp-vh[2].timestamp)>timeWindow || 
				Math.abs(vh[1].timestamp-vh[3].timestamp)>timeWindow ||
				Math.abs(vh[2].timestamp-vh[3].timestamp)>timeWindow ) {
			//System.out.println("Out of time window "+timeWindow);
			return;
		}

		calcPosH.add(vh[0].doubleValue());
		calcPosV.add(vh[1].doubleValue());
		calcSizeH.add(vh[2].doubleValue());
		calcSizeV.add(vh[3].doubleValue());
		calcChi2H.add(vh[4].doubleValue());
		calcChi2V.add(vh[5].doubleValue());
		calcFwhmH.add(vh[6].doubleValue());
		calcFwhmV.add(vh[7].doubleValue());
		
		double area= Math.PI * calcSizeH.avg * calcSizeV.avg / 4.0;
		double areaMin= Math.PI * calcSizeH.min * calcSizeV.min / 4.0;
		double areadDev=  Math.sqrt(calcSizeH.std*calcSizeH.std/calcSizeH.avg/calcSizeH.avg + calcSizeV.std*calcSizeV.std/calcSizeV.avg/calcSizeV.avg);
		double areaStd= area*areadDev;
		
		double deviation= Math.max(areadDev,Math.max(calcPosH.std/calcPosH.avg,calcPosV.std/calcPosV.avg));
		
		updateAlarm(sev, sta);
		
		getRecord(POS_H).setValue(calcPosH.avg);
		getRecord(POS_V).setValue(calcPosV.avg);
		getRecord(SIZE_H).setValue(calcSizeH.avg);
		getRecord(SIZE_V).setValue(calcSizeV.avg);
		getRecord(CHI2_H).setValue(calcChi2H.max);
		getRecord(CHI2_V).setValue(calcChi2V.max);
		getRecord(FWHM_H).setValue(calcFwhmH.max);
		getRecord(FWHM_V).setValue(calcFwhmV.max);
		
		getRecord(POS_H_STD).setValue(calcPosH.std);
		getRecord(POS_V_STD).setValue(calcPosV.std);
		getRecord(SIZE_H_STD).setValue(calcSizeH.std);
		getRecord(SIZE_V_STD).setValue(calcSizeV.std);

		getRecord(AREA).setValue(area);
		getRecord(AREA_MIN).setValue(areaMin);
		getRecord(AREA_STD).setValue(areaStd);
		
		getRecord(DEVIATION).setValue(deviation);
		
		boolean stIR= calcPosH.avg<=rangeHmax && calcPosH.avg>=rangeHmin && calcPosV.avg<=rangeVmax && calcPosV.avg>=rangeVmin;
		boolean stCe= calcPosH.avg*calcPosH.avg + calcPosV.avg*calcPosV.avg <= rangeCenter*rangeCenter;
		boolean stSt= Math.abs(deviation) <= rangeDeviation;
		boolean stSm= area<=rangeArea && area>0.001;
		boolean stFi= calcChi2H.max>=rangeChi2Min && calcChi2H.max<=rangeChi2Max && calcChi2V.max>=rangeChi2Min && calcChi2V.max<=rangeChi2Max;
		boolean stVa= stIR && stSt && stSm && stFi;
		
		getRecord(STATUS_IN_RANGE).setValue(stIR);
		getRecord(STATUS_CENTRED).setValue(stCe);
		getRecord(STATUS_STABLE).setValue(stSt);
		getRecord(STATUS_SMALL).setValue(stSm);
		getRecord(STATUS_FIT_GOOD).setValue(stFi);
		getRecord(STATUS_VALID).setValue(stVa);
		
		double[] d= new double[BeamSpotData.values().length];
		
		d[BeamSpotData.POS_H.ordinal()]=calcPosH.avg;
		d[BeamSpotData.POS_V.ordinal()]=calcPosV.avg;
		d[BeamSpotData.SIZE_H.ordinal()]=calcSizeH.avg;
		d[BeamSpotData.SIZE_V.ordinal()]=calcSizeV.avg;
		d[BeamSpotData.AREA.ordinal()]=area;
		d[BeamSpotData.CHI2_H.ordinal()]=calcChi2H.avg;
		d[BeamSpotData.CHI2_V.ordinal()]=calcChi2V.avg;
		d[BeamSpotData.FWHM_H.ordinal()]=calcFwhmH.avg;
		d[BeamSpotData.FWHM_V.ordinal()]=calcFwhmV.avg;
		d[BeamSpotData.DEV.ordinal()]=deviation;
		d[BeamSpotData.ST_RANGE.ordinal()]=stIR ? 1.0 : 0.0;
		d[BeamSpotData.ST_CENTER.ordinal()]=stCe ? 1.0 : 0.0;
		d[BeamSpotData.ST_STABLE.ordinal()]=stSt ? 1.0 : 0.0;
		d[BeamSpotData.ST_SMALL.ordinal()]=stSm ? 1.0 : 0.0;
		d[BeamSpotData.ST_FIT.ordinal()]=stFi ? 1.0 : 0.0;
		d[BeamSpotData.ST_VALID.ordinal()]=stVa ? 1.0 : 0.0;
		d[BeamSpotData.SIZE_H_STD.ordinal()]=calcSizeH.std;
		d[BeamSpotData.SIZE_V_STD.ordinal()]=calcSizeV.std;
		
		getRecord(DATA).setValue(d);

	}
	
	private void updateAlarm(Severity sev, Status sta) {
		updateErrorSum(sev, sta);
		getRecord(POS_H).updateAlarm(sev, sta);
		getRecord(POS_V).updateAlarm(sev, sta);
		getRecord(CHI2_H).updateAlarm(sev, sta);
		getRecord(CHI2_V).updateAlarm(sev, sta);
		getRecord(FWHM_H).updateAlarm(sev, sta);
		getRecord(FWHM_V).updateAlarm(sev, sta);
		getRecord(SIZE_H).updateAlarm(sev, sta);
		getRecord(SIZE_V).updateAlarm(sev, sta);
		getRecord(AREA).updateAlarm(sev, sta);
	}
	
	
	private boolean takeNewBackground(Task t) {
		log4debug("Taking background");
		try {
			// switch off laser
			boolean laserOn= getLinks(LINK_LASER_ENABLED).consumeAsBooleanAnd();
			if (laserOn) {
				getLinks(LINK_LASER_ENABLED).setValue(0);
			}
			
			// turn off beam find
			boolean findb= getLinks(LINK_FIND_BEAM_ENABLED).consumeAsBooleanAnd();
			if (findb) {
				getLinks(LINK_FIND_BEAM_ENABLED).setValue(0);
			}

			t.delay(T_WAIT_FOR_LASER);
			
			// take new bakcground image
			getLinks(LINK_BACKGROUND_TAKE).setValue(1);

			// waiting for busy status at screen IOC to kick in
			for(int i=0; i<10; i++) {
				if (t.bgBusy) {
					break;
				}
				t.delay(T_WAIT_FOR_BG);
			}
			
			// waiting for busy status at screen IOC to go out
			if (t.bgBusy) {
				for(int i=0; i<60; i++) {
					if (t.bgDone) {
						break;
					}
					t.delay(T_WAIT_FOR_BG);
				}
			}
			
			// switch on laser
			if (laserOn) {
				getLinks(LINK_LASER_ENABLED).setValue(1);
			}
			
			// turn on beam find
			if (findb) {
				getLinks(LINK_FIND_BEAM_ENABLED).setValue(1);
			}

			t.delay(T_WAIT_FOR_LASER);

		} catch (Exception e) {
			log4error("Set failed", e);
			return false;
		} finally {
			getRecord(STATUS_BG_BUSY).setValue(false);
		}
		return true;
	}


}
