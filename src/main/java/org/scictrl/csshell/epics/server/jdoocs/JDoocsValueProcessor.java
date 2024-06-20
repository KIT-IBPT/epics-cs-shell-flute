/**
 * 
 */
package org.scictrl.csshell.epics.server.jdoocs;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scictrl.csshell.epics.EPICSUtilities;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import ttf.doocs.clnt.EqAdr;
import ttf.doocs.clnt.EqCall;
import ttf.doocs.clnt.EqData;
import ttf.doocs.clnt.eq_rpc;

/**
 * <p>JDoocsValueProcessor class, two directional gateway that translates JDOOCS control system remote property values to EPICS PV values. 
 * Connects to JDOCS remote channel and exports corresponding EPICS channel with configured PV.
 * EPICS PV can be read-only or writable.</p>
 * 
 * <p>ENSHOST property value must be set either as system property on level of system environment or system properties in order to find JDOOCS values.</p>
 *
 * @author igor@scictrl.com
 */
public class JDoocsValueProcessor extends MemoryValueProcessor {

	
	{
		//log.info("ENV:ENSHOST "+System.getenv().get("ENSHOST"));
		//log.info("SYS:ENSHOST "+System.getProperty("ENSHOST"));

		//ENSHOST=flute-pc-mtca01.anka-flute.kit.edu
		if (!System.getenv().containsKey("ENSHOST")) {
			
			if (!System.getProperties().containsKey("ENSHOST")) {
				
//				System.getenv().put("ENSHOST", "flute-pc-mtca01.anka-flute.kit.edu");
				System.getProperties().put("ENSHOST", "ldap://flute-pc-mtca01.anka-flute.kit.edu");
				log.info("SYS:ENSHOST "+System.getProperty("ENSHOST"));
			}
			
		}
		
		//log.info("ENV:ENSHOST "+System.getenv().get("ENSHOST"));
		//log.info("SYS:ENSHOST "+System.getProperty("ENSHOST"));
		
	}
	
	private static ThreadPoolExecutor executor;
	private static int threadPoolSize = 1;
	private static Logger log= LogManager.getLogger(JDoocsValueProcessor.class);	

	private static synchronized ThreadPoolExecutor getExecutor() {
		if (executor == null) {
			int size= Math.max(threadPoolSize , 1);
			int coresize= Math.max(threadPoolSize/2, 1);
			executor = new ThreadPoolExecutor(coresize, size, 60L, TimeUnit.SECONDS,
		              new LinkedBlockingQueue<Runnable>(),Executors.defaultThreadFactory()){
				@Override
				protected void afterExecute(Runnable r, Throwable t) {
					super.afterExecute(r, t);
					if (t != null) {
						t.printStackTrace();
					}
				}
			};
		}
		return executor;
	}

	/**
	 * <p>toErrorDesc.</p>
	 *
	 * @param data a {@link ttf.doocs.clnt.EqData} object
	 * @return a {@link java.lang.String} object
	 */
	static final public String toErrorDesc(EqData data) {
		StringBuilder sb= new StringBuilder(256);
		sb.append("{DOOCS error: '");
		sb.append(data.error());
		sb.append("' desc: '");
		sb.append(data.get_string());
		sb.append("'}");
		return sb.toString();
	}

	private final class ValueReader implements Runnable {
		
		String dName;
		private EqAdr adr;
		private EqData dataIn;
		private EqData dataOut;
		private EqCall call;
		private long start;
		
		boolean aborted=false;
		boolean busy=false;

		public ValueReader(String dName) {
			this.dName = dName;
		}
		
		public void abort() {
			aborted=true;
		}
		
		public void rearm() {
			busy=true;
		}
		
		@Override
		public void run() {
			
			if (aborted) {
				return;
			}
			
			if (adr==null) {
				adr = new EqAdr(dName);
			}
			if (dataIn==null) {
				dataIn = new EqData();
			}
			if (dataOut==null) {
				dataOut = new EqData();
			}
			if (call==null) {
				call = new EqCall();
			}

			start= System.currentTimeMillis();
			
			try {
				call.get(adr, dataOut, dataIn);
			} catch (Throwable t) {
				log.error("Failed with error "+t+" while reading value from '"+dName+"'.",t);
				valueUpdateFail("Failed with error "+t+" while reading value from '"+dName+"'.");
				call=null;
			}
			
			busy=false;
			
			if (dataIn.error() != 0) {
				log.error("Returned with "+toErrorDesc(dataIn)+" while reading value from '"+dName+"'.");
				valueUpdateFail("Returned with "+toErrorDesc(dataIn)+" while reading value from '"+dName+"'.");
				call=null;
			}

			int c= dataIn.array_length();
			if (c==0) {
				double value = dataIn.get_double();
				valueUpdate(value);
			} else {
				float[] value = dataIn.get_float_array();
				getRecord().setCount(c);
				valueUpdate(value);
			}
		}
		
