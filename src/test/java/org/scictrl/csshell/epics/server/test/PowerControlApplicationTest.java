/**
 * 
 */
package org.scictrl.csshell.epics.server.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.server.test.AbstractConfiguredServerTest;

/**
 * Tests for PowerControlApplication.
 *
 * @author igor@kriznar.com
 */
public class PowerControlApplicationTest extends AbstractConfiguredServerTest {

	/**
	 * New instance.
	 */
	public PowerControlApplicationTest() {
		pvCount+=3+20;
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
		
		Record rPow= server.getDatabase().getRecord("A:TEST:Power");
		Record rSwr1= server.getDatabase().getRecord("A:TEST:Swr1");
		Record rSwr2= server.getDatabase().getRecord("A:TEST:Swr2");
		
		Record rPowSet= server.getDatabase().getRecord("A:TEST:Power:Set");
		Record rPowSetGet= server.getDatabase().getRecord("A:TEST:Power:Set:Get");
		Record rSwrLimWg= server.getDatabase().getRecord("A:TEST:Power:SWR:WG:Limit");
		Record rSwrLimKly= server.getDatabase().getRecord("A:TEST:Power:SWR:Kly:Limit");

		Record rCmdOn= server.getDatabase().getRecord("A:TEST:Power:Cmd:On");
		Record rCmdOff= server.getDatabase().getRecord("A:TEST:Power:Cmd:Off");
		Record rCmdSync= server.getDatabase().getRecord("A:TEST:Power:Set:Sync");
		
		Record rStaOn= server.getDatabase().getRecord("A:TEST:Power:Status:On");
		Record rStaLock= server.getDatabase().getRecord("A:TEST:Power:Status:Locked");
		Record rStaLockWg= server.getDatabase().getRecord("A:TEST:Power:Status:WG:Locked");
		Record rStaLockKly= server.getDatabase().getRecord("A:TEST:Power:Status:Kly:Locked");
		Record rStaScan= server.getDatabase().getRecord("A:TEST:Power:Status:Scanning");

		
		assertNotNull(rPow);
		assertNotNull(rSwr1);
		assertNotNull(rSwr2);
		assertNotNull(rPowSet);
		assertNotNull(rPowSetGet);
		assertNotNull(rSwrLimWg);
		assertNotNull(rSwrLimKly);
		assertNotNull(rCmdOn);
		assertNotNull(rCmdOff);
		assertNotNull(rCmdSync);
		assertNotNull(rStaOn);
		assertNotNull(rStaLock);
		assertNotNull(rStaLockWg);
		assertNotNull(rStaLockKly);
		assertNotNull(rStaScan);
		
		rSwrLimWg.setValue(1.0);
		rSwrLimKly.setValue(1.0);

		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
		
		rPowSet.setValue(1.0);

		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
		
		rCmdOn.write(1);
		wait(0.1);

		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(true, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(true, rStaScan.getValueAsBoolean());
		
		wait(1.1);

		assertEquals(1.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(true, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());

		rPowSet.write(3.0);
		wait(5.0);

		assertEquals(3.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(3.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(3.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(true, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
		
		rCmdOff.write(1);
		wait(0.1);
		rPowSet.write(1.0);
		wait(1.1);

		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
		
		rCmdOn.write(1);
		wait(1.1);

		assertEquals(1.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(true, rStaOn.getValueAsBoolean());
		assertEquals(false, rStaLock.getValueAsBoolean());
		assertEquals(false, rStaLockWg.getValueAsBoolean());
		assertEquals(false, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());

		rSwr1.setValue(1.1);
		rSwr2.setValue(1.1);
		wait(1.1);
		
		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(true, rStaLock.getValueAsBoolean());
		assertEquals(true, rStaLockWg.getValueAsBoolean());
		assertEquals(true, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
		
		rPowSet.write(2.0);
		wait(1.1);
		
		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(2.0, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(true, rStaLock.getValueAsBoolean());
		assertEquals(true, rStaLockWg.getValueAsBoolean());
		assertEquals(true, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());

		rPowSet.setValue(0.5);
		wait(1.1);
		
		assertEquals(0.0, rPow.getValueAsDouble(), 0.0001);
		assertEquals(0.5, rPowSet.getValueAsDouble(), 0.0001);
		assertEquals(0.0, rPowSetGet.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimWg.getValueAsDouble(), 0.0001);
		assertEquals(1.0, rSwrLimKly.getValueAsDouble(), 0.0001);
		assertEquals(false, rStaOn.getValueAsBoolean());
		assertEquals(true, rStaLock.getValueAsBoolean());
		assertEquals(true, rStaLockWg.getValueAsBoolean());
		assertEquals(true, rStaLockKly.getValueAsBoolean());
		assertEquals(false, rStaScan.getValueAsBoolean());
	}

}
