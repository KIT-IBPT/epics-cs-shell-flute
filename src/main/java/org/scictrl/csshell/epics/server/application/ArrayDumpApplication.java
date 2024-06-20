/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;

import gov.aps.jca.dbr.DBRType;
import si.ijs.anka.config.BootstrapLoader;

/**
 * <p>ArrayDumpApplication monitors input PV array record, and dumps array values into file, if any value in array exceeds threshold value, configurable by PV.</p>
 *
 * <p>Files are stored into directory provided with dataDir XML parameter and file names have prefix 'ArrayDump-'.
 * When dataDir contains more generated files than maxFiles parameter specifies, oldest files are deleted until file number falls below max files limit.</p>
 *
 * <p>Supported XML parameters</p>
 *
 * <ul>
 * <li>input - PV name for input record, must be array record. Required.</li>
 * <li>treshold - threshold value, above which array is dumped to file. Default is 1.0</li>
 * <li>maxFiles - maximal number generated filed, old files are removed if exceeded.</li>
 * <li>dataDir - location of generated files.</li>
 * </ul>
 *
 * <p>Application provides PVs with following suffixes:</p>
 *
 * <ul>
 * <li>Peak - peak value of last received array.</li>
 * <li>Treshold - threshold value for array, array is dumped to file if exceeded.</li>
 * <li>PV - String with PV name of input record.</li>
 * <li>Dir - Directory to which array dump files are saved.</li>
 * </ul>
 *
 * @author igor@scictrl.com
 */
public class ArrayDumpApplication extends AbstractApplication {

	private static final String TRESHOLD = "Treshold";
	private static final String PV = "PV";
	private static final String INPUT = "input";
	private static final String DIR = "Dir";
	private static final String ENABLED = "Enabled";
	private static final String PEAK = "Peak";
	
	
	
	private String pv;
	private double treshold;
	private ValueLinks input;
	private File dataDir;
	private boolean dataDirOK;
	private int maxFiles;
	private String filePref;
	private ExecutorService executor;
	private boolean enabled;

	/**
	 * <p>Constructor for ArrayDumpApplication.</p>
	 */
	public ArrayDumpApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		pv= config.getString("input");
		
		if (pv==null || pv.length()==0) {
			throw new IllegalArgumentException("Property 'input' is not defined!");
		}
		
		treshold= config.getDouble(TRESHOLD.toLowerCase(), 1.0);
		maxFiles= config.getInt("maxFiles", 100);

		String dataDirN= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());
		
		log4info("Data dir: '"+dataDirN+"'");
				
		addRecordOfMemoryValueProcessor(PEAK, "Peak value", Double.MIN_VALUE, Double.MAX_VALUE, "", (short) 4, 0.0);

		Record r= addRecordOfMemoryValueProcessor(TRESHOLD, "Value treshold", Double.MIN_VALUE, Double.MAX_VALUE, "", (short) 2, treshold);
		r.setPersistent(true);
		
		r= addRecordOfMemoryValueProcessor(PV, "Array PV", new byte[128]);
		r.setValueAsString(pv);
		
		r= addRecordOfMemoryValueProcessor(DIR, "Data directory", new byte[128]);
		
		dataDir= new File(dataDirN);
		
		File p= dataDir.getParentFile();
		if (dataDirOK= (p.exists() && p.isDirectory())) {
			r.setValueAsString(dataDir.getAbsolutePath());
		} else {
			r.setValueAsString("ERROR, no '"+dataDir.getAbsolutePath()+"'!");
		}
		

		enabled=false;
		addRecordOfMemoryValueProcessor(ENABLED, "Enabled", DBRType.BYTE, enabled);

		input= connectLinks(INPUT, pv);
		
		filePref= "ArrayDump-"+pv;
		
		executor= Executors.newSingleThreadExecutor();
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordChange(String name, boolean alarmOnly) {
		super.notifyRecordChange(name, alarmOnly);
		
		if (name==TRESHOLD) {
			treshold= getRecord(TRESHOLD).getValueAsDouble();
		} else if (name==ENABLED) {
			enabled= getRecord(ENABLED).getValueAsBoolean();
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==INPUT) {
			
			if (enabled && input.isReady() && !input.isInvalid()) {
				
				ValueHolder vh= input.consume()[0];
				double[] d= vh.doubleArrayValue();
				
				process(d,vh.timestamp,treshold);
				
			}
		}
	}

	private void process(final double[] d, final long ts, final double t) {
		
		executor.execute(new Runnable() {
			
			@Override
			public void run() {
				_process(d, ts, t);
			}
		});
		
	}
	private void _process(final double[] d, final long ts, final double t) {
		
		if (d==null) {
			return;
		}
		
		double peak= Double.NEGATIVE_INFINITY;
		boolean b=false;
		for (double e : d) {
			if (e>peak) {
				peak=e;
			}
			if (e>t) {
				b=true;
				//break;
			}
		}
		
		getRecord(PEAK).setValue(peak);

		File p= dataDir.getParentFile();
		boolean dirOK= p.exists() && p.isDirectory();
		if (dataDirOK != dirOK) {
			dataDirOK=dirOK;
			
			if (dataDirOK) {
				getRecord(DIR).setValueAsString(dataDir.getAbsolutePath());
			} else {
				getRecord(DIR).setValueAsString("ERROR, no '"+dataDir.getAbsolutePath()+"'!");
			}
		}
		
		if (b && dataDirOK) {
			String tss= DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), TimeZone.getDefault().toZoneId()));
					
			dataDir.mkdirs();
			
			File[] ff= dataDir.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(filePref);
				}
			});
			
			if (maxFiles>0 && ff.length>=maxFiles*1.1) {
				
				Arrays.sort(ff, new Comparator<File>() {
					@Override
					public int compare(File o1, File o2) {
						return o1.getName().compareTo(o2.getName());
					}
				});
				
				for (int i = 0; i <= ff.length-maxFiles; i++) {
					ff[i].delete();
				}
				
			}
			
			File dataFile= new File(dataDir,"ArrayDump-"+pv+"-"+tss+".txt");
			
			
			String f= dataFile.getAbsolutePath();
			log4debug("Data file created '"+f+"'");
			
			PrintWriter pw=null;

			try {
				pw= new PrintWriter(new BufferedWriter(new FileWriter(dataFile), 1024));
				
				pw.println("# Array Dump");
				pw.print("# PV: ");
				pw.println(pv);
				pw.print("# timestamp: ");
				pw.println(tss);
				pw.print("# size: ");
				pw.println(d.length);
				pw.print("# treshold: ");
				pw.println(t);
				pw.println("#");
				
				for (double e : d) {
					pw.println(e);
				}
				
				log4debug("Data file saved '"+f+"'");
				
			} catch (Exception e) {
				
				log4error("File saved failed '"+f+"': "+e.toString(), e);
			
			} finally {
				if (pw!=null) {
					pw.close();
				}
			}

		}
			

	}
	
	

}
