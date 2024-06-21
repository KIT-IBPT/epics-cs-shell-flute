/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.logging.log4j.Logger;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.application.ScanApplication.Repeat;
import org.scictrl.csshell.epics.server.application.ScanApplication.ScanningTask;
import org.scictrl.csshell.epics.server.application.ScanApplication.ScanningTask.ScanController;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>PowerControlApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class PowerControlApplication extends AbstractApplication {

	
	/** Constant PV suffix <code>SWR_WG="SWR"</code> */
	public static final String SWR_WG = 	"SWR:WG";
	/** Constant PV suffix <code>SWR_KLY="SWR:Kly"</code> */
	public static final String SWR_KLY = 	"SWR:Kly";
	/** Constant PV suffix <code>SWR_WG_LIMIT="SWR:WG:Limit"</code> */
	public static final String SWR_WG_LIMIT = 	"SWR:WG:Limit";
	/** Constant PV suffix <code>SWR_KLY_LIMIT="SWR:Kly:Limit"</code> */
	public static final String SWR_KLY_LIMIT = 	"SWR:Kly:Limit";

	/** Constant PV suffix <code>CMD_ON="Cmd:On"</code> */
	public static final String CMD_ON =  "Cmd:On";
	/** Constant PV suffix <code>CMD_OFF="Cmd:Off"</code> */
	public static final String CMD_OFF = "Cmd:Off";
	
	/** Constant PV suffix <code>OFF_ON="OffOn"</code> */
	public static final String OFF_ON = 	"OffOn";
	/** Constant PV suffix <code>OFF_ON_DIRECT="OffOn:Direct"</code> */
	public static final String OFF_ON_DIRECT = "OffOn:Direct";
	
	/** Constant PV suffix <code>STATUS_ON="Status:On"</code> */
	public static final String STATUS_ON =         "Status:On";
	/** Constant PV suffix <code>STATUS_LOCKED="Status:Locked"</code> */
	public static final String STATUS_LOCKED =  "Status:Locked";
	/** Constant PV suffix <code>STATUS_WG_LOCKED="Status:WG:Locked"</code> */
	public static final String STATUS_WG_LOCKED =  "Status:WG:Locked";
	/** Constant PV suffix <code>STATUS_KLY_LOCKED="Status:Kly:Locked"</code> */
	public static final String STATUS_KLY_LOCKED = "Status:Kly:Locked";
	/** Constant PV suffix <code>STATUS_SCANNING="Status:Scanning"</code> */
	public static final String STATUS_SCANNING = "Status:Scanning";

	/** Constant PV suffix <code>POWER_LINK="POWER_LINK"</code> */
	public static final String POWER_LINK =     "POWER_LINK";
	/** Constant PV suffix <code>POWER_SET="Set"</code> */
	public static final String POWER_SET =      "Set";
	/** Constant PV suffix <code>POWER_SET_GET="Set:Get"</code> */
	public static final String POWER_SET_GET =  "Set:Get";
	/** Constant PV suffix <code>POWER_SET_SYNC="Set:Sync"</code> */
	public static final String POWER_SET_SYNC = "Set:Sync";
	/** Constant PV suffix <code>POWER_SET_DIFF="Set:Diff"</code> */
	public static final String POWER_SET_DIFF = "Set:Diff";
	
	private String powerPV;
	private ValueLinks power;
	private ScanningTask task;
	
	private int undefined= 2;
	private String swrWgPV;
	private String swrKlyPV;
	private Double offLimit;
	private double offLimitWaitS;
	private long offLimitWait;
	private Long offLimitTime=null;
	
	/**
	 * <p>Constructor for PowerControlApplication.</p>
	 */
	public PowerControlApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		offLimit= config.getDouble("offLimit",null);
		offLimitWaitS= config.getDouble("offLimitWait", 10.0);
		
		if (offLimitWaitS<0.0001) {
			offLimitWaitS= 10.0;
		}
		
		offLimitWait=(long)(offLimitWaitS*1000.0);
		
		swrWgPV= config.getString("swrWGPV");
		if (swrWgPV==null || swrWgPV.length()==0) {
			throw new IllegalArgumentException("Configuration for 'swrWGPV' not provided.");
		}
		
		swrKlyPV= config.getString("swrKlyPV");
		if (swrKlyPV==null || swrKlyPV.length()==0) {
			throw new IllegalArgumentException("Configuration for 'swrKlyPV' not provided.");
		}

		powerPV= config.getString("powerPV");
		if (powerPV==null || powerPV.length()==0) {
			throw new IllegalArgumentException("Configuration for 'powerPV' not provided.");
		}

		//switchPV= config.getString("switchPV");
		//if (switchPV==null || switchPV.length()==0) {
		//	throw new IllegalArgumentException("Configuration for 'switchPV' not provided.");
		//}

		power= connectLinks(POWER_LINK, powerPV);

		addRecordOfMemoryValueProcessor(POWER_SET, "Power control", 0.0, 100.0, "V", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(POWER_SET_GET, "Power control", 0.0, 100.0, "V", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(POWER_SET_DIFF, "Power set and readback differs", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(SWR_WG_LIMIT, "WG SWR value limit", 0.0, 1000.0, "", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(SWR_KLY_LIMIT, "Kly SWR value limit", 0.0, 1000.0, "", (short)2, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_LOCKED, "Power control locked", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_WG_LOCKED, "WG Power control locked", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_KLY_LOCKED, "Kly Power control locked", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_ON, "Power On", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(STATUS_SCANNING, "Power value is scanning", DBRType.BYTE, 0);
		addRecordOfMemoryValueProcessor(OFF_ON, "Sets off 0 or on 1", new String[] {"Off","On"}, (short)0);
		addRecordOfMemoryValueProcessor(OFF_ON_DIRECT, "Sets off 0 or on 1, no ramping", new String[] {"Off","On"}, (short)0);
		
		addRecordOfCommandProcessor(CMD_ON, "Power On", 1000);
		addRecordOfCommandProcessor(CMD_OFF, "Power Off", 1000);
		addRecordOfCommandProcessor(POWER_SET_SYNC, "Synchronize setpoint and setpoint get", 1000);
		
		addRecordOfOnLinkValueProcessor(SWR_WG, "SWR WG value", DBRType.DOUBLE, swrWgPV);
		addRecordOfOnLinkValueProcessor(SWR_KLY, "SWR Kly value", DBRType.DOUBLE, swrKlyPV);

		getRecord(SWR_KLY_LIMIT).setPersistent(true);
		getRecord(SWR_WG_LIMIT).setPersistent(true);
		getRecord(POWER_SET).setPersistent(true);
		
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==POWER_LINK) {
			
			if (power.isReady()) {
				
				Severity sev= power.getLastSeverity();
				Status sta= power.getLastStatus();
				
				double[] d= power.consumeAsDoubles();
				
				Record rGet=getRecord(POWER_SET_GET);
				
				//log.info("UNDEFINED "+undefined+" "+d[0]+" "+sev.getName()+" "+sta.getName(),new Error("Trace me."));

				if (d!=null && d.length==1 && !Double.isNaN(0)) {
					rGet.setValue(d);
					rGet.updateAlarm(sev, sta);

					if (undefined>0) undefined--;
					if (undefined==0) {
						undefined=-1;
						if (d[0] > 0.000001 ) {
							getRecord(STATUS_ON).setValue(1);
							getRecord(OFF_ON).setValue(1);
						}
					}

				} else {
					rGet.updateAlarm(Severity.INVALID_ALARM, Status.UDF_ALARM);
				}
				
				updateDiff(null, rGet);
				
				
			} else {
				getRecord(POWER_SET_GET).updateAlarm(Severity.INVALID_ALARM, Status.LINK_ALARM);
			}
			
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (name==POWER_SET) {
			writePower(false);
		} else if (name==POWER_SET_SYNC) {
			Record rSet= getRecord(POWER_SET);
			Record rGet=getRecord(POWER_SET_GET);
			rSet.setValue(rGet.getValue());
			updateDiff(rSet, rGet);
		} else if (name==CMD_ON) {
			powerSwitch(true,false);
		} else if (name==CMD_OFF) {
			powerSwitch(false,false);
		} else if (name==OFF_ON) {
			powerSwitch(getRecord(OFF_ON).getValueAsBoolean(),false);
		} else if (name==OFF_ON_DIRECT) {
			powerSwitch(getRecord(OFF_ON_DIRECT).getValueAsBoolean(),true);
		}
	}
	
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==SWR_KLY || name==SWR_KLY_LIMIT || name==SWR_WG || name==SWR_WG_LIMIT) {
			updateLock();
		} else if (name==POWER_SET) {
			writePower(false);
		} else if (name==STATUS_ON && offLimit!=null) {
			boolean b= getRecord(STATUS_ON).getValueAsBoolean();
			if (b) {
				if (offLimitTime==null) {
					offLimitTime= System.currentTimeMillis();
				}
			} else {
				offLimitTime=null;
			}
		}
		
	}
	
	private void updateLock() {
		
		double swrWg= getRecord(SWR_WG).getValueAsDouble();
		double swrKly= getRecord(SWR_KLY).getValueAsDouble();
		
		boolean lockWg=false;
		boolean lockKly=false;
		
		double offLim=0.0;
		
		if (
				offLimit!=null && // if it is enabled at all 
				( 
					( // if it is inside grace wait period
						offLimitTime!=null && 
						( (offLimitTime-System.currentTimeMillis())<offLimitWait ) 
					) || // or it is off
					!getRecord(STATUS_ON).getValueAsBoolean()
				)
			) 
		{
			offLim=offLimit;
		}
		
		if (getRecord(SWR_WG).getAlarmSeverity()!=Severity.INVALID_ALARM) {
			double lim= getRecord(SWR_WG_LIMIT).getValueAsDouble();
			if (swrWg>=lim && swrWg>=offLim) {
				lockWg=true;
			}
		}
		if (getRecord(SWR_KLY).getAlarmSeverity()!=Severity.INVALID_ALARM) {
			double lim= getRecord(SWR_KLY_LIMIT).getValueAsDouble();
			if (swrKly>=lim && swrKly>=offLim) {
				lockKly=true;
			}
		}
		
		getRecord(STATUS_KLY_LOCKED).setValue(lockKly);
		getRecord(STATUS_WG_LOCKED).setValue(lockWg);
		getRecord(STATUS_LOCKED).setValue(lockKly || lockWg);
		
		if (lockKly || lockWg) {
			powerSwitch(false, false);
		}
	}
	
	private void powerSwitch(boolean on, boolean direct) {
		if (on) {
			boolean lock= getRecord(STATUS_LOCKED).getValueAsBoolean();
			if (!lock) {
				getRecord(STATUS_ON).setValue(1);
				getRecord(OFF_ON).setValue(1);
				getRecord(OFF_ON_DIRECT).setValue(1);
				writePower(false);
			}
		} else {
			getRecord(STATUS_ON).setValue(0);
			getRecord(OFF_ON).setValue(0);
			getRecord(OFF_ON_DIRECT).setValue(0);
			if (!power.isInvalid() && !power.isLastSeverityInvalid() && power.isReady()) {
				try {
					power.setValueToAll(0.0);
				} catch (Exception e) {
					log4error("Set failed", e);
				}
			}
		}
	}
	
	private void writePower(boolean direct) {
		boolean b= getRecord(STATUS_ON).getValueAsBoolean();
		
		if (!b) {
			return;
		}
		
		if (!power.isInvalid() && !power.isLastSeverityInvalid() && power.isReady()) {

			double dd= power.consumeAsDoubles()[0];
			boolean lock= getRecord(STATUS_LOCKED).getValueAsBoolean();
			Record rSet=getRecord(POWER_SET);
			double d= rSet.getValueAsDouble();
			
			boolean blocked=false;
			
			if (lock) {
				if (d>dd) {
					d=dd;
					blocked=true;
				}
			}
			
			if (d<=dd || direct) {
				cancelScan();
				try {
					power.setValueToAll(d);
				} catch (Exception e) {
					log4error("Set failed", e);
				}
			} else {
				scan(dd,d);
			}
			
			if (blocked) {
				rSet.setValue(dd);
				rSet.updateAlarm(Severity.MAJOR_ALARM, Status.HW_LIMIT_ALARM);
			} else {
				rSet.updateAlarm(Severity.NO_ALARM, Status.NO_ALARM);
			}
			
			updateDiff(rSet, null);

		}
			
	}
	
	private synchronized void cancelScan() {
		if (task!=null) {
			task.cancel();
			task=null;
		}
		getRecord(STATUS_SCANNING).setValue(0);
	}

	private synchronized void scan(double start, double end) {
		long delay=0L;
		if (task!=null) {
			task.cancel();
			delay=1000L;
		}
		getRecord(STATUS_SCANNING).setValue(1);
		task= new ScanningTask(start,end,1.0,1000,0,Repeat.SINGLE,0.001,new ScanController() {
			@Override
			public boolean isSetReady() {
				return !power.isInvalid() && !power.isLastSeverityInvalid() && power.isReady();
			}
			@Override
			public Logger log() {
				return log;
			}
			@Override
			public boolean setValue(double d) {
				try {
					power.setValueToAll(d);
					return true;
				} catch (Exception e) {
					log4error("Set failed", e);
				}
				return false;
			}
			@Override
			public void error(String message, Throwable e) {
				log4error(message, e);
			}
			@Override
			public void done(ScanningTask t) {
				if (t==task) {
					task=null;
					getRecord(STATUS_SCANNING).setValue(0);
				}
			}
		},1000);
		database.schedule(task, delay);
	}
	
	private void updateDiff(Record rSet, Record rGet) {
		if (rSet==null) {
			rSet=getRecord(POWER_SET);
		}
		if (rGet==null) {
			rGet=getRecord(POWER_SET_GET);
		}
		
		double diff= Math.abs(rSet.getValueAsDouble()-rGet.getValueAsDouble());
		
		getRecord(POWER_SET_DIFF).setValue(diff>0.001);
		
	}
	
}
