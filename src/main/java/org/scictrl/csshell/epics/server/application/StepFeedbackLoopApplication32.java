/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Database;
import org.scictrl.csshell.epics.server.application.ScanApplication.StartPoint;
import org.scictrl.csshell.epics.server.processor.LinkedValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>StepFeedbackLoopApplication32 class.</p>
 *
 * @author igor@scictrl.com
 */
public class StepFeedbackLoopApplication32 extends StepFeedbackLoopApplication3 {

	private static enum Step {INITIAL,READY,UP1,UP2,UP3,DOWN1,DOWN2,DOWN3,TOP1,TOP2,TOP3,BOTTOM1,BOTTOM2,BOTTOM3};

	/** Constant <code>OFF="Off"</code> */
	public static final String OFF = "Off";

	private String offPV;
	private LinkedValueProcessor off;
	private ScanApplication scan1;
	private ScanApplication scan2;
	private ScanApplication scan3;
	
	// last step, it is part of a state machine
	private Step step= Step.INITIAL;

	/**
	 * <p>Constructor for StepFeedbackLoopApplication32.</p>
	 */
	public StepFeedbackLoopApplication32() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void initialize(Database database) {
		super.initialize(database);
		
		database.addAll(scan1.getRecords());
		database.addAll(scan2.getRecords());
		database.addAll(scan3.getRecords());

	}
	
