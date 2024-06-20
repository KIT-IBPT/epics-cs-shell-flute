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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.Tools;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.application.BeamSpotApplication.BeamSpotData;
import org.scictrl.csshell.epics.server.application.control.AbstractController;
import org.scictrl.csshell.epics.server.application.control.ProbePoint;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import si.ijs.anka.config.BootstrapLoader;

/**
 * Filters and averages the BPM values. Values with Q below treshold ar thrown away.
 *
 * @author igor@scictrl.com
 */
public class EnergyScanApplication extends AbstractApplication {
	
	private enum State {
		READY("Ready"),
		SETTING_BEND("Setting bend"),
		SCANNING_SOLENOID("Scanning solenoid"),
		SCANNING_BEND("Scanning bend"),
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
	
	private enum Mode {
		SOLENOID_AND_BEND("Solenoid and Bend"),
		SOLENOID_ONLY("Solenoid Only"),
		SEEDS_AND_BEND("Bend with Seeds"),
		FIND_ENERGY("Find energy");
		private String name;
		private Mode(String name) {
			this.name=name;
		}
		@Override
		public String toString() {
			return name;
		}
	}

	private static final long T_WAIT_FOR_SOLENOID = 2000;
	private static final long T_WAIT_FOR_BAND = 1000;
	private static final long T_BG_TAKE_ESTIMATE = 20000;
	private static final long T_WAIT_FOR_BG = 1000;

	private static final String BPM = 	"BPM";
	private static final String BEAM = 	"Beam";
	private static final String LINK_LASER_ENABLED = "LaserSwitch";
	private static final String LINK_TAKE_BACKGROUND = "TakeBackground";
	private static final String LINK_TAKE_BACKGROUND_BUSY = "TakeBackgroundBusy";
	private static final String LINK_ENABLE_BACKGROUND = "EnableBackground";
	private static final String LINK_CYCLE = "Cycle";

	private static final String MEASUREMENT_LAST = 	"Meas:Last";
	private static final String MEASUREMENT_PEAK = 	"Meas:Peak";
	private static final String MEASUREMENT_TABLE = "Meas:Table";
	
	private static final String SEED_TABLE = "Seed:Table";
	private static final String SEED_BEND =	 "Seed:Bend";
	
	private static final String CMD_STOP = 		"Cmd:Stop";
	private static final String CMD_START = 	"Cmd:Start";
	private static final String CMD_PAUSE = 	"Cmd:Pause";
	private static final String CMD_SKIP = 		"Cmd:Skip";
	private static final String CMD_SEED_NOW = 	"Cmd:SeedNow";

	private static final String STATUS = 				"Status";
	private static final String STATUS_SCANNING = 		"Status:Scanning";
	private static final String STATUS_PROGRESS = 		"Status:Progress";
	private static final String STATUS_REMAINING = 		"Status:Remaining";
	private static final String STATUS_REMAINING_MS = 	"Status:Remaining:ms";
	private static final String STATUS_BEND_DIFF_ALARM = "Status:BendDiffAlarm";
	private static final String STATUS_DATA_FILE = 		"Status:DataFile";
	private static final String STATUS_SEEDS_FILE = 	"Status:SeedsFile";
	private static final String STATUS_REPEAT = 		"Status:Repeat";
	private static final String STATUS_BEND_ITER = 		"Status:Bend:Iter";
	private static final String STATUS_BEND_MEAS = 		"Status:Bend:Meas";
	
	private static final String SCREEN_SWITCH = "ScreenSwitch";
	private static final String WAIT = 			"Wait";
	
	private static final String MEASUREMENT_POSH1 = "Meas:PosH1";
	private static final String MEASUREMENT_POSH2 = "Meas:PosH2";
	private static final String MEASUREMENT_POSH3 = "Meas:PosH3";

	private static final String OPT_LASER_OFF = 	"Opt:LaserOff";
	private static final String OPT_LASER_ON = 		"Opt:LaserOn";
	private static final String OPT_BEND_OFF = 		"Opt:BendOff";
	private static final String OPT_TAKE_BG = 		"Opt:TakeBg"; 
	private static final String OPT_ENABLE_BG = 	"Opt:EnableBg"; 
	private static final String OPT_VALID_ONLY = 	"Opt:ValidOnly"; 
	private static final String OPT_BEND_SCAN = 	"Opt:BendScan"; 
	private static final String OPT_POWER_SCAN = 	"Opt:PowerScan"; 
	private static final String OPT_PHASE_SCAN = 	"Opt:PhaseScan"; 
	private static final String OPT_SOLENOID_SCAN = "Opt:SolenoidScan"; 
	private static final String OPT_REPEAT = 		"Opt:Repeat"; 
	private static final String OPT_CYCLE = 		"Opt:Cycle"; 
	private static final String OPT_MODE = 			"Opt:Mode"; 
	private static final String OPT_POWER_BACK = 	"Opt:PowerBack"; 
	private static final String OPT_PHASE_BACK = 	"Opt:PhaseBack"; 
	private static final String OPT_SOLENOID_BACK = "Opt:SolenoidBack"; 
	private static final String OPT_BEND_IN_PREC =  "Opt:Bend:InPrec"; 
	private static final String OPT_BEND_MAX_STEP = "Opt:Bend:MaxStep"; 
	private static final String OPT_USE_BPM = 		"Opt:UseBpm"; 

	private static final String MOMENTUM = 	"Momentum";
	private static final String ICT_Q = 	"ICT:Q";
	private static final String ICT_MIN = 	"ICT:Min";
	
	private static final int VALUE_SCREEN_FORWARD= 1; 
	private static final int VALUE_SCREEN_SIDE= 2;

	final static class MSeed implements Cloneable {
		public double power;
		public double phase;
		public double solenoid;
		public double bend;
		
		public MSeed() {
			super();
		}
		
		public MSeed(Measurement m) {
			this();
			copy(m);
		}

		public void copy(MSeed m) {
			power=m.power;
			phase=m.phase;
			solenoid=m.solenoid;
			bend=m.bend;
		}
		
		public void copy(Measurement m) {
			power=m.power;
			phase=m.phase;
			solenoid=m.solenoid;
			bend=m.bend;
		}

		public double[] toArray() {
			return new double[]{power,phase,solenoid,bend};
		}
		
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append(" po=");
			sb.append(Tools.format1D(power));
			sb.append(" ph=");
			sb.append(Tools.format1D(phase));
			sb.append(" so=");
			sb.append(Tools.format1D(solenoid));
			sb.append(" be=");
			sb.append(Tools.format4D(bend));
			
			return sb.toString();
		}

