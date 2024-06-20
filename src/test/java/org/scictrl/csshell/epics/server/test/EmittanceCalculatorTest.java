package org.scictrl.csshell.epics.server.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.scictrl.csshell.python.EmittanceCalculator;
import org.scictrl.csshell.python.PythonRunner.Result;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import si.ijs.anka.config.BootstrapLoader;

@TestMethodOrder(MethodName.class)
class EmittanceCalculatorTest {
	
	
	class Measurements {
		private double[] quads;
		private double[] widths;
		private double[] widthstd;

		public Measurements(File f) throws IOException {
			
			List<String> l= FileUtils.readLines(f, StandardCharsets.UTF_8);
			
			ArrayList<Double> q= new ArrayList<Double>(l.size());
			ArrayList<Double> w= new ArrayList<Double>(l.size());
			ArrayList<Double> ws= new ArrayList<Double>(l.size());
			
			for (String s : l) {
				s=s.replace('[', ' ');
				s=s.replace(']', ' ');
				s=s.trim();
				String[] ss= s.split("\s+");
				if (ss!=null && ss.length==3) {
					q.add(Double.parseDouble(ss[0]));
					w.add(Double.parseDouble(ss[1]));
					ws.add(Double.parseDouble(ss[2]));
				}
			}
			
			quads=new double[q.size()]; 
			widths=new double[q.size()]; 
			widthstd=new double[q.size()];
			
			for (int i = 0; i < quads.length; i++) {
				quads[i]=q.get(i);
				widths[i]=w.get(i);
				widthstd[i]=ws.get(i);
			}
			
		}
	}

	private BootstrapLoader bs;

	/**
	 * <p>Constructor for EmittanceCalculatorTest.</p>
	 */
	public EmittanceCalculatorTest() {
		System.setProperty(BootstrapLoader.BUNDLE_CONF, "./src/test/config");
		bs= BootstrapLoader.getInstance();
		//System.out.println(bs.getBundleConfDir());
	}
	
	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	/**
	 * <p>test1Info.</p>
	 */
	@Order(0)
	@Test
	public void test1Info() {
		try {
			
			System.out.println("# config home : "+bs.getBundleConfDir());
			File dir= bs.getApplicationConfigFolder("Python");
			System.out.println("# config dir  : "+dir);
			System.out.println("# config files: "+Arrays.toString(dir.list()));
			System.out.println();
			
			ProcessBuilder pb= new ProcessBuilder("/usr/bin/python3","test.py");
			pb.directory(dir);
			Process p= pb.start();
			BufferedReader br = p.inputReader();
			BufferedReader er= p.errorReader();
			p.waitFor();
			
			while(br.ready()) {
				System.out.println(br.readLine());
			}
			while(er.ready()) {
				System.out.println("ERROR: "+er.readLine());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Test.
	 */
	@Test
	void test3All() {
		
		double e= 5.56885;
		//double e= 5.81;
		int c=26;
		
		System.out.println("HOR");
		for (int i = 0; i < 24; i++) {
			String m="emit_data_sample_hor_"+i+".txt";
			double[] d= measurement(m, c, e);
			System.out.println(String.format("%02d %s", i,Arrays.toString(d)));
		}
		
		System.out.println("VER");
		for (int i = 0; i < 24; i++) {
			String m="emit_data_sample_ver_"+i+".txt";
			double[] d= measurement(m, c, e);
			System.out.println(String.format("%02d %s", i,Arrays.toString(d)));
		}
	}
	
	//@Test
	void test2One() {
		
		double e= 5.56885;

		int c=26;
		int i=0;
		
		System.out.println("HOR");
		String m="emit_data_sample_hor_"+i+".txt";
		double[] d= measurement(m, c, e);
		System.out.println(String.format("%02d %s", i,Arrays.toString(d)));
		
		System.out.println("VER");
		m="emit_data_sample_ver_"+i+".txt";
		d= measurement(m, c, e);
		System.out.println(String.format("%02d %s", i,Arrays.toString(d)));
	}

	
	private double[] measurement(String measurements, int measurementsCount, double energy) {
		

		String script= "emittance.py";
		
		EmittanceCalculator ec= new EmittanceCalculator();
		
		File measF= new File(ec.getDirectory(),measurements);
		File scriptF= new File(ec.getDirectory(),script);
		
		assertTrue(measF.exists());
		assertTrue(scriptF.exists());
		
		try {
			
			Measurements m= new Measurements(measF);
			
			assertNotNull(m);
			assertNotNull(m.quads);
			assertNotNull(m.widths);
			assertNotNull(m.widthstd);
			assertEquals(measurementsCount,m.quads.length);
			assertEquals(measurementsCount,m.widths.length);
			assertEquals(measurementsCount,m.widthstd.length);
			
			
			ec.setScript(script);
			ec.setEnergy(energy);
			ec.inputs(m.quads, m.widths, m.widthstd);
			
			assertEquals(energy, ec.getEnergy(),0.00001);
			
			Result r= ec.calculateEmittance();
			
			assertNotNull(r);

			double[] d= r.data;
			//String s= r.output;
			
			assertTrue(r.hasData());
			assertTrue(!r.hasError());
			assertTrue(r.isOK());
			
			assertNotNull(d);
			assertEquals(2, d.length);
			
			//System.out.println(Arrays.toString(d));
			
			return d;
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
		
		return new double[] {0.0,0.0};
	}
	

}
