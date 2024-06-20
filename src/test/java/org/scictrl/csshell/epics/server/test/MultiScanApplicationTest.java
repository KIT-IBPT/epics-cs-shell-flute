/**
 * 
 */
package org.scictrl.csshell.epics.server.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

import org.junit.Test;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.application.MultiScanApplication;
import org.scictrl.csshell.server.test.AbstractConfiguredServerTest;

import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;

/**
 *
 * Tests for MultiScanApplication
 *
 * @author igor@kriznar.com
 */
public class MultiScanApplicationTest extends AbstractConfiguredServerTest {

	/**
	 * New instance.
	 */
	public MultiScanApplicationTest() {
		pvCount+=3+3+16+3*19;
	}
	
	/**
	 * <p>getConfigDir.</p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	protected String getConfigDir() {
		return "src/test/config/";
	}

	/**
	 * Test.
	 */
	@Test
	public void test() {
		
		Record rSet1= server.getDatabase().getRecord("A:TEST:Set1");
		Record rSet2= server.getDatabase().getRecord("A:TEST:Set2");
		Record rSet3= server.getDatabase().getRecord("A:TEST:Set3");

		Record rMeas1= server.getDatabase().getRecord("A:TEST:Meas1");
		Record rMeas2= server.getDatabase().getRecord("A:TEST:Meas2");
		Record rMeas3= server.getDatabase().getRecord("A:TEST:Meas3");

		Record rMsMeasLast= server.getDatabase().getRecord("A:TEST:MultiScan:Meas:Last");
		Record rMsMeasTable= server.getDatabase().getRecord("A:TEST:MultiScan:Meas:Table");
		Record rMsMeasPVs= server.getDatabase().getRecord("A:TEST:MultiScan:Meas:PVs");

		Record rMsCmdStop= server.getDatabase().getRecord("A:TEST:MultiScan:Cmd:Stop");
		Record rMsCmdStart= server.getDatabase().getRecord("A:TEST:MultiScan:Cmd:Start");
		Record rMsCmdPause= server.getDatabase().getRecord("A:TEST:MultiScan:Cmd:Pause");
		
		Record rMsStatus= server.getDatabase().getRecord("A:TEST:MultiScan:Status");
		Record rMsStatusScanning= server.getDatabase().getRecord("A:TEST:MultiScan:Status:Scanning");
		Record rMsStatusProgress= server.getDatabase().getRecord("A:TEST:MultiScan:Status:Progress");
		Record rMsStatusRemaining= server.getDatabase().getRecord("A:TEST:MultiScan:Status:Remaining");
		Record rMsStatusDataFile= server.getDatabase().getRecord("A:TEST:MultiScan:Status:DataFile");
		Record rMsWait= server.getDatabase().getRecord("A:TEST:MultiScan:Wait");

		Record rMsScan1Pv= server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:SetpointPv");
		Record rMsScan2Pv= server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:SetpointPv");
		Record rMsScan3Pv= server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:SetpointPv");
		
		assertNotNull(rSet1);
		assertNotNull(rSet2);
		assertNotNull(rSet3);
		assertNotNull(rMeas1);
		assertNotNull(rMeas2);
		assertNotNull(rMeas3);
		assertNotNull(rMsMeasLast);
		assertNotNull(rMsMeasTable);
		assertNotNull(rMsMeasPVs);
		assertNotNull(rMsCmdStop);
		assertNotNull(rMsCmdStart);
		assertNotNull(rMsCmdPause);
		assertNotNull(rMsStatus);
		assertNotNull(rMsStatusScanning);
		assertNotNull(rMsStatusProgress);
		assertNotNull(rMsStatusRemaining);
		assertNotNull(rMsStatusDataFile);
		assertNotNull(rMsWait);
		assertNotNull(rMsScan1Pv);
		assertNotNull(rMsScan2Pv);
		assertNotNull(rMsScan3Pv);

		
		MultiScanApplication scan= (MultiScanApplication) rMsStatus.getApplication();
		
		/*
		 * Iterator<Record> it=server.getDatabase().recordsIterator(); while
		 * (it.hasNext()) { System.out.println(it.next().getName()); }
		 */
		
		assertNotNull(scan);
		
		pause(100);
		
		checkRecord(rSet1);
		checkRecord(rSet2);
		checkRecord(rSet3);
		checkRecord(rMeas1);
		checkRecord(rMeas1);
		checkRecord(rMsMeasLast);
		checkRecord(rMsMeasTable);
		checkRecord(rMsMeasPVs);
		checkRecord(rMsCmdStop);
		checkRecord(rMsCmdStart);
		checkRecord(rMsCmdPause);
		checkRecord(rMsStatus);
		checkRecord(rMsStatusScanning);
		checkRecord(rMsStatusProgress);
		checkRecord(rMsStatusRemaining);
		checkRecord(rMsStatusDataFile);
		checkRecord(rMsWait);
		
		assertEquals(1.0, rMsWait.getValueAsDouble(), 0.000001);
		rMsWait.setValue(0.1);
		assertEquals(0.1, rMsWait.getValueAsDouble(), 0.000001);

		String fs= scan.getDataDir();
		assertNotNull(fs);
		
		String s= rMsMeasPVs.getValueAsString();
		assertNotNull(s);
		assertEquals("A:TEST:Meas1,A:TEST:Meas2", s);
		
		String sMeas="A:TEST:Meas1,A:TEST:Meas2,A:TEST:Meas3";
		
		rMsMeasPVs.setValueAsString(sMeas);
		s= rMsMeasPVs.getValueAsString();
		assertNotNull(s);
		assertEquals(sMeas, s);
		
		assertEquals(0.0, rMeas1.getValueAsDouble(),0.000001);
		assertEquals(0.0, rMeas2.getValueAsDouble(),0.000001);
		assertEquals(0.0, rMeas3.getValueAsDouble(),0.000001);

		rMeas1.setValue(1.1);
		rMeas2.setValue(1.2);
		rMeas3.setValue(1.3);
		
		assertEquals(1.1, rMeas1.getValueAsDouble(),0.000001);
		assertEquals(1.2, rMeas2.getValueAsDouble(),0.000001);
		assertEquals(1.3, rMeas3.getValueAsDouble(),0.000001);
		
		assertEquals(Severity.NO_ALARM, rMeas1.getAlarmSeverity());
		assertEquals(Status.NO_ALARM, rMeas1.getAlarmStatus());

		s= rMsScan1Pv.getValueAsString();
		assertNotNull(s);
		assertEquals("", s);
		
		s= rMsScan2Pv.getValueAsString();
		assertNotNull(s);
		assertEquals("", s);

		s= rMsScan3Pv.getValueAsString();
		assertNotNull(s);
		assertEquals("", s);

		rMsScan1Pv.write(rSet1.getName());
		s= rMsScan1Pv.getValueAsString();
		assertEquals(rSet1.getName(), s);
		
		rMsScan2Pv.write(rSet2.getName());
		s= rMsScan2Pv.getValueAsString();
		assertNotNull(s);
		assertEquals(rSet2.getName(), s);

		rMsScan3Pv.write(rSet3.getName());
		s= rMsScan3Pv.getValueAsString();
		assertNotNull(s);
		assertEquals(rSet3.getName(), s);
		
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:Start").setValue(0.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:End").setValue(1.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:Step").setValue(0.5);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:Start").setValue(0.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:End").setValue(1.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:Step").setValue(0.5);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:Start").setValue(0.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:End").setValue(1.0);
		server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:Step").setValue(0.5);
		
		assertEquals(0.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:Start").getValueAsDouble(), 0.00001);
		assertEquals(1.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:End").getValueAsDouble(), 0.00001);
		assertEquals(0.5, server.getDatabase().getRecord("A:TEST:MultiScan:Scan1:Step").getValueAsDouble(), 0.00001);
		assertEquals(0.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:Start").getValueAsDouble(), 0.00001);
		assertEquals(1.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:End").getValueAsDouble(), 0.00001);
		assertEquals(0.5, server.getDatabase().getRecord("A:TEST:MultiScan:Scan2:Step").getValueAsDouble(), 0.00001);
		assertEquals(0.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:Start").getValueAsDouble(), 0.00001);
		assertEquals(1.0, server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:End").getValueAsDouble(), 0.00001);
		assertEquals(0.5, server.getDatabase().getRecord("A:TEST:MultiScan:Scan3:Step").getValueAsDouble(), 0.00001);
		
		assertTrue(!server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan1").getValueAsBoolean());
		assertTrue(!server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan2").getValueAsBoolean());
		assertTrue(!server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan3").getValueAsBoolean());

		rSet1.addPropertyChangeListener(new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("1> "+evt.getNewValue());
			}
		});
		rSet2.addPropertyChangeListener(new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("2> "+evt.getNewValue());
			}
		});
		rSet3.addPropertyChangeListener(new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("3> "+evt.getNewValue());
			}
		});
		
		
		assertEquals(0.0,rSet1.getValueAsDouble(),0.000001);
		assertEquals(0.0,rSet2.getValueAsDouble(),0.000001);
		assertEquals(0.0,rSet3.getValueAsDouble(),0.000001);
		
		assertTrue(!rMsStatusScanning.getValueAsBoolean());
		
		// for this scan all sacns are disabled, there should be no value change
		rMsCmdStart.write(1);

		assertTrue(rMsStatusScanning.getValueAsBoolean());

		wait(1.0);
		
		assertTrue(!rMsStatusScanning.getValueAsBoolean());

		assertEquals(0.0,rSet1.getValueAsDouble(),0.000001);
		assertEquals(0.0,rSet2.getValueAsDouble(),0.000001);
		assertEquals(0.0,rSet3.getValueAsDouble(),0.000001);
		
		double[] dd= rMsMeasLast.getValueAsDoubleArray();
		System.out.println(Arrays.toString(dd));
		assertNotNull(dd);
		assertEquals(7, dd.length);
		assertArrayEquals(new double[]{1.0,0.0,0.0,0.0,1.1,1.2,1.3}, dd);
		
		// do some actual scanning with some sets
		
		server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan1").setValue(1);
		server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan2").setValue(1);
		server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan3").setValue(1);
		
		assertTrue(server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan1").getValueAsBoolean());
		assertTrue(server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan2").getValueAsBoolean());
		assertTrue(server.getDatabase().getRecord("A:TEST:MultiScan:Opt:Scan3").getValueAsBoolean());
		
		assertTrue(!rMsStatusScanning.getValueAsBoolean());
		
		// for this scan all sacns are disabled, there should be no value change
		rMsCmdStart.write(1);

		assertTrue(rMsStatusScanning.getValueAsBoolean());

		wait(20.0);
		
		assertTrue(!rMsStatusScanning.getValueAsBoolean());

		dd= rMsMeasLast.getValueAsDoubleArray();
		System.out.println(Arrays.toString(dd));
		assertNotNull(dd);
		assertEquals(7, dd.length);
		assertArrayEquals(new double[]{1.0,1.0,1.0,1.0,1.1,1.2,1.3}, dd);

	}

}