		public Appendable toLogString(Appendable sb) throws IOException {
			sb.append(Tools.format2D(power));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(solenoid));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bend));
			return sb;
		}
		
		public Appendable toDataString(Appendable sb) throws IOException {
			sb.append(Tools.format2D(power));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(solenoid));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bend));
			return sb;
		}

		public static Appendable toHeader(Appendable sb) throws IOException {
			sb.append(" power");
			sb.append(',');
			sb.append(" phase");
			sb.append(',');
			sb.append(" solenoid");
			sb.append(',');
			sb.append(" bend");
			return sb;
		}
		
		@Override
		public Object clone() {
			try {
				return super.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return new MSeed();
		}
	}
	
	final static class Measurement implements Cloneable {
		public Instant time;
		public double power;
		public double powerOut;
		public double phase;
		public double solenoid;
		public double bend;
		public double mom;
		public double posH;
		public double posV;
		public double sizeH;
		public double sizeV;
		public double area;
		public double fwhmH;
		public double fwhmV;
		public double chi2H;
		public double chi2V;
		public double q;
		public double valid;
		public double ok;
		public double bendpos;
		public double spreadBend;
		public double spreadMom;
		
		public Measurement() {
			super();
			this.time=Instant.now();
		}
		
		public void copy(Measurement m) {
			time=m.time;
			power=m.power;
			powerOut=m.powerOut;
			phase=m.phase;
			solenoid=m.solenoid;
			bend=m.bend;
			mom=m.mom;
			posH=m.posH;
			posV=m.posV;
			sizeH=m.sizeH;
			sizeV=m.sizeV;
			area=m.area;
			fwhmH=m.fwhmH;
			fwhmV=m.fwhmV;
			chi2H=m.chi2H;
			chi2V=m.chi2V;
			q=m.q;
			valid=m.valid;
			ok=m.ok;
			bendpos=m.bendpos;
			spreadBend=m.spreadBend;
			spreadMom=m.spreadMom;
		}
		
		public void copy(MSeed m) {
			power=m.power;
			phase=m.phase;
			solenoid=m.solenoid;
			bend=m.bend;
		}

		boolean validRelaxed() {
			return true;
		}

		boolean valid() {
			return valid!=0.0; 
		}
		
		boolean ok() {
			return ok!=0.0; 
		}

		public double[] toArray() {
			return new double[]{power,powerOut,phase,solenoid,bend,mom,posH,q,valid,ok,bendpos,spreadBend,spreadMom};
		}
		
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append(" po=");
			sb.append(Tools.format1D(power));
			sb.append(" pou=");
			sb.append(Tools.format1D(powerOut));
			sb.append(" ph=");
			sb.append(Tools.format1D(phase));
			sb.append(" so=");
			sb.append(Tools.format1D(solenoid));
			sb.append(" be=");
			sb.append(Tools.format4D(bend));
			sb.append(" ph=");
			sb.append(Tools.format3D(posH));
			sb.append(" sh=");
			sb.append(Tools.format3D(sizeH));
			sb.append(" ar=");
			sb.append(Tools.format3D(area));
			sb.append(" fh=");
			sb.append(Tools.format3D(fwhmH));
			sb.append(" fv=");
			sb.append(Tools.format3D(fwhmV));
			sb.append(" c2h=");
			sb.append(Tools.format3D(chi2H));
			sb.append(" c2v=");
			sb.append(Tools.format3D(chi2V));
			sb.append(" q=");
			sb.append(Tools.format3D(q));
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
			sb.append(Tools.format2D(power));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(powerOut));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(solenoid));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bend));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(mom));
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
			sb.append(Tools.format2D(area));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(fwhmH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(fwhmV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(chi2H));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(chi2V));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(q));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(ok!=0.0));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bendpos));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(spreadBend));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(spreadMom));
			return sb;
		}
		
		public Appendable toDataString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(power));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(powerOut));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format2D(solenoid));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bend));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(mom));
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
			sb.append(Tools.format4D(area));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(fwhmH));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(fwhmV));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(chi2H));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(chi2V));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(q));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(ok!=0.0));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(bendpos));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(spreadBend));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(spreadMom));
			return sb;
		}

		public static Appendable toHeader(Appendable sb) throws IOException {
			sb.append("time");
			sb.append(',');
			sb.append(" power");
			sb.append(',');
			sb.append(" powerOut");
			sb.append(',');
			sb.append(" phase");
			sb.append(',');
			sb.append(" solenoid");
			sb.append(',');
			sb.append(" bend");
			sb.append(',');
			sb.append(" mom");
			sb.append(',');
			sb.append(" posH");
			sb.append(',');
			sb.append(" posV");
			sb.append(',');
			sb.append(" sizeH");
			sb.append(',');
			sb.append(" sizeV");
			sb.append(',');
			sb.append(" area");
			sb.append(',');
			sb.append(" fwhmH");
			sb.append(',');
			sb.append(" fwhmV");
			sb.append(',');
			sb.append(" chi2H");
			sb.append(',');
			sb.append(" chi2V");
			sb.append(',');
			sb.append(" q");
			sb.append(',');
			sb.append(" valid");
			sb.append(',');
			sb.append(" stable");
			sb.append(',');
			sb.append(" bend/pos");
			sb.append(',');
			sb.append(" spreadBend");
			sb.append(',');
			sb.append(" spreadMom");
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

	class OptimizerController extends AbstractController {
		
		private ScanningTask task;
		private Measurement measurement;

		public OptimizerController(ScanningTask task,Measurement m) {
			this.task=task;
			this.measurement=m;
		}
		
		@Override
		protected boolean takeMeasurements(ProbePoint[] points) {
			boolean b=true;
			for (int i = 0; i < points.length; i++) {
				if (!task.canRun()) {
					return false;
				}
				b = b & takeEnergyMeasurement(task, points[i], measurement);
			}
			return b;
		}
		
		@Override
		public boolean isAborted() {
			return !task.canRun();
		}
		
		public double ratioBendPos(double minDif) {
			
			List<Double> r= new ArrayList<>();
			List<ProbePoint> mes= new ArrayList<ProbePoint>(getMeasurements().values());
			
			for (int i = 0; i < mes.size(); i++) {
				for (int j = i+1; j < mes.size(); j++) {
					ProbePoint pa= mes.get(i);
					ProbePoint pb= mes.get(j);
					
					double db= pb.inp-pa.inp;
					double dp= pb.out-pa.out;
					
					if (Math.abs(dp)>minDif) {
						r.add(db/dp);
					}
				}
			}
			
			double ra=0;
			
			for (Iterator<Double> it = r.iterator(); it.hasNext();) {
				ra+=it.next();
			}
			
			ra=ra/r.size();
			
			return ra;
		}
		
		private void updateCounts() {
			getRecord(STATUS_BEND_ITER).setValue(this.getSteps());
			getRecord(STATUS_BEND_MEAS).setValue(this.getMeasurementCount());
		}
		
		@Override
		protected void notifyStepEnd() {
			updateCounts();
		}
		
		@Override
		protected void notifyStepStart() {
			updateCounts();
		}
	}

	class ScanningTask implements Runnable {
		
		private boolean skip=false;
		private boolean aborted=false;
		private boolean paused=false;
		double time=-1;
		private PrintWriter dataLog;
		int steps;
		int step;
		List<Measurement> data;
		List<MSeed> seeds;
		long start;
		int repeat=0;
		int countMeasurements=0;
		int countMeasurementsOK=0;

		public ScanningTask(int repeat) {
			this.repeat=repeat;
			log4info("Scan task started, repeat count "+repeat);
		}
		
		public void initData(int i) {
			data= new ArrayList<EnergyScanApplication.Measurement>(i);
			seeds= new ArrayList<EnergyScanApplication.MSeed>(i);
		}
		
		public void advanceProgress() {
			step++;
			progress.setValue(((double)step)/((double)steps)*100.0);
			//System.out.println("STEP "+step);
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
		
		public synchronized void skipSolenoid() {
			skip=true;
		}
		
		public boolean isSkip() {
			return skip;
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
				dataFile= new File(dataFile,"EnergyScan-"+Tools.FORMAT_ISO_DATE_TIME.format(new Date())+".csv");
				
				
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

		private void printSeedsToFile() throws IOException {
			
			File dataFile= new File(dataDir);
			dataFile.mkdirs();
			dataFile= new File(dataFile,"EnergySeed-"+Tools.FORMAT_ISO_DATE_TIME.format(new Date())+".csv");
				
			String f= dataFile.getAbsolutePath();
				
			log4info("Seeds file created "+f);

			getRecord(STATUS_SEEDS_FILE).setValue(f);

			PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(dataFile), 1024));

			pw.print("# ");
			MSeed.toHeader(pw);
			pw.println();
			
			for (MSeed ms : seeds) {
				ms.toDataString(pw);
				pw.println();
			}

			if (pw!=null) {
				pw.flush();
				pw.close();
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
			MSeed ms= new MSeed(m);
			seeds.add(ms);
		}
		
		public void loadSeeds(List<MSeed> l) {
			for (MSeed ms : l) {
				Measurement m= new Measurement();
				m.copy(ms);
				data.add(m);
				seeds.add(ms);
			}
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
	
	
	private ScanApplication powerScan;
	private ScanApplication phaseScan;
	private ScanApplication solenoidScan;
	private ScanApplication bendScan;
	private String beamSpotPV;
	
	private long measurementWait=10000;
	private ScanningTask task;
	private Instant start;
	private String bendDiffPV;
	private String screenSwitchPV;
	private String momentumPV;
	private String ictqPV;
	private double minQ;
	private String powerOutPV;
	private String dataDir;
	private String laserSwitchPV;
	private String takeBackgroundPV;
	private String takeBackgroundBusyPV;
	private int paused;
	private Record progress;
	private Record ictq;
	
	private final boolean debug_ignore_minQ=false;
	private String enableBackgroundPV;
	private String cyclePV;
	private double scanOptInpPrec;
	private double scanOptOutPrec;
	private Double powerBack;
	private Double phaseBack;
	private Double solenoidBack;
	private String bpmHorPV;
	private String bpmVerPV;
	private String bpmQPV;

	/**
	 * <p>Constructor for EnergyScanApplication.</p>
	 */
	public EnergyScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		powerScan = configureScan(name,"Power", config);
		phaseScan = configureScan(name,"Phase", config);
		solenoidScan = configureScan(name,"Solenoid", config);
		bendScan = configureScan(name,"Bend", config);

		beamSpotPV= config.getString("beamSpotPv");
		bendDiffPV= config.getString("bendDiffPv");
		momentumPV= config.getString("momentumPv");
		screenSwitchPV= config.getString("screenSwitchPv");
		ictqPV= config.getString("ictqPv");
		powerOutPV= config.getString("powerOutPv");
		laserSwitchPV= config.getString("laserSwitchPv");
		enableBackgroundPV= config.getString("enableBackgroundPv");
		takeBackgroundPV= config.getString("takeBackgroundPv");
		takeBackgroundBusyPV= config.getString("takeBackgroundBusyPv");
		cyclePV= config.getString("cyclePv");
		
		bpmHorPV= config.getString("bpmHorPv");
		bpmVerPV= config.getString("bpmVerPv");
		bpmQPV= config.getString("bpmQPv");

		if (bpmHorPV==null || bpmHorPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'bpmHorPV' is not set!");
		}
		if (bpmVerPV==null || bpmVerPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'bpmVerPV' is not set!");
		}
		if (bpmQPV==null || bpmQPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'bpmQPV' is not set!");
		}
		if (beamSpotPV==null || beamSpotPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'beamSpotPv' is not set!");
		}
		if (bendDiffPV==null || bendDiffPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'bendDiffPv' is not set!");
		}
		if (momentumPV==null || momentumPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'momentumPv' is not set!");
		}
		if (screenSwitchPV==null || screenSwitchPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'screenSwitchPv' is not set!");
		}
		if (ictqPV==null || ictqPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'ictqPv' is not set!");
		}
		if (powerOutPV==null || powerOutPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'powerOutPv' is not set!");
		}
		if (laserSwitchPV==null || laserSwitchPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'laserSwitchPv' is not set!");
		}
		if (enableBackgroundPV==null || enableBackgroundPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'enableBackgroundPv' is not set!");
		}
		if (takeBackgroundPV==null || takeBackgroundPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'takeBackgroundPv' is not set!");
		}
		if (takeBackgroundBusyPV==null || takeBackgroundBusyPV.length()==0) {
			throw new IllegalArgumentException("Parameter 'takeBackgroundBusyPv' is not set!");
		}
		if (cyclePV==null || cyclePV.length()==0) {
			throw new IllegalArgumentException("Parameter 'cyclePv' is not set!");
		}
		

		
		measurementWait= config.getLong("measurementWait", 10000);
		minQ= config.getDouble("ictqMin", 1.0);
		dataDir= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());

		scanOptInpPrec= config.getDouble("scanOpt.inpPrec", 0.01);
		scanOptOutPrec= config.getDouble("scanOpt.outPrec", 0.01);

		
		connectLinks(BPM, bpmHorPV, bpmVerPV, bpmQPV, powerOutPV);
		connectLinks(BEAM, beamSpotPV+":Data", powerOutPV);
		connectLinks(LINK_LASER_ENABLED, laserSwitchPV);
		connectLinks(LINK_TAKE_BACKGROUND, takeBackgroundPV);
		connectLinks(LINK_TAKE_BACKGROUND_BUSY, takeBackgroundBusyPV);
		connectLinks(LINK_ENABLE_BACKGROUND, enableBackgroundPV);
		connectLinks(LINK_CYCLE, cyclePV);
		
		addRecordOfOnLinkValueProcessor(STATUS_BEND_DIFF_ALARM, "Bend current and readback diff too large", DBRType.BYTE, bendDiffPV);
		addRecordOfOnLinkValueProcessor(SCREEN_SWITCH, "Screen switch", DBRType.INT, screenSwitchPV);
		addRecordOfOnLinkValueProcessor(MOMENTUM, MOMENTUM, DBRType.DOUBLE, momentumPV);
		addRecordOfOnLinkValueProcessor(ICT_Q, "ICT Q charge", DBRType.DOUBLE, ictqPV);
		
		addRecordOfMemoryValueProcessor(MEASUREMENT_LAST, "Last beam measurement", -1000.0, 1000.0, "", (short)3, new Measurement().toArray());
		addRecordOfMemoryValueProcessor(MEASUREMENT_PEAK, "Peak beam measurement", -1000.0, 1000.0, "", (short)3, new Measurement().toArray());
		addRecordOfMemoryValueProcessor(MEASUREMENT_TABLE, "Energy measurement results",new byte[1048576]);
		addRecordOfMemoryValueProcessor(MEASUREMENT_POSH1, "First H pos for energy measurement", -20.0, 20.0, "", (short)1, -4.0);
		addRecordOfMemoryValueProcessor(MEASUREMENT_POSH2, "Second H pos for energy measurement", -20.0, 20.0, "", (short)1, 0.0);
		addRecordOfMemoryValueProcessor(MEASUREMENT_POSH3, "Third H pos for energy measurement", -20.0, 20.0, "", (short)1, 4.0);

		addRecordOfMemoryValueProcessor(SEED_TABLE, "Seed points",new byte[1048576]);
		addRecordOfMemoryValueProcessor(SEED_BEND, "Bend seed", 0.0, 15.0, "A", (short)1, 0.0);
		
		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING_MS, "Remaining time of scan", 0, 1000000, "ms", 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{State.READY.toString(),State.SETTING_BEND.toString(),State.SCANNING_SOLENOID.toString(),State.SCANNING_BEND.toString(),State.PAUSED.toString(),State.ERROR.toString()}, (short)0);
		addRecordOfMemoryValueProcessor(STATUS_DATA_FILE, "Data file", new byte[1024]);
		addRecordOfMemoryValueProcessor(STATUS_SEEDS_FILE, "Seeds file", new byte[1024]);
		addRecordOfMemoryValueProcessor(STATUS_REPEAT, "Current repeat count", 0, 1000, "No.", 0);
		addRecordOfMemoryValueProcessor(STATUS_BEND_ITER, "Bend iterations count", 0, 10000, "No.", 0);
		addRecordOfMemoryValueProcessor(STATUS_BEND_MEAS, "Bend measurements count", 0, 10000, "No.", 0);

		addRecordOfMemoryValueProcessor(OPT_LASER_OFF, "Switches laser off at the end", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_LASER_ON, "Switches laser on at the start", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_BEND_OFF, "Sets spectormeter bend to 0 at the end", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_TAKE_BG, "Takes new background each measurement", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_ENABLE_BG, "Enables background substractions", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_VALID_ONLY, "Accept only valid measurements", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_BEND_SCAN, "Bend Scan Method", new String[]{"Disabled","Simple","Optimized"}, (short)2);
		addRecordOfMemoryValueProcessor(OPT_POWER_SCAN, "Power scan enabled", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_PHASE_SCAN, "Phase scan enabled", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_SOLENOID_SCAN, "Solenoid scan enabled", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_REPEAT, "How many repeats", 1, 10000,"No.",1);
		addRecordOfMemoryValueProcessor(OPT_CYCLE, "Cycle at end", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_MODE, "Scan Mode", new String[]{Mode.SOLENOID_AND_BEND.toString(),Mode.SOLENOID_ONLY.toString(),Mode.SEEDS_AND_BEND.toString(),Mode.FIND_ENERGY.toString()}, (short)2);
		addRecordOfMemoryValueProcessor(OPT_POWER_BACK, "Sets Power value back after scan", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_PHASE_BACK, "Sets Phase value back after scan", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_SOLENOID_BACK, "Sets Solenoid value back after scan", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_BEND_IN_PREC, "Beam spot precision", 0.0, 1.0, "mm", (short)4, scanOptInpPrec);
		addRecordOfMemoryValueProcessor(OPT_BEND_MAX_STEP, "Max iterations", 1, 100, "No.", 16);
		addRecordOfMemoryValueProcessor(OPT_USE_BPM, "USe BPM for beam emasurement instead of beam server", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(CMD_STOP, "Stops scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_PAUSE, "Pauses scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_SKIP, "Skip scanning and go to bend", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_SEED_NOW, "Take current values as seed", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(WAIT, "Wait for measurement", 0, 1000, "s", 0);
		addRecordOfMemoryValueProcessor(ICT_MIN, "ICT Q charge treshold", 0.0, 1000.0, "pC", (short)3,minQ);
		
		getRecord(MEASUREMENT_POSH1).setPersistent(true);
		getRecord(MEASUREMENT_POSH2).setPersistent(true);
		getRecord(MEASUREMENT_POSH3).setPersistent(true);
		getRecord(OPT_MODE).setPersistent(true);
		getRecord(OPT_CYCLE).setPersistent(true);
		getRecord(OPT_REPEAT).setPersistent(true);
		getRecord(OPT_SOLENOID_SCAN).setPersistent(true);
		getRecord(OPT_PHASE_SCAN).setPersistent(true);
		getRecord(OPT_POWER_SCAN).setPersistent(true);
		getRecord(OPT_BEND_SCAN).setPersistent(true);
		getRecord(OPT_VALID_ONLY).setPersistent(true);
		getRecord(OPT_ENABLE_BG).setPersistent(true);
		getRecord(OPT_TAKE_BG).setPersistent(true);
		getRecord(OPT_BEND_OFF).setPersistent(true);
		getRecord(OPT_LASER_OFF).setPersistent(true);
		getRecord(OPT_LASER_ON).setPersistent(true);
		getRecord(OPT_POWER_BACK).setPersistent(true);
		getRecord(OPT_PHASE_BACK).setPersistent(true);
		getRecord(OPT_SOLENOID_BACK).setPersistent(true);
		getRecord(OPT_BEND_IN_PREC).setPersistent(true);
		getRecord(OPT_BEND_MAX_STEP).setPersistent(true);
		getRecord(SEED_BEND).setPersistent(true);

		progress= getRecord(STATUS_PROGRESS);
		ictq= getRecord(ICT_Q);

		Record r= getRecord(WAIT);
		r.setPersistent(true);
		
		if (r.getValueAsInt()==0) {
			r.setValue((int)(measurementWait/1000.0));
		} else {
			measurementWait=r.getValueAsInt()*1000;
		}
		
		r= getRecord(ICT_MIN);
		r.setPersistent(true);
		minQ= r.getValueAsDouble();
		

		if (getRecord(ERROR_SUM).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
		}
		if (getRecord(LINK_ERROR).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateLinkError(false, "");
		}

		try {
			updateData(null);
		} catch (IOException e) {
			log4error(e.toString(), e);
		}
		try {
			updateSeeds(null);
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
		
		powerScan.getSetpoint().addPropertyChangeListener(l);
		phaseScan.getSetpoint().addPropertyChangeListener(l);
		solenoidScan.getSetpoint().addPropertyChangeListener(l);
		bendScan.getSetpoint().addPropertyChangeListener(l);

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
	
	private Mode getMode() {
		Record r= getRecord(OPT_MODE);
		int i= r.getValueAsInt();
		if (i<0 || i>3) {
			return null;
		}
		Mode m= Mode.values()[i];
		return m;
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
			t= estimateStepStart() + estimateStepSolenoid()*stepsSolenoid() + estimateStepBend()+stepsBend();
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
			updateTimeEst();
		} else if (name==ICT_MIN) {
			Record r= getRecord(ICT_MIN);
			minQ= r.getValueAsDouble();
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
		} else if (name==CMD_SKIP) {
			ScanningTask t=task;
			if (t!=null) {
				log4info("Solenoid scan skipped on request");
				t.skipSolenoid();
			}
		} else if (name==CMD_SEED_NOW) {
			if (getRecord(STATUS).getValueAsInt()!=State.READY.ordinal()) {
				return;
			}
			
			setSeedNow();
			
		}
	}
	
	private List<MSeed> setSeedNow() {
		
		MSeed s= takeSeedMeasurement();
		List<MSeed> l= new ArrayList<MSeed>(1);
		l.add(s);
		try {
			updateSeeds(l);
		} catch (IOException e) {
			log4error("Failed to measure seed: "+e.toString(), e);
		}
		
		return l;
		
	}
	
	private void checkEnableBackground(ScanningTask t) {
		if ((t!=null && !t.canRun()) || !isActivated()) {
			return;
		}

		log4debug("Enabling/Disabling background substraction");
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

		powerScan.stopManualScan();
		phaseScan.stopManualScan();
		solenoidScan.stopManualScan();
		bendScan.stopManualScan();

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
		
		if (powerBack!=null && powerScan!=null) {
			try {
				powerScan.getSetpoint().setValue(powerBack);
			} catch (Exception e) {
				log4error("Set failed", e);
			}
		}
		
		if (phaseBack!=null && phaseScan!=null) {
			try {
				phaseScan.getSetpoint().setValue(phaseBack);
			} catch (Exception e) {
				log4error("Set failed", e);
			}
		}
		
		if (solenoidBack!=null && solenoidScan!=null) {
			try {
				solenoidScan.getSetpoint().setValue(solenoidBack);
			} catch (Exception e) {
				log4error("Set failed", e);
			}
		}

		if (getRecord(OPT_BEND_OFF).getValueAsBoolean() || getRecord(OPT_CYCLE).getValueAsBoolean()) {
			try {
				getRecord(SCREEN_SWITCH).setValue(VALUE_SCREEN_FORWARD);
				
				if (getRecord(OPT_CYCLE).getValueAsBoolean()) {
					getLinks(LINK_CYCLE).setValue(1);
				} else {
					bendScan.getSetpoint().setValue(0.0);
				}

			} catch (Exception e) {
				log4error("Set failed", e);
			}
		}

	}
	
	private int stepsBend() {
		int i = (getRecord(OPT_POWER_SCAN).getValueAsBoolean() ? powerScan.getStepCount() : 1) * 
				(getRecord(OPT_PHASE_SCAN).getValueAsBoolean() ? phaseScan.getStepCount() : 1);
		if (getRecord(OPT_BEND_SCAN).getValueAsInt()!=0.0) {
			i *= bendScan.getStepCount();
		}
		return i;
	}
	
	private int stepsSolenoid() {
		return 
				(getRecord(OPT_POWER_SCAN).getValueAsBoolean() ? powerScan.getStepCount() : 1) * 
				(getRecord(OPT_PHASE_SCAN).getValueAsBoolean() ? phaseScan.getStepCount() : 1) *
				(getRecord(OPT_SOLENOID_SCAN).getValueAsBoolean() ? solenoidScan.getStepCount() : 1);
	}
	
	private long estimateStepStart() {
		return T_WAIT_FOR_BAND*60*4*2; 
	}

	private long estimateStepBend() {
		return 
				T_WAIT_FOR_BAND +
				measurementWait +
				(getRecord(OPT_TAKE_BG).getValueAsBoolean() ? T_BG_TAKE_ESTIMATE : 0); 
	}
	
	private long estimateStepSolenoid() {
		return 
				T_WAIT_FOR_SOLENOID + 
				measurementWait +
				(getRecord(OPT_TAKE_BG).getValueAsBoolean() ? T_BG_TAKE_ESTIMATE : 0); 
	}


	private boolean rampDownBend(ScanningTask t) throws IOException {

		getRecord(STATUS).setValue(State.SETTING_BEND.ordinal());
		getRecord(SCREEN_SWITCH).setValue(VALUE_SCREEN_FORWARD);

		double bend= bendScan.getSetpoint().getValueAsDouble();
		
		if (bend>0.000001) {
			
			bendScan.getSetpoint().setValue(0.0);
			
			log4info("Waiting for bend to cycle down");

			t.dataLogPrintln("# Waiting for bend "+LocalDateTime.now().toString());
			t.dataLogFlush();
			
			if (!waitForBend(t)) {
				return false;
			}
			
			if (bendScan.getSetpoint().getValueAsDouble()>0.00001) {
				bendScan.getSetpoint().setValue(0.0);
				
				log4info("Waiting for bend again to cycle down");

				if (!waitForBend(t)) {
					return false;
				}
			}
		}
		
		return true;
	}	
	
	
	/**
	 * Return false if failed.
	 * @param t
	 * @throws IOException 
	 */
	private boolean scanStart(ScanningTask t) throws IOException {
		
		powerBack=null;
		phaseBack=null;
		solenoidBack=null;
		
		if (getRecord(OPT_POWER_BACK).getValueAsBoolean()) {
			powerBack= powerScan.getSetpointValue();
		}
		if (getRecord(OPT_PHASE_BACK).getValueAsBoolean()) {
			phaseBack= phaseScan.getSetpointValue();
		}
		if (getRecord(OPT_SOLENOID_BACK).getValueAsBoolean()) {
			solenoidBack= solenoidScan.getSetpointValue();
		}


		Mode mode= getMode();
		if (mode==null) {
			log4error("MODE not valid, aborting!");
			return false;
		}

		start= Instant.now();
		
		checkEnableBackground(t);
		
		t.dataLogPrintln("# Start - mode "+mode.toString());
		t.dataLogPrintln("# Start - time "+LocalDateTime.now().toString());
		t.dataLogPrintln("#");
		t.dataLogFlush();

		if (mode== Mode.SOLENOID_AND_BEND || mode==Mode.SOLENOID_ONLY) {

			t.initData(powerScan.getStepCount() *  phaseScan.getStepCount());
			
			updateData(t.data);
			
			if (mode== Mode.SOLENOID_AND_BEND) {
				t.startProgress(stepsBend()+stepsSolenoid());
			} else {
				t.startProgress(stepsSolenoid());
			}
			
			log4info("steps: "+t.steps+" "+stepsSolenoid()+" "+stepsBend()+" "+powerScan.getStepCount()+" "+phaseScan.getStepCount()+" "+solenoidScan.getStepCount()+" "+bendScan.getStepCount());
			
			boolean b= rampDownBend(t);
			
			if (!b) {
				return false;
			}
			
			b= scanSolenoid(t);
			
			t.dataLogPrintln("# Solenoid Scan end "+LocalDateTime.now().toString());
			t.dataLogPrintln("# Seeds/Valid measurements/all measurements: "+t.seeds.size()+"/"+t.countMeasurementsOK+"/"+t.countMeasurements);
			t.dataLogFlush();
			
			if (!b) {
				return false;
			}
			
			if (t.data.size()==0) {
				
				t.dataLogPrintln("# NO Bend scan measurements "+LocalDateTime.now().toString());
				t.dataLogPrintln("# NO Momentum spread results "+LocalDateTime.now().toString());
				t.dataLogFlush();
				
				progress.setValue(100.0);
				return true;

			}
			
			t.printSeedsToFile();
		}
		
		if (mode== Mode.SEEDS_AND_BEND || mode== Mode.FIND_ENERGY) {

			List<MSeed> l;
			
			if (mode== Mode.SEEDS_AND_BEND) {
				l= getSeeds();
			} else {
				l=setSeedNow();
			}

			t.initData(l.size());
			
			t.loadSeeds(l);
			t.printSeedsToFile();
			
			t.dataLogPrintln("# Seeds loaded, count : "+t.seeds.size());

			t.startProgress(l.size());
			
		}

		if (mode== Mode.SOLENOID_AND_BEND || mode==Mode.SEEDS_AND_BEND || mode== Mode.FIND_ENERGY) {

			int scan= getRecord(OPT_BEND_SCAN).getValueAsInt();
	
			// only optimized
			scan = 2;
			
			boolean b=true;
			
			if (scan>0) {
				if (scan==1) {
					b= scanEnergySimple(t);
				} else {
					b= scanEnergyOptimizer(t);
				}
				
				t.dataLogPrintln("# Momentum spread results "+LocalDateTime.now().toString());
				t.dataLogPrintHeader();
				t.dataLogFlush();
				
				for (Measurement m : t.data) {
					t.dataLogPrint(m);
				}
				t.dataLogFlush();
			}
	
			if (!b) {
				return false;
			}
	
			log4info("Steps "+t.step+"/"+t.steps+" in "+(System.currentTimeMillis()-t.start));
	
			t.endProgress();
			
		}
		
		return true;
	}

	private boolean scanSolenoid(ScanningTask t) throws IOException {
		
		getRecord(STATUS).setValue(State.SCANNING_SOLENOID.ordinal());

		t.dataLogPrintHeader();
		t.dataLogFlush();
		
		boolean spower= getRecord(OPT_POWER_SCAN).getValueAsBoolean();
		if (spower) {
			powerScan.startManualScan();
		}
		
		do {
			
			if (t.isSkip()) {
				return true;
			}
			
			boolean sphase= getRecord(OPT_PHASE_SCAN).getValueAsBoolean();

			if (sphase) {
				phaseScan.startManualScan();
			}

			do {
				
				if (t.isSkip()) {
					return true;
				}

				Measurement peak=null;

				boolean ssole= getRecord(OPT_SOLENOID_SCAN).getValueAsBoolean();
				
				if (ssole) {
					solenoidScan.startManualScan();
				}
				
				do {
					
					if (t.isSkip()) {
						break;
					}

					if (ssole && !t.delay(T_WAIT_FOR_SOLENOID)) {
						return false;
					}
					
					if (t.isSkip()) {
						break;
					}

					if (!t.delay(measurementWait)) {
						return false;
					}

					if (t.isSkip()) {
						break;
					}

					if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) {
						boolean r= takeNewBackground(t);
						if (!r) {
							return false;
						}
					}

					if (t.isSkip()) {
						break;
					}

					Measurement m= new Measurement();
					boolean mes= takeBeamMeasurement(m);
					
					t.incCountM();
					
					if (mes) {
						
						t.incCountMOK();

						getRecord(MEASUREMENT_LAST).setValue(m.toArray());
						log4info("Measurement "+m.toString());

						if ((peak==null || peak.area==0.0 || peak.area>m.area) && (!getRecord(OPT_VALID_ONLY).getValueAsBoolean() || m.valid())) {
							peak=m;
							getRecord(MEASUREMENT_PEAK).setValue(peak.toArray());
						}
						
						t.dataLogPrint(m);
						t.dataLogFlush();
						
					}
						
					if (!t.canRun()) {
						return false;
					}

					t.advanceProgress();

					if (!t.canRun()) {
						return false;
					}
					
					if (ssole) {
						solenoidScan.stepManualScan();
					}
					
				} while(ssole && solenoidScan.isManualScanActive());
				
				if (!t.canRun()) {
					return false;
				}
				
				if (!ssole || (peak!=null && (debug_ignore_minQ || peak.q>minQ))) {
					getRecord(MEASUREMENT_PEAK).setValue(peak.toArray());
					
					t.addData(peak);
					updateData(t.data);
					updateSeeds(t.seeds);
				}
				
				if (sphase) {
					phaseScan.stepManualScan();
				}
				
			} while(sphase && phaseScan.isManualScanActive());		
			
			if (!t.canRun()) {
				return false;
			}
			
			if (spower) {
				powerScan.stepManualScan();
			}
			
		} while(spower && powerScan.isManualScanActive());
		
		return true;
		
	}
	
	

	private boolean scanEnergySimple(ScanningTask t) throws IOException {
		
		getRecord(SCREEN_SWITCH).setValue(VALUE_SCREEN_SIDE);
		getRecord(STATUS).setValue(State.SETTING_BEND.ordinal());

		bendScan.getSetpoint().setValue(bendScan.getStartValue());
		
		log4info("Waiting for bend");
		t.dataLogPrintln("# Waiting for bend "+LocalDateTime.now().toString());
		t.dataLogFlush();

		if (!waitForBend(t)) {
			log4error("Bend waiting timeout");
			return false;
		}
		
		t.dataLogPrintln("# Bend scan measurements "+LocalDateTime.now().toString());
		t.dataLogPrintHeader();
		t.dataLogFlush();
		
		getRecord(STATUS).setValue(State.SCANNING_BEND.ordinal());

		for (Iterator<Measurement> iterator = t.data.iterator(); iterator.hasNext();) {
			Measurement m = iterator.next();

			powerScan.getSetpoint().setValue(m.power);
			phaseScan.getSetpoint().setValue(m.phase);
			solenoidScan.getSetpoint().setValue(m.solenoid);
			
			m.posH=0.0;
			m.posV=0.0;
			m.sizeH=0.0;
			m.sizeV=0.0;
			m.area=0.0;
			m.bend=0.0;
			
			bendScan.startManualScan();
			
			Measurement m1=m;
			
			int blank=0;
			
			while(bendScan.isManualScanActive()) {
				
				if (!waitForBend(t)) {
					return false;
				}

				if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) {
					boolean r= takeNewBackground(t);
					if (!r) {
						return false;
					}
				}

				if (!t.delay(measurementWait*2)) {
					return false;
				}

				Measurement mm= (Measurement) m.clone();
				boolean mes= takeEnergyMeasurement(mm);
				
				if (mes) {
					
					getRecord(MEASUREMENT_LAST).setValue(mm.toArray());
					log4info("Measurement "+mm.toString());

					if ((m.bend==0.0 || Math.abs(mm.posH)<Math.abs(m.posH)) && mm.validRelaxed()) {
						if (m.bend==0.0) {
							m1=(Measurement) mm.clone();
						} else {
							m1=(Measurement) m.clone();
						}
						m.copy(mm);
						getRecord(MEASUREMENT_PEAK).setValue(m.toArray());
					}
					
					updateData(t.data);
					t.dataLogPrint(mm);
					t.dataLogFlush();
					
					if (m.bend!=0.0 && mm.area<0.001) {
						blank++;
					} else {
						blank=0;
					}
					
					if (blank>5) {
						log4info("More than 5 blank beams");
						break;
					}

					//System.out.println("mm "+mm.bend+" "+mm.posH+" "+mm.validRelaxed());
					//System.out.println("m  "+m.bend+" "+m.posH+" "+m.validRelaxed());
					//System.out.println("m1 "+m1.bend+" "+m1.posH+" "+m1.validRelaxed());
					
					if (
							m.bend!=0.0 && 
							m1.bend!=0.0 && 
							Math.abs(mm.posH)>Math.abs(m1.posH) && 
							Math.abs(mm.posH)>Math.abs(m.posH)) {
						
						// getting worse
						log4info("Best 1st "+m.toString());
						log4info("Best 2nd "+m1.toString());
						
						double dh= m.posH-m1.posH;
						if (dh>0.001) {
							// bend0= -(posH1*bend2-posh2-bend1)/(posH2-posH1) 
							double b0= -(m1.posH*m.bend - m.posH*m1.bend) / (dh);
							log4info("Interpolated 0 posH to bend "+b0);
							
							if (b0<bendScan.getEndValue() && b0>bendScan.getStartValue()) {
								bendScan.getSetpoint().setValue(b0);
								if (!waitForBend(t)) {
									return false;
								}
								if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) {
									boolean r= takeNewBackground(t);
									if (!r) {
										return false;
									}
								}
								if (!t.delay(measurementWait*2)) {
									return false;
								}
								mm= (Measurement) m.clone();
								mes= takeEnergyMeasurement(mm);
								if (mes) {
									getRecord(MEASUREMENT_LAST).setValue(mm.toArray());
									log4info("Measurement "+mm.toString());
									updateData(t.data);
									t.dataLogPrint(mm);
									t.dataLogFlush();
									if (Math.abs(mm.posH)<Math.abs(m.posH) && mm.validRelaxed()) {
										m.copy(mm);
										getRecord(MEASUREMENT_PEAK).setValue(m.toArray());
									}
								}
								break;
							}
						}
					}
				}
					
				if (!t.canRun()) {
					return false;
				}

				t.advanceProgress();

				if (!t.canRun()) {
					return false;
				}
				
				bendScan.stepManualScan();
			}

			updateData(t.data);
			
		}
		
		return true;
	}

	private boolean scanEnergyOptimizer(ScanningTask t) throws IOException {
		
		getRecord(SCREEN_SWITCH).setValue(VALUE_SCREEN_SIDE);
		getRecord(STATUS).setValue(State.SETTING_BEND.ordinal());

		t.dataLogPrintln("# Bend scan measurements "+LocalDateTime.now().toString());
		t.dataLogPrintHeader();
		t.dataLogFlush();
		
		Measurement peak=null;
		
		for (Iterator<Measurement> iterator = t.data.iterator(); iterator.hasNext();) {
			Measurement m = iterator.next();

			powerScan.getSetpoint().setValue(m.power);
			phaseScan.getSetpoint().setValue(m.phase);
			solenoidScan.getSetpoint().setValue(m.solenoid);
			
			log.debug("Optimizer seeds "+m.toString());
			
			if (!t.canRun()) { return false; }
			
			OptimizerController contrl= new OptimizerController(t,m);
			contrl.initialize(bendScan.getStartValue(), bendScan.getEndValue(), getRecord(OPT_BEND_IN_PREC).getValueAsDouble(), scanOptOutPrec);
			contrl.setCacheMeasurements(true);
			contrl.setMaxSteps(getRecord(OPT_BEND_MAX_STEP).getValueAsInt());

			double bend= m.bend;
			
			if (bend>1.0 && bend>bendScan.getStartValue() && bend<bendScan.getEndValue()) {
				contrl.setSeeds(new ProbePoint[]{new ProbePoint(bend-0.02),new ProbePoint(bend),new ProbePoint(bend+0.02)});
			} else {
				bend= getRecord(SEED_BEND).getValueAsDouble();
				if (bend>1.0 && bend>bendScan.getStartValue() && bend<bendScan.getEndValue()) {
					contrl.setSeeds(new ProbePoint[]{new ProbePoint(bend-0.02),new ProbePoint(bend),new ProbePoint(bend+0.02)});
				}
			}
					
			contrl.start();

			if (!t.canRun()) { return false; }

			t.advanceProgress();

			ProbePoint pp= contrl.getBest();
			if (pp!=null) {
				if (pp.data==null) {
					log.warn("No data for point "+pp);
				} else {
					
					Measurement mes= (Measurement) pp.data;
					
					double bp= contrl.ratioBendPos(1.0);
					double db= bp * mes.sizeH;
					double dm= bend2mom(db);
					
					mes.bendpos=bp;
					mes.spreadBend=db;
					mes.spreadMom=dm;
					
					m.copy((Measurement) pp.data);
					
					getRecord(MEASUREMENT_LAST).setValue(m.toArray());
					log4info("Measurement "+m.toString());
					updateData(t.data);
					t.dataLogPrint(m);
					t.dataLogFlush();
					
					if (peak==null || peak.mom<m.mom) {
						peak=(Measurement) m.clone();
						getRecord(MEASUREMENT_PEAK).setValue(peak.toArray());
					}
				}
			}
			
			if (!t.canRun()) { return false; }
				
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
			m.area= d[BeamSpotData.AREA.ordinal()];
			m.fwhmH= d[BeamSpotData.FWHM_H.ordinal()];
			m.fwhmV= d[BeamSpotData.FWHM_V.ordinal()];
			m.chi2H= d[BeamSpotData.CHI2_H.ordinal()];
			m.chi2V= d[BeamSpotData.CHI2_V.ordinal()];
			m.valid= d[BeamSpotData.ST_VALID.ordinal()];
			m.ok= d[BeamSpotData.ST_RANGE.ordinal()]*d[BeamSpotData.ST_STABLE.ordinal()];
			m.powerOut= vh[1].doubleValue();
			m.q= ictq.getValueAsDouble();
			m.power= powerScan.getSetpointValue();
			m.phase= phaseScan.getSetpointValue();
			m.solenoid= solenoidScan.getSetpointValue();
			
			return true;
		}
		log4error("Measurement links are not available: "+vl);
		return false;
	}

	private MSeed takeSeedMeasurement() {
			
		MSeed s= new MSeed();
		s.bend= bendScan.getSetpointValue();
		s.phase= phaseScan.getSetpointValue();
		s.power= powerScan.getSetpointValue();
		s.solenoid= solenoidScan.getSetpointValue();
		
		return s;
			
	}

	/**
	 * Returns true if measurement was successful
	 * @param m
	 * @param q1 
	 * @return
	 */
	private boolean takeEnergyMeasurement(Measurement m) {
		
		boolean useBpm= getRecord(OPT_USE_BPM).getValueAsBoolean();
		
		if (useBpm) {

			ValueLinks vl= getLinks(BPM);
			
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid() ) {
				
				ValueHolder[] vh= vl.consume();
				
				m.time= Instant.now();
				m.posH= vh[0].doubleValue();
				m.posV= vh[1].doubleValue();
				m.powerOut= vh[3].doubleValue();
				
				m.bend= bendScan.getSetpoint().getValueAsDouble();
				m.mom= getRecord(MOMENTUM).getValueAsDouble();
				m.q= ictq.getValueAsDouble();
				
				m.valid= ( Math.abs(m.q-vh[2].doubleValue())/m.q<0.8 ) ? 1.0 : 0.0;
				m.ok= 1.0;

				return true;
			}
			log4error("Measurement links are not available: "+vl);
			
		} else {
		
			ValueLinks vl= getLinks(BEAM);
			
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid() ) {
				
				ValueHolder[] vh= vl.consume();
				double[] d= vh[0].doubleArrayValue();
				
				m.time= Instant.now();
				m.posH= d[BeamSpotData.POS_H.ordinal()];
				m.posV= d[BeamSpotData.POS_V.ordinal()];
				m.sizeH= d[BeamSpotData.SIZE_H.ordinal()];
				m.sizeV= d[BeamSpotData.SIZE_V.ordinal()];
				m.area= d[BeamSpotData.AREA.ordinal()];
				m.fwhmH= d[BeamSpotData.FWHM_H.ordinal()];
				m.fwhmV= d[BeamSpotData.FWHM_V.ordinal()];
				m.chi2H= d[BeamSpotData.CHI2_H.ordinal()];
				m.chi2V= d[BeamSpotData.CHI2_V.ordinal()];
				m.valid= d[BeamSpotData.ST_VALID.ordinal()];
				m.ok= d[BeamSpotData.ST_RANGE.ordinal()]*d[BeamSpotData.ST_STABLE.ordinal()];
				m.powerOut= vh[1].doubleValue();
				
				m.bend= bendScan.getSetpoint().getValueAsDouble();
				m.mom= getRecord(MOMENTUM).getValueAsDouble();
				m.q= ictq.getValueAsDouble();
				
				return true;
			}
			log4error("Measurement links are not available: "+vl);
		}
		return false;
	}

	private boolean takeEnergyMeasurement(ScanningTask t, ProbePoint pp, Measurement mm) {
		
		log4info("Energy measurement for b: "+pp.inp);

		bendScan.getSetpoint().setValue(pp.inp);
		if (!waitForBend(t)) { return false; }
		
		if (!t.delay(measurementWait)) { return false; }
		if (!t.canRun()) { return false; }
		if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) { if (!takeNewBackground(t)) { return false; } }
		if (!t.canRun()) { return false; }

		Measurement m= (Measurement) mm.clone();
		boolean b= takeEnergyMeasurement(m);
		
		if (!t.canRun()) { return false; }
		
		if (!b || !m.valid()) {
			if (getRecord(OPT_TAKE_BG).getValueAsBoolean()) { if (!takeNewBackground(t)) { return false; } }
			if (!t.canRun()) { return false; }
			b= takeEnergyMeasurement(m);
		}
		
		if (!t.canRun()) { return false; }
		
		if (!b) {
			b= takeEnergyMeasurement(m);
		}
		
		if (!t.canRun()) { return false; }
		
		pp.out=m.posH;
		pp.valid=getRecord(OPT_VALID_ONLY).getValueAsBoolean() ? m.valid() : m.ok();
		pp.data=m;
		
		try {
			updateData(t.data);
			t.dataLogPrint(m);
			t.dataLogFlush();
		} catch (IOException e) {
			log4error("Log failed "+e.toString(), e);
		}

		
		return b;
		
	}

	/**
	 * Waits for bend diff alarm to be false for up to 1 minute. If alarm is false within minute, then false is returned, otherwise true.
	 * @return If alarm is false within minute, then false is returned, otherwise true
	 */
	private boolean waitForBend(ScanningTask t) {

		if (!t.delay(T_WAIT_FOR_BAND)) {
			return false;
		}
		for(int i=0; i<300; i++) {
			if (!t.delay(T_WAIT_FOR_BAND)) {
				return false;
			}
			boolean b= getRecord(STATUS_BEND_DIFF_ALARM).getValueAsBoolean();
			if (!b) {
				return true;
			}
		}
		return false;
	}
	
	private void updateData(List<Measurement> data) throws IOException {
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
		
		getRecord(MEASUREMENT_TABLE).setValue(sb.toString());

	}
	
	private void updateSeeds(List<MSeed> data) throws IOException {
		StringBuilder sb= new StringBuilder(1024);
		sb.append('#');
		MSeed.toHeader(sb);
		sb.append('\n');

		if (data!=null) {
			for (Iterator<MSeed> iterator = data.iterator(); iterator.hasNext();) {
				MSeed m = iterator.next();

				m.toLogString(sb);
				sb.append('\n');
			}
		}
		
		getRecord(SEED_TABLE).setValue(sb.toString());

	}

	private List<MSeed> getSeeds() throws IOException {
		
		List<MSeed> l= new ArrayList<EnergyScanApplication.MSeed>(16);
		
		String s= getRecord(SEED_TABLE).getValueAsString();
		
		StringTokenizer st= new StringTokenizer(s, "\n", false);
		
		while(st.hasMoreTokens()) {
			String lin= st.nextToken().trim();
			if (lin.charAt(0)=='#') {
				continue;
			}
			StringTokenizer stt= new StringTokenizer(lin, ",", false);
			
			MSeed ms= new MSeed();
			
			if (stt.hasMoreTokens()) {
				String ss= stt.nextToken().trim();
				try {
					ms.power= Double.parseDouble(ss);
				} catch (NumberFormatException e) {
					log4error("Failed to convert to number '"+ss+"': "+e.toString(), e);
				}
				if (stt.hasMoreTokens()) {
					ss= stt.nextToken().trim();
					try {
						ms.phase= Double.parseDouble(ss);
					} catch (NumberFormatException e) {
						log4error("Failed to convert to number '"+ss+"': "+e.toString(), e);
					}
					if (stt.hasMoreTokens()) {
						ss= stt.nextToken().trim();
						try {
							ms.solenoid= Double.parseDouble(ss);
						} catch (NumberFormatException e) {
							log4error("Failed to convert to number '"+ss+"': "+e.toString(), e);
						}
						if (stt.hasMoreTokens()) {
							ss= stt.nextToken().trim();
							try {
								ms.bend= Double.parseDouble(ss);
							} catch (NumberFormatException e) {
								log4error("Failed to convert to number '"+ss+"': "+e.toString(), e);
							}
						}
					}
				}
			}
			l.add(ms);
		}
		return l;
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
	
	private double bend2mom(double bend) {
		double f0= 0.0009;
		double f1= 0.0057;
		double m0= 0.0;
		double m1= 89.9377374;
		
		double field= f0+ f1 * bend;
		
		double mom= m0 + m1 * field;
		
		return mom;

	}
}
