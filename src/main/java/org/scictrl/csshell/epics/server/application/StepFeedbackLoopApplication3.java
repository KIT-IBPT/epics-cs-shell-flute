package org.scictrl.csshell.epics.server.application;

import java.lang.reflect.Array;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>StepFeedbackLoopApplication3 class.</p>
 *
 * @author igor@scictrl.com
 */
public class StepFeedbackLoopApplication3 extends AbstractApplication {

	/** Constant <code>OFF_ON_ERROR="OffOnError"</code> */
	public static final String OFF_ON_ERROR = "OffOnError";
	/** Constant <code>LOOP_RATE="LoopRate"</code> */
	public static final String LOOP_RATE 	= "LoopRate";
	/** Constant <code>STATE="Status"</code> */
	public static final String STATE 		= "Status";
	/** Constant <code>STATE_DESC="Status:Desc"</code> */
	public static final String STATE_DESC 	= "Status:Desc";
	/** Constant <code>STATE_INTLCK="Status:Interlock"</code> */
	public static final String STATE_INTLCK = "Status:Interlock";
	/** Constant <code>ENABLED="Enabled"</code> */
	public static final String ENABLED 		= "Enabled";
	/** Constant <code>ERROR_SUM="ErrorSum"</code> */
	public static final String ERROR_SUM 	= "ErrorSum";
	/** Constant <code>INTERLOCK="Interlock"</code> */
	public static final String INTERLOCK 	= "Interlock";
	
	/** Constant <code>VAC_ALARM_HIGH="VacAlarmHigh"</code> */
	public static final String VAC_ALARM_HIGH 	= "VacAlarmHigh";
	/** Constant <code>VAC_ALARM_LOW="VacAlarmLow"</code> */
	public static final String VAC_ALARM_LOW 	= "VacAlarmLow";
	/** Constant <code>VAC_WARNING_HIGH="VacWarningHigh"</code> */
	public static final String VAC_WARNING_HIGH = "VacWarningHigh";
	/** Constant <code>VAC_WARNING_LOW="VacWarningLow"</code> */
	public static final String VAC_WARNING_LOW 	= "VacWarningLow";
	
	/** Constant <code>INPUT_TARGET_LOW="Input:TargetLow"</code> */
	public static final String INPUT_TARGET_LOW  = "Input:TargetLow";
	/** Constant <code>INPUT_TARGET_HIGH="Input:TargetHigh"</code> */
	public static final String INPUT_TARGET_HIGH = "Input:TargetHigh";
	/** Constant <code>INPUT_TARGET_OFF="Input:TargetOff"</code> */
	public static final String INPUT_TARGET_OFF  = "Input:TargetOff";
	
	/** Constant <code>INPUT="Input"</code> */
	public static final String INPUT 	 = "Input";
	/** Constant <code>INPUT_MIN="Input:Min"</code> */
	public static final String INPUT_MIN = "Input:Min";
	/** Constant <code>INPUT_MAX="Input:Max"</code> */
	public static final String INPUT_MAX = "Input:Max";

	/**
	 * Feedback algorithm must implement this interface in order to be controlled by this application.
	 */
	public interface FeedbackControl {
		
		/**
		 * Configuration as offered by application server configuration.
		 * @param name application name
		 * @param config server configuration sub-ection
		 */
		public void configure(String name, HierarchicalConfiguration config);
		
		/**
		 * Feedback must perform cut-off procedure, this means switching off completely control input to device.  
		 * Cat-off condition if switched on or off by this application.
		 * @param cutOff {@link Boolean} for on or off the cutoff condition
		 */
		public void cutOff(boolean cutOff);
		/**
		 * Make step up with controlled values.
		 * @return success
		 */
		public boolean stepUp();
		/**
		 * MAke step down with controlled values.
		 * @return success
		 */
		public boolean stepDown();
		/**
		 * Feedback must make quick and considerable step down with control values, thus drastically reducing load on system. 
		 * It is one-time operation, if this does not help to cool down, then {@link #cutOff(boolean)} is called.
		 */
		public void coolDown();
		/**
		 * Notification, that user has enabled this algorithm, implementation must go into initial state and wait for further instructions, 
		 * ideally this would be {@link #stepUp()}.  
		 */
		public void notifyEnabled();
	}
	
	
	/**
	 * State of last operation.
	 */
	public enum SM {
		/** Initial condition. State before algorithm is enabled. */
		INITIAL,
		/** Idle, algorithm is enabled but not doing anything. */
		IDLE, 
		/** Increase of control values has been performed, after FeedbackControl.stepUp. */
		INCREASE,
		/** Decrease of control values has been performed, after FeedbackControl.stepDown. */
		DECREASE, 
		/** Rapid decrease of control values has been performed, after FeedbackControl.cooldDown. */
		COOL_DOWN, 
		/** General error. */
		ERROR, 
		/** Interlock condition or signal registered. */
		INTERLOCK;

