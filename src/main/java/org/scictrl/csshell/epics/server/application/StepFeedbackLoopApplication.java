package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>StepFeedbackLoopApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class StepFeedbackLoopApplication extends AbstractApplication {

	/** Constant <code>LOOP_RATE="LoopRate"</code> */
	public static final String LOOP_RATE = "LoopRate";
	/** Constant <code>STATE="Status"</code> */
	public static final String STATE = "Status";
	/** Constant <code>ENABLED="Enabled"</code> */
	public static final String ENABLED = "Enabled";
	/** Constant <code>ERROR_SUM="ErrorSum"</code> */
	public static final String ERROR_SUM = "ErrorSum";
	/** Constant <code>OFF="Off"</code> */
	public static final String OFF = "Off";
	/** Constant <code>INTERLOCK="Interlock"</code> */
	public static final String INTERLOCK = "Interlock";
	/** Constant <code>VAC_ALARM="VacAlarm"</code> */
	public static final String VAC_ALARM = "VacAlarm";
	/** Constant <code>VAC_WARNING="VacWarning"</code> */
	public static final String VAC_WARNING = "VacWarning";
	/** Constant <code>OUTPUT="Output"</code> */
	public static final String OUTPUT = "Output";
	/** Constant <code>OUTPUT_SET="OutputSet"</code> */
	public static final String OUTPUT_SET = "OutputSet";
	/** Constant <code>INPUT_TARGET_LOW="Input:TargetLow"</code> */
	public static final String INPUT_TARGET_LOW = "Input:TargetLow";
	/** Constant <code>INPUT_TARGET_HIGH="Input:TargetHigh"</code> */
	public static final String INPUT_TARGET_HIGH = "Input:TargetHigh";
	/** Constant <code>INPUT_TARGET_OFF="Input:TargetOff"</code> */
	public static final String INPUT_TARGET_OFF = "Input:TargetOff";
	/** Constant <code>INPUT="Input"</code> */
	public static final String INPUT = "Input";
	/** Constant <code>OUTPUT_STEP="Output:Step"</code> */
	public static final String OUTPUT_STEP = "Output:Step";
	/** Constant <code>OUTPUT_STEP_COOL="Output:StepCool"</code> */
	public static final String OUTPUT_STEP_COOL = "Output:StepCool";
	/** Constant <code>INPUT_MIN="Input:Min"</code> */
	public static final String INPUT_MIN = "Input:Min";
	/** Constant <code>INPUT_MAX="Input:Max"</code> */
	public static final String INPUT_MAX = "Input:Max";
	/** Constant <code>OUTPUT_MIN="Output:Min"</code> */
	public static final String OUTPUT_MIN = "Output:Min";
	/** Constant <code>OUTPUT_MAX="Output:Max"</code> */
	public static final String OUTPUT_MAX = "Output:Max";
	private String inputPV;
	private String outputPV;
	
	private enum SM {
		INITIAL, IDLE, INCREASE, DECREASE, COOL_DOWN, FEEDBACK_FAILURE, INTERLOCK;
		
		static public String[] labels() {
			return new String[] {SM.INITIAL.name(),SM.IDLE.name(),SM.INCREASE.name(),SM.DECREASE.name(),COOL_DOWN.name(),FEEDBACK_FAILURE.name()};
		}
	};

	private LinkedValueProcessor input;
	private LinkedValueProcessor output;
	private int trigger;
	private Thread looper;
	private SM state;
	private String offPV;
	private LinkedValueProcessor off;
	private String interlockPV;
	private LinkedValueProcessor interlock;
	private String vacAlarmPV;
	private String vacWarnPV;

	/**
	 * <p>Constructor for StepFeedbackLoopApplication.</p>
	 */
	public StepFeedbackLoopApplication() {
	}

	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);

		inputPV = config.getString("inputPV");
		outputPV = config.getString("outputPV");
		offPV = config.getString("offPV");
		interlockPV = config.getString("interlockPV");

		vacAlarmPV = config.getString("vacAlarmPV");
		vacWarnPV = config.getString("vacWarningPV");
		
		trigger = config.getInt("trigger", 70000);
		double step = config.getDouble("step");
		double stepCool = config.getDouble("stepCool",step*10.0);
		double targetOff = config.getDouble("targetOff");
		double targetLow = config.getDouble("targetLow");
		double targetHigh = config.getDouble("targetHigh");
		boolean enabled = config.getBoolean("enabled", false);
		double outMin = config.getDouble("outputMin");
		double outMax = config.getDouble("outputMax");

		if (inputPV == null || inputPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no inputPV parameter!");
		}
		if (outputPV == null || outputPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no outputPV parameter!");
		}
		if (offPV == null || offPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no offPV parameter!");
		}
		if (interlockPV == null || interlockPV.length() == 0) {
			log.error("Configuration for '" + name + "' has no interlockPV parameter!");
		}


		// creates records of variables to be stored in IOC shell
		
		
		
		input= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Input", DBRType.DOUBLE, "Input value for the feedback.", inputPV);
		output= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Output", DBRType.DOUBLE, "Output channel value.", outputPV);
		off= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Off", DBRType.DOUBLE, "Off channel value.", offPV);
		interlock= LinkedValueProcessor.newProcessor(this.name + nameDelimiter + "Interlock", DBRType.BYTE, "Interlock channel value.", interlockPV);
		
		if (vacAlarmPV!=null) {
			connectLinks(VAC_ALARM, vacAlarmPV);
		}
		if (vacWarnPV!=null) {
			connectLinks(VAC_WARNING, vacWarnPV);
		}
		
		
		this.state=SM.INITIAL;

		addRecord(INPUT, input.getRecord());
		addRecord(OUTPUT, output.getRecord());
		addRecord(OFF, off.getRecord());
		addRecord(INTERLOCK, interlock.getRecord());

		addRecordOfMemoryValueProcessor("OutputPV", "Output PV", DBRType.STRING, true, false, new Object[] {outputPV});
		addRecordOfMemoryValueProcessor("InputPV", "Input PV", DBRType.STRING, true, false, new Object[] {inputPV});
		addRecordOfMemoryValueProcessor(ENABLED, "Feedback loop is enabled and active.", DBRType.BYTE,enabled);

		addRecordOfMemoryValueProcessor(LOOP_RATE, "Control loop update rate", 1, 1000, "s", (int)(trigger/1000.0));
		addRecordOfMemoryValueProcessor(OUTPUT_STEP, "Step value", 0.0, 1000.0, "W", (short) 3, step);
		addRecordOfMemoryValueProcessor(OUTPUT_STEP_COOL, "Step value at cooldown", 0.0, 1000.0, "W", (short) 3, stepCool);
		addRecordOfMemoryValueProcessor(OUTPUT_SET, "Desired output values", 0.0, 1000.0, "W", (short) 3, 0.0);
		addRecordOfMemoryValueProcessor(OUTPUT_MIN, "Min allowed output value", 0.0, 1000.0, "W", (short) 3, outMin);
		addRecordOfMemoryValueProcessor(OUTPUT_MAX, "Max allowed output value", 0.0, 1000.0, "W", (short) 3, outMax);
		addRecordOfMemoryValueProcessor(INPUT_TARGET_LOW, "Input target low", 0.0, 1000.0, "No", (short) 0, targetLow);
		addRecordOfMemoryValueProcessor(INPUT_TARGET_HIGH, "Input target high", 0.0, 1000.0, "No", (short) 0, targetHigh);
		addRecordOfMemoryValueProcessor(INPUT_TARGET_OFF, "Input target cut-off", 0.0, 1000.0, "No", (short) 0, targetOff);
		addRecordOfMemoryValueProcessor(STATE, "Latest state of loopback cycle.", SM.labels(), (short) state.ordinal());
		
	}

	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		looper= new Thread("ProcessingThread-"+getName()) {
			
			@Override
			public synchronized void run() {
				
				long last=0;

				while (database.isActive()) {
					
					try {
						wait(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					if (getRecord(ENABLED).getValueAsBoolean()) {
						
						boolean intOK= interlock.getValueAsBoolean();
						
						if (!intOK) {
							getRecord(STATE).setValue((state=SM.INTERLOCK).ordinal());
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
		}

	}


	/*
	 * Feedback State Machine This is a simple state machine: it is active when
	 * ARMED is set, waits until some condition, then attempts two power
	 * corrections in order to resolve this condition. This function should NOT
	 * contain blocking code
	 */
	private void triggerFeedbackLoop() {
		
		SM nextState=SM.INITIAL;
		
		if (input.getRecord().getAlarmStatus()!=Status.NO_ALARM) {
			nextState= SM.FEEDBACK_FAILURE;
		} else {
			
			double inp= input.getRecord().getValueAsDouble();
			double targetLow = getRecord(INPUT_TARGET_LOW).getValueAsDouble();
			double targetHigh = getRecord(INPUT_TARGET_HIGH).getValueAsDouble();
			double targetOff = getRecord(INPUT_TARGET_OFF).getValueAsDouble();
			
			boolean vacAlarm=false;
			boolean vacWarn=false;
			
			ValueLinks vl= getLinks(VAC_ALARM);
			if (vl!=null && !vl.isInvalid() && vl.isReady()) {
				vacAlarm=vl.consumeAsBooleanAnd();
			}
			vl= getLinks(VAC_WARNING);
			if (vl!=null && !vl.isInvalid() && vl.isReady()) {
				vacWarn=vl.consumeAsBooleanAnd();
			}
			
			if (inp>targetOff || vacAlarm) {
				nextState=SM.COOL_DOWN;
			} else if (inp>targetHigh || vacWarn) {
				nextState=SM.DECREASE;
			} else if (inp>targetLow) {
				nextState=SM.IDLE;
			} else if (inp<targetLow) {
				nextState=SM.INCREASE;
			} else {
				nextState=SM.IDLE;
			}
			
			//System.out.println("CONTROL "+state+" "+nextState+" "+inp+" "+vacWarn+" "+vacAlarm);
		}
		
		double out = getRecord(OUTPUT).getValueAsDouble();
		double step = getRecord(OUTPUT_STEP).getValueAsDouble();
		double stepCool = getRecord(OUTPUT_STEP_COOL).getValueAsDouble();
		double outMin= getRecord(OUTPUT_MIN).getValueAsDouble();
		double outMax= getRecord(OUTPUT_MAX).getValueAsDouble();
		
		if (state==SM.COOL_DOWN && nextState!=SM.COOL_DOWN) {
			cutOff(false);
		}
		
		switch (nextState) {
		
			// initializes state machine variables
			case INITIAL:
				state = SM.IDLE;
				break;
				
			// waits until receiving an alarm
			case IDLE:
				state=nextState;
				break;
				
			// tries increasing the power
			case INCREASE:
				if (out + step < outMax) {
					state=nextState;
					setOutputValue(out + step);
				} else {
					state = SM.IDLE;
				}
				break;
	
			// tries decreasing the power
			case DECREASE:
				if (out - step > outMin) {
					state=nextState;
					setOutputValue(out - step);
				} else {
					state = SM.IDLE;
				}
				break;
				
			// tries decreasing the power and go into cool off time
			case COOL_DOWN:
				cutOff(true);
				if (state!=nextState) {
					if (out - stepCool > outMin) {
						setOutputValue(out - stepCool);
					} else if (out > outMin) {
						setOutputValue(outMin);
					}
				}
				state=nextState;
				break;
					
			// feedback has failed
			case FEEDBACK_FAILURE:
				state=nextState;
				break;
				
			case INTERLOCK:
				state=nextState;
				break;
		}
		
		getRecord(STATE).setValue(state.ordinal());
	}

	private void triggerCutOffLoop() {
		
		double inp= input.getRecord().getValueAsDouble();
		double targetOff = getRecord(INPUT_TARGET_OFF).getValueAsDouble();
		
		boolean vacAlarm=false;
		
		ValueLinks vl= getLinks(VAC_ALARM);
		
//		if (vl!=null) {
//			System.out.println("VAC "+vl.isInvalid()+" "+vl.isReady()+" "+Arrays.toString(vl.consume()));
//		} else {
//			System.out.println("VAC NULL");
//		}
		
		if (vl!=null && !vl.isInvalid() && vl.isReady()) {
			vacAlarm=vl.consumeAsBooleanAnd();
		}
		
		if (inp>targetOff || vacAlarm) {
			cutOff(true);
			if (state!=SM.COOL_DOWN) {
				double out = getRecord(OUTPUT).getValueAsDouble();
				double stepCool = getRecord(OUTPUT_STEP_COOL).getValueAsDouble();
				double outMin= getRecord(OUTPUT_MIN).getValueAsDouble();
				if (out - stepCool > outMin) {
					setOutputValue(out - stepCool);
				} else if (out > outMin) {
					setOutputValue(outMin);
				}
			}
			getRecord(STATE).setValue((state=SM.COOL_DOWN).ordinal());
		}
		//System.out.println("CUTOFF "+state+" "+inp+" "+vacAlarm);
	}

	/**
	 * Sets output parameter to output PV.
	 * 
	 * @param output
	 *            the output parameter to be set
	 */
	private void setOutputValue(double out) {
		try {
			getRecord(OUTPUT_SET).setValue(new double[] { out });
			output.setValue(new double[] { out });
			
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
			log.debug("Setting out '" + output + "' to '" + outputPV + "'.");
			
		} catch (Exception e) {
			log.error("Remote setting of '" + outputPV + "' failed " + e.toString(), e);
			updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
			
			getRecord(STATE).setValue((state=SM.FEEDBACK_FAILURE).ordinal());
			
			return;
		} finally {
		}
	}

	private void cutOff(boolean cutOff) {
		try {
			off.setValue(!cutOff);
			updateErrorSum(Severity.NO_ALARM, Status.NO_ALARM);
			log.debug("Setting off '" + off + "' to '" + !cutOff + "'.");
			
		} catch (Exception e) {
			log.error("Remote setting of '" + offPV + "' failed " + e.toString(), e);
			updateErrorSum(Severity.MAJOR_ALARM, Status.LINK_ALARM);
			
			getRecord(STATE).setValue((state=SM.FEEDBACK_FAILURE).ordinal());
			
			return;
		} finally {
		}
	}

}
