/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.Tools;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.application.BeamSpotApplication.BeamSpotData;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;
import org.scictrl.csshell.python.EmittanceCalculator;
import org.scictrl.csshell.python.PythonRunner.Result;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import si.ijs.anka.config.BootstrapLoader;

/**
 * Filters and averages the BPM values. Values with Q below treshold ar thrown away.
 *
 * @author igor@scictrl.com
 */
public class EmittanceScanApplication extends AbstractApplication {
	
	private enum State {
		READY("Ready"),
		SETTING_BEND("Setting bend"),
		SCANNING("Scanning Quad"),
		PAUSED("Paused"),
		ERROR("Error");
		private String name;
		private State(String name) {
			this.name=name;
		}
		@Override
		public String toString() {
			return name;
		}
	}
	
	private static final long T_WAIT_FOR_QUAD = 1000;
	private static final long T_BG_TAKE_ESTIMATE = 20000;
	private static final long T_WAIT_FOR_BG = 1000;

	private static final String BEAM = 						"Beam";
	private static final String LINK_LASER_ENABLED = 		"LaserSwitch";
	private static final String LINK_TAKE_BACKGROUND = 		"TakeBackground";
	private static final String LINK_TAKE_BACKGROUND_BUSY = "TakeBackgroundBusy";
	private static final String LINK_ENABLE_BACKGROUND = 	"EnableBackground";

	private static final String MEASUREMENT_LAST = 	"Meas:Last";
	private static final String MEASUREMENT_TABLE = "Meas:Table";
	private static final String MEASUREMENT_DEBUG = "Meas:Debug";
	
	private static final String CMD_STOP = 		"Cmd:Stop";
	private static final String CMD_START = 	"Cmd:Start";
	private static final String CMD_PAUSE = 	"Cmd:Pause";

	private static final String STATUS = 				"Status";
	private static final String STATUS_SCANNING = 		"Status:Scanning";
	private static final String STATUS_PROGRESS = 		"Status:Progress";
	private static final String STATUS_REMAINING = 		"Status:Remaining";
	private static final String STATUS_REMAINING_MS = 	"Status:Remaining:ms";
	private static final String STATUS_DATA_FILE = 		"Status:DataFile";
	private static final String STATUS_REPEAT = 		"Status:Repeat";
	private static final String STATUS_ERROR = 		"Status:Error";
	
	private static final String WAIT = 			"Wait";
	private static final String SCREEN_SWITCH = "ScreenSwitch";
	private static final String ENERGY = 		"Energy";
	
	private static final String EMITTANCE_H = 	  "Emittance:H";
	private static final String EMITTANCE_V = 	  "Emittance:V";
	private static final String EMITTANCE_H_STD = "Emittance:H:Std";
	private static final String EMITTANCE_V_STD = "Emittance:V:Std";

	private static final String OPT_LASER_OFF = 	"Opt:LaserOff";
	private static final String OPT_LASER_ON = 		"Opt:LaserOn";
	private static final String OPT_TAKE_BG = 		"Opt:TakeBg"; 
	private static final String OPT_ENABLE_BG = 	"Opt:EnableBg"; 
	private static final String OPT_VALID_ONLY = 	"Opt:ValidOnly"; 
	private static final String OPT_REPEAT = 		"Opt:Repeat"; 

	final static class Measurement implements Cloneable {
		public Instant time;
		public double quad;
		public double posH;
		public double posV;
		public double sizeH;
		public double sizeV;
		public double sizeStdH;
		public double sizeStdV;
		public double valid;
		public double ok;
		
		public Measurement() {
			super();
			this.time=Instant.now();
		}
		
		public void copy(Measurement m) {
			time=m.time;
			quad=m.quad;
			posH=m.posH;
			posV=m.posV;
			sizeH=m.sizeH;
			sizeV=m.sizeV;
			sizeStdH=m.sizeStdH;
			sizeStdV=m.sizeStdV;
			valid=m.valid;
			ok=m.ok;
		}
		
		boolean validRelaxed() {
			return true;
		}

		boolean valid() {
			return valid!=0.0; 
		}
		
