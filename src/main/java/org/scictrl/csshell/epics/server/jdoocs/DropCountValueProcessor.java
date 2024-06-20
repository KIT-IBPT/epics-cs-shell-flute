/**
 * 
 */
package org.scictrl.csshell.epics.server.jdoocs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.processor.MemoryValueProcessor;

import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * <p>DropCountValueProcessor class, calculates LLRF1 breakdown incidences, a weak workaround for missing diagnostic.</p>
 *
 * @deprecated NOT IN USE, was part of specific LLRF1 routine, not in use since LRF1 does not exist any more.
 * 
 * @author igor@scictrl.com
 */
public class DropCountValueProcessor extends MemoryValueProcessor implements PropertyChangeListener {

	private double treshold_level;
	private double treshold_drop;
	private String inputPV;
	private ValueLinks input;
	private long interval;
	private LinkedList<Long> drops;
	private boolean rate1m;
	private double last=0.0;

	/**
	 * <p>Constructor for DropCountValueProcessor.</p>
	 */
	public DropCountValueProcessor() {
	}
	
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		treshold_level= config.getDouble("tresholdLevel", 1.0);
		treshold_drop= config.getDouble("tresholdDrop", 20.0);
		
		long s= config.getLong("interval", 60);
		
		interval= (long) (s*1000);
		
		rate1m= config.getBoolean("rate1m", false);

		inputPV= config.getString("inputPV");
		
		if (inputPV==null || inputPV.length()==0) {
			log.error("Configuration for '"+record.getName()+"' has no inputPV parameter!");
		}
		
		input= new ValueLinks(record.getName(), new String[]{inputPV}, this, Record.PROPERTY_VALUE);

		drops= new LinkedList<Long>();
		
		if (trigger == 0) {
			trigger = 1000;
		}
		
		if (rate1m) {
			type = DBRType.DOUBLE;
		} else {
			type = DBRType.INT;
		}
		
	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		
		input.activate(record.getDatabase());
	}
	
	/** {@inheritDoc} */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName()==Record.PROPERTY_VALUE) {

			if (input==null) {
				return;
			}
			if (input.isInvalid()) {
				record.updateAlarm(Severity.INVALID_ALARM,Status.LINK_ALARM,true);
				return;
			}
			if (!input.isReady()) {
				return;
			}
			
			double[] val= input.consumeAsDoubles();
			
			
			if (val==null || val.length!=1) { 
				return;
			}
			
			double v=val[0]; 
			
			if (v<treshold_level && (last-v)>treshold_drop) {
				drops.add(System.currentTimeMillis());
				countDrops();
				record.updateNoAlarm();
			}
			
			last=v;
			
		}

	}
	
	private synchronized void countDrops() {
		
		ListIterator<Long> it = drops.listIterator();
		
		int c=0;
		long i= System.currentTimeMillis()-interval;
		
		while (it.hasNext()) {
			Long t= it.next();
			if (t<i) {
				it.remove();
			} else {
				c++;
			}
		}
		
		//System.out.println("C "+c+" "+drops.size());
		if(rate1m && interval>=60000) {
			double d= (double)c/((double)interval/(double)60000);
			_setValue(d, null,null,true);
		} else {
			_setValue(c, null,null,true);
		}
	}
	
	/** {@inheritDoc} */
	@Override
	public void process() {
		super.process();
		
		countDrops();
	}

}
