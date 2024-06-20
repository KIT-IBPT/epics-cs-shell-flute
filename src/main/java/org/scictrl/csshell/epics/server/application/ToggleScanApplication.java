/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * Filters and averages the BPM values. Values with Q below treshold ar thrown away.
 *
 * @author igor@scictrl.com
 */
public class ToggleScanApplication extends AbstractApplication {
	
	private enum State {
		READY("Ready"),
		LOW_CYCLE("Low Cycle"),
		HIGH_CYCLE("High Cycle"),
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
	
	private static final String CFG_TOGGLE = 			"toggle";
	private static final String CFG_TOGGLE_HIGH_VALUE = "high";
	private static final String CFG_TOGGLE_LOW_VALUE = 	"low";
	private static final String CFG_TOGGLE_PV = 		"pv";
	private static final String CFG_TOGGLE_DESC = 		"description";

	private static final String CMD_STOP = 	"Cmd:Stop";
	private static final String CMD_START = "Cmd:Start";
	private static final String CMD_PAUSE = "Cmd:Pause";

	private static final String STATUS = 				"Status";
	private static final String STATUS_SCANNING = 		"Status:Scan";
	private static final String STATUS_PROGRESS = 		"Status:Progress";
	private static final String STATUS_REMAINING = 		"Status:Remaining";
	private static final String STATUS_REMAINING_MS = 	"Status:Remaining:ms";
	private static final String STATUS_REPEAT = 		"Status:Repeat";
	
	private static final String OPT_TIME_LOW =  "Opt:TimeLow";
	private static final String OPT_TIME_HIGH = "Opt:TimeHigh";
	private static final String OPT_REPEAT =    "Opt:Repeat";
	
	private static final String TOGGLE = 		  	 "Toggle:";
	private static final String TOGGLE_ENABLED =  	 ":Enabled";
	private static final String TOGGLE_DESCRIPTION = ":Desc";

	
	class Toggle {
		
		double valueLow;
		double valueHigh;
		String pv;
		ValueLinks link;
		String linkTag;
		String description;
		Record enabled;

		Toggle(String link, HierarchicalConfiguration config) {
			this.linkTag=link;
			valueLow=config.getDouble(CFG_TOGGLE_LOW_VALUE,0L);
			valueHigh=config.getDouble(CFG_TOGGLE_HIGH_VALUE,0L);
			pv=config.getString(CFG_TOGGLE_PV);
			description=config.getString(CFG_TOGGLE_DESC);
		}
		
		boolean enabled() {
			return enabled.getValueAsBoolean();
		}
		
	}
	

	class ScanningTask implements Runnable {
		
		private boolean aborted=false;
		private boolean paused=false;
		double time=-1;
		int steps;
		int step;
		long start;

		public ScanningTask() {
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
			}
			
			if (aborted) {
				log4debug("Aborted");
			}
			
			scanStop(this,b);
			
			log4debug("Stopped");

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
	
	
	private ScanningTask task;
	private Instant start;
	private int paused;
	private Record progress;
	
	private int timeHigh;
	private int timeLow;
	private HashMap<String,Toggle> toggles;

	/**
	 * <p>Constructor for EmittanceScanApplication.</p>
	 */
	public ToggleScanApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		timeHigh =  config.getInt("timeHigh",  10000);
		timeLow = config.getInt("timeLow", 10000);
		
		toggles= new HashMap<String,Toggle>(8);
		
		for (int i=1; ; i++) {
			String prefix= CFG_TOGGLE+i;
			List<HierarchicalConfiguration> l = config.configurationsAt(prefix);
			if (l.size()==1) {
				Toggle t= new Toggle(prefix, l.get(0));
				
				if (t.pv==null || t.pv.length()==0) {
					throw new IllegalArgumentException("Parameter "+prefix+CFG_TOGGLE_PV+"is missing!");
				}
				
				t.link = connectLinks(t.linkTag, t.pv);
				toggles.put(t.linkTag, t);
				
				(t.enabled = addRecordOfMemoryValueProcessor(TOGGLE+i+TOGGLE_ENABLED, "Toggle enabled", DBRType.BYTE, t.enabled)).setPersistent(true);
				addRecordOfMemoryValueProcessor(TOGGLE+i+TOGGLE_DESCRIPTION, "Toggle description", DBRType.STRING, t.description);

			} else {
				break;
			}
		}
		
