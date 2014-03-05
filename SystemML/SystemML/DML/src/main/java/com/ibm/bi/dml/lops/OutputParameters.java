/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2013
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.lops;

import com.ibm.bi.dml.hops.HopsException;

/**
 * class to maintain output parameters for a lop.
 * 
 */

public class OutputParameters 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	public enum Format {
		TEXT, BINARY, MM, CSV
	};

	boolean blocked_representation = true;
	long num_rows = -1;
	long num_cols = -1;
	long _nnz = -1;	
	long num_rows_in_block = -1;
	long num_cols_in_block = -1;
	String file_name = null;
	String file_label = null;

	Format matrix_format = Format.BINARY;
	
	public String getFile_name() {
		return file_name;
	}

	public String getLabel() {
		return file_label;
	}

	public void setFile_name(String fileName) {
		file_name = fileName;
	}

	public void setLabel(String label) {
		file_label = label;
	}

	public void setDimensions(long rows, long cols, long rows_per_block, long cols_per_block, long nnz) throws HopsException {
		num_rows = rows;
		num_cols = cols;
		_nnz = nnz;
		num_rows_in_block = rows_per_block;
		num_cols_in_block = cols_per_block;

		if ( num_rows_in_block == 0 && num_cols_in_block == 0 ) {
			blocked_representation = false;
		}
		else if (num_rows_in_block == -1 && num_cols_in_block == -1) {
			blocked_representation = false;
 		}
		else if ( num_rows_in_block > 0 && num_cols_in_block > 0 ) {
			blocked_representation = true;
		}
		else {
			throw new HopsException("In OutputParameters Lop, Invalid values for blocking dimensions: [" + num_rows_in_block + "," + num_cols_in_block +"].");
		}
	}

	public void setFormat(Format fmt) {
		matrix_format = fmt;

	}

	public Format getFormat() {
		return matrix_format;
	}

	public boolean isBlocked_representation() {
		return blocked_representation;
	}

	public Long getNnz() {
		return _nnz;
	}
	
	public Long getNum_rows() {
		return num_rows;
	}

	public Long getNum_cols() {
		return num_cols;
	}

	public Long get_rows_in_block() {
		return num_rows_in_block;
	}

	public Long get_cols_in_block() {
		return num_cols_in_block;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("rows=" + getNum_rows() + Lop.VALUETYPE_PREFIX);
		sb.append("cols=" + getNum_cols() + Lop.VALUETYPE_PREFIX);
		sb.append("nnz=" + getNnz() + Lop.VALUETYPE_PREFIX);
		sb.append("rowsInBlock=" + get_rows_in_block() + Lop.VALUETYPE_PREFIX);
		sb.append("colsInBlock=" + get_cols_in_block() + Lop.VALUETYPE_PREFIX);
		sb.append("isBlockedRepresentation=" + isBlocked_representation() + Lop.VALUETYPE_PREFIX);
		sb.append("format=" + getFormat() + Lop.VALUETYPE_PREFIX);
		sb.append("label=" + getLabel() + Lop.VALUETYPE_PREFIX);
		sb.append("filename=" + getFile_name());
		return sb.toString();
	}
}