		/**
		 * State labels.
		 * @return state labels
		 */
		static public String[] labels() {
			return new String[] {SM.INITIAL.name(),SM.IDLE.name(),SM.INCREASE.name(),SM.DECREASE.name(),COOL_DOWN.name(),ERROR.name(),INTERLOCK.name()};
		}
	};

	private String inputPV;
	private LinkedValueProcessor input;
	private int trigger;
	private Thread looper;
	private SM state;
	private String interlockPV;
	private long interlockOK;
	private LinkedValueProcessor interlock;
	private String vacAlarmHighPV;
	private String vacAlarmLowPV;
	private String vacWarnHighPV;
	private String vacWarnLowPV;
	
	private boolean readonly=false;
	private FeedbackControl feedback;
	//private boolean gateOpen=true;

	/**
	 * <p>Constructor for StepFeedbackLoopApplication3.</p>
	 */
	public StepFeedbackLoopApplication3() {
	}
	
	/**
	 * <p>newFeedbackControl.</p>
	 *
	 * @return a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.FeedbackControl} object
	 */
	protected FeedbackControl newFeedbackControl() {
		return new FeedbackControl() {
			
			public static final String OFF = "Off";
			public static final String OUTPUT = "Output";
			public static final String OUTPUT_SET = "OutputSet";
			public static final String OUTPUT_STEP = "Output:Step";
			public static final String OUTPUT_STEP_COOL = "Output:StepCool";
			public static final String OUTPUT_MIN = "Output:Min";
			public static final String OUTPUT_MAX = "Output:Max";

			private String outputPV;
			private String offPV;
			private LinkedValueProcessor output;
			private LinkedValueProcessor off;
			
			public void configure(String name, HierarchicalConfiguration config) {

				outputPV = config.getString("outputPV");
				offPV = config.getString("offPV");

				double step = config.getDouble("step");
				double stepCool = config.getDouble("stepCool",step*10.0);
				double outMin = config.getDouble("outputMin");
				double outMax = config.getDouble("outputMax");

				if (outputPV == null || outputPV.length() == 0) {
					log.error("Configuration for '" + name + "' has no outputPV parameter!");
				}
				if (offPV == null || offPV.length() == 0) {
					log.error("Configuration for '" + name + "' has no offPV parameter!");
				}

				// creates records of variables to be stored in IOC shell
				
				output= LinkedValueProcessor.newProcessor(name + nameDelimiter + "Output", DBRType.DOUBLE, "Output channel value.", outputPV);
				off= LinkedValueProcessor.newProcessor(name + nameDelimiter + "Off", DBRType.DOUBLE, "Off channel value.", offPV);
				
				addRecord(OUTPUT, output.getRecord());
				addRecord(OFF, off.getRecord());

				addRecordOfMemoryValueProcessor("OutputPV", "Output PV", DBRType.STRING, true, false, new Object[] {outputPV});

				addRecordOfMemoryValueProcessor(OUTPUT_STEP, "Step value", 0.0, 1000.0, "W", (short) 3, step);
				addRecordOfMemoryValueProcessor(OUTPUT_STEP_COOL, "Step value at cooldown", 0.0, 1000.0, "W", (short) 3, stepCool);
				addRecordOfMemoryValueProcessor(OUTPUT_SET, "Desired output values", 0.0, 1000.0, "W", (short) 3, 0.0);
				addRecordOfMemoryValueProcessor(OUTPUT_MIN, "Min allowed output value", 0.0, 1000.0, "W", (short) 3, outMin);
				addRecordOfMemoryValueProcessor(OUTPUT_MAX, "Max allowed output value", 0.0, 1000.0, "W", (short) 3, outMax);

				getRecord(OUTPUT_STEP).setPersistent(true);
				getRecord(OUTPUT_STEP_COOL).setPersistent(true);
				getRecord(OUTPUT_SET).setPersistent(true);
				getRecord(OUTPUT_MIN).setPersistent(true);
				getRecord(OUTPUT_MAX).setPersistent(true);

			}

			/**
			 * Sets output parameter to output PV.
			 * 
			 * @param output
			 *            the output parameter to be set
			 */
			public void setOutputValue(double out) {
				
				if (readonly) {
					getRecord(OUTPUT_SET).setValue(new double[] { out });
					log.warn("READONLY mode, value '" + out + "' NOT set.");
					return;
				}
				
				try {
					getRecord(OUTPUT_SET).setValue(new double[] { out });
					output.setValue(new double[] { out });
					
					updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
					log.debug("Setting out '" + output + "' to '" + outputPV + "'.");
					
				} catch (Exception e) {
					String s= "Remote setting of '" + outputPV + "' failed " + e.toString();
					log.error(s, e);
					updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
					
					setState(SM.ERROR,s);
					
					return;
				} finally {
				}
			}

			public void cutOff(boolean cutOff) {
				try {
					off.setValue(!cutOff);
					updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
					log.debug("Setting off '" + off + "' to '" + !cutOff + "'.");
					
				} catch (Exception e) {
					String s= "Remote setting of '" + offPV + "' failed " + e.toString();
					log.error(s, e);
					updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
					
					setState(SM.ERROR,s);
					
					return;
				} finally {
				}
			}
			
			public boolean stepUp() {
				double out = getRecord(OUTPUT).getValueAsDouble();
				double step = getRecord(OUTPUT_STEP).getValueAsDouble();
				//double stepCool = getRecord(OUTPUT_STEP_COOL).getValueAsDouble();
				//double outMin= getRecord(OUTPUT_MIN).getValueAsDouble();
				double outMax= getRecord(OUTPUT_MAX).getValueAsDouble();

				if (out + step < outMax) {
					setOutputValue(out + step);
					return true;
				} else {
					return false;
				}
				
			}
			
			public boolean stepDown() {
				double out = getRecord(OUTPUT).getValueAsDouble();
				double step = getRecord(OUTPUT_STEP).getValueAsDouble();
				//double stepCool = getRecord(OUTPUT_STEP_COOL).getValueAsDouble();
				double outMin= getRecord(OUTPUT_MIN).getValueAsDouble();
				//double outMax= getRecord(OUTPUT_MAX).getValueAsDouble();

				if (out - step > outMin) {
					setOutputValue(out - step);
					return true;
				} else {
					return false;
				}
			}

			public void coolDown() {
				double out = getRecord(OUTPUT).getValueAsDouble();
				//double step = getRecord(OUTPUT_STEP).getValueAsDouble();
				double stepCool = getRecord(OUTPUT_STEP_COOL).getValueAsDouble();
				double outMin= getRecord(OUTPUT_MIN).getValueAsDouble();
				//double outMax= getRecord(OUTPUT_MAX).getValueAsDouble();

				if (out - stepCool > outMin) {
					setOutputValue(out - stepCool);
				} else if (out > outMin) {
					setOutputValue(outMin);
				}
			}
			
			@Override
			public void notifyEnabled() {
			}
		};

	}

	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		inputPV = config.getString("inputPV");
		interlockPV = config.getString("interlockPV");
		interlockOK = config.getLong("interlockOK",0);

