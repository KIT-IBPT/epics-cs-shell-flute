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
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.Tools;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;
import org.scictrl.csshell.math.PatternSearch;
import org.scictrl.csshell.math.Smoothing;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import si.ijs.anka.config.BootstrapLoader;

/**
 * Scans phase, analyzes ICT reading and extracts breaking points.
 *
 * @author igor@scictrl.com
 */
public class PhaseScanApplication extends AbstractApplication {
	
	private enum State {
		READY("Ready"),
		SCANNING("Scanning"),
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
	
	private static final String ICT = "ICT";

	private static final long T_WAIT_FOR_ICT = 1000;

	private static final String MEASUREMENT_PHASE= "Meas:Phase";
	private static final String MEASUREMENT_ICT =  "Meas:ICT";
	
	private static final String DATA_ICT =  		"Data:ICT";
	private static final String DATA_SAMPLES =		"Data:Samples";
	private static final String DATA_BREAKPOINTS = 	"Data:Breakpoints";
	private static final String DATA_WORKPOINTS =  	"Data:Workpoints";
	private static final String DATA_BREAKPOINTS_ICT = 	"Data:Breakpoints:ICT";
	private static final String DATA_WORKPOINTS_ICT =  	"Data:Workpoints:ICT";

	private static final String CMD_STOP = 		"Cmd:Stop";
	private static final String CMD_START = 	"Cmd:Start";
	private static final String CMD_CALCULATE = "Cmd:Calc";

	private static final String STATUS = 				"Status";
	private static final String STATUS_SCANNING = 		"Status:Scanning";
	private static final String STATUS_PROGRESS = 		"Status:Progress";
	private static final String STATUS_REMAINING = 		"Status:Remaining";
	private static final String STATUS_REMAINING_MS = 	"Status:Remaining:ms";
	private static final String STATUS_DATA_FILE = 		"Status:DataFile";
	private static final String STATUS_REPEAT = 		"Status:Repeat";
	private static final String STATUS_ERROR = 			"Status:Error";
	
	private static final String OPT_WAIT = 			"Opt:Wait";

	final static class Measurement implements Cloneable {
		public Instant time;
		public double phase;
		public double charge;
		public double valid;
		
		public Measurement() {
			super();
			this.time=Instant.now();
		}
		
		public void copy(Measurement m) {
			time=m.time;
			phase=m.phase;
			charge=m.charge;
			valid=m.valid;
		}
		
		boolean validRelaxed() {
			return true;
		}

		boolean valid() {
			return valid!=0.0; 
		}
		
		public double[] toArray() {
			return new double[]{phase,charge,valid};
		}
		
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append(" ph=");
			sb.append(Tools.format4D(phase));
			sb.append(" Q=");
			sb.append(Tools.format3D(charge));
			sb.append(" val=");
			sb.append(valid());
			
			return sb.toString();
		}

