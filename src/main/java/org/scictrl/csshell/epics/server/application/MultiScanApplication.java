/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.Tools;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import si.ijs.anka.config.BootstrapLoader;

/**
 * Filters and averages the BPM values. Values with Q below treshold ar thrown away.
 *
 * @author igor@scictrl.com
 */
public class MultiScanApplication extends AbstractApplication {
	
	private enum State {
		READY("Ready"),
		SCANNING("Scanning"),
		MEASURING("Measuring"),
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
	
	private static final String MEASUREMENT_LAST = 	"Meas:Last";
	private static final String MEASUREMENT_TABLE = "Meas:Table";
	private static final String MEASUREMENT_PVS =   "Meas:PVs";
	private static final String CMD_STOP = 			"Cmd:Stop";
	private static final String CMD_START = 		"Cmd:Start";
	private static final String CMD_PAUSE = 		"Cmd:Pause";
	private static final String STATUS = 			"Status";
	private static final String STATUS_SCANNING = 	"Status:Scanning";
	private static final String STATUS_PROGRESS = 	"Status:Progress";
	private static final String STATUS_REMAINING = 	"Status:Remaining";
	private static final String STATUS_DATA_FILE = 	"Status:DataFile";
	private static final String WAIT = 				"Wait";
	private static final String REPEAT =			"Repeat";
	
	private static final String OPT_SCAN1 = "Opt:Scan1";
	private static final String OPT_SCAN2 = "Opt:Scan2";
	private static final String OPT_SCAN3 = "Opt:Scan3";

	private static final String MEASUREMENTS = "Measurements";

	final class Measurement implements Cloneable {
		public Instant time;
		public double[] data;
		
		public Measurement() {
			super();
			this.time=Instant.now();
			data= new double[]{0.0};
		}
		
		public Measurement copy(Measurement m) {
			time=m.time;
			data=Arrays.copyOf(m.data, m.data.length);
			return this;
		}
		
		boolean valid() {
			return data[0]!=0.0; 
		}
		
		public String toString() {
			StringBuilder sb= new StringBuilder(128);
			sb.append(valid());
			sb.append(" data=");
			sb.append(Arrays.toString(data));
			
			return sb.toString();
		}

		public Appendable toLogString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(Boolean.toString(valid()));
			for (int i = 1; i < data.length; i++) {
				sb.append(',');
				sb.append(' ');
				sb.append(Tools.format4D(data[i]));
			}
			return sb;
		}
		
		public Appendable toDataString(Appendable sb) throws IOException {
			sb.append(Tools.FORMAT_ISO_DATE_TIME.format(time.toEpochMilli()));
			sb.append(',');
			sb.append(' ');
			sb.append(String.valueOf((int)(data[0])));
			for (int i = 1; i < data.length; i++) {
				sb.append(',');
				sb.append(' ');
				sb.append(Tools.format4D(data[i]));
			}
			return sb;
		}

