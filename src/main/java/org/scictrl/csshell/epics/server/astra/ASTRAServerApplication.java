/**
 * 
 */
package org.scictrl.csshell.epics.server.astra;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.scictrl.csshell.epics.server.application.AbstractApplication;

import si.ijs.anka.config.BootstrapLoader;

/**
 * <p>ASTRAServerApplication class.</p>
 *
 * @author igor@scictrl.com
 */
public class ASTRAServerApplication extends AbstractApplication {
	
	/** Constant <code>STATUS_ASTRA_DURATION="Status:AstraDuration"</code> */
	public static final String STATUS_ASTRA_DURATION = "Status:AstraDuration";
	/** Constant <code>STATUS_GENERATE_DURATION="Status:GenerateDuration"</code> */
	public static final String STATUS_GENERATE_DURATION = "Status:GenerateDuration";
	/** Constant <code>STATUS_INIT_PART_DIST="Status:InitPartDist"</code> */
	public static final String STATUS_INIT_PART_DIST = "Status:InitPartDist";
	/** Constant <code>CMD_ASTRA_INIT_PART_DIST="Cmd:AstraInitPartDist"</code> */
	public static final String CMD_ASTRA_INIT_PART_DIST = "Cmd:AstraInitPartDist";
	/** Constant <code>CMD_GENERATE_INIT_PART_DIST="Cmd:GenerateInitPartDist"</code> */
	public static final String CMD_GENERATE_INIT_PART_DIST = "Cmd:GenerateInitPartDist";
	
	
	class StreamBufferer extends Thread {
		private InputStream is;
		private StringBuilder sb;

		public StreamBufferer(InputStream is) {
			this.is=is;
			this.sb=new StringBuilder();
		}
		