		public Appendable toLogString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(charge));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			return sb;
		}
		
		public Appendable toDataString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(phase));
			sb.append(',');
			sb.append(' ');
			sb.append(Tools.format4D(charge));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			return sb;
		}

		public static Appendable toHeader(Appendable sb) throws IOException {
			sb.append("time");
			sb.append(',');
			sb.append(" phase");
			sb.append(',');
			sb.append(" charge");
			sb.append(',');
			sb.append(" valid");
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
			data= new ArrayList<PhaseScanApplication.Measurement>(i);
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
			
			log4debug("Aborted");
			aborted=true;
			
			scanStop(this,b);
			
			scanCleanup();
			
		}
		
		private PrintWriter getDataLog() throws IOException {
			
			if (dataLog==null) {
				File dataFile= new File(dataDir);
				dataFile.mkdirs();
				dataFile= new File(dataFile,"PhaseScan-"+Tools.FORMAT_ISO_DATE_TIME.format(Instant.now().toEpochMilli())+".csv");
				
				
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
			
			return !aborted;
		}
		
	}
	
	
	private ScanApplication phaseScan;
	private String ictPV;
	
	private long measurementWait=10000;
	private ScanningTask task;
	private Instant start;
	private String dataDir;
	private Record progress;

	private ValueLinks ict;
	

	/**
	 * <p>Constructor for EmittanceScanApplication.</p>
	 */
	public PhaseScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		phaseScan = configureScan(name,"Phase", config);
		
		ictPV= config.getString("ictPV","F:INJ-1:ICT:01:Q");
		
		if (ictPV==null || ictPV.length()==0) {
			throw new IllegalArgumentException("Configured ictPV is not set!");
		}
		
		ict= connectLinks(ICT, ictPV);

		measurementWait= config.getLong("measurementWait", 10000);
		dataDir= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());

		addRecordOfMemoryValueProcessor(MEASUREMENT_PHASE, "Phase measurements", -1000.0, 1000.0, "", (short)2, new double[10000]);
		addRecordOfMemoryValueProcessor(MEASUREMENT_ICT, "Charge measurements", -1000.0, 1000.0, "", (short)2, new double[10000]);

		addRecordOfMemoryValueProcessor(DATA_ICT, "Charge Smoothed", -1000.0, 1000.0, "", (short)2, new double[10000]);
		addRecordOfMemoryValueProcessor(DATA_BREAKPOINTS, "Breakpoints", -1000.0, 1000.0, "", (short)2, new double[3]);
		addRecordOfMemoryValueProcessor(DATA_WORKPOINTS, "Workpoints", -1000.0, 1000.0, "", (short)2, new double[4]);
		addRecordOfMemoryValueProcessor(DATA_BREAKPOINTS_ICT, "Breakpoints ICT values", -1000.0, 1000.0, "", (short)2, new double[3]);
		addRecordOfMemoryValueProcessor(DATA_WORKPOINTS_ICT, "Workpoints ICT values", -1000.0, 1000.0, "", (short)2, new double[4]);
		addRecordOfMemoryValueProcessor(DATA_SAMPLES, "Smoothing Sample count", 1, 1000, "No.", 1);

		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING_MS, "Remaining time of scan", 0, 1000000, "ms", 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{State.READY.toString(),State.SCANNING.toString(),State.ERROR.toString()}, (short)0);
		addRecordOfMemoryValueProcessor(STATUS_DATA_FILE, "Data file", new byte[1024]);
		addRecordOfMemoryValueProcessor(STATUS_ERROR, "Last result has errors", DBRType.BYTE, 0);

		addRecordOfMemoryValueProcessor(CMD_STOP, "Stops scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_CALCULATE, "Calculate results", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(OPT_WAIT, "Wait for measurement", 0, 1000, "s", 0);
		
		progress= getRecord(STATUS_PROGRESS);

		Record r= getRecord(OPT_WAIT);
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
		
		phaseScan.getSetpoint().addPropertyChangeListener(l);

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
	
	private void scanCleanup() {
		// maybe later
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
			t= estimateStepPhase() * stepsPhase();
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
		
		database.addAll(phaseScan.getRecords());
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
		
		if (name==OPT_WAIT) {
			Record r= getRecord(OPT_WAIT);
			measurementWait=r.getValueAsInt()*1000;
			updateTimeEst();
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (name==CMD_START) {
			if (task!=null) {
				log4info("Scan request denied, scan in progress");
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
		} else if (name==CMD_CALCULATE) {
			scanCalc();
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

		phaseScan.stopManualScan();

		if (success) {
			getRecord(STATUS).setValue(State.READY.ordinal());
		} else {
			getRecord(STATUS).setValue(State.ERROR.ordinal());
		}
		getRecord(STATUS_SCANNING).setValue(0);
		
	}
	
	private int stepsPhase() {
		return phaseScan.getStepCount();
	}
	
	private long estimateStepPhase() {
		return 
				T_WAIT_FOR_ICT + 
				measurementWait; 
	}

	/**
	 * Return false if failed.
	 * @param t
	 * @throws IOException 
	 */
	private boolean scanStart(ScanningTask t) throws IOException {
		
		start= Instant.now();
		
		getRecord(STATUS_ERROR).setValue(false);
		
		t.dataLogPrintln("# Start, time "+LocalDateTime.now().toString());
		t.dataLogPrintln("#");
		t.dataLogFlush();

		t.initData(phaseScan.getStepCount());
		
		updateData(t.data);
		
		t.startProgress(stepsPhase());
		
		log4info("steps: "+t.steps+" "+stepsPhase());
		
		boolean b= scanPhase(t);
		
		t.dataLogPrintln("# Scan end "+LocalDateTime.now().toString());
		
		t.dataLogFlush();
		
		if (!b) {
			return false;
		}
		
		if (t.data.size()==0) {
			t.dataLogPrintln("# NO scan measurements "+LocalDateTime.now().toString());
			t.dataLogFlush();
			
			return true;
		}
		
		progress.setValue(100.0);
		
		scanCalc();
		
		return true;
	}

	private boolean scanCalc() {

		double[] ictRaw= getRecord(MEASUREMENT_ICT).getValueAsDoubleArray();
		double[] phase= getRecord(MEASUREMENT_PHASE).getValueAsDoubleArray();
		
		double[][] pre= Smoothing.collapseSame(phase, ictRaw);
		
		if (pre[0].length!=phase.length) {
			ictRaw = pre[1];
			phase = pre[0];
			getRecord(MEASUREMENT_ICT).setValue(ictRaw);
			getRecord(MEASUREMENT_PHASE).setValue(phase);
		}
		
		int samples= (int)(ictRaw.length/100.0);
		
		double[] ictSmooth= Smoothing.smoothAvg(ictRaw, samples);
		
		int[] brkPn= PatternSearch.findBreakpointsHiLoHHi(ictSmooth, 1.0);
		
		double[] brkP= new double[brkPn.length];
		double[] brkIct= new double[brkPn.length];
		
		for (int i = 0; i < brkP.length; i++) {
			if (brkPn[i]>-1) {
				brkP[i] = phase[brkPn[i]];
				brkIct[i] = ictSmooth[brkPn[i]];
			} else {
				brkP[i] = Double.NaN;
			}
		}
		
		double[] wrkP = new double[4];
		
		wrkP[0] = brkP[0] + 90.0 - 50.0; // Q max
		wrkP[1] = brkP[0] + 90.0 - 16.0; // Q/A max
		wrkP[2] = brkP[0] + 90.0;        // p max
		wrkP[3] = brkP[0] + 90.0 + 21.0; // sigma min
		
		double[] wrkIct= new double[4];
		
		double rate= (brkPn[brkPn.length-1] - brkPn[0])/(brkP[brkP.length-1] - brkP[0]);
		
		if (!Double.isNaN(rate)) {
			try {
				wrkIct[0] = ictSmooth[(int)(brkPn[0] + (90.0 - 50.0) * rate)]; // Q max
				wrkIct[1] = ictSmooth[(int)(brkPn[0] + (90.0 - 16.0) * rate)]; // Q/A max
				wrkIct[2] = ictSmooth[(int)(brkPn[0] + (90.0) * rate)];        // p max
				wrkIct[3] = ictSmooth[(int)(brkPn[0] + (90.0 + 21.0) * rate)]; // sigma min
			} catch (Exception e) {
				log4error("Failed to find ICT values "+e.toString(), e);
			}
		}
		
		
		getRecord(DATA_ICT).setValue(ictSmooth);
		getRecord(DATA_BREAKPOINTS).setValue(brkP);
		getRecord(DATA_SAMPLES).setValue(samples);
		getRecord(DATA_WORKPOINTS).setValue(wrkP);
		getRecord(DATA_BREAKPOINTS_ICT).setValue(brkIct);
		getRecord(DATA_WORKPOINTS_ICT).setValue(wrkIct);
		
		return true;
	}
	
	private boolean scanPhase(ScanningTask t) throws IOException {
		
		getRecord(STATUS).setValue(State.SCANNING.ordinal());

		t.dataLogPrintHeader();
		t.dataLogFlush();
		
		phaseScan.startManualScan();
		
		do {
			
			if (!t.delay(measurementWait)) {
				return false;
			}

			Measurement m= new Measurement();
			boolean mes= takeBeamMeasurement(m);
			
			t.incCountM();
			
			if (mes) {
				
				t.addData(m);
				t.incCountMOK();

				log4info("Measurement "+m.toString());

				t.dataLogPrint(m);
				t.dataLogFlush();
				
				updateData(t.data);

			}
				
			if (!t.canRun()) {
				return false;
			}

			t.advanceProgress();

			if (!t.canRun()) {
				return false;
			}
			
			phaseScan.stepManualScan();
			
		} while(phaseScan.isManualScanActive());
		
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
		
		if (!ict.isInvalid() && ict.isReady() && !ict.isLastSeverityInvalid()) {
			
			ValueHolder[] vh= ict.consume();
			double d= vh[0].doubleValue();
			
			m.time= Instant.now();
			m.valid= 1;
			m.phase= phaseScan.getSetpoint().getValueAsDouble();
			m.charge= d;
			
			return true;
		}
		log4error("Measurement links are not available");
		return false;
	}

	private void updateData(List<Measurement> data) throws IOException {
		
		if (data==null || data.size()==0) {
			getRecord(MEASUREMENT_PHASE).setValue(0.0);
			getRecord(MEASUREMENT_ICT).setValue(0.0);
			return;
		}

		double[] phs= new double[data.size()];
		double[] ict= new double[data.size()];

		for (int i = 0; i < data.size(); i++) {
			
			Measurement m= data.get(i);
			
			phs[i]=m.phase;
			ict[i]=m.charge;
		}
		
		getRecord(MEASUREMENT_PHASE).setValue(phs);
		getRecord(MEASUREMENT_ICT).setValue(phs);

	}
		
}
