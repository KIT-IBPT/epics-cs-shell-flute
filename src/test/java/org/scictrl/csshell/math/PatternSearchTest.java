package org.scictrl.csshell.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PatternSearchTest {

	@Test
	void test() {
		//              0    1    2    3    4    5    6    7    8    9    10   11    12   13   14   15   16   17   18
		double[] data= {0.0, 0.1, 0.2, 1.0, 7.0, 6.0, 5.0, 7.1, 8.0, 7.5, 8.5, 10.0, 9.0, 8.0, 7.0, 0.5, 0.2, 0.1, 0.0};
		
		int[] res= PatternSearch.findBreakpointsHiLoHHi(data, 0.1);

		assertNotNull(res);
		assertEquals(3, res.length);
		
		System.out.println(Arrays.toString(res));
		
		assertEquals(4, res[0]);
		assertEquals(6, res[1]);
		assertEquals(11, res[2]);
		
		

		data= new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
		
		res= PatternSearch.findBreakpointsHiLoHHi(data, 0.1);

		assertNotNull(res);
		assertEquals(3, res.length);
		
		System.out.println(Arrays.toString(res));
		
		assertEquals(-1, res[0]);
		assertEquals(-1, res[1]);
		assertEquals(0, res[2]);
	
		
		
		data= new double[]{0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0, 0.0};
		
		res= PatternSearch.findBreakpointsHiLoHHi(data, 0.1);

		assertNotNull(res);
		assertEquals(3, res.length);
		
		System.out.println(Arrays.toString(res));
		
		assertEquals(-1, res[0]);
		assertEquals(-1, res[1]);
		assertEquals(7, res[2]);

	}

}
