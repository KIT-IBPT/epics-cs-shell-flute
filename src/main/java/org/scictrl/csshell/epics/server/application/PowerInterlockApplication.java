package org.scictrl.csshell.epics.server.application;

import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.csshell.epics.server.application.RunningCounterApplication.AverageCalculator;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * Listens to preflected power limiter interlock signal, which is triggered when RF cavity sparking occurs. With this interlock disabled
 *
 * @author igor@scictrl.com
 */
public class PowerInterlockApplication extends AbstractApplication {
	
	/**
	 * Calculates rate of interlocks on normalized time interval.
	 */
	static public class RateCalculator {
		class Incident {
			long start;
			long duration;
			
			public Incident() {
				start= System.currentTimeMillis();
				duration=-1;
			}
			
			public void extend() {
				duration=System.currentTimeMillis()-start;
			}
			
			@Override
				public String toString() {
					return "{"+start+"+"+duration+"="+(start+duration)+"}";
				}
		}
		
		private long window=10000;
		private double rate=0.0;
		private LinkedList<Incident> buffer= new LinkedList<>();

		/**
		 * New instance.
		 */
		public RateCalculator() {
		}
		
		/**
		 * Returns time window in ms.
		 * @return time window in ms
		 */
		public long getWindow() {
			return window;
		}
 
		/**
		 * Sets time window in ms.
		 * @param window time window in ms
		 */
		public void setWindow(long window) {
			this.window = window;
		}
		
		/**
		 * Reports to the calculator interlock high incident.
		 * @param high <code>true</code> if interlock is high, <code>false</code> at end of interlock
		 */
		public synchronized void report(boolean high) {
			
			if (buffer.size()>0) {
				Incident e= buffer.getLast();
				if (high && e.duration<=0) {
					// do nothing, we do not move the start
				} else if (high && e.duration>0) {
					//this is new event, previous has been closed
					buffer.add(new Incident());
				} else if (!high && e.duration<=0) {
					// close event
					e.extend();
				} else if (!high && e.duration>0) {
					// do not extend already closed event
				}
			} else {
				if (high) {
					buffer.add(new Incident());
				}
			}
		}
		
		/**
		 * Calculates and returns interlock rate
		 * @return calculated interlock rate
		 */
		public synchronized double calculate() {
			
			long t2= System.currentTimeMillis();
			long t1= t2-window;
			//System.out.println(t1+" -> "+t2);
			long high=0;
			
			ListIterator<Incident> it= buffer.listIterator();
			
			while (it.hasNext()) {
				Incident i= it.next();
				//System.out.println("2> "+i);
				if (i.start<t1 && i.duration>0 && i.start+i.duration<t1) {
					//System.out.println("removed");
					it.remove();
				} else if (i.start<t1) {
					if (i.duration>0) {
						i.duration=i.duration-(t1-i.start);
						high+=i.duration;
					} else {
						high+= window;
					}
					i.start=t1;
					//System.out.println("3> "+i);
					//System.out.println("H1 "+high);
				} else if (i.duration>=0) {
					high+=i.duration;
					//System.out.println("H2 "+high);
				} else if (i.duration<0) {
					high+= t2-i.start;
					//System.out.println("H3 "+high);
				}
			}
			
			rate= (double)high/(double)window;
			
			return rate;
		}
		
		/**
		 * Last calculated rate.
		 * @return last calculated rate
		 */
		public double getRate() {
			return rate;
		}
	}

	/** Constant <code>ENABLED="Enabled"</code> */
	public static final String ENABLED = 	"Enabled";
	/** Constant <code>STATE="Status:State"</code> */
	public static final String STATE = 		"Status:State";
	
	/** Constant <code>OFF_ON="OffOn"</code> */
	public static final String OFF_ON = 	"OffOn";
	
	/** Constant <code>RATE_MAX="Rate:Max"</code> */
	public static final String RATE_MAX = 	"Rate:Max";
	/** Constant <code>RATE="Rate"</code> */
	public static final String RATE = 		"Rate";