		vacAlarmHighPV = config.getString("vacAlarmHighPV");
		vacAlarmLowPV = config.getString("vacAlarmLowPV");
		vacWarnHighPV = config.getString("vacWarningHighPV");
		vacWarnLowPV = config.getString("vacWarningLowPV");
		
		trigger = config.getInt("trigger", 70000);
		double targetOff = config.getDouble("targetOff",0.0);
		double targetLow = config.getDouble("targetLow",0.0);
		double targetHigh = config.getDouble("targetHigh",0.0);
		boolean enabled = config.getBoolean("enabled", false);

		if (inputPV == null || inputPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no inputPV parameter!");
			inputPV=null;
		}
		if (interlockPV == null || interlockPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no interlockPV parameter!");
		}

		// creates records of variables to be stored in IOC shell
		
		input= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Input", DBRType.DOUBLE, "Input value for the feedback.", inputPV);
		if (interlockPV!=null) {
			interlock= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Interlock", DBRType.BYTE, "Interlock channel value.", interlockPV);
			addRecord(INTERLOCK, interlock.getRecord());
		}
		
		if (vacAlarmHighPV!=null && vacAlarmLowPV!=null) {
			connectLinks(VAC_ALARM_HIGH, vacAlarmHighPV);
			connectLinks(VAC_ALARM_LOW, vacAlarmLowPV);
		}
		if (vacWarnHighPV!=null && vacWarnLowPV!=null) {
			connectLinks(VAC_WARNING_HIGH, vacWarnHighPV);
			connectLinks(VAC_WARNING_LOW, vacWarnLowPV);
		}
		
