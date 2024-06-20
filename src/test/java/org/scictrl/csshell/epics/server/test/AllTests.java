package org.scictrl.csshell.epics.server.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Suite with all tests in this package.
 *
 * @author igor@scictrl.com
 */
@RunWith(Suite.class)
@SuiteClasses({ MultiScanApplicationTest.class, PowerControlApplicationTest.class, RateCalculatorTest.class})
public class AllTests {
	
	private AllTests() {
	}
}
