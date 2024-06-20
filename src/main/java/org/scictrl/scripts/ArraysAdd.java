package org.scictrl.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * <p>ArraysAdd class.</p>
 *
 * @version $Id: $Id
 * @author igor@scictrl.com
 */
public class ArraysAdd {

	/**
	 * <p>Constructor for ArraysAdd.</p>
	 */
	public ArraysAdd() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main(String[] args) {
		
		PrintWriter wr=null;
		BufferedReader[] br= null;
		
		double d= 25703.957828;
		
		try {
			
			File dir= new File(".");
			
			File[] ff=dir.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return !name.contains("out");
					
				}
			});
			
			File out= new File(dir,"out.csv");
			out.delete();
			wr= new PrintWriter(new BufferedWriter(new FileWriter(out),1024));
			
			br= new BufferedReader[ff.length];
			
			wr.print('#');
			for (int i = 0; i < br.length; i++) {
				br[i]= new BufferedReader(new FileReader(ff[i]),1024);
				wr.print(ff[i].getName());
				wr.print(',');
			}
			wr.println();
			
			boolean done=false;
			
			while(!done) {
				
				for (int i = 0; i < br.length; i++) {
					String s= br[i].readLine();
					if (s==null || s.length()==0) {
						done=true;
						break;
					}
					while (s.charAt(0)=='#') {
						s= br[i].readLine();
					}
					
					double dd= Double.parseDouble(s);
					dd=dd*d;
					wr.print(dd);
					wr.print(',');
				}
				wr.println();
				
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (wr!=null) {
				wr.flush();
				wr.close();
			}
			if (br!=null) {
				for (int i = 0; i < br.length; i++) {
					if (br[i]!=null) {
						try {
							br[i].close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

}
