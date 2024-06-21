package org.scictrl.csshell.math;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SmoothingTest {

	@Test
	void testAvg1() {
		
		double[] data1= {0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		
		double sum= Smoothing.sum(data1, 0, data1.length);
		assertEquals(10.0, sum, 0.000001);
		
		sum= Smoothing.sum(data1, 0, 5);
		assertEquals(0.0, sum, 0.000001);
		
		sum= Smoothing.sum(data1, 6, 5);
		assertEquals(0.0, sum, 0.000001);

		sum= Smoothing.sum(data1, 3, 5);
		assertEquals(10.0, sum, 0.000001);
		
		double avg= Smoothing.avg(data1, 0, data1.length);
		assertEquals(10.0/11.0, avg, 0.000001);
		
		
		int sm= 1;
		double[] data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1[i], data1sm[i], 0.000001,"at "+i);
		}
		
		sm= 2;
		double[] data1rs= {0.0, 0.0, 0.0, 0.0, 0.0, 5.0, 5.0, 0.0, 0.0, 0.0, 0.0}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.000001,"at "+i);
		}
		
		sm= 3;
		data1rs= new double[]{0.0, 0.0, 0.0, 0.0, 3.33, 3.333, 3.33, 0.0, 0.0, 0.0, 0.0}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.01,"at "+i);
		}

	}

	@Test
	void testAvg2() {
		
		double[] data1= {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0};
		
		double sum= Smoothing.sum(data1, 0, data1.length);
		assertEquals(66.0, sum, 0.000001);
		
		sum= Smoothing.sum(data1, 0, 5);
		assertEquals(15.0, sum, 0.000001);
		
		sum= Smoothing.sum(data1, 6, 5);
		assertEquals(45.0, sum, 0.000001);

		sum= Smoothing.sum(data1, 3, 5);
		assertEquals(30.0, sum, 0.000001);
		
		double avg= Smoothing.avg(data1, 0, data1.length);
		assertEquals(66.0/11.0, avg, 0.000001);
		
		
		int sm= 1;
		double[] data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1[i], data1sm[i], 0.000001,"at "+i);
		}
		
		sm= 2;
		double[] data1rs= {1.0, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.5}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.000001,"at "+i);
		}
		
		sm= 3;
		data1rs= new double[]{1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 10.5}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.01,"at "+i);
		}

		sm= 4;
		data1rs= new double[]{1.5, 2.0, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.0}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.01,"at "+i);
		}
		
		sm= 5;
		data1rs= new double[]{2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 9.5, 10.0}; 
		data1sm= Smoothing.smoothAvg(data1, sm);
		for (int i = 0; i < data1sm.length; i++) {
			assertEquals(data1rs[i], data1sm[i], 0.01,"at "+i);
		}

	}
	
	@Test
	void testCollapse() {
		
		double[] x= {1.0, 2.0, 2.0, 3.0, 4.0, 4.0, 4.0, 5.0, 6.0,  7.0,  8.0,  8.0,  8.0,  8.0,  8.0,  9.0, 10.0, 11.0};
		double[] y= {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0};
		
		double[] rx= {1.0, 2.0, 3.0, 4.0, 5.0, 6.0,  7.0,  8.0,  9.0, 10.0, 11.0};
		double[] ry= {1.0, 2.5, 4.0, 6.0, 8.0, 9.0, 10.0, 13.0, 16.0, 17.0, 18.0};
		
		double[][] r= Smoothing.collapseSame(x, y);
		
		assertArrayEquals(rx, r[0], 0.0001);
		assertArrayEquals(ry, r[1], 0.0001);
		
		
	}

}
