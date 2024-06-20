/**
 * 
 */
package org.scictrl.csshell.jdoocs;

import java.util.Arrays;

import org.scictrl.csshell.epics.EPICSUtilities;

import gov.aps.jca.dbr.DBRType;
import ttf.doocs.clnt.EqAdr;
import ttf.doocs.clnt.EqCall;
import ttf.doocs.clnt.EqData;

/**
 * <p>JDoocsDemo class.</p>
 *
 * @author igor@scictrl.com
 */
public class JDoocsDemo {

	/**
	 * <p>Constructor for JDoocsDemo.</p>
	 */
	public JDoocsDemo() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * <p>main.</p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main(String[] args) {
		
		System.getProperties().put("ENSHOST", "ldap://flute-pc-mtca01.anka-flute.kit.edu");

	    // create the required objects 
	    EqAdr  ea = new EqAdr(); 
	    EqData ed = new EqData(); 
	    EqData result = null;
	    EqCall eq = new EqCall(); 

	    // fill the address class 
	    //ea.adr("FLUTE.RF/LLRF.CONTROLLER/GUNWG_FORWARD.FLUTE/POWER.SAMPLE");
	    ea.adr("FLUTE.RF/LLRF.CONTROLLER/GUNWG_FORWARD.FLUTE/POWER");

	    // initialize object to be sent
	    //String data = "-1 0 4 100";

	    //ed.set_type(eq_rpc.DATA_IIII);

	    //ed.set_from_string(data);

	    // do a call
	    result = eq.get(ea, ed); 
	    System.out.println(result.type_string());
	    System.out.println(result.get_string());
	    
	    float[] d=result.get_float_array();
	    System.out.println(result.array_length());
	    System.out.println(d.length);
	    System.out.println(Arrays.toString(d));

		Object o= EPICSUtilities.convertToDBRValue(d, DBRType.DOUBLE);
	    System.out.println(o);

	}

}