	/** Constant <code>INTERLOCK="Interlock"</code> */
	public static final String INTERLOCK = 		"Interlock";

	/** Constant <code>CMD_RESET="Cmd:Reset"</code> */
	public static final String CMD_RESET = 	"Cmd:Reset";

	/** Constant <code>AR_ENABLED="Autoreset:Enabled"</code> */
	public static final String AR_ENABLED = "Autoreset:Enabled";
	/** Constant <code>AR_DELAY="Autoreset:Delay"</code> */
	public static final String AR_DELAY = 	"Autoreset:Delay";
	
	/** Constant <code>AL_ENABLED="Autolimit:Enabled"</code> */
	public static final String AL_ENABLED = "Autolimit:Enabled";
	/** Constant <code>AL_RATE="Autolimit:Rate"</code> */
	public static final String AL_RATE = 	"Autolimit:Rate";
	/** Constant <code>AL_INPUT="Autolimit:Input"</code> */
	public static final String AL_INPUT = 	"Autolimit:Input";
	/** Constant <code>AL_MIN="Autolimit:Min"</code> */
	public static final String AL_MIN = 	"Autolimit:Min";
	/** Constant <code>LIMITER_LEVEL="LimiterLevel"</code> */
	public static final String AL_LIMITER_LEVEL = "LimiterLevel";

	/** Constant <code>RFSWITCH_ENABLED="RfSwitch:Enabled"</code> */
	public static final String RFSWITCH_ENABLED = "RfSwitch:Enabled";
	/** Constant <code>RFSWITCH="RfSwitch"</code> */
	public static final String RFSWITCH			= "RfSwitch";
	
	private enum SM {
		DISABLED, ENABLED, INTERLOCK, ERROR, RESET;
		
		public static String[] labels() {
			return new String[] {SM.DISABLED.name(),SM.ENABLED.name(),SM.INTERLOCK.name(),SM.ERROR.name(),SM.RESET.name()};
		}
		
		@SuppressWarnings("unused")
		public static boolean isInterlock(int i) {
			return i==INTERLOCK.ordinal();
		}

		public static boolean isReset(int i) {
			return i==RESET.ordinal();
		}
	};

	private Thread looper;

	private Record interlockrate;
	private ValueLinks off;
	private LinkedValueProcessor interlock;
	
	private ValueLinks 			 autolimInput;
	private AverageCalculator 	 autolimCalc;
	private double 				 autolimMin;
	private LinkedValueProcessor autolimLimiterLevel;
	
	private int autoresetDelay;
	private Long interlockTime=null;

	private int 		rfSwitchStateOK;
	private ValueLinks 	rfSwitchState;
	private Integer 	rfSwitchStateLast;

	private RateCalculator interlockRateCalc= new RateCalculator();

	
	
	/**
	 * <p>Constructor for PowerInterlockApplication.</p>
	 */
	public PowerInterlockApplication() {
	}

	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		/* PV provides values of reflected power, which are base for autolimiter threshold */
		String autolimInputPV = config.getString("autolimitInputPV");
		
		/* PV is limiter on LLRF which is adjusted with autolimiter loop*/
		String limiterLevelPV = config.getString("limiterLevelPV");
		
		/* RF state leaving Tiggered mode disables output */
		String rfStatePV = config.getString("rfStatePV");
		rfSwitchStateOK = config.getInt("rfStateOK",3);

		double autolimRate = config.getDouble("autolimitRate",120.0);
		long autolimSpan = (long)(config.getDouble("autolimitSpan",20.0)*1000.0);
		autolimMin = config.getDouble("autolimitMin",1.0);
		
		String offPV = config.getString("offPV");
		String interlockPV = config.getString("interlockPV");
		
		autoresetDelay = config.getInt("autoresetDelay", 10);
		boolean enabled = config.getBoolean("enabled", true);
		long window= config.getLong("window", 10000);
		interlockRateCalc.setWindow(window);