		@Override
		public void run() {
			byte[] buff = new byte[1024];
			try {
				while(is.available() > 0)
				{
					int i = is.read(buff, 0, 1024);
					if(i < 0)
						break;
					sb.append(new String(buff, 0, i));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		@Override
		public String toString() {
			return sb.toString();
		}
		
	}
	
	
	private String varDirName;
	private String generatorCmd;
	private String astraCmd;
	private String generatorIn;
	private String astraIn;
	private String templatesDirName;
	private File confDir;
	private File varDir;
	private File templatesDir;
	private File generatorCmdFile;
	private File astraCmdFile;
	private File generatorInFile;
	private File astraInFile;

	/**
	 * <p>Constructor for ASTRAServerApplication.</p>
	 */
	public ASTRAServerApplication() {
	}
	
	/** {@inheritDoc} */
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		confDir= BootstrapLoader.getInstance().getApplicationConfigFolder("ASTRAServer");
		
		varDirName=config.getString("var_dir", "var");
		varDir= new File(confDir,varDirName);
		
		templatesDirName=config.getString("templates_dir", "templates");
		templatesDir= new File(confDir,templatesDirName);
				
		generatorCmd=config.getString("generator_cmd","generator.sh");
		generatorCmdFile= new File(confDir,generatorCmd);
		
		astraCmd=config.getString("astra_cmd","astra.sh");
		astraCmdFile= new File(confDir,astraCmd);
		
		generatorIn=config.getString("generator_in","inputs/generator.in");
		generatorInFile= new File(confDir,generatorIn);
		
		astraIn=config.getString("astra_in","inputs/gun.in");
		astraInFile= new File(confDir,astraIn);
		
		
		addRecordOfCommandProcessor(CMD_GENERATE_INIT_PART_DIST, "Run Generator Tool ", 10000);
		addRecordOfCommandProcessor(CMD_ASTRA_INIT_PART_DIST, "Run Astra Tool ", 10000);
		addRecordOfMemoryValueProcessor(STATUS_INIT_PART_DIST, "Initial particle distribution has been generated", new byte[]{0});
		addRecordOfMemoryValueProcessor(STATUS_GENERATE_DURATION, "Typical duration of generate command", 0.0, Double.POSITIVE_INFINITY, "s", (short)1, 0.0);
		addRecordOfMemoryValueProcessor(STATUS_ASTRA_DURATION, "Typical duration of astra command", 0.0, Double.POSITIVE_INFINITY, "s", (short)1, 0.0);
		
		
	}; 
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyRecordWrite(String name) {
		super.notifyRecordWrite(name);
		
		if (CMD_GENERATE_INIT_PART_DIST == name) {
			boolean b= generate();
			getRecord(STATUS_INIT_PART_DIST).setValue(b);
			getRecord(CMD_GENERATE_INIT_PART_DIST).setValue(b);
		} else if (CMD_ASTRA_INIT_PART_DIST == name) {
			if (!getRecord(STATUS_INIT_PART_DIST).getValueAsBoolean()) {
				return;
			}
			boolean b= astra();
			getRecord(CMD_ASTRA_INIT_PART_DIST).setValue(b);
		}
		
	}

	private boolean generate() {
		
		try {
			
			FileUtils.copyDirectory(templatesDir, varDir, false);
			FileUtils.copyFileToDirectory(generatorInFile, varDir, false);
			
			ProcessBuilder pb= new ProcessBuilder();
			pb.directory(varDir);
			pb.command(generatorCmdFile.getAbsolutePath(),generatorInFile.getName());
			
			long t= System.currentTimeMillis();
			
			Process p= pb.start();
			
			StreamBufferer is= new StreamBufferer(p.getInputStream());
			StreamBufferer es= new StreamBufferer(p.getErrorStream());
			
			is.start();
			es.start();
			
			p.waitFor(10000,TimeUnit.MILLISECONDS);
			
			t= System.currentTimeMillis()-t;
			
			double d= getRecord(STATUS_GENERATE_DURATION).getValueAsDouble();
			d= 0.9 * d + ((double)t)/10000.0;
			
			getRecord(STATUS_GENERATE_DURATION).setValue(d);

			Thread.yield();
			
			log.debug("Generate Out: "+is.toString());
			log.debug("Generate Err: "+es.toString());
			log.debug("Generate Exit: "+p.exitValue());
			log.debug("Generate Duration: "+d);
			
		} catch (IOException e) {
			//e.printStackTrace();
			log.error("Commad failed", e);
			return false;
		} catch (InterruptedException e) {
			//e.printStackTrace();
			log.error("Commad failed", e);
			return false;
		}
		
		
		return true;
	}

	private boolean astra() {
		
		try {

			
			FileUtils.copyDirectory(templatesDir, varDir, false);
			FileUtils.copyFileToDirectory(astraInFile, varDir, false);
			
			ProcessBuilder pb= new ProcessBuilder();
			pb.directory(varDir);
			pb.command(astraCmdFile.getAbsolutePath(),astraInFile.getName());
			
			long t= System.currentTimeMillis();
			
			Process p= pb.start();
			
			StreamBufferer is= new StreamBufferer(p.getInputStream());
			StreamBufferer es= new StreamBufferer(p.getErrorStream());
			
			is.start();
			es.start();
			
			p.waitFor();
			
			t= System.currentTimeMillis()-t;
			
			double d= getRecord(STATUS_ASTRA_DURATION).getValueAsDouble();
			d= 0.9 * d + ((double)t)/100.0;
			
			getRecord(STATUS_ASTRA_DURATION).setValue(d);

			Thread.yield();
			
			log.debug("Astra Out: "+is.toString());
			log.debug("Astra Err: "+es.toString());
			log.debug("Astra Exit: "+p.exitValue());
			
		} catch (IOException e) {
			//e.printStackTrace();
			log.error("Commad failed", e);
			return false;
		} catch (InterruptedException e) {
			//e.printStackTrace();
			log.error("Commad failed", e);
			return false;
		}
		
		
		return true;
	}
}
