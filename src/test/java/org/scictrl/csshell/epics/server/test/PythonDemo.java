package org.scictrl.csshell.epics.server.test;

import java.io.BufferedReader;
import java.io.File;
import java.util.Arrays;

import si.ijs.anka.config.BootstrapLoader;

/**
 * Silly lither Python demo runner.
 *
 * @author igor@scictrl.com
 */
public class PythonDemo {

	/**
	 * New instance.
	 */
	public PythonDemo() {
	}

	/**
	 * Main method.
	 *
	 * @param args arguments
	 */
	public static void main(String[] args) {
		
		try {
			//System.out.println(new File(".").getAbsolutePath());
			//System.setProperty(BootstrapLoader.BUNDLE_CONF, "../FLUTE-Servers/config");
			System.setProperty(BootstrapLoader.BUNDLE_CONF, "src/test/config");
			
			BootstrapLoader bl= BootstrapLoader.getInstance();
			
			System.out.println("# config home : "+bl.getBundleConfDir());
			
			File dir= bl.getApplicationConfigFolder("Python");
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

}
