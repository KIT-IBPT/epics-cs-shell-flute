/**
 * 
 */
package org.scictrl.csshell.epics.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scictrl.csshell.epics.server.application.PowerInterlockApplication.RateCalculator;

/**
 *
 */
class RateCalculatorTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	void tearDown() throws Exception {
	}

	/**
	 * Test.
	 *
	 * @throws java.lang.InterruptedException if any.
	 */
	@Test
	public void test() throws InterruptedException {
		
		double prec=0.01;
		
		RateCalculator c= new RateCalculator();
		
		c.setWindow(500);
		
		assertEquals(500,c.getWindow());
		assertEquals(0.0,c.getRate(),0.000001);
		
		c.calculate();
		
		assertEquals(0.0,c.getRate(),0.000001);
		
		c.report(true);
		c.calculate();
		assertEquals(0.00001,c.getRate(),0.005);
		
		synchronized (this) {wait(100);}
		c.report(true);
		c.calculate();
		assertEquals(0.2,c.getRate(),prec);

		synchronized (this) {wait(200);}
		c.report(true);
		c.calculate();
		assertEquals(0.6,c.getRate(),prec);

		synchronized (this) {wait(200);}
		c.report(true);
		c.calculate();
		assertEquals(1.0,c.getRate(),prec);
		
		synchronized (this) {wait(100);}
		c.report(true);
		c.calculate();
		assertEquals(1.0,c.getRate(),prec);

		c.report(false);
		synchronized (this) {wait(100);}
		c.calculate();
		assertEquals(0.8,c.getRate(),prec);
		
		c.report(false);
		synchronized (this) {wait(100);}
		c.calculate();
		assertEquals(0.6,c.getRate(),prec);

		c.report(false);
		synchronized (this) {wait(100);}
		c.calculate();
		assertEquals(0.4,c.getRate(),prec);

		c.report(true);
		synchronized (this) {wait(100);}
		c.calculate();
		assertEquals(0.4,c.getRate(),prec);
	}

}