		this.state=SM.INITIAL;

		addRecord(INPUT, input.getRecord());

		addRecordOfMemoryValueProcessor("InputPV", "Input PV", DBRType.STRING, true, false, new Object[] {inputPV});
		addRecordOfMemoryValueProcessor(ENABLED, "Feedback loop is enabled and active.", DBRType.BYTE,enabled);
		addRecordOfMemoryValueProcessor(OFF_ON_ERROR, "Error truns power off.", DBRType.BYTE, false);

		addRecordOfMemoryValueProcessor(LOOP_RATE, "Control loop update rate", 1, 1000, "s", (int)(trigger/1000.0));
		addRecordOfMemoryValueProcessor(INPUT_TARGET_LOW, "Input target low", 0.0, 1000.0, "No", (short) 0, targetLow);
		addRecordOfMemoryValueProcessor(INPUT_TARGET_HIGH, "Input target high", 0.0, 1000.0, "No", (short) 0, targetHigh);
		addRecordOfMemoryValueProcessor(INPUT_TARGET_OFF, "Input target cut-off", 0.0, 1000.0, "No", (short) 0, targetOff);
		addRecordOfMemoryValueProcessor(STATE, "Latest state of loopback cycle.", SM.labels(), (short) getState().ordinal());
		addRecordOfMemoryValueProcessor(STATE_DESC, "Latest state description.", DBRType.STRING, "Initial");
		addRecordOfMemoryValueProcessor(STATE_INTLCK, "In interlock due to Klystron.", DBRType.BYTE, false);

		getRecord(LOOP_RATE).setPersistent(true);
		getRecord(INPUT_TARGET_LOW).setPersistent(true);
		getRecord(INPUT_TARGET_HIGH).setPersistent(true);
		getRecord(INPUT_TARGET_OFF).setPersistent(true);
		
		feedback= newFeedbackControl();
		feedback.configure(name, config);
		
		if (readonly) {
			log.warn("READONLY mode, no value is set.");
		}
		