		public double[] toArray() {
			return new double[]{quad,posH,posV,sizeH,sizeV,sizeStdH,sizeStdV,valid,ok};
		}
		
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append(" qu=");
			sb.append(Tools.format4D(quad));
			sb.append(" pH=");
			sb.append(Tools.format3D(posH));
			sb.append(" sH=");
			sb.append(Tools.format3D(sizeH));
			sb.append(" pV=");
			sb.append(Tools.format3D(posV));
			sb.append(" sV=");
			sb.append(Tools.format3D(sizeV));
			sb.append(" val=");
			sb.append(valid());
			sb.append(" stb=");
			sb.append(ok!=0.0);
			
			return sb.toString();
		}

		public Appendable toLogString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(quad));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(posH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(posV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeStdH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeStdV));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(ok!=0.0));
			return sb;
		}
		
		public Appendable toDataString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(quad));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(posH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(posV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeStdH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(sizeStdV));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(ok!=0.0));
			return sb;
		}

		public static Appendable toHeader(Appendable sb) throws IOException {
			sb.append("time");
			sb.append(',');
			sb.append(" quad");
			sb.append(',');
			sb.append(" posH");
			sb.append(',');
			sb.append(" posV");
			sb.append(',');
			sb.append(" sizeH");
			sb.append(',');
			sb.append(" sizeV");
			sb.append(',');
			sb.append(" sizeStdH");
			sb.append(',');
			sb.append(" sizeStdV");
			sb.append(',');
			sb.append(" valid");
			sb.append(',');
			sb.append(" stable");
			return sb;
		}
		
		@Override
		public Object clone() {
			try {
				return super.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return new Measurement();
		}
	}

	class ScanningTask implements Runnable {
		
		private boolean aborted=false;
		private boolean paused=false;
		double time=-1;
		private PrintWriter dataLog;
		int steps;
		int step;
		List<Measurement> data;
		long start;
		int repeat=0;
		int countMeasurements=0;
		int countMeasurementsOK=0;

		public ScanningTask(int repeat) {
			this.repeat=repeat;
			log4info("Scan task started, repeat count "+repeat);
		}
		
		public void initData(int i) {
			data= new ArrayList<EmittanceScanApplication.Measurement>(i);
		}
		
		public void advanceProgress() {
			step++;
			progress.setValue(((double)step)/((double)steps)*100.0);
		}
		
		public void startProgress(int steps) {
			this.steps=steps;
			step=0;
			progress.setValue(0.0);
			start=System.currentTimeMillis();
		}

		public void endProgress() {
			progress.setValue(100.0);
		}
		
		public void incCountMOK() {
			countMeasurementsOK++;
		}

		public void incCountM() {
			countMeasurements++;
		}

		public synchronized void abort() {
			aborted=true;
			notifyAll();
		}
		
		public boolean isAborted() {
			return aborted;
		}
		
		@Override
		public synchronized void run() {
			
			getRecord(STATUS_REPEAT).setValue(repeat);
			
			log4debug("Scanning started");
			
			if (getRecord(OPT_LASER_ON).getValueAsBoolean()) {
				try {
					getLinks(LINK_LASER_ENABLED).setValue(1);
				} catch (Exception e) {
					log4error("Set failed", e);
				}
			}

			boolean b= false;
			try {
				b= scanStart(this);
			} catch (Exception e) {
				log4error("Scanning failed "+e, e);
				try {
					getDataLog().println("# Scanning failed "+e+" "+LocalDateTime.now().toString());
					getDataLog().flush();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} finally {
				closeDataLog();
			}
			
			boolean canRepeat= b && !aborted;
			log4debug("Aborted");
			aborted=true;
			
			scanStop(this,b);
			
			int r= getRecord(OPT_REPEAT).getValueAsInt();
			if (r<1) {
				r=1;
			}

			if (canRepeat && repeat<r) {
				
				task= new ScanningTask(repeat+1);
				database.schedule(task, 0);
				
			} else {
				
				scanCleanup();

			}
			
		}
		
		private PrintWriter getDataLog() throws IOException {
			
			if (dataLog==null) {
				File dataFile= new File(dataDir);
				dataFile.mkdirs();
				dataFile= new File(dataFile,"EmittanceScan-"+Tools.FORMAT_ISO_DATE_TIME.format(Instant.now().toEpochMilli())+".csv");
				
				
				String f= dataFile.getAbsolutePath();
				
				log4info("Data file created "+f);

				getRecord(STATUS_DATA_FILE).setValue(f);
				
				dataLog= new PrintWriter(new BufferedWriter(new FileWriter(dataFile), 1024));
			}

			return dataLog;
			
		}
		
		protected void closeDataLog() {
			if (dataLog!=null) {
				dataLog.flush();
				dataLog.close();
				dataLog=null;
			}
		}

		void dataLogPrintln(String s) throws IOException {
			getDataLog().println(s);
		}
		
		void dataLogPrintHeader() throws IOException {
			getDataLog().print("# ");
			Measurement.toHeader(getDataLog());
			getDataLog().println();
		}
		
		void dataLogPrint(Measurement m) throws IOException {
			m.toDataString(getDataLog());
			getDataLog().println();
		}

		void dataLogFlush() throws IOException {
			getDataLog().flush();
		}
		
		void addData(Measurement m) {
			data.add(m);
		}
		
		@Override
		protected void finalize() throws Throwable {
			closeDataLog();
		}
		
		/**
		 * Waits the calling thread for t milliseconds. Returns false if main loop should abort.
		 * @param t
		 * @return false if main loop should abort
		 */
		public synchronized boolean delay(long t) {
			try {
				this.wait(t);
			} catch (InterruptedException e) {
				log4error("Wait interupted: "+e.toString(), e);
			}
			return canRun();
		}
		
		/**
		 * Checks if thread can continue, if should abort then returns false
		 * @return true if can continue, otherwise false for abort
		 */
		private synchronized boolean canRun() {
			if (task==null || task!=this) {
				return false;
			}
			
			while (paused && !aborted) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					log4error("Wait interupted: "+e.toString(), e);
				}
			}
			
			return !aborted;
		}
		
		public boolean isPaused() {
			return paused;
		}
		
		private synchronized void pause(boolean pause) {
			this.paused=pause;
			this.notifyAll();
		}


	}
	
	
	private ScanApplication quadScan;
	private String beamSpotPV;
	
	private long measurementWait=10000;
	private ScanningTask task;
	private Instant start;
	private String screenSwitchPV;
	private String dataDir;
	private String laserSwitchPV;
	private String takeBackgroundPV;
	private String takeBackgroundBusyPV;
	private int paused;
	private Record progress;
	
	private String enableBackgroundPV;
	private EmittanceCalculator emittanceCalcH;
	private EmittanceCalculator emittanceCalcV;
	private String pythonDirName;
	private String pythonScript;
	private File pythonDir;

	/**
	 * <p>Constructor for EmittanceScanApplication.</p>
	 */
	public EmittanceScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		quadScan = configureScan(name,"Quad", config);
		
		beamSpotPV= config.getString("beamSpotPv", "F:GL:BeamSpot:01");
		screenSwitchPV= config.getString("screenSwitchPv", "F:GL:Python:01:DiagCam:Camera");

		laserSwitchPV= config.getString("laserSwitchPv", "F:LAS:Timing:01:PulsePicker:Enabled");
		enableBackgroundPV= config.getString("enableBackgroundPv", "F:GL:Python:01:DiagCam:Subt_BG");
		takeBackgroundPV= config.getString("takeBackgroundPv", "F:GL:BeamSpot:01:Cmd:BgTake");
		takeBackgroundBusyPV= config.getString("takeBackgroundBusyPv", "F:GL:BeamSpot:01:Status:BgBusy");
		pythonDirName= config.getString("pythonDir", BootstrapLoader.getInstance().getBundleConfDir().getPath()+"/Python");
		pythonScript= config.getString("pythonScript", "emittance.py");
		pythonDir= new File(pythonDirName);
		
		measurementWait= config.getLong("measurementWait", 10000);
		dataDir= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());

		connectLinks(BEAM, beamSpotPV+":Data");
		connectLinks(LINK_LASER_ENABLED, laserSwitchPV);
		connectLinks(LINK_TAKE_BACKGROUND, takeBackgroundPV);
		connectLinks(LINK_TAKE_BACKGROUND_BUSY, takeBackgroundBusyPV);
		connectLinks(LINK_ENABLE_BACKGROUND, enableBackgroundPV);
		
		addRecordOfOnLinkValueProcessor(SCREEN_SWITCH, "Screen switch", DBRType.INT, screenSwitchPV);
		
		addRecordOfMemoryValueProcessor(ENERGY, "Beam energy", 0.0, 100.0, "MeV", (short)2, 5.81);
		addRecordOfMemoryValueProcessor(EMITTANCE_H, "Emittance Hor", -1000.0, 1000.0, "mm×mrad", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(EMITTANCE_V, "Emittance Ver", -1000.0, 1000.0, "mm×mrad", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(EMITTANCE_H_STD, "Emittance Hor STD", 0.0, 1000.0, "mm×mrad", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(EMITTANCE_V_STD, "Emittance Ver STD", 0.0, 1000.0, "mm×mrad", (short)2, 0.0);
		
		addRecordOfMemoryValueProcessor(MEASUREMENT_LAST, "Last measurement", -1000.0, 1000.0, "", (short)3, new Measurement().toArray());
		addRecordOfMemoryValueProcessor(MEASUREMENT_TABLE, "Emittance measurement results",new byte[1048576]);
		addRecordOfMemoryValueProcessor(MEASUREMENT_DEBUG, "Measurement debug",new byte[1048576]);

		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING_MS, "Remaining time of scan", 0, 1000000, "ms", 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{State.READY.toString(),State.SETTING_BEND.toString(),State.SCANNING.toString(),State.PAUSED.toString(),State.ERROR.toString()}, (short)0);
		addRecordOfMemoryValueProcessor(STATUS_DATA_FILE, "Data file", new byte[1024]);
		addRecordOfMemoryValueProcessor(STATUS_REPEAT, "Current repeat count", 0, 1000, "No.", 0);
		addRecordOfMemoryValueProcessor(STATUS_ERROR, "Last result has errors", DBRType.BYTE, 0);

		addRecordOfMemoryValueProcessor(OPT_LASER_OFF, "Switches laser off at the end", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_LASER_ON, "Switches laser on at the start", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_TAKE_BG, "Takes new background each measurement", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_ENABLE_BG, "Enables background substractions", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_VALID_ONLY, "Accept only valid measurements", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_REPEAT, "How many repeats", 1, 10000,"No.",1);
		
		addRecordOfMemoryValueProcessor(CMD_STOP, "Stops scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_PAUSE, "Pauses scanning task", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(WAIT, "Wait for measurement", 0, 1000, "s", 0);
		
		getRecord(OPT_REPEAT).setPersistent(true);
		getRecord(OPT_VALID_ONLY).setPersistent(true);
		getRecord(OPT_ENABLE_BG).setPersistent(true);
		getRecord(OPT_TAKE_BG).setPersistent(true);
		getRecord(OPT_LASER_OFF).setPersistent(true);
		getRecord(OPT_LASER_ON).setPersistent(true);

		progress= getRecord(STATUS_PROGRESS);

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

		try {
			updateData(null,null);
		} catch (IOException e) {
			log4error(e.toString(), e);
		}
		
		PropertyChangeListener l= new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				Record r= (Record)evt.getSource();
				Severity sev= r.getAlarmSeverity();
				if (sev.isEqualTo(Severity.INVALID_ALARM) && isActivated()) {
					//Thread.dumpStack();
					log4info("ALARM "+r.getName()+" "+sev);
					StringBuilder sb= new StringBuilder();
					try {
						((LinkedValueProcessor)r.getProcessor()).printLinkDebug(sb);
					} catch (IOException e) {
						e.printStackTrace();
					}
					log4info("LINK DEBUG:"+sb.toString());
					((LinkedValueProcessor)r.getProcessor()).reconnect();
				}
			}
		};
		
		quadScan.getSetpoint().addPropertyChangeListener(l);

	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		database.schedule(new Runnable() {
			@Override
			public void run() {
				updateTimeEst();
			}
		}, 1000, 1000);
	}
	
	private void updateTimeEst() {
		double t=0.0;
		Duration d= null;

		double p= getRecord(STATUS_PROGRESS).getValueAsDouble();

		if (start!=null) {
			d= Duration.between(start, Instant.now());
			t= d.toMillis()*(100.0/p-1.0);
		}
		
		if (start==null || p<0.0001 || Math.abs(p-100.0)<0.0001) {
			t= estimateStepQuad() * stepsQuad();
		}
		
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
		
		getRecord(STATUS_REMAINING_MS).setValue(t);
		getRecord(STATUS_REMAINING).setValue(sb.toString());
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void initialize(Database database) {
		super.initialize(database);
		
		database.addAll(quadScan.getRecords());
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
			updateTimeEst();
		} else if (name==OPT_TAKE_BG) {
			updateTimeEst();
		} else if (name==OPT_ENABLE_BG) {
			checkEnableBackground(null);
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (name==CMD_START) {
			if (task!=null) {
				if (task.isPaused()) {
					getRecord(STATUS).setValue(paused);
					task.pause(false);
					log4info("Scan unpaused on request");
				} else {
					log4info("Scan request denied, scan in progress");
				}
				return;
			}
			
			getRecord(STATUS_SCANNING).setValue(1);
			log4info("Scan started on request");
			task= new ScanningTask(1);
			database.schedule(task, 0);
			
		} else if (name==CMD_STOP) {
			getRecord(STATUS).setValue(State.READY.ordinal());
			getRecord(STATUS_SCANNING).setValue(0);
			ScanningTask t=task;
			if (t!=null) {
				log4info("Scan stopped on request");
				t.abort();
				scanStop(t,true);
				scanCleanup();
			}
			task=null;
		} else if (name==CMD_PAUSE) {
			if (getRecord(STATUS).getValueAsInt()==State.PAUSED.ordinal()) {
				return;
			}
			paused=getRecord(STATUS).getValueAsInt();
			getRecord(STATUS).setValue(State.PAUSED.ordinal());
			ScanningTask t=task;
			if (t!=null) {
				log4info("Scan paused on request");
				t.pause(true);
			}
		}
	}
	
	private void checkEnableBackground(ScanningTask t) {
		if ((t!=null && !t.canRun()) || !isActivated()) {
			return;
		}
		log4debug("Enabling/Disabling background sbstraction");
		try {
			int b= getRecord(OPT_ENABLE_BG).getValueAsInt();
			getLinks(LINK_ENABLE_BACKGROUND).setValue(b);

		} catch (Exception e) {
			log4error("Set failed", e);
			return;
		}
	}

	/**
	 * 
	 * @param t
	 * @param success if false, error is reported
	 */
	private void scanStop(ScanningTask t, boolean success) {
		
		if (t!=task) {
			return;
		}
		task=null;
		start=null;

		quadScan.stopManualScan();

		if (success) {
			getRecord(STATUS).setValue(State.READY.ordinal());
		} else {
			getRecord(STATUS).setValue(State.ERROR.ordinal());
		}
		getRecord(STATUS_SCANNING).setValue(0);
		
	}
	
	private synchronized void scanCleanup() {

		if (getRecord(OPT_LASER_OFF).getValueAsBoolean()) {
			try {
				getLinks(LINK_LASER_ENABLED).setValue(0);
			} catch (Exception e) {
				log4error("Set failed", e);
			}
		}
		
	}
	
	private int stepsQuad() {
		return quadScan.getStepCount();
	}
	
	private long estimateStepQuad() {
		return 
				T_WAIT_FOR_QUAD + 
				measurementWait +
				(getRecord(OPT_TAKE_BG).getValueAsBoolean() ? T_BG_TAKE_ESTIMATE : 0); 
	}

	/**
	 * Return false if failed.
	 * @param t
	 * @throws IOException 
	 */
	private boolean scanStart(ScanningTask t) throws IOException {
		
		start= Instant.now();
		
		getRecord(STATUS_ERROR).setValue(false);
		
		checkEnableBackground(t);
		
		t.dataLogPrintln("# Start, time "+LocalDateTime.now().toString());
		t.dataLogPrintln("#");
		t.dataLogFlush();

		t.initData(quadScan.getStepCount());
		
		updateData(t.data,null);
		
		t.startProgress(stepsQuad());
		
		log4info("steps: "+t.steps+" "+stepsQuad());
		
		boolean b= scanQuad(t);
		

		t.dataLogPrintln("# Scan end "+LocalDateTime.now().toString());
		t.dataLogPrintln("# Valid measurements/all measurements: "+t.countMeasurementsOK+"/"+t.countMeasurements);
		t.dataLogPrintln("# Emittance calculation result");
		
		StringBuilder sb= new StringBuilder(1024);
		if (t.data.size()>2) {
			double[] d= calculateEmittance(t.data);
			sb.append("# emittance H, emittance V, emittance H STD, emittance V STD\n");
			sb.append(Tools.format4D(d[0])+","+Tools.format4D(d[1])+","+Tools.format4D(d[2])+","+Tools.format4D(d[3]));
			sb.append("\n");
		} else {
			sb.append("# Emittance is not calculated, to few measurements\n");
		}
		
		String s= sb.toString();
		updateData(t.data,s);
		t.dataLogPrintln(s);

		t.dataLogFlush();
		
		if (emittanceCalcH!=null && emittanceCalcV!=null && emittanceCalcH.getLastResult()!=null && emittanceCalcV.getLastResult()!=null) {
			
			getRecord(STATUS_ERROR).setValue(emittanceCalcH.getLastResult().hasError() || emittanceCalcV.getLastResult().hasError());
			
			Result r= emittanceCalcH.getLastResult();
			sb= new StringBuilder(1024);
			sb.append("HOR Output Stream:"+(r.output==null ? "None" : "\n"+r.output));
			sb.append('\n');
			sb.append("HOR Error Stream:"+(r.error==null ? "None" : "\n"+r.error));
			sb.append('\n');
			
			r= emittanceCalcV.getLastResult();
			sb.append('\n');
			sb.append("VER Output Stream:"+(r.output==null ? "None" : "\n"+r.output));
			sb.append('\n');
			sb.append("VER Error Stream:"+(r.error==null ? "None" : "\n"+r.error));
			
			getRecord(MEASUREMENT_DEBUG).setValue(sb.toString());
		}
		

		if (!b) {
			return false;
		}
		
		if (t.data.size()==0) {
			t.dataLogPrintln("# NO scan measurements "+LocalDateTime.now().toString());
			t.dataLogFlush();
			
			return true;
		}
		
		progress.setValue(100.0);
		
		return true;
	}

	private boolean scanQuad(ScanningTask t) throws IOException {
		
		getRecord(STATUS).setValue(State.SCANNING.ordinal());

		t.dataLogPrintHeader();
		t.dataLogFlush();
		
		quadScan.startManualScan();
		
		do {
			
			
			if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) {
				boolean r= takeNewBackground(t);
				if (!r) {
					return false;
				}
			}

			if (!t.delay(measurementWait)) {
				return false;
			}

			Measurement m= new Measurement();
			boolean mes= takeBeamMeasurement(m);
			
			t.incCountM();
			
			if (mes) {
				
				t.addData(m);
				t.incCountMOK();

				getRecord(MEASUREMENT_LAST).setValue(m.toArray());
				log4info("Measurement "+m.toString());

				t.dataLogPrint(m);
				t.dataLogFlush();
				
				updateData(t.data,null);

				if (t.data.size()>2) {
					calculateEmittance(t.data);
				}
			}
				
			if (!t.canRun()) {
				return false;
			}

			t.advanceProgress();

			if (!t.canRun()) {
				return false;
			}
			
			quadScan.stepManualScan();
			
		} while(quadScan.isManualScanActive());
		
		if (!t.canRun()) {
			return false;
		}
		
		return true;
		
	}
	
	



	/**
	 * Returns true if measurement was successful
	 * @param m
	 * @return
	 */
	private boolean takeBeamMeasurement(Measurement m) {
		ValueLinks vl= getLinks(BEAM);
		
		if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
			
			ValueHolder[] vh= vl.consume();
			double[] d= vh[0].doubleArrayValue();
			
			m.time= Instant.now();
			m.posH= d[BeamSpotData.POS_H.ordinal()];
			m.posV= d[BeamSpotData.POS_V.ordinal()];
			m.sizeH= d[BeamSpotData.SIZE_H.ordinal()];
			m.sizeV= d[BeamSpotData.SIZE_V.ordinal()];
			m.sizeStdH= d[BeamSpotData.SIZE_H_STD.ordinal()];
			m.sizeStdV= d[BeamSpotData.SIZE_V_STD.ordinal()];
			m.valid= d[BeamSpotData.ST_VALID.ordinal()];
			m.ok= d[BeamSpotData.ST_RANGE.ordinal()]*d[BeamSpotData.ST_STABLE.ordinal()];
			m.quad= quadScan.getSetpoint().getValueAsDouble();
			
			return true;
		}
		log4error("Measurement links are not available");
		return false;
	}

	private void updateData(List<Measurement> data, String results) throws IOException {
		StringBuilder sb= new StringBuilder(1024);
		sb.append('#');
		Measurement.toHeader(sb);
		sb.append('\n');

		if (data!=null) {
			for (Iterator<Measurement> iterator = data.iterator(); iterator.hasNext();) {
				Measurement m = iterator.next();

				m.toLogString(sb);
				sb.append('\n');
			}
		}
		
		if (results!=null) {
			sb.append(results);
		}
		
		getRecord(MEASUREMENT_TABLE).setValue(sb.toString());

	}
	
	private boolean takeNewBackground(ScanningTask t) {
		if (!t.canRun()) {
			return false;
		}
		log4debug("Taking background");
		try {
			// switch off laser
			// take new bakcground image
			getLinks(LINK_TAKE_BACKGROUND).setValue(1);

			// wait till it is one
			ValueLinks vl= getLinks(LINK_TAKE_BACKGROUND_BUSY);

			for(int i=0; i<200; i++) {
				if (!t.delay(T_WAIT_FOR_BG)) {
					return false;
				}
				boolean b= vl.consumeAsBooleanAnd();
				if (!b) {
					break;
				}
			}

		} catch (Exception e) {
			log4error("Set failed", e);
			return false;
		}
		return true;
	}
	
	/**
	 * <p>calculateEmittance.</p>
	 *
	 * @param data a {@link java.util.List} object
	 * @return an array of {@link double} objects
	 */
	public double[] calculateEmittance(List<Measurement> data) {
		
		if (data.size()<3) {
			log.warn("Emittance calculation denised while there are less than 3 measurements.");
			return new double[4];
		}
		
		if (emittanceCalcH==null || emittanceCalcV==null) {
			emittanceCalcH= new EmittanceCalculator();
			emittanceCalcH.init(pythonDir, pythonScript);
			emittanceCalcV= new EmittanceCalculator();
			emittanceCalcV.init(pythonDir, pythonScript);
		}
		
		emittanceCalcH.setEnergy(getRecord(ENERGY).getValueAsDouble());
		emittanceCalcV.setEnergy(emittanceCalcH.getEnergy());
		
		double[] q= new double[data.size()];
		double[] bh= new double[data.size()];
		double[] bsh= new double[data.size()];
		double[] bv= new double[data.size()];
		double[] bsv= new double[data.size()];
		
		for (int i = 0; i < q.length; i++) {
			Measurement m= data.get(i);
			q[i]= m.quad;
			bh[i]= m.sizeH;
			bsh[i]= m.sizeStdH;
			bv[i]= m.sizeV;
			bsv[i]= m.sizeStdV;
		}
		
		emittanceCalcH.inputs(q, bh, bsh);
		emittanceCalcV.inputs(q, bv, bsv);
		
		emittanceCalcH.calculateEmittance();
		emittanceCalcV.calculateEmittance();
		
		double[] res= new double[4];
		
		getRecord(EMITTANCE_H).setValue(res[0]=emittanceCalcH.getLastEmittance());
		getRecord(EMITTANCE_H_STD).setValue(res[1]=emittanceCalcH.getLastEmittanceStd());
		getRecord(EMITTANCE_V).setValue(res[2]=emittanceCalcV.getLastEmittance());
		getRecord(EMITTANCE_V_STD).setValue(res[3]=emittanceCalcV.getLastEmittanceStd());
		
		return res;
	}
	
}