		@Override
		public Object clone() {
			return new Measurement().copy(this);
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

		public ScanningTask() {
		}
		
		public void advanceProgress() {
			step++;
			progress.setValue(((double)step)/((double)steps)*100.0);
			System.out.println("STEP "+step);
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

		public synchronized void abort() {
			aborted=true;
			notifyAll();
		}
		
		public boolean isAborted() {
			return aborted;
		}
		
		@Override
		public synchronized void run() {
			
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
			
		}
		
		private PrintWriter getDataLog() throws IOException {
			
			if (dataLog==null) {
				File dataFile= new File(dataDir);
				dataFile.mkdirs();
				dataFile= new File(dataFile,"MultiScan-"+DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())+".csv");
				
				
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
			if (t>0L) {
				try {
					this.wait(t);
				} catch (InterruptedException e) {
					log4error("Wait interupted: "+e.toString(), e);
				}
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
	
	private long measurementWait=10000;
	private ScanningTask task;
	private Instant start;
	private String dataDir;
	private int paused;
	private Record progress;
	private ScanApplication scan1;
	private ScanApplication scan2;
	private ScanApplication scan3;
	private String[] measPVs;
	private int measurementRepeat;

	/**
	 * <p>Constructor for MultiScanApplication.</p>
	 */
	public MultiScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		scan1 = configureScan(name,"Scan1","scan1", config);
		scan2 = configureScan(name,"Scan2","scan2", config);
		scan3 = configureScan(name,"Scan3","scan3", config);
		
		measPVs= config.getStringArray("measurePVs");

		measurementWait= config.getLong("measurementWait", 10000);
		measurementRepeat= config.getInteger("measurementRepeat", 1);
		dataDir= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());
		
		addRecordOfMemoryValueProcessor(MEASUREMENT_LAST, "Last beam measurement", -1000.0, 1000.0, "", (short)3, new double[1+3+measPVs.length]);
		addRecordOfMemoryValueProcessor(MEASUREMENT_TABLE, "Energy measurement results",new byte[1048576]);
		
		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{State.READY.toString(),State.SCANNING.toString(),State.MEASURING.toString(),State.PAUSED.toString(),State.ERROR.toString()}, (short)0);
		addRecordOfMemoryValueProcessor(STATUS_DATA_FILE, "Data file", new byte[1024]);

		addRecordOfMemoryValueProcessor(OPT_SCAN1, "Scan 1 enabled", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_SCAN2, "Scan 2 enabled", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OPT_SCAN3, "Scan 3 enabled", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(CMD_STOP, "Stops scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_PAUSE, "Pauses scanning task", DBRType.BYTE, 0);
		
		addRecordOfMemoryValueProcessor(WAIT, "Wait for measurement", 0.0, 1000.0,"s",(short)1, (double)(measurementWait/1000.0));
		addRecordOfMemoryValueProcessor(REPEAT, "Repeat measurementa", 1, 1000,"No.", measurementRepeat);
		
		progress= getRecord(STATUS_PROGRESS);

		Record r= getRecord(WAIT);
		r.setPersistent(true);
		
		if (r.getValueAsDouble()==0) {
			r.setValue((double)(measurementWait/1000.0));
		} else {
			measurementWait=(long)(r.getValueAsDouble()*1000.0);
		}
		
		r= getRecord(REPEAT);
		r.setPersistent(true);
		
		if (r.getValueAsInt()==0) {
			r.setValue(measurementRepeat);
		} else {
			measurementRepeat=r.getValueAsInt();
		}

		addRecordOfMemoryValueProcessor(MEASUREMENT_PVS, "Measurements PVs", new byte[1024]);
		
		
		r= getRecord(MEASUREMENT_PVS);
		r.setValue(String.join(",", measPVs).getBytes());
		r.setPersistent(true);
		
		if (getRecord(ERROR_SUM).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
		}
		if (getRecord(LINK_ERROR).getAlarmSeverity()==Severity.INVALID_ALARM) {
			updateLinkError(false, "");
		}

	}
	
	/**
	 * <p>Getter for the field <code>dataDir</code>.</p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getDataDir() {
		return dataDir;
	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		reconnectLinks(MEASUREMENTS, measPVs);
		
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
			t= estimateStepTime()*steps();
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
		
		getRecord(STATUS_REMAINING).setValue(sb.toString());
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void initialize(Database database) {
		super.initialize(database);
		
		database.addAll(scan1.getRecords());
		database.addAll(scan2.getRecords());
		database.addAll(scan3.getRecords());
	}
	
	private ScanApplication configureScan(String parent, String name, String confName, HierarchicalConfiguration config) {
		
		ScanApplication sa= new ScanApplication();
		HierarchicalConfiguration c= config.configurationAt(confName);
		sa.configure(parent+":"+name, c);
		return sa;
		
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==WAIT) {
			Record r= getRecord(WAIT);
			measurementWait=(long)(r.getValueAsDouble()*1000.0);
			updateTimeEst();
		} else if (name==REPEAT) {
			Record r= getRecord(REPEAT);
			measurementRepeat=r.getValueAsInt();
			updateTimeEst();
		} else if (name==MEASUREMENT_PVS) {
			
			String s= getRecord(name).getValueAsString();
			if (s!=null) {
				
				s= s.trim();
				String[] ss= s.split(",");
				
				if (ss!=null && ss.length>0) {
					
					List<String> l= new ArrayList<String>(ss.length);
					
					for (String st : ss) {
						if (st!=null) {
							st=st.trim();
							if (st.length()>0) {
								l.add(st);
							}
						}
					}
					if (l.size()>0) {
						measPVs=l.toArray(new String[l.size()]);
						reconnectLinks(MEASUREMENTS, measPVs);
					}
				}
			}
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
			task= new ScanningTask();
			database.schedule(task, 0);
			
		} else if (name==CMD_STOP) {
			getRecord(STATUS).setValue(State.READY.ordinal());
			getRecord(STATUS_SCANNING).setValue(0);
			ScanningTask t=task;
			if (t!=null) {
				log4info("Scan stopped on request");
				t.abort();
				scanStop(t,true);
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

		scan1.stopManualScan();
		scan2.stopManualScan();
		scan3.stopManualScan();

		if (success) {
			getRecord(STATUS).setValue(State.READY.ordinal());
		} else {
			getRecord(STATUS).setValue(State.ERROR.ordinal());
		}
		getRecord(STATUS_SCANNING).setValue(0);
		
	}
	
	private int steps() {
		return 
				(getRecord(OPT_SCAN1).getValueAsBoolean() ? scan1.getStepCount() : 1) * 
				(getRecord(OPT_SCAN2).getValueAsBoolean() ? scan2.getStepCount() : 1) *
				(getRecord(OPT_SCAN3).getValueAsBoolean() ? scan3.getStepCount() : 1);
	}
	
	private long estimateStepTime() {
		return 
				measurementWait; 
	}

	/**
	 * Return false if failed.
	 * @param t
	 * @throws IOException 
	 */
	private boolean scanStart(ScanningTask t) throws IOException {

		t.data= new ArrayList<MultiScanApplication.Measurement>(steps());
		
		start= Instant.now();
		
		t.startProgress(steps());
		
		log4info("steps: "+t.steps+" "+steps()+" "+scan1.getStepCount()+" "+scan2.getStepCount()+" "+scan3.getStepCount());
		
		boolean b= scan(t);
		
		if (!b) {
			return false;
		}

		log4info("Steps "+t.step+"/"+t.steps+" in "+(System.currentTimeMillis()-t.start));

		t.endProgress();
		
		return true;
	}

	private boolean scan(ScanningTask t) throws IOException {
		
		getRecord(STATUS).setValue(State.SCANNING.ordinal());

		t.getDataLog().println("# Multi scan measurements "+LocalDateTime.now().toString());
		t.getDataLog().print("# ");
		toHeader(t.getDataLog());
		t.getDataLog().println();
		t.getDataLog().flush();
		
		boolean s1= getRecord(OPT_SCAN1).getValueAsBoolean();
		
		if (s1) {
			scan1.startManualScan();
			waitStep(scan1);
		}
		
		do {
			
			boolean s2= getRecord(OPT_SCAN2).getValueAsBoolean();

			if (s2) {
				scan2.startManualScan();
				waitStep(scan2);
			}

			do {
				
				boolean s3= getRecord(OPT_SCAN3).getValueAsBoolean();
				
				if (s3) {
					scan3.startManualScan();
					waitStep(scan3);
				}
				
				do {
					
					int c=0;
					do {
						if (!t.delay(measurementWait)) {
							return false;
						}
	
						if (!t.canRun()) {
							return false;
						}
	
						Measurement m= takeMeasurement(t);
						
						getRecord(MEASUREMENT_LAST).setValue(m.data);
						log4info("Measurement "+m.toString());
	
						m.toDataString(t.getDataLog());
						t.getDataLog().println();
						t.getDataLog().flush();
						c++;
					} while (c<measurementRepeat);
						
					t.advanceProgress();

					if (!t.canRun()) {
						return false;
					}
					
					if (s3) {
						scan3.stepManualScan();
						waitStep(scan3);
					}
					
				} while(s3 && scan3.isManualScanActive());
				
				if (!t.canRun()) {
					return false;
				}
				
				if (s2) {
					scan2.stepManualScan();
					waitStep(scan2);
				}
				
			} while(s2 && scan2.isManualScanActive());		
			
			if (!t.canRun()) {
				return false;
			}
			
			if (s1) {
				scan1.stepManualScan();
				waitStep(scan1);
			}
			
		} while(s1 && scan1.isManualScanActive());
		
		return true;
		
	}
	

	private void waitStep(ScanApplication scan) {
		long rate= (long)(scan.getRateValue()*1000.0);
		
		synchronized (scan) {
			try {
				scan.wait(rate);
			} catch (InterruptedException e) {
				log4error("Wait interrupted", e);
			}
		}

	}

	private Measurement takeMeasurement(ScanningTask t) {
		
		log4info("Taking measurement");

		if (!t.canRun()) {
			return new Measurement();
		}
		
		Measurement m= new Measurement();
		
		ValueLinks vl= getLinks(MEASUREMENTS);
		
		ValueHolder[] vh;
		try {
			vh = vl.getValue();
			
			double[] d= new double[1+3+vh.length];
			
			boolean valid=vl.isReady() && !vl.isInvalid();
			
			d[1]=scan1.getSetpointValue();
			d[2]=scan2.getSetpointValue();
			d[3]=scan3.getSetpointValue();
			
			for (int i = 0; i < vh.length; i++) {
				ValueHolder v= vh[i];
				valid=valid && v!=null && !v.isAlarm();
				d[4+i]=v.doubleValue();
			}
			
			d[0]=valid?1.0:0.0;

			m.data=d;
			
			return m;
		} catch (Exception e) {
			log4error("Failed to read measurements: "+e.toString(), e);
			return new Measurement();
		}
	}

	/**
	 * <p>toHeader.</p>
	 *
	 * @param sb a {@link java.lang.Appendable} object
	 * @return a {@link java.lang.Appendable} object
	 * @throws java.io.IOException if any.
	 */
	public Appendable toHeader(Appendable sb) throws IOException {
		sb.append("time");
		sb.append(", ");
		sb.append("valid");
		sb.append(", ");
		sb.append(scan1.getSetpoint().getName());
		sb.append(", ");
		sb.append(scan2.getSetpoint().getName());
		sb.append(", ");
		sb.append(scan3.getSetpoint().getName());
		
		for (int i = 0; i < measPVs.length; i++) {
			sb.append(", ");
			sb.append(measPVs[i]);
		}
		return sb;
	}

	
}
