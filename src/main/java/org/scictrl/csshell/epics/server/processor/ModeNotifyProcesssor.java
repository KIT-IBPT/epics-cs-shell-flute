/**
 * 
 */
package org.scictrl.csshell.epics.server.processor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.Properties;

import javax.mail.MessagingException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.tree.DefaultExpressionEngine;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.mail.MailHandler;

import gov.aps.jca.dbr.DBRType;

/**
 * <p>ModeNotifyProcesssor forwards mail to predefined users when FLUTe mode is changed.</p>
 *
 * <p>Status: ALPHA, not finished, not tested</p>
 *
 * @author igor@scictrl.com
 */
public class ModeNotifyProcesssor extends MemoryValueProcessor implements PropertyChangeListener {

	private String modePV;
	private String beamModePV;
	private ValueLinks links;
	private MailHandler mail;

	/**
	 * <p>Constructor for ModeNotifyProcesssor.</p>
	 */
	public ModeNotifyProcesssor() {
		super();
		
		this.type= DBRType.BYTE;
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(Record record, HierarchicalConfiguration config) {
		super.configure(record, config);
		
		modePV= config.getString("inputs.mode");
		beamModePV= config.getString("inputs.beamMode");
		
		links= new ValueLinks("modes", new String[]{modePV,beamModePV}, this, Record.PROPERTY_VALUE); 
		
		SubnodeConfiguration sub= config.configurationAt("mail");
		
		DefaultExpressionEngine e= new DefaultExpressionEngine();
		e.setEscapedDelimiter(".");
		e.setPropertyDelimiter("");
		sub.setExpressionEngine(e);
		
		Properties p= new Properties();
		
		
		Iterator<String> it= sub.getKeys();
		while (it.hasNext()) {
			String key = it.next();
			p.setProperty(key, sub.getString(key));
		}
		
		//Properties p= ConfigurationConverter.getProperties(sub);
		
		mail= new MailHandler(p);
	}
	
	/** {@inheritDoc} */
	@Override
	public void activate() {
		super.activate();
		links.activate(record.getDatabase());
	}
	
	/** {@inheritDoc} */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		
		if (!getValueAsBoolean()) {
			return;
		}
		
		if (links.isReady() && !links.isInvalid()) {
			
			ValueHolder[] vh= links.consume();
			
			long mode= vh[0].longValue();
			long beamMode= vh[1].longValue();
			
			if (mode==3 && beamMode!=0) {
				String modeS= links.getMetaData(0).getState((int)mode);
				String beamModeS= links.getMetaData(1).getState((int)beamMode);
				
				mail.getProperties().setProperty("mode", modeS);
				mail.getProperties().setProperty("beamMode", beamModeS);
				
				try {
					mail.sendMail(null);
				} catch (MessagingException e) {
					log.error("Failed to send mail: "+e, e);
				}
			}
			
			
		}
		
	}

}