		if (offPV == null || offPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no offPV parameter!");
		}
		if (interlockPV == null || interlockPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no interlockPV parameter!");
		}
		if (autolimInputPV == null || autolimInputPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no autolimInputPV parameter!");
		}
		if (limiterLevelPV == null || limiterLevelPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no limiterPV parameter!");
		}
		if (rfStatePV == null || rfStatePV.length() == 0) {
			log.error("Configuration for '" + name + "' has no rfStatePV parameter!");
		}

		SM state = enabled ? SM.ENABLED : SM.DISABLED;

		// creates records of variables to be stored in IOC shell
		interlock= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + INTERLOCK, DBRType.BYTE, "Interlock channel value.", interlockPV);
		autolimLimiterLevel= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + AL_LIMITER_LEVEL, DBRType.DOUBLE, "LLRF Limiter interlock level.", limiterLevelPV);
		addRecord(INTERLOCK, interlock.getRecord());
		addRecord(AL_LIMITER_LEVEL, autolimLimiterLevel.getRecord());
		
		off= connectLinks(OFF_ON, offPV);
		autolimInput= connectLinks(AL_INPUT, autolimInputPV);
		rfSwitchState= connectLinks(RFSWITCH, getRecordNames());
		
		addRecordOfMemoryValueProcessor(ENABLED, "Feedback loop is enabled and active.", DBRType.BYTE,enabled);
		interlockrate= addRecordOfMemoryValueProcessor(RATE, "Interlock rate", 0.0, 100.0, "%", (short)1, 0.0);
		Record r= addRecordOfMemoryValueProcessor(RATE_MAX, "Maximum allowed interlock rate", 0.0, 100.0, "%", (short)1, 50.0);
		r.setPersistent(true);

		addRecordOfMemoryValueProcessor(AL_ENABLED, "Autolimiter enabled.", DBRType.BYTE,enabled);
		r= addRecordOfMemoryValueProcessor(AL_RATE, "Autolimiter relative rate", 100.0,1000.0,"%",(short)1,autolimRate);
		r.setPersistent(true);
		r= addRecordOfMemoryValueProcessor(AL_MIN, "Autolimiter minimal level", 0.0,1000.0,"V",(short)1,autolimMin);
		r.setPersistent(true);

		addRecordOfMemoryValueProcessor(AR_ENABLED, "Autoreset enabled.", DBRType.BYTE,false);
		addRecordOfMemoryValueProcessor(AR_DELAY, "Autoreset delay time", 1, 60000, "s", autoresetDelay);

		addRecordOfMemoryValueProcessor(RFSWITCH_ENABLED, "RF state switch enabled.", DBRType.BYTE,enabled);

		addRecordOfMemoryValueProcessor(STATE, "Latest state of loopback cycle.", SM.labels(), (short) state.ordinal());
		addRecordOfCommandProcessor(CMD_RESET, "Reset interlock", 1000);

		autolimCalc= new AverageCalculator();
		autolimCalc.span=autolimSpan;
	}

	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		looper= new Thread("ProcessingThread-"+getName()) {
			
			@Override
			public void run() {
				
				while (database.isActive()) {
					
					try {
						synchronized (this) {
							wait(1000);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					feedbackLoop();
				}
			}
		};
		looper.start();
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		
		if (INTERLOCK==name) {
			interlockRateCalc.report(interlock.getValueAsBoolean());
			triggerLoop();
		} else if (ENABLED==name || AR_ENABLED==name) {
			triggerLoop();
		} else if (AR_DELAY==name) {
			autoresetDelay=getRecord(AR_DELAY).getValueAsInt();
			triggerLoop();
		} else if (AL_RATE==name) {
			autolimiterUpdate();
		} else if (AL_MIN==name) {
			autolimMin=getRecord(AL_MIN).getValueAsDouble();
		}

	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (CMD_RESET==name) {
			getRecord(STATE).setValue(SM.RESET.ordinal());
			triggerLoop();
		}

	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==AL_INPUT) {
			if (autolimInput.isReady() && !autolimInput.isInvalid()) {
				ValueHolder[] vh= autolimInput.consume();
				if (vh!=null && vh.length>0 && vh[0]!=null) {
					autolimCalc.addValid(vh[0],false);
					autolimCalc.trim();
					autolimCalc.update();
					autolimiterUpdate();
				}
			}
		} else if (name==RFSWITCH) {
			rfSwitchUpdateRFInterlock();
		}
	}
	
	private void autolimiterUpdate() {
		if (!getRecord(AL_ENABLED).getValueAsBoolean()) {
			return;
		}
		
		double level= autolimCalc.avg;
		double rate= getRecord(AL_RATE).getValueAsDouble();
		
		double autolimit = level*rate/100.0;

		if (!Double.isNaN(autolimit) && autolimit<autolimMin) {
			autolimit=autolimMin;
		}
		
		if (!autolimLimiterLevel.isInvalid()) {
			try {
				autolimLimiterLevel.setValue(autolimit);
			} catch (Exception e) {
				log4error("Setting autolimiter failed '"+e.toString()+"'", e);
			}
		}
	}
	
	private void rfSwitchUpdateRFInterlock() {
		boolean b= getRecord(RFSWITCH_ENABLED).getValueAsBoolean();
		
		if (b) {
			if (rfSwitchState.isReady() && !rfSwitchState.isInvalid() && !rfSwitchState.isLastSeverityInvalid()) {
				long[] l= rfSwitchState.consumeAsLongs();
				if (l!=null && l.length>0) {
					int state= (int)l[0];
					if (rfSwitchStateLast!=null && rfSwitchStateLast==rfSwitchStateOK && state!=rfSwitchStateOK) {
						setOff(true);
					}
					rfSwitchStateLast= state;
				}
			}
		}
	}

	/**
	 * <p>triggerLoop.</p>
	 */
	public void triggerLoop() {
		if (looper!=null) {
			synchronized (looper) {
				looper.notify();
			}
		}
	}


	/*
	 */
	private void feedbackLoop() {
		
		if (interlock.getRecord().getAlarmStatus()!=Status.NO_ALARM) {
			getRecord(STATE).setValue((short)SM.ERROR.ordinal());
			return;
		}
		
		boolean ilock= interlock.getValueAsBoolean();
		interlockRateCalc.report(ilock);
		interlockRateCalc.calculate();
		double r=interlockRateCalc.getRate()*100.0;
		interlockrate.setValue(r);
		
		if (!getRecord(ENABLED).getValueAsBoolean()) {
			getRecord(STATE).setValue((short)SM.DISABLED.ordinal());
			return;
		}
		
		double rm=getRecord(RATE_MAX).getValueAsDouble();
		
		if (r>rm) {
			if (interlockTime==null) {
				interlockTime= System.currentTimeMillis();
			}
			//if (!SM.isInterlock(getRecord(STATE).getValueAsInt())) {
				boolean b= setOff(true);
				if (b) {
					getRecord(STATE).setValue((short)SM.INTERLOCK.ordinal());
				} else {
					getRecord(STATE).setValue((short)SM.ERROR.ordinal());
				}
			//}
		} else {
			boolean reset= SM.isReset(getRecord(STATE).getValueAsInt());
			if (reset || (getRecord(AR_ENABLED).getValueAsBoolean() && interlockTime!=null)) {
				long delta= System.currentTimeMillis() - (interlockTime!=null ? interlockTime : 0L);
				if (reset || delta>autoresetDelay*1000) {
					boolean b= setOff(false);
					if (b) {
						interlockTime=null;
						getRecord(STATE).setValue((short)SM.ENABLED.ordinal());
					} else {
						getRecord(STATE).setValue((short)SM.ERROR.ordinal());
					}
				}
			}
		}
			
	}

	private boolean setOff(boolean cutOff) {
		try {
			off.setValue(!cutOff);
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
			//log.debug("Setting off '" + off + "' to '" + !cutOff + "'.");
			return true;
		} catch (Exception e) {
			log.error("Remote setting OFF failed " + e.toString(), e);
			updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
			return false;
		} finally {
		}
	}

}
