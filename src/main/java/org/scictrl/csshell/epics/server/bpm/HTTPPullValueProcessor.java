package org.scictrl.csshell.epics.server.bpm;

import java.lang.reflect.Array;
import java.net.MalformedURLException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.EPICSUtilities;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.bpm.BPMPullEngine.Data;
import org.scictrl.csshell.epics.server.processor.AbstractValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import gov.aps.jca.dbr.TimeStamp;


/**
 * <p>HTTPPullValueProcessor class.</p>
 *
 * @author igor@scictrl.com
 */
public class HTTPPullValueProcessor extends AbstractValueProcessor {

	/**
	 * Internal storage of main value.
	 */
	protected Object value;
	/**
	 * Taimestamp of value.
	 */
	protected TimeStamp timestamp;
	/**
	 * Timestamp of last value access.
	 */
	protected TimeStamp lastAccessTimestamp;
	private BPMPullEngine bpm;
	private int index=-1;
	private String register;
	private String referer;
	private String url;

	/**
	 * <p>Constructor for HTTPPullValueProcessor.</p>
	 */
	public HTTPPullValueProcessor() {
		super();
	}
	
	/** {@inheritDoc} */
	@Override
	public TimeStamp getTimestamp() {
		return timestamp;
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * This method is used by remote EPICS entity or other record within same Database.
	 * By definition calling setValue must always update value, because further processing
	 * must be triggered.
	 */
	@Override
	public void setValue(Object value) {
		if (value.getClass().isArray() && Array.getLength(value)>0) {
			value=Array.get(value, 0);
		}
		if (value instanceof Number) {
			int i= ((Number)value).intValue();
			try {
				bpm.writeData(index, i);
				_setValue(i, 0, true);
			} catch (Exception e) {
				log.error("Fail to set as '"+i+"', error: "+e.toString(), e);
			}
		} else {
			try {
				int l= Integer.valueOf(value.toString());
				bpm.writeData(index, l);
				_setValue(l, 0, true);
			} catch (Exception e) {
				log.error("Fail to set as long '"+value+"', error: "+e.toString(), e);
			}
		}
	}

	/**
	 * Internal set: sets new value to this processor. It checks if it is fixed, it never sets fixed value.
	 * Fires value update event only if value was changes and notify is true.
	 * If force is true, then updates value and fires notify, if notify is true, even if value was not changed.
	 * If the value is not an array it tries to convert it to a single element array.
	 * Updates timestamp and count on record when changing value.
	 * If required fires notify event.
	 *
	 * @param value value to be set
	 * @param notify if <code>true</code> fire notify event if value was change, <code>false</code> suppresses events
	 * @return true if value was updated, regardless if based on difference or force
	 * @param timestamp a long
	 */
	protected synchronized boolean _setValue(Number value, long timestamp, boolean notify) {
		this.lastAccessTimestamp= new TimeStamp();

		if (type.isDOUBLE()) {
			if (value==null) {
				if (Array.getDouble(this.value, 0)==Double.NaN) {
					return false;
				}
				Array.setDouble(this.value,0,Double.NaN);
				record.updateAlarm(Severity.INVALID_ALARM, Status.LINK_ALARM, notify);
			} else {
				if (Array.getDouble(this.value, 0)==value.doubleValue()) {
					return false;
				}
				Array.setDouble(this.value,0,value.doubleValue());
				record.updateAlarm(Severity.NO_ALARM, Status.NO_ALARM, notify);
			}
		} else {
			if (value==null) {
				if (type.isENUM()) {
					if (Array.getShort(this.value, 0)==-1) {
						return false;
					}
					Array.setShort(this.value,0,(short)-1);
				} else {
					if (Array.getInt(this.value, 0)==-1) {
						return false;
					}
					Array.setInt(this.value,0,-1);
				}
				record.updateAlarm(Severity.INVALID_ALARM, Status.LINK_ALARM, notify);
			} else {
				if (type.isENUM()) {
					if (Array.getShort(this.value, 0)==value.shortValue()) {
						return false;
					}
					Array.setShort(this.value,0,value.shortValue());
				} else {
					if (Array.getInt(this.value, 0)==value.intValue()) {
						return false;
					}
					Array.setInt(this.value,0,value.intValue());
				}
				record.updateAlarm(Severity.NO_ALARM, Status.NO_ALARM, notify);
			}
		}
		
		if (timestamp==0) {
			this.timestamp= lastAccessTimestamp;
		} else {
			this.timestamp= EPICSUtilities.toTimeStamp(timestamp);
		}

		
		if (notify) {
			record.fireValueChange();
		}
		
		return true;
	}
	
	/**
	 * <p>getValueAsInt.</p>
	 *
	 * @return a int
	 */
	public int getValueAsInt() {
		return Array.getInt(value, 0);
	}

	/**
	 * <p>getValueAsDouble.</p>
	 *
	 * @return a double
	 */
	public double getValueAsDouble() {
		return Array.getDouble(value, 0);
	}

	/** {@inheritDoc} */
	@Override
	public Object getValue() {
		return value;
	}

	/**
	 * Returns timestamp of last attempt to change value, regardless if value was actually changed and notify event fired.
	 *
	 * @return timestamp of last attempt to change value regardless of success
	 */
	public TimeStamp getLastAccessTimestamp() {
		return lastAccessTimestamp;
	}

	/** {@inheritDoc} */
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		if (trigger==0) {
			trigger=1000;
		}
		
		record.setCount(1);
		record.updateAlarm(Severity.INVALID_ALARM, Status.UDF_ALARM, false);
		
		register= config.getString("register");
		
		if (register==null) {
			throw new IllegalArgumentException("Record '"+record.getName()+"' is missing the register definition.");
		}
		
		register=register.trim();
		String t= register.substring(0, 1).toUpperCase();
		if (t.equals("F")||t.equals("C")) {
			if (record.getType()==DBRType.ENUM) {
				type=DBRType.ENUM;
				value= new short[]{-1};
			} else {
				type=DBRType.INT;
				value= new int[]{-1};
			}
		} else {
			type=DBRType.DOUBLE;
			value= new double[]{Double.NaN};
		}
		
		referer= config.getString("referer");
		
		if (referer==null) {
			throw new IllegalArgumentException("Record '"+record.getName()+"' is missing the referer definition.");
		}
		
		url= config.getString("url");
		
		if (url==null) {
			throw new IllegalArgumentException("Record '"+record.getName()+"' is missing the referer definition.");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();

		try {
			bpm= BPMPullEngine.getIntance(url,referer);
			index=bpm.registerRequest(register);
		} catch (MalformedURLException e) {
			log.fatal("Failed to initialize HTTP server connection for record '"+getName()+"' while: "+e.toString(), e);
		}
		
		
	}
	
	/** {@inheritDoc} */
	@Override
	public void process() {
		if (index>-1) {
			try {
				Data data= bpm.readData();
				Number value= data.get(index);
				long time= data.timestamp;
				_setValue(value, time, true);
			} catch (Exception e) {
				log.debug("Reading value failed", e);
				_setValue(null, 0, true);
			}
		}
	}
	
}