	/**
	 * <p>newFeedbackControl.</p>
	 *
	 * @return a FeedbackControl object
	 */
	protected FeedbackControl newFeedbackControl() {
		return new FeedbackControl() {
			
			
			
			
			private ScanApplication configureScan(String parent, String name, String confName, HierarchicalConfiguration config) {
				
				ScanApplication sa= new ScanApplication();
				HierarchicalConfiguration c= config.configurationAt(confName);
				sa.configure(parent+":"+name, c);
				return sa;
				
			}

			
			public void configure(String name, HierarchicalConfiguration config) {

				scan1 = configureScan(name,"Scan1","scan1", config);
				scan2 = configureScan(name,"Scan2","scan2", config);
				scan3 = configureScan(name,"Scan3","scan3", config);

				offPV = config.getString("offPV");

				if (offPV == null || offPV.length() == 0) {
					log.error("Configuration for '" + name + "' has no offPV parameter!");
				}

				// creates records of variables to be stored in IOC shell
				
				off= LinkedValueProcessor.newProcessor(name + nameDelimiter + "Off", DBRType.DOUBLE, "Off channel value.", offPV);
				
				addRecord(OFF, off.getRecord());

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
			
			private boolean initial() {
				if (step==Step.INITIAL) {
					if (!initScan1(true)) {
						return false;
					}
					if (!initScan2(true)) {
						return false;
					}
					if (!initScan3(true)) {
						return false;
					}
					scan1.startManualScan();
					scan2.startManualScan();
					scan3.startManualScan();
					step=Step.READY;
				}
				return true;
			}

			/**
			 * IF necessary initializes scan1
			 * Returns true if success
			 * @return true if success
			 */
			private boolean initScan1(boolean force) {
				if (scan1.getStartValue()>scan1.getSetpointValue()) {
					setState(SM.ERROR, "Scan 1 start point higher than current value");
					return false;
				}
				if (force || !scan1.isManualScanActive()) {
					scan1.startManualScan(force ? StartPoint.START : StartPoint.CURRENT);
				}
				return true;
			}
			
			/**
			 * IF necessary initializes scan2
			 * Returns true if success
			 * @return true if success
			 */
			private boolean initScan2(boolean force) {
				if (scan2.getStartValue()>scan2.getSetpointValue()) {
					setState(SM.ERROR, "Scan 2 start point higher than current value");
					return false;
				}
				if (force || !scan2.isManualScanActive()) {
					scan2.startManualScan(force ? StartPoint.START : StartPoint.CURRENT);
				}
				return true;
			}

			/**
			 * IF necessary initializes scan2
			 * Returns true if success
			 * @return true if success
			 */
			private boolean initScan3(boolean force) {
				if (scan3.getStartValue()>scan3.getSetpointValue()) {
					setState(SM.ERROR, "Scan 3 start point higher than current value");
					return false;
				}
				if (force || !scan3.isManualScanActive()) {
					scan3.startManualScan(force ? StartPoint.START : StartPoint.CURRENT);
				}
				return true;
			}

			public boolean stepUp() {
				
				boolean b= initial();
				if (!b) {
					return b;
				}
				
				if (
						step==Step.READY || 
						step==Step.UP1 || step==Step.DOWN1 || step==Step.BOTTOM1  || 
						step==Step.UP2 || step==Step.DOWN2 || step==Step.BOTTOM2  ||  
						step==Step.UP3 || step==Step.DOWN3 || step==Step.BOTTOM3 
						) 
				{
					// check scan1
					if (!initScan1(false)) {
						return false;
					}
					b= scan1.stepManualScan();
					b&=!scan1.isAtEnd();
					if (b) {
						step=Step.UP1;
					} else {
						step=Step.TOP1;
						if (scan2.isAtEnd()) {
							step=Step.TOP2;
							if (scan3.isAtEnd()) {
								step=Step.TOP3;
							}
						}
					}
					return true;
				} 
				
				if (
						step==Step.TOP1
						)
				{
					// reset scan1
					if (!initScan1(true)) {
						return false;
					}
					// check scan2
					if (!initScan2(false)) {
						return false;
					}
					b= scan2.stepManualScan();
					b&=!scan2.isAtEnd();
					if (b) {
						step=Step.UP2;
					} else {
						step=Step.TOP2;
						if (!scan1.isAtEnd()) {
							step=Step.UP1;
						} else if (scan3.isAtEnd()) {
							step=Step.TOP3;
						}
					}
					return true;
				} 
				
				if (
						step==Step.TOP2
						)
				{
					// reset scan1
					if (!initScan1(true)) {
						return false;
					}
					// reset scan2
					if (!initScan2(true)) {
						return false;
					}
					// check scan3
					if (!initScan3(false)) {
						return false;
					}
					b= scan3.stepManualScan();
					b&=!scan3.isAtEnd();
					if (b) {
						step=Step.UP3;
					} else {
						step=Step.TOP3;
						if (!scan1.isAtEnd()) {
							step=Step.UP1;
						} else if (!scan2.isAtEnd()) {
							step=Step.UP2;
						}

					}
					return true;
				}

				return false;
			}
			
			public boolean stepDown() {

				boolean b= initial();
				if (!b) {
					return b;
				}
				
				if (
						step==Step.READY || 
						step==Step.UP1 || step==Step.DOWN1 || step==Step.TOP1 || 
						step==Step.UP2 ||                     step==Step.TOP2 || 
						step==Step.UP3 ||                     step==Step.TOP3 
						) 
				{
					// reset scan 1, go directly to min
					initScan1(true);
					b=!scan1.isAtStart();
					if (b) {
						step=Step.DOWN1;
					} else {
						step=Step.BOTTOM1;
					}
					return true;
				}
				
				if (
						step==Step.BOTTOM1 || 
						step==Step.DOWN2
						) 
				{
					// check scan2
					if (!initScan2(false)) {
						return false;
					}
					b= scan2.stepManualScanInv();
					b&=!scan2.isAtStart();
					if (b) {
						step=Step.DOWN2;
					} else {
						step=Step.BOTTOM2;
					}
					return true;
				} 
				
				if (
						step==Step.BOTTOM2 || 
						step==Step.DOWN3
						) 
				{
					// check scan3
					if (!initScan3(false)) {
						return false;
					}
					b= scan3.stepManualScanInv();
					b&=!scan3.isAtStart();
					if (b) {
						step=Step.DOWN3;
					} else {
						step=Step.BOTTOM3;
					}
					return true;
				}

				return false;

			}

			public void coolDown() {

				boolean b= initial();
				if (!b) {
					return;
				}
				
				initScan1(true);
				initScan2(true);
				initScan3(true);

				b=!scan1.isAtStart();
				if (b) {
					step=Step.DOWN1;
				} else {
					step=Step.BOTTOM1;
				}
				b=!scan2.isAtStart();
				if (b) {
					step=Step.DOWN2;
				} else {
					step=Step.BOTTOM2;
				}
				b=!scan3.isAtStart();
				if (b) {
					step=Step.DOWN3;
				} else {
					step=Step.BOTTOM3;
				}
				
			}
			
			@Override
			public void notifyEnabled() {
				
				if (step!=Step.INITIAL) {
					step=Step.READY;
				}
				scan1.stopManualScan();
				scan2.stopManualScan();
				scan3.stopManualScan();
				
			}
		};

	}


}
