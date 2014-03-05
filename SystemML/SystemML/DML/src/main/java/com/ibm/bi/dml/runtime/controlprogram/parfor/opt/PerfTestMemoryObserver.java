/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2013
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.runtime.controlprogram.parfor.opt;

import java.lang.ref.WeakReference;

/**
 * Observer thread for asynchronously monitor the memory consumption.
 * It periodically measures the used memory with period <code>MEASURE_INTERVAL</code>,
 * by explicitly invoking the garbage collector (if required until it is really executed)
 * and afterwards obtaining the currently used memory.
 * 
 * Protocol: (1) measure start, (2) start thread, (3) *do some work*, (4) join thread, (5) get max memory.
 *  
 */
public class PerfTestMemoryObserver implements Runnable
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	public static final int MEASURE_INTERVAL = 50; //in ms 
	
	private long    _startMem = -1;
	private long    _maxMem   = -1; 
	private boolean _stopped  = false;
	
	public PerfTestMemoryObserver()
	{
		_startMem = -1;
		_maxMem   = -1; 
		_stopped  = false;			
	}
		
	/**
	 * 
	 */
	public void measureStartMem()
	{
		forceGC( true );
		_startMem =  Runtime.getRuntime().totalMemory()
		           - Runtime.getRuntime().freeMemory();
	}
	
	/**
	 * 
	 * @return
	 */
	public long getMaxMemConsumption()
	{
		long val = _maxMem - _startMem;
		return (val < 0) ? 0 : val; 
	}
	
	/**
	 * 
	 */
	public void setStopped()
	{
		_stopped = true;
	}

	@Override
	public void run() 
	{
		try
		{
			while( !_stopped )
			{
				forceGC( true );
				long value =   Runtime.getRuntime().totalMemory()
		                     - Runtime.getRuntime().freeMemory(); 
				
				_maxMem = Math.max(value, _maxMem);
				
				Thread.sleep( MEASURE_INTERVAL );
			}
		}
		catch(Exception ex)
		{
			throw new RuntimeException("Error measuring Java memory usage", ex);
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public static double getUsedMemory()
	{
		forceGC( true );
		return  ( Runtime.getRuntime().totalMemory()
		           - Runtime.getRuntime().freeMemory() );
	}
	
	/** 
	 * 
	 * @param force
	 */
	private static void forceGC( boolean force )
	{
		if( force )
		{
			//request gc until weak reference is eliminated by gc
			Object o = new Object();
			WeakReference<Object> ref = new WeakReference<Object>(o); //collected, everytime gc is actually invoked
			while((o=ref.get())!= null) 
				System.gc();
		}
		else
		{
			System.gc(); System.gc(); System.gc(); System.gc();
			System.gc(); System.gc(); System.gc(); System.gc();
			System.gc(); System.gc(); System.gc(); System.gc();
			System.gc(); System.gc(); System.gc(); System.gc();	
		}
	}
}