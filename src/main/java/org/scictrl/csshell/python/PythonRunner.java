package org.scictrl.csshell.python;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import si.ijs.anka.config.BootstrapLoader;

/**
 * <p>PythonRunner executes python scripts in working directory and parses returned output as double array.</p>
 *
 * @author igor@scictrl.com
 */
public class PythonRunner {

	/** String representing positive Infinity in Python */
	private static final String PYTHON_INF_POS = "inf";
	
	/** String representing negative Infinity in Python */
	private static final String PYTHON_INF_NEG = "-inf";

	/** String representing Not A Number in Python */
	private static final String PYTHON_NAN = "nan";

	
	/** Constant <code>PYTHON_BIN="/usr/bin/python3"</code>, points to Python executable, but be present on system. */
	public static final String PYTHON_BIN = "/usr/bin/python3";

	/**
	 * Parse string representing Python number and return as Java numeral.
	 * @param value string representing Python number 
	 * @return number
	 */
	public static final double parseDouble(final String value) {
		
		String s= value.trim().toLowerCase();
		
		if (PYTHON_INF_POS.endsWith(s)) {
			return Double.POSITIVE_INFINITY;
		}
		
		if (PYTHON_INF_NEG.endsWith(s)) {
			return Double.NEGATIVE_INFINITY;
		}
		
		if (PYTHON_NAN.endsWith(s)) {
			return Double.NaN;
		}
		
		return NumberUtils.toDouble(s);
	}
	
	
	/**
	 * Script result object.
	 */
	public static final class Result {
		/**
		 * Returned data parsed into double array.
		 */
		public double[] data;
		/**
		 * Possible error.
		 */
		public String error;
		/**
		 * Possible exception.
		 */
		public Exception exception;
		/**
		 * Script output.
		 */
		public String output;
		
		/**
		 * Result constructor.
		 */
		public Result() {
		}

		/**
		 * Result has data.
		 * @return <code>true</code> if has data
		 */
		public boolean hasData() {
			return data!=null && data.length>0;
		}
		
		/**
		 * Result is with error.
		 * @return <code>true</code> if is error
		 */
		public boolean hasError() {
			return !(error==null || error.length()==0) || exception!=null;
		}

		/**
		 * Returns <code>true</code> if there is data and no error.
		 * @return <code>true</code> if there is data and no error
		 */
		public boolean isOK() {
			return hasData() && !hasError();
		}
	}
	
	File directory;
	String script;
	Logger log= LogManager.getLogger(this.getClass());
	private Result lastResult;
	private double[] lastData;

	/**
	 * <p>Constructor for PythonRunner.</p>
	 */
	public PythonRunner() {
		
		File bin= new File(PYTHON_BIN);
		
		if (!bin.exists()) {
			log.error("Python executable '' NOT found! Install Python 3 or reconfigure this class.",PYTHON_BIN);
		}
		
		directory= new File(BootstrapLoader.getInstance().getBundleConfDir(),"Python");
		
		log.debug("Setting defautl dir to {}", directory.toString());
	}
	
	/**
	 * <p>Setter for the field <code>directory</code>.</p>
	 *
	 * @param directory a {@link java.io.File} object
	 */
	public void setDirectory(File directory) {
		if (directory==null || this.directory == directory || !directory.exists() || !directory.isDirectory()) {
			throw new IllegalArgumentException("New directory '"+directory+"' is not valid!");
		}
		this.directory = directory;
	}
	
	/**
	 * <p>Getter for the field <code>directory</code>.</p>
	 *
	 * @return a {@link java.io.File} object
	 */
	public File getDirectory() {
		return directory;
	}
	
	/**
	 * <p>Setter for the field <code>script</code>.</p>
	 *
	 * @param script a {@link java.lang.String} object
	 */
	public void setScript(String script) {
		this.script = script;
	}
	
	/**
	 * <p>Getter for the field <code>script</code>.</p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getScript() {
		return script;
	}
	
	/**
	 * <p>Executes string, add input double values are provided as list of input parameters for script, separated by space.</p>
	 * <p>Returned result is expected to be series of string lines. If line is started by # or empty space it is added to string result.
	 *    If starts with character, then results are search within ( ) ad array separated by comma.
	 *
	 * @param inputs a double
	 * @return a {@link org.scictrl.csshell.python.PythonRunner.Result} object
	 */
	public Result executeArrayTransaction(double... inputs) {
		
		if (script==null) {
			throw new IllegalArgumentException("Fiels 'script' has not been set!");
		}
		
		Result r= new Result();
		
		ArrayList<String> cmd= new ArrayList<String>(inputs.length+2);
		cmd.add(PYTHON_BIN);
		cmd.add(script);
		for (int i = 0; i < inputs.length; i++) {
			cmd.add(Double.toString(inputs[i]));
		}
		
		
		try {
			ProcessBuilder pb= new ProcessBuilder(cmd);
			pb.directory(directory);
			Process p= pb.start();
			
			BufferedReader br = p.inputReader();
			BufferedReader er= p.errorReader();
			
			p.waitFor();
			
			StringBuilder sb= new StringBuilder();
			while(br.ready()) {
				String rs=br.readLine();
				sb.append(rs);
				sb.append('\n');
				if ( ! ( rs.startsWith("#") || rs.startsWith(" ")) ) {
					int st= rs.indexOf('(');
					int en= rs.indexOf(')');
					
					if (st>0 && en>st+1) {
						rs=rs.substring(st+1, en);
						String[] s= rs.split(",");
						r.data=new double[s.length];
						for (int i = 0; i < s.length; i++) {
							r.data[i]=parseDouble(s[i].trim());
						}
					}
				}
			}
			
			r.output=sb.toString();
			log.debug("Output Stream:"+(r.output==null ? "None" : "\n"+r.output));
			
			sb= new StringBuilder();
			while(er.ready()) {
				sb.append(er.readLine());
				sb.append("\n");
			}
			
			r.error=sb.toString();
			log.debug("Error Stream:"+(r.error==null ? "None" : "\n"+r.error));

		} catch (Exception e) {
			r.exception=e;
			log.warn("Python script execution failed: "+e.toString(), e);
		}
		
		lastResult= r;
		lastData=r.data;
		return r;
	}
	
	/**
	 * <p>Getter for the field <code>lastData</code>.</p>
	 *
	 * @return an array of {@link double} objects
	 */
	public double[] getLastData() {
		return lastData;
	}
	
	/**
	 * <p>Getter for the field <code>lastResult</code>.</p>
	 *
	 * @return a {@link org.scictrl.csshell.python.PythonRunner.Result} object
	 */
	public Result getLastResult() {
		return lastResult;
	}

}
