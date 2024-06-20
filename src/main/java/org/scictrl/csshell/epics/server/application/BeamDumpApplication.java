/**
 * 
 */
package org.scictrl.csshell.epics.server.application;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.scictrl.csshell.epics.server.Record;
import org.scictrl.csshell.epics.server.ValueLinks;
import org.scictrl.csshell.epics.server.ValueLinks.ValueHolder;
import org.scictrl.scripts.Array2Image;

import gov.aps.jca.dbr.DBRType;
import si.ijs.anka.config.BootstrapLoader;

/**
 * <p>BeamDumpApplication stores image (of a beam) into files on drive on event from tirgger PV called drops.
 * Image is stored as array, additionally there is information of timestamp and of width and height of image.</p>
 *
 * <p>Files are stored into directory provided with dataDir XML parameter and file names have prefix 'BeamDump-'.
 * When dataDir contains more generated files than maxFiles parameter specifies, oldest files are deleted unil file number falls below max files limit.</p>
 *
 * <p>Supported XML parameters</p>
 *
 * <ul>
 * <li>dropsPV - PV name for trigger record. Required.</li>
 * <li>imageHeightPV - PV name for record with image height. Required.</li>
 * <li>imageWidthPV - PV name for record with image width. Required.</li>
 * <li>imagePV - PV name for record with array containing image. Required.</li>
 * <li>maxFiles - maximal number generated filed, old files are removed if exceeded.</li>
 * <li>dataDir - location of generated files.</li>
 * </ul>
 *
 * <p>Application provides PVs with following suffixes:</p>
 *
 * <ul>
 * <li>Enabled - enables/disables the application.</li>
 * <li>Last:Image - last saved image.</li>
 * <li>Last:Time - last saved image timestamp.</li>
 * <li>Last:Time:Str - last saved image timestamp as human friendly string.</li>
 * <li>Dir - Directory to which array dump files are saved.</li>
 * </ul>
 *
 * @author igor@scictrl.com
 */
public class BeamDumpApplication extends AbstractApplication {

	private static final String ENABLED = 		"Enabled";
	private static final String LAST_IMAGE = 	"Last:Image";
	private static final String LAST_TIME = 	"Last:Time";
	private static final String LAST_TIME_STR = "Last:Time:Str";

	private static final String DIR = 	"Dir";
	private static final String IMG_H = "imgH";
	private static final String IMG_W = "imgW";
	private static final String IMG = 	"img";
	private static final String DROPS = "Drops";
	
	private File dataDir;
	private boolean dataDirOK;
	private int maxFiles;
	private String filePref;
	private ExecutorService executor;
	private String pvDrops;
	private String pvImgH;
	private String pvImgW;
	private String pvImg;
	private ValueLinks imgH;
	private ValueLinks imgW;
	private ValueLinks img;
	private ValueLinks drops;

	/**
	 * <p>Constructor for BeamDumpApplication.</p>
	 */
	public BeamDumpApplication() {
	}
	
	/** {@inheritDoc} */
	@Override
	public void configure(String name, HierarchicalConfiguration config) {
		super.configure(name, config);
		
		pvDrops= getNotNull("dropsPV",config,"");
		pvImgH= getNotNull("imageHeightPV",config,"");
		pvImgW= getNotNull("imageWidthPV",config,"");
		pvImg= getNotNull("imagePV",config,"");
				
		maxFiles= config.getInt("maxFiles", 100);

		String dataDirN= config.getString("dataDir", new File(BootstrapLoader.getInstance().getBundleHomeDir(),"data").getAbsolutePath());
		
		log4info("Data dir: '"+dataDirN+"'");
				
		Record r= addRecordOfMemoryValueProcessor(DIR, "Data directory", new byte[128]);
		
		dataDir= new File(dataDirN);
		
		File p= dataDir.getParentFile();
		if (dataDirOK= (p.exists() && p.isDirectory())) {
			r.setValueAsString(dataDir.getAbsolutePath());
		} else {
			r.setValueAsString("ERROR, no '"+dataDir.getAbsolutePath()+"'!");
		}
		
		imgH=connectLinks(IMG_H, pvImgH);
		imgW=connectLinks(IMG_W, pvImgW);
		img=connectLinks(IMG, pvImg);
		drops=connectLinks(DROPS, pvDrops);
		
		addRecordOfMemoryValueProcessor(LAST_IMAGE, "Last dumped image", 0, 4096, "pixel", new int[300000]);
		addRecordOfMemoryValueProcessor(LAST_TIME, "Last dumped image timestamp", DBRType.CTRL_INT, 0);
		addRecordOfMemoryValueProcessor(LAST_TIME_STR, "Last dumped image timestamp", DBRType.STRING, "");

		addRecordOfMemoryValueProcessor(ENABLED, "Enabled", DBRType.BYTE, false);

		filePref= "BeamDump-";
		
		executor= Executors.newSingleThreadExecutor();
	}
	