		addRecordOfMemoryValueProcessor(STATUS_PROGRESS, "Scanning progress", 0.0, 100.0, "%", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Flag indicating scanning in progress", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING, "Remaining time of scan", DBRType.STRING, 0);
		addRecordOfMemoryValueProcessor(STATUS_REMAINING_MS, "Remaining time of scan", 0, 1000000, "ms", 0);
		addRecordOfMemoryValueProcessor(STATUS, "Scanning status", new String[]{State.READY.toString(),State.LOW_CYCLE.toString(),State.HIGH_CYCLE.toString(),State.PAUSED.toString(),State.ERROR.toString()}, (short)0);
		addRecordOfMemoryValueProcessor(STATUS_REPEAT, "Current repeat count", 0, 1000, "No.", 0);

		addRecordOfMemoryValueProcessor(OPT_REPEAT,   "How many repeats",  1, 10000, "No.", 1).setPersistent(true);
		addRecordOfMemoryValueProcessor(OPT_TIME_LOW, "Time of Low cycle", 1, 10000, "ms",  timeLow).setPersistent(true);
		addRecordOfMemoryValueProcessor(OPT_TIME_HIGH,  "Time of High cycle",  1, 10000, "ms",  timeHigh).setPersistent(true);
		
		addRecordOfMemoryValueProcessor(CMD_STOP,  "Stops scanning task",  DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_START, "Start scanning task",  DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(CMD_PAUSE, "Pauses scanning task", DBRType.BYTE, 0);
		
		progress= getRecord(STATUS_PROGRESS);

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
				updateTimeEst();
			}
		}, 1000, 1000);
	}
	
	private void updateTimeEst() {
		double t=0.0;
		Duration d= null;

		double p= getRecord(STATUS_PROGRESS).getValueAsDouble();

		if (start!=null) {
			//d= Duration.between(start, Instant.now());
			//t= d.toMillis()*(100.0/p-1.0);
			
			t=(1.0-p/100.0)*((double)totalTime());
			
		}
		
		if (start==null || p<0.0001 || Math.abs(p-100.0)<0.0001) {
			t= totalTime();
		}
		
		d= Duration.ofMillis((long)t);
		
		StringBuilder sb= new StringBuilder(128);
		long a= d.toHours();
		if (a>0) {
			sb.append(a);
			sb.append("h ");
			d=d.minusHours(a);
		}
		a=d.toMinutes();
		if (a>0) {
			sb.append(a);
			sb.append("min ");
			d=d.minusMinutes(a);
		}
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

		getRecord(STATUS_SCANNING).setValue(0);

		if (success) {
			getRecord(STATUS).setValue(State.READY.ordinal());
		} else {
			getRecord(STATUS).setValue(State.ERROR.ordinal());
		}
		
		
	}
	
	private int steps() {
		return getRecord(OPT_REPEAT).getValueAsInt();
	}
	
	private int cycleTime() {
		return getRecord(OPT_TIME_LOW).getValueAsInt() + getRecord(OPT_TIME_HIGH).getValueAsInt();
	}

	private int totalTime() {
		return steps()*cycleTime();
	}

	/**
	 * Return false if failed.
	 * @param t
	 * @throws IOException 
	 */
	private boolean scanStart(ScanningTask t) throws IOException {
		
		start= Instant.now();
		
		t.startProgress(steps()*2);
		
		boolean b= scanToggles(t);
		
		if (!b) {
			return false;
		}

		t.endProgress();
		
		return true;
	}
	
	
	private boolean toggle(boolean on) {
		
		Collection<Toggle> tg= toggles.values();
		for (Iterator<Toggle> it = tg.iterator(); it.hasNext();) {
			Toggle t = it.next();
			if (t.enabled()) {
				if (!t.link.isInvalid() && t.link.isReady()) {
					try {
						t.link.setValue(on ? t.valueHigh : t.valueLow);
						t.enabled.updateAlarm(Severity.NO_ALARM, Status.NO_ALARM);
					} catch (Exception e) {
						log.error("Error setting value "+e.toString());
						t.enabled.updateAlarm(Severity.INVALID_ALARM, Status.LINK_ALARM);
						return false;
					}
				} else {
					t.enabled.updateAlarm(Severity.INVALID_ALARM, Status.LINK_ALARM);
					return false;
				}
			}
		}
		return true;
		
	}

	private boolean scanToggles(ScanningTask t) throws IOException {
		
		getRecord(STATUS_SCANNING).setValue(1);

		int steps= steps();
		
		timeLow= getRecord(OPT_TIME_LOW).getValueAsInt();
		timeHigh= getRecord(OPT_TIME_HIGH).getValueAsInt();
		
		for (int step= 1; step<=steps; step++) {
			
			log4info("Scan step "+step+" / "+steps);
			
			getRecord(STATUS).setValue(State.HIGH_CYCLE.ordinal());
					
			boolean r= toggle(true);
			if (!r) {
				return false;
			}
			
			if (!t.delay(timeHigh)) {
				return false;
			}
			
			t.advanceProgress();
			
			getRecord(STATUS).setValue(State.LOW_CYCLE.ordinal());

			r= toggle(false);
			if (!r) {
				return false;
			}
		
			if (!t.delay(timeLow)) {
				return false;
			}
			
			t.advanceProgress();

		}
		
		return true;
		
	}
	
	
}
