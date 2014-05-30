/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2014
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */


package com.ibm.bi.dml.runtime.matrix.mapred;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.OutputCollector;

import com.ibm.bi.dml.runtime.matrix.io.AdaptivePartialBlock;
import com.ibm.bi.dml.runtime.matrix.io.IJV;
import com.ibm.bi.dml.runtime.matrix.io.MatrixBlock;
import com.ibm.bi.dml.runtime.matrix.io.MatrixIndexes;
import com.ibm.bi.dml.runtime.matrix.io.PartialBlock;
import com.ibm.bi.dml.runtime.matrix.io.SparseRowsIterator;
import com.ibm.bi.dml.runtime.matrix.io.TaggedAdaptivePartialBlock;

/**
 * 
 * 
 */
public class ReblockBuffer 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2014\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	//default buffer size: 5M -> 5M * 3x8B = 120MB 
	public static final int DEFAULT_BUFFER_SIZE = 5000000;
	public static final int BLOCK_THRESHOLD = 16;
	
	private long[]   _buffRows = null;
	private long[]   _buffCols = null;
	private double[] _buffVals = null;
	
	private int _bufflen = -1;
	private int _count = -1;
	
	private long _rlen = -1;
	private long _clen = -1;
	private int _brlen = -1;
	private int _bclen = -1;
	
	public ReblockBuffer( long rlen, long clen, int brlen, int bclen )
	{
		this( DEFAULT_BUFFER_SIZE, rlen, clen, brlen, bclen );
	}
	
	public ReblockBuffer( int buffersize, long rlen, long clen, int brlen, int bclen  )
	{
		//System.out.println("Creating reblock buffer of size "+buffersize);
		
		_bufflen = buffersize;
		_count = 0;
		
		_buffRows = new long[ _bufflen ];
		_buffCols = new long[ _bufflen ];
		_buffVals = new double[ _bufflen ];
		
		_rlen = rlen;
		_clen = clen;
		_brlen = brlen;
		_bclen = bclen;
	}
	
	/**
	 * 
	 * @param r
	 * @param c
	 * @param v
	 */
	public void appendCell( long r, long c, double v )
	{
		_buffRows[ _count ] = r;
		_buffCols[ _count ] = c;
		_buffVals[ _count ] = v;
		_count++;
	}
	
	/**
	 * 
	 * @param r_offset
	 * @param c_offset
	 * @param inBlk
	 * @param index
	 * @param out
	 * @throws IOException
	 */
	public void appendBlock(long r_offset, long c_offset, MatrixBlock inBlk, byte index, OutputCollector<Writable, Writable> out ) 
		throws IOException
	{
		if( inBlk.isInSparseFormat() ) //SPARSE
		{
			SparseRowsIterator iter = inBlk.getSparseRowsIterator();
			while( iter.hasNext() )
			{
				IJV cell = iter.next();
				_buffRows[ _count ] = r_offset + cell.i;
				_buffCols[ _count ] = c_offset + cell.j;
				_buffVals[ _count ] = cell.v;
				_count++;
				
				//check and flush if required
				if( _count ==_bufflen )
					flushBuffer(index, out);
			}
		}
		else //DENSE
		{
			//System.out.println("dense merge with ro="+r_offset+", co="+c_offset);
			int rlen = inBlk.getNumRows();
			int clen = inBlk.getNumColumns();
			for( int i=0; i<rlen; i++ )
				for( int j=0; j<clen; j++ )
				{
					double val = inBlk.getValueDenseUnsafe(i, j);
					if( val !=0 )
					{
						_buffRows[ _count ] = r_offset + i;
						_buffCols[ _count ] = c_offset + j;
						_buffVals[ _count ] = val;
						_count++;
						
						//check and flush if required
						if( _count ==_bufflen )
							flushBuffer(index, out);
					}
				}
		}
	}
	
	public int getSize()
	{
		return _count;
	}
	
	public int getCapacity()
	{
		return _bufflen;
	}
	
	
	/**
	 * 
	 * @param index
	 * @param out
	 * @throws IOException
	 */
	public void flushBuffer( byte index, OutputCollector<Writable, Writable> out ) 
		throws IOException
	{
		if( _count == 0 )
			return;
		
		//Step 1) scan for number of created blocks
		HashSet<MatrixIndexes> IX = new HashSet<MatrixIndexes>();
		MatrixIndexes tmpIx = new MatrixIndexes();
		for( int i=0; i<_count; i++ )
		{
			long bi = getBlockIndex(_buffRows[i], _brlen);
			long bj = getBlockIndex(_buffCols[i], _bclen);
			
			tmpIx.setIndexes(bi, bj);
			if( !IX.contains(tmpIx) ){ //probe
				IX.add(tmpIx);
				tmpIx = new MatrixIndexes();
			}
		}
		
		//Step 2) decide on intermediate representation
		long blockedSize = ((long)IX.size())*_brlen*4 + 12*_count; //worstcase
		long cellSize = 24 * _count;
		boolean blocked = ( IX.size()<=BLOCK_THRESHOLD && blockedSize<=cellSize );
		
		//Step 3)
		TaggedAdaptivePartialBlock outTVal = new TaggedAdaptivePartialBlock();
		AdaptivePartialBlock outVal = new AdaptivePartialBlock();
		outTVal.setTag(index);
		outTVal.setBaseObject(outVal); //setup wrapper writables
		if( blocked ) //output binaryblock
		{
			//create intermediate blocks
			boolean sparse = MatrixBlock.evalSparseFormatInMemory(_brlen, _bclen, _count/IX.size());					      
			HashMap<MatrixIndexes,MatrixBlock> blocks = new HashMap<MatrixIndexes,MatrixBlock>();
			
			for( MatrixIndexes ix : IX )
			{
				blocks.put(ix, new MatrixBlock(
						Math.min(_brlen, (int)(_rlen-(ix.getRowIndex()-1)*_brlen)),
						Math.min(_bclen, (int)(_clen-(ix.getColumnIndex()-1)*_bclen)),
						sparse));
			}
			
			//put values into blocks
			for( int i=0; i<_count; i++ )
			{
				long bi = getBlockIndex(_buffRows[i], _brlen);
				long bj = getBlockIndex(_buffCols[i], _bclen);
				int ci = getIndexInBlock(_buffRows[i], _brlen);
				int cj = getIndexInBlock(_buffCols[i], _bclen);
				tmpIx.setIndexes(bi, bj);
				MatrixBlock blk = blocks.get(tmpIx);
				blk.appendValue(ci, cj, _buffVals[i]); //sort on output
			}
			
			//output blocks
			for( Entry<MatrixIndexes,MatrixBlock> e : blocks.entrySet() )
			{
				MatrixIndexes ix = e.getKey();
				MatrixBlock blk = e.getValue();
				if( blk.isInSparseFormat() )
					blk.sortSparseRows();
				outVal.set(blk); //in outTVal;
				out.collect(ix, outTVal);
			}
			
		}
		else //output binarycell
		{
			PartialBlock tmpVal = new PartialBlock();
			outVal.set(tmpVal);
			for( int i=0; i<_count; i++ )
			{
				long bi = getBlockIndex(_buffRows[i], _brlen);
				long bj = getBlockIndex(_buffCols[i], _bclen);
				int ci = getIndexInBlock(_buffRows[i], _brlen);
				int cj = getIndexInBlock(_buffCols[i], _bclen);
				tmpIx.setIndexes(bi, bj);
				tmpVal.set(ci, cj, _buffVals[i]); //in outVal, in outTVal
				out.collect(tmpIx, outTVal);
			}
		}
		
		_count = 0;
	}
	
	/**
	 * 
	 * @param ix
	 * @param blen
	 * @return
	 */
	private static long getBlockIndex( long ix, int blen )
	{
		return (ix-1)/blen+1;
	}
	
	/**
	 * 
	 * @param ix
	 * @param blen
	 * @return
	 */
	public static int getIndexInBlock( long ix, int blen )
	{
		return (int)((ix-1)%blen);
	}
}
