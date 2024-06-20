/**
 * 
 */
package org.scictrl.csshell.epics.server.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.processor.ModeNotifyProcesssor;
import org.scictrl.csshell.server.test.AbstractConfiguredServerTest;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 * Tests for ModeNotifyProcessor.
 *
 * @author igor@kriznar.com
 */
public class ModeNotifyProcessorTest extends AbstractConfiguredServerTest {

	/**
	 * New isntace.
	 */
	public ModeNotifyProcessorTest() {
		pvCount += 3;
	}
	
	/** {@inheritDoc} */
	@Override
	protected String getConfigDir() {
		return "src/test/config/";
	}

	/**
	 * Test.
	 */
	@Test
	public void test() {

		Record rec = server.getDatabase().getRecord("A:TEST:Operation:01:MailNotify");
		ModeNotifyProcesssor proc = (ModeNotifyProcesssor) rec.getProcessor();

		assertNotNull(proc);
		assertEquals(0, rec.getValueAsInt());
		assertEquals(Severity.NO_ALARM, rec.getAlarmSeverity());
		assertEquals(Status.NO_ALARM, rec.getAlarmStatus());
		
		Record modeR = server.getDatabase().getRecord("A:TEST:Operation:01:Mode");
		Record beamModeR = server.getDatabase().getRecord("A:TEST:Operation:01:BeamMode");

		assertEquals(0, modeR.getValueAsInt());
		assertEquals(0, beamModeR.getValueAsInt());

		modeR.setValue(3);
		beamModeR.setValue(1);
		
		assertEquals(3, modeR.getValueAsInt());
		assertEquals(1, beamModeR.getValueAsInt());
		
		
	}

}