		interlockOK();
	}
	
	private boolean interlockOK() {
		boolean okOverride = false;
		if (interlock!=null) {
			Object o= interlock.getValue();
			boolean ok=true;
			if (o instanceof Number) {
				ok= interlockOK==((Number)o).longValue();
			} else if (o.getClass().isArray()) {
				long l=Array.getLong(o, 0);
				ok= l==interlockOK;
			}
			getRecord(STATE_INTLCK).setValue(!ok);
			return ok || okOverride; 
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		looper= new Thread("ProcessingThread-"+getName()) {
			
			@Override
			public void run() {
				
				long last=0;

				while (database.isActive()) {
					
					try {
						synchronized (this) {
							wait(1000);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					if (getRecord(ENABLED).getValueAsBoolean()) {
						
						boolean intOK= interlockOK();
						
						if (!intOK) {
							String s= "Disabeling due to interlock signal!";
							log.warn(s);
							setState(SM.INTERLOCK,s);
							getRecord(ENABLED).setValue(0);
						} else {
							if (System.currentTimeMillis()>last+trigger) {
								triggerFeedbackLoop();
								last= System.currentTimeMillis();
							} else {
								triggerCutOffLoop();
							}
						}
					}
				}
			}
		};
		looper.start();
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		
		if (LOOP_RATE.equals(name)) {
			int rate= getRecord(LOOP_RATE).getValueAsInt();
			trigger=rate*1000;
		} else if (INPUT==name || INTERLOCK==name) {
			interlockOK();
			triggerLoop();
		}

	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		if (ENABLED==name) {
			log4info("User enable request: "+getRecord(ENABLED).getValueAsBoolean());
			if (getRecord(ENABLED).getValueAsBoolean()) {
				feedback.notifyEnabled();
			}
			triggerLoop();
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (VAC_ALARM_HIGH==name) {
			triggerLoop();
		/*} else if (GATE==name) {
			ValueLinks vl= getLinks(GATE);
			if (!vl.isInvalid() && !vl.isLastSeverityInvalid() && vl.isReady()) {
				gateOpen= vl.consumeAsBooleanAnd();
				if (!gateOpen) {
					
				}
			}*/
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
	 * Feedback State Machine This is a simple state machine: it is active when
	 * ARMED is set, waits until some condition, then attempts two power
	 * corrections in order to resolve this condition. This function should NOT
	 * contain blocking code
	 */
	private void triggerFeedbackLoop() {

		if (!interlockOK()) {
			return;
		}

		SM nextState=SM.INITIAL;
		String errorDesc=null;
		
		if (input.getRecord().getAlarmStatus()!=Status.NO_ALARM) {
			nextState= SM.ERROR;
			errorDesc="Recors '"+inputPV+"' is in alarm: '"+input.getRecord().getAlarmStatus().toString()+"'";
			log4error(errorDesc);
		} else {
			
			double inp= input.getRecord().getValueAsDouble();
			double targetLow = getRecord(INPUT_TARGET_LOW).getValueAsDouble();
			double targetHigh = getRecord(INPUT_TARGET_HIGH).getValueAsDouble();
			double targetOff = getRecord(INPUT_TARGET_OFF).getValueAsDouble();
			
			boolean vacAlarmOn=false;
			boolean vacAlarmOff=false;
			boolean vacWarnOn=false;
			boolean vacWarnOff=false;
			
			ValueLinks vl= getLinks(VAC_ALARM_HIGH);
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
				vacAlarmOn=vl.consumeAsBooleanAnd();
			} else {
				nextState= SM.ERROR;
				errorDesc="Link "+vl.getLinkNames()[0]+" connection problem.";
				log4error(errorDesc);
			}
			vl= getLinks(VAC_ALARM_LOW);
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
				vacAlarmOff=vl.consumeAsBooleanAnd();
			} else {
				nextState= SM.ERROR;
				errorDesc="Link "+vl.getLinkNames()[0]+" connection problem.";
				log4error(errorDesc);
			}
			vl= getLinks(VAC_WARNING_HIGH);
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
				vacWarnOn=vl.consumeAsBooleanAnd();
			} else {
				nextState= SM.ERROR;
				errorDesc="Link "+vl.getLinkNames()[0]+" connection problem.";
				log4error(errorDesc);
			}
			vl= getLinks(VAC_WARNING_LOW);
			if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
				vacWarnOff=vl.consumeAsBooleanAnd();
			} else {
				nextState= SM.ERROR;
				errorDesc="Link "+vl.getLinkNames()[0]+" connection problem.";
				log4error(errorDesc);
			}
			
			if (nextState != SM.ERROR) {
				if (inp>targetOff || vacAlarmOn) {
					nextState=SM.COOL_DOWN;
					log.info("State "+nextState.name()+" based on drops "+inp+" > "+targetOff+" or vacAlarmOn "+vacAlarmOn);
				} else if (getState()==SM.COOL_DOWN && vacAlarmOff) {
					nextState=SM.COOL_DOWN;
					log.info("State "+nextState.name()+" based on state "+getState()+" is COOL_DOWN and vacAlarmOff "+vacAlarmOff);
				} else if (inp>targetHigh || vacAlarmOff) {
					nextState=SM.DECREASE;
					log.debug("State "+nextState.name()+" based on drops "+inp+" > "+targetHigh+" or vacAlarmOff "+vacAlarmOff);
				} else if (inp>targetLow || vacWarnOn) {
					nextState=SM.IDLE;
					log.debug("State "+nextState.name()+" based on drops "+inp+" > "+targetLow+" or vacWarnOn "+vacWarnOn);
				} else if (getState()==SM.IDLE && vacWarnOff) {
					nextState=SM.IDLE;
					log.debug("State "+nextState.name()+" based on state "+getState()+" is IDLE and vacWarnOff "+vacWarnOff);
				} else if (inp<targetLow) {
					nextState=SM.INCREASE;
					log.debug("State "+nextState.name()+" based on drops "+inp+" < "+targetLow);
				} else {
					nextState=SM.IDLE;
					log.debug("State "+nextState.name()+" by default");
				}
			}
			
			
			//System.out.println("CONTROL "+state+" "+nextState+" "+inp+" "+vacWarn+" "+vacAlarm);
		}
		
		if (getState()==SM.COOL_DOWN && nextState!=SM.COOL_DOWN) {
			feedback.cutOff(false);
		}
		
		SM st1=getState();
		SM st=st1;
		
		switch (nextState) {
		
			// initializes state machine variables
			case INITIAL:
				st = SM.IDLE;
				setState(st);
				break;
				
			// waits until receiving an alarm
			case IDLE:
				st = nextState;
				setState(st);
				break;
				
			// tries increasing the power
			case INCREASE:
				setState(nextState);
				if (feedback.stepUp()) {
					st  = nextState;
				} else {
					if (getState()!=SM.ERROR) {
						st = SM.IDLE;
						setState(st);
					}
				}
				break;
	
			// tries decreasing the power
			case DECREASE:
				setState(nextState);
				if (feedback.stepDown()) {
					st = nextState;
				} else {
					if (getState()!=SM.ERROR) {
						st = SM.IDLE;
						setState(st);
					}
				}
				break;
				
			// tries decreasing the power and go into cool off time
			case COOL_DOWN:
				feedback.cutOff(true);
				if (st != nextState) {
					feedback.coolDown();
				}
				st = nextState;
				setState(st);
				break;
					
			// feedback has failed
			case ERROR:
				st = nextState;
				getRecord(ENABLED).setValue(0);
				setState(st,errorDesc);
				if (getRecord(OFF_ON_ERROR).getValueAsBoolean()) {
					if (feedback!=null) {
						feedback.cutOff(true);
					}
				}
				break;
				
			case INTERLOCK:
				st = nextState;
				getRecord(ENABLED).setValue(0);
				setState(st);
				break;
		}
		
	}

	private void triggerCutOffLoop() {
		
		if (!interlockOK()) {
			return;
		}
		
		double inp= input.getRecord().getValueAsDouble();
		double targetOff = getRecord(INPUT_TARGET_OFF).getValueAsDouble();
		
		boolean vacAlarm=false;
		
		ValueLinks vl= getLinks(VAC_ALARM_HIGH);
		if (vl!=null && !vl.isInvalid() && vl.isReady() && !vl.isLastSeverityInvalid()) {
			vacAlarm=vl.consumeAsBooleanAnd();
		}
		
		if (inp>targetOff || vacAlarm) {
			feedback.cutOff(true);
			if (getState()!=SM.COOL_DOWN) {
				log.info("COOLDOWN: drops '"+inp+"' > '"+targetOff+"' or vacAlarmOn '"+vacAlarm+"' ("+vl.getLastStatus()+","+vl.getLastSeverity()+")");
				feedback.coolDown();
			}
			setState(SM.COOL_DOWN);
		}
	}

	/**
	 * <p>Getter for the field <code>state</code>.</p>
	 *
	 * @return a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.SM} object
	 */
	public SM getState() {
		return state;
	}
	
	/**
	 * <p>Setter for the field <code>state</code>.</p>
	 *
	 * @param state a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.SM} object
	 * @return a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.SM} object
	 */
	public SM setState(SM state) {
		return setState(state,null);
	}

	/**
	 * <p>Setter for the field <code>state</code>.</p>
	 *
	 * @param state a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.SM} object
	 * @param desc a {@link java.lang.String} object
	 * @return a {@link org.scictrl.csshell.epics.server.application.StepFeedbackLoopApplication3.SM} object
	 */
	public SM setState(SM state, String desc) {
		getRecord(STATE).setValue((this.state=state).ordinal());
		if (desc!=null) {
			getRecord(STATE_DESC).setValue(state.toString()+": "+desc);
			if (state==SM.ERROR) {
				log4error("ERROR state: "+desc);
			}
		} else {
			getRecord(STATE_DESC).setValue(state.toString());
		}
		return state;
	}
}