	/** {@inheritDoc} */
	@Override
	protected synchronized void notifyLinkChange(String name) {
		super.notifyLinkChange(name);
		
		if (name==DROPS) {

			boolean enabled= getRecord(ENABLED).getValueAsBoolean();
			
			if (enabled && drops.isReady() && !drops.isInvalid()) {
				
				ValueHolder vh= drops.consume()[0];
				//long d= vh.longValue();

				// value 1 comes so fast, that when this happesn it is already back to 0
				//if (d>0) {
					process(vh.timestamp);
				//}
			}
		}
	}

	private void process(final long ts) {
		
		//log4debug("Img: "+img.isReady()+" "+!img.isInvalid()+" "+!img.isLastSeverityInvalid());

		if (img.isReady()) {
			
			ValueHolder[] vh= img.consume();
			if (vh!=null && vh.length>0) {
				long[] l= (long[]) vh[0].value; 
						
				if (l!=null && l.length>0) {
					
					executor.execute(new Runnable() {
						
						@Override
						public void run() {
							_process(ts,l);
						}
					});
					
				}
			}
			
		}
		
		
		
	}
	private void _process(final long ts, final long[] l) {

		String tss= DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), TimeZone.getDefault().toZoneId()));

		getRecord(LAST_TIME).setValue(ts);
		getRecord(LAST_TIME_STR).setValue(tss);
		getRecord(LAST_IMAGE).setValue(l);
		
		int width=0;
		int height=0;
		
		long[] w= imgW.consumeAsLongs();
		if (w!=null && w.length>0) {
			width=(int)w[0];
		}
		
		long[] h= imgH.consumeAsLongs();
		if (h!=null && h.length>0) {
			height=(int)h[0];
		}

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
		
		if (dataDirOK) {
					
			dataDir.mkdirs();
			
			File[] ff= dataDir.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(filePref);
				}
			});
			
			if (maxFiles>0 && ff.length>=maxFiles*2.1) {
				
				Arrays.sort(ff, new Comparator<File>() {
					@Override
					public int compare(File o1, File o2) {
						return o1.getName().compareTo(o2.getName());
					}
				});
				
				for (int i = 0; i <= ff.length-maxFiles; i++) {
					ff[i].delete();
					ff[++i].delete();
				}
				
			}
			
			int[] buf= new int[height*width];
			int max=0;
			
			for (int i = 0; i < buf.length; i++) {
				int v= (int) l[i];
				if (v>max) {
					max=v;
				}
				buf[i]=v;
			}
			
			File dataFile= new File(dataDir,filePref+tss+"-"+max+".txt");
			File imgFile= new File(dataDir,filePref+tss+"-"+max+".png");
			
			String f= dataFile.getAbsolutePath();
			log4debug("Data file created '"+f+"'");
			
			PrintWriter pw=null;

			try {
				pw= new PrintWriter(new BufferedWriter(new FileWriter(dataFile), 1024));
				
				pw.println("# Beam Dump");
				pw.print("# PV: ");
				pw.println(pvImg);
				pw.print("# timestamp: ");
				pw.println(tss);
				pw.print("# max pixel: ");
				pw.println(max);
				pw.print("# width: ");
				pw.println(width);
				pw.print("# heigth: ");
				pw.println(height);
				pw.println("#");
				
				for (long e : l) {
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
			
			BufferedImage img= Array2Image.convertN(buf, width, height, dataFile.getName());
			
			try {
				ImageIO.write(img, "png", imgFile);
			} catch (IOException e) {
				log4error("Failed to save image file "+imgFile.toString(), e);
			}

		}
			

	}
	
	

}
