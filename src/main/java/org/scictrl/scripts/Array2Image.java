package org.scictrl.scripts;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * <p>Array2Image class.</p>
 *
 * @version $Id: $Id
 * @author igor@scictrl.com
 */
public final class Array2Image {
	
	private static final double CF=0xFFFF/4096.0;

	/**
	 * <p>Constructor for Array2Image.</p>
	 */
	public Array2Image() {
	}
	
	
	/**
	 * <p>convert.</p>
	 *
	 * @param array an array of {@link int} objects
	 * @param width a int
	 * @param height a int
	 * @param text a {@link java.lang.String} object
	 * @return a {@link java.awt.image.BufferedImage} object
	 */
	public static final BufferedImage convert(int[] array, int width, int height, String text) {
		BufferedImage img= new BufferedImage(width,height,BufferedImage.TYPE_USHORT_GRAY);
		
		//WritableRaster wr= (WritableRaster)img.getData();
		//wr.setPixels(0, 0, width, height, array);

		DataBufferUShort b = (DataBufferUShort) img.getRaster().getDataBuffer();
		
		for (int i = 0; i < array.length; i++) {
			b.setElem(i, array[i]);
		}
		
		if (text!=null && text.length()>0) {
			Graphics2D g= (Graphics2D) img.getGraphics();
			g.drawString(text, 10, 20);
		}
		
		return img;
	}

	/**
	 * <p>convertN.</p>
	 *
	 * @param array an array of {@link int} objects
	 * @param width a int
	 * @param height a int
	 * @param text a {@link java.lang.String} object
	 * @return a {@link java.awt.image.BufferedImage} object
	 */
	public static final BufferedImage convertN(int[] array, int width, int height, String text) {
		BufferedImage img= new BufferedImage(width,height,BufferedImage.TYPE_USHORT_GRAY);
		
		//WritableRaster wr= (WritableRaster)img.getData();
		//wr.setPixels(0, 0, width, height, array);

		DataBufferUShort b = (DataBufferUShort) img.getRaster().getDataBuffer();
		
		double max=0.0;
		for (int i = 0; i < array.length; i++) {
			if (array[i]>max) {
				max=array[i];
			};
		}
		
		double c= (double)(0xFFFF/max);

		for (int i = 0; i < array.length; i++) {
			b.setElem(i, (int)(array[i]*c));
		}
		
		if (text!=null && text.length()>0) {
			Graphics2D g= (Graphics2D) img.getGraphics();
			g.drawString(text, 10, 20);
		}

		return img;
	}

	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 * @throws java.io.IOException if any.
	 */
	public static void main(String[] args) throws IOException {
		
		if (args!=null && args[0]!=null) {
			
			File f= new File(args[0]);
			
			BufferedReader r= new BufferedReader(new FileReader(f));
			
			String s=null;
			int width=0;
			int height=0;
			int[] buf;
			
			List<Integer> l= new ArrayList<Integer>(); 
			
			while ((s=r.readLine()) !=null ) {
				s.trim();
				
				if (s.startsWith("# width: ")) {
					width=Integer.parseInt(s.substring(9).trim());
				} else if (s.startsWith("# heigth: ")) {
					height=Integer.parseInt(s.substring(10).trim());
				} else if (s.charAt(0)!='#') {
					l.add(Integer.parseInt(s));
				}
			}
			
			r.close();
			
			buf= new int[width*height];
			
			
			
			for (int i = 0; i < buf.length; i++) {
				buf[i]=(int)(l.get(i)*CF);
				//System.out.print(buf[i]+", ");
			}
			//System.out.println();
			
			BufferedImage img= convertN(buf, width, height, f.toString());
			
			String nimg=f.toString();
			
			if (nimg.endsWith(".txt")) {
				nimg= nimg.replace(".txt", ".png");
			} else {
				nimg=nimg+".png";
			}
			
			ImageIO.write(img, "png", new File(nimg));
			
			System.out.println("Output: "+nimg);
		}

	}

}