		public long getStart() {
			return start;
		}
		
		public boolean isBusy() {
			return busy;
		}
	}

	private final class ValueWriter implements Runnable {
		
		String dName;
		private EqAdr adr;
		private EqData dataIn;
		private EqData dataOut;
		private EqCall call;
		
		private double value;

		public ValueWriter(String dName,double value) {
			this.dName = dName;
			this.value=value;
		}
		
		@Override
		public void run() {
			
			if (adr==null) {
				adr = new EqAdr(dName);
			}
			if (dataIn==null) {
				dataIn = new EqData();
			}
			if (dataOut==null) {
				dataOut = new EqData();
			}
			if (call==null) {
				call = new EqCall();
			}

			dataOut.set_type(eq_rpc.DATA_DOUBLE);
			dataOut.set(value);
			
			try {
				call.set(adr, dataOut, dataIn);
			} catch (Throwable t) {
				log.error("Error "+t+" while writing value to '"+dName+"'.",t);
				valueUpdateFail("Error "+t+" while writing value to '"+dName+"'.");
				call=null;
			}
			
			if (dataIn.error() != 0) {
				log.error("Remote error "+toErrorDesc(dataIn)+" returned while writing value to '"+dName+"'.");
				valueUpdateFail("Remote error "+toErrorDesc(dataIn)+" returned while writing value to '"+dName+"'.");
				call=null;
			}
		}
		
	}

	private String dName;
	
	private ValueReader reader;
	private boolean writable;
	private boolean demo=false;

	/**
	 * <p>Constructor for JDoocsValueProcessor.</p>
	 */
	public JDoocsValueProcessor() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		dName= config.getString("link");
		
		if (dName==null) {
			throw new IllegalArgumentException("Record '"+record.getName()+"' is missing 'link' config for DOOCS property!");
		}
		
		log.info("["+getName()+"] linked to '"+dName+"'");
		
		writable = config.getBoolean("writable",false);
		record.setWritable(writable);

		demo = config.getBoolean("demo",false);
		
		if (demo) {
			log.info("["+getName()+"] DEMO mode, no remote call to DOOCS are made!");
		}

	}
	
	/**
	 * <p>valueUpdate.</p>
	 *
	 * @param value a double
	 */
	public void valueUpdate(double value) {
		
		_setValue(value,Severity.NO_ALARM,Status.NO_ALARM, true);
		
	}

	/**
	 * <p>valueUpdate.</p>
	 *
	 * @param value an array of {@link float} objects
	 */
	public void valueUpdate(float[] value) {
		
		_setValue(value,Severity.NO_ALARM,Status.NO_ALARM, true);
		
	}

	/**
	 * <p>valueUpdateFail.</p>
	 *
	 * @param string a {@link java.lang.String} object
	 */
	public void valueUpdateFail(String string) {
		record.updateAlarm(Severity.MAJOR_ALARM, Status.LINK_ALARM);
	}

	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
	}
	
	/** {@inheritDoc} */
	@Override
	public synchronized void process() {
		super.process();
		
		if (demo || !getRecord().getDatabase().getServer().isActive()) {
			return;
		}
		
		if (reader!=null && reader.isBusy()) {
			if (reader.getStart()>0 && System.currentTimeMillis()-reader.getStart()>getTrigger()*10) {
				reader.abort();
				//System.out.println(System.currentTimeMillis()+" "+reader.getStart()+" "+getTrigger());
				log.error("["+getName()+"] request timeout");
				reader=null;
			} else {
				return;
			}
		} 
		
		if (reader==null) {
			reader= new ValueReader(dName);
		}
		
		reader.rearm();
		getExecutor().submit(reader);

	}
	
	/** {@inheritDoc} */
	@Override
	public void setValue(Object value) {
		super.setValue(value);
		
		if (!demo && writable) {
			
			double d= EPICSUtilities.toDouble(value);
			//System.out.println("SET "+d);
			
			ValueWriter vw= new ValueWriter(dName, d);
			getExecutor().execute(vw);
		}
	}
	
}
