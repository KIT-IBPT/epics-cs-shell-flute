/**
 * 
 */
package org.scictrl.csshell.epics.server.bpm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * <p>BPMPullEngine class.</p>
 *
 * @author igor@scictrl.com
 */
public class BPMPullEngine {
	
	static Map<String,BPMPullEngine> instances= new HashMap<String, BPMPullEngine>(32);
	
	static BPMPullEngine getIntance(String url, String reference) throws MalformedURLException {
		String ref= url+"R"+reference;
		BPMPullEngine e= instances.get(ref);
		if (e != null) {
			return e;
		}
		e= new BPMPullEngine(url, reference);
		instances.put(ref, e);
		return e;
	}
	
	/**
	 * BPM Data class.
	 */
	public class Data {
		Number[] values;
		long timestamp;
		
		/**
		 * Created new Data object.
		 * @param values BPM values
		 * @param timestamp the measurement timestamp
		 */
		public Data(Number[] values, long timestamp) {
			this.values=values;
			this.timestamp=timestamp;
			if (timestamp==0) {
				this.timestamp=System.currentTimeMillis();
			}
		}
		
		/**
		 * Return data at index.
		 * @param i the index
		 * @return value at index
		 */
		public Number get(int i) {
			if (values!=null && values.length>i) {
				return values[i];
			}
			
			return null;

		}
	}

	
	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main(String[] args) {
		try {
			
			BPMPullEngine bpm=BPMPullEngine.getIntance("http://ipc1353/action/webmap_read", "http://ipc1353/cav_bpm.htm? SMP_ADDR_REG=0x00860000&SMP_ADDR_MEM=0x00900000&SYS_INIT=0x00838000&CFG_SN_ADDR=0x00003870&FL_ADDR=0x00810000&VME_P2_ADDR=0x00102000&I2C_ADDR=0x00840000&CFG_P0HS_RD=0x00003A20&DEVICE=LINAC+BPM1");
			
			String s0="F00838024";
			int i0= bpm.registerRequest(s0);
			
			System.out.println("> "+s0+" > "+i0);
			
			Number d0= bpm.readData().get(i0);
			
			System.out.println("> "+s0+" > "+i0+" > "+d0);

			bpm.readData();
			
			d0= bpm.readData().get(i0);
			
			System.out.println("> "+s0+" > "+i0+" > "+d0);
			
			d0=d0.intValue()+1;
			
			System.out.println("> "+s0+" > "+i0+" > "+d0);

			bpm.writeData(i0,d0);

			d0= bpm.readData().get(i0);
			
			System.out.println("> "+s0+" > "+i0+" > "+d0);
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	URL url;
	String urlStr;
	String referer;
	Properties requestProperties= new Properties();
	String request=null;
	long updateRate= 1000;
	long updateRateMin= 500;
	
	Map<Integer, String> index2request= new HashMap<Integer, String>(64);

	long lastUpdate;
	long lastUpdateDuration;
	
	DecimalFormat format= new DecimalFormat("000");
	private URL urlWrite;
	
	Data lastData= new Data(new Number[0],0);
	
	/**
	 * <p>Constructor for BPMPullEngine.</p>
	 *
	 * @throws java.net.MalformedURLException if URL is not valid
	 * @param url a {@link java.lang.String} object
	 * @param reference a {@link java.lang.String} object
	 */
	public BPMPullEngine(String url, String reference) throws MalformedURLException {
		this.urlStr=url;
		this.url= new URL(url);
		this.urlWrite= new URL(url.replace("read", "write"));
		this.referer=reference;
	}
	
	private void doReadData() throws IOException {
		
		long t= System.currentTimeMillis();
		
		HttpURLConnection conn= (HttpURLConnection) url.openConnection();
		
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		
		for (Object key : requestProperties.keySet()) {
			conn.setRequestProperty(key.toString(), requestProperties.get(key).toString());
		}
		
		OutputStreamWriter os= new OutputStreamWriter(conn.getOutputStream(),Charset.forName("UTF-8"));
		
		if (request==null) {
			request= createReadRequest();
		}
		
		os.write(request);
		os.flush();
		os.close();
		
		
		StringBuilder sb= new StringBuilder();
		InputStreamReader is= new InputStreamReader(conn.getInputStream(),Charset.forName("UTF-8"));
		int c;
		while ((c=is.read())>=0) {
			sb.append(Character.toString((char) c));
		}
		
		String[] resultStr= sb.toString().split("@");
		Number[] data= new Number[resultStr.length];
		
		for (int i = 0; i < data.length; i++) {
			if (resultStr[i].length()>3) {
				String s= resultStr[i].substring(3);
				if (s.contains(".")) {
					data[i]= Double.parseDouble(s);
				} else {
					data[i]= Integer.parseInt(s);
				}
			} else {
				data[i]= null;
			}
		}
		
		lastData= new Data(data,conn.getHeaderFieldDate("", 0));
		lastUpdate= System.currentTimeMillis();
		lastUpdateDuration= lastUpdate-t;
		
	}
	
	private synchronized void invalidateLastData() {
		lastData=null;
	}
	
	private synchronized void checkUpdate() throws IOException {
		long t= System.currentTimeMillis();
		
		if (lastData==null || (t-lastUpdate>=updateRateMin && t+lastUpdateDuration>=lastUpdate+updateRate)) {
			doReadData();
		}
		
	}

	private String createReadRequest() {
		
		StringBuilder sb= new StringBuilder(16*index2request.size());
		
		int i = 0;
		
		if (i<index2request.size()) {
			sb.append("000");
			sb.append(index2request.get(i));
			sb.append("001");
		}
		
		for (i=1; i < index2request.size(); i++) {
			sb.append("@");
			sb.append(format.format(i));
			sb.append(index2request.get(i));
			sb.append("001");
		}
		
		return sb.toString();
	}
	
	private String createWriteRequest(int index, Number data) {
		
		StringBuilder sb= new StringBuilder(32);
		
		sb.append("000");
		sb.append(index2request.get(index));
		sb.append("001");
		if (data instanceof Double) {
			sb.append(data.doubleValue());
		} else {
			sb.append(data.intValue());
		}

		return sb.toString();
	}

	/**
	 * <p>registerRequest.</p>
	 *
	 * @param requestStr a {@link java.lang.String} object
	 * @return a int
	 */
	public int registerRequest(String requestStr) {
		
		int n= index2request.size();
		
		index2request.put(n,requestStr);
		
		request=null;
		
		return n;
	}
	
	/**
	 * <p>readData.</p>
	 *
	 * @return a {@link org.scictrl.csshell.epics.server.bpm.BPMPullEngine.Data} object
	 * @throws java.io.IOException if any.
	 */
	public Data readData() throws IOException {
		checkUpdate();
		
		return lastData;
	}

	/**
	 * <p>writeData.</p>
	 *
	 * @param index a int
	 * @param data a {@link java.lang.Number} object
	 * @throws java.io.IOException if any.
	 */
	public void writeData(int index, Number data) throws IOException {
		
		//long t= System.currentTimeMillis();
		
		HttpURLConnection conn= (HttpURLConnection) urlWrite.openConnection();
		
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		//conn.setDoInput(false);
		//conn.setConnectTimeout(10);
		conn.setReadTimeout(1);
		
		for (Object key : requestProperties.keySet()) {
			conn.setRequestProperty(key.toString(), requestProperties.get(key).toString());
		}
		
		OutputStreamWriter os= new OutputStreamWriter(conn.getOutputStream(),Charset.forName("UTF-8"));
		
		String r= createWriteRequest(index,data);
		
		os.write(r);
		os.flush();
		os.close();
		
		conn.disconnect();
		
		try {
			// workaround to force server to apply value.
			@SuppressWarnings("unused")
			InputStream is= conn.getInputStream();
		} catch (Exception e) {
			// ignored
		}
		
		//StringBuilder sb= new StringBuilder();
		//InputStreamReader is= new InputStreamReader(conn.getInputStream(),Charset.forName("UTF-8"));
		/*int c;
		while ((c=is.read())>=0) {
			sb.append(Character.toString((char) c));
		}*/

		//System.out.println(sb.toString());
		
		invalidateLastData();
		
	}
}
