/**
 * ORACP - Database Copy utility
 *
 *  Copyright 2016 by Chad Juliano
 *
 *  Licensed under GNU Lesser General Public License v3.0 only.
 *  Some rights reserved. See LICENSE.
 *
 * @license LGPL-3.0 <http://spdx.org/licenses/LGPL-3.0>
 */

package org.oracp.sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.jdbc.OracleCallableStatement;

/**
 * Represents a file located within an Oracle Directory object. This is used in
 * combination with OraInputStream to transfer files using the <a href=
 * "https://docs.oracle.com/database/121/ARPLS/u_file.htm#ARPLS069">DBMS.UTL_FILE</a>
 * package.
 * @see OraInputStream
 * @author Chad Juliano
 */
public class OraFile
{
	private static final Logger	LOG					= LoggerFactory.getLogger(OraFile.class);
	private static final int	MAX_SQL_BUF			= 32767;
	private final static long	PROGRESS_DELAY_MS	= 1000;

	private final byte[]		_buf				= new byte[MAX_SQL_BUF];
	private final Connection	_dbc;
	private final String		_dir;
	private String				_fileName;

	private int					_length				= 0;
	private boolean				_exists				= false;
	private int					_fileId;
	private int					_fileType;

	/**
	 * Constructor
	 * @param _dbc JDBC connection
	 * @param _dir Directoy object.
	 * @param _fileName File within directory.
	 * @throws SQLException
	 */
	public OraFile(Connection _dbc, String _dir, String _fileName) throws SQLException
	{
		this._dir = _dir;
		this._dbc = _dbc;
		this._fileName = _fileName;
		readAttr();
	}

	/**
	 * Constructor
	 * @param _dbc JDBC connection
	 * @param _dir Directoy object.
	 */
	public OraFile(Connection _dbc, String _dir)
	{
		this._dir = _dir;
		this._dbc = _dbc;
	}

	/**
	 * The length of the file.
	 * @return
	 */
	public int length()
	{
		return this._length;
	}

	/**
	 * Indicates if the file exists in the directory.
	 * @return
	 */
	public boolean exists()
	{
		return this._exists;
	}

	/**
	 * Get the JDBC connection.
	 * @return
	 */
	public Connection getConnection()
	{
		return this._dbc;
	}

	/**
	 * Get the directory object.
	 * @return
	 */
	public String getDirectory()
	{
		return this._dir;
	}

	/**
	 * Get the filename.
	 * @return
	 */
	public String getFileName()
	{
		return this._fileName;
	}

	@Override
	public String toString()
	{
		return String.format("%s/%s", this._dir, this._fileName);
	}

	/**
	 * Get the ID of the file for UTL_FILE.
	 * @return
	 */
	protected int getOraId()
	{
		return this._fileId;
	}

	/**
	 * Get the file type for UTL_FILE.
	 * @return
	 */
	protected int getOraType()
	{
		return this._fileType;
	}

	/**
	 * Calls UTL_FILE.FGETATTR procedure.
	 * @throws SQLException
	 */
	protected void readAttr()
			throws SQLException
	{
		// UTL_FILE.FGETATTR(
		// location IN VARCHAR2,
		// filename IN VARCHAR2,
		// exists OUT BOOLEAN,
		// file_length OUT NUMBER,
		// blocksize OUT NUMBER);

		StringBuilder _sb = new StringBuilder();
		_sb.append("DECLARE ");
		_sb.append("v_bool BOOLEAN; ");
		_sb.append("v_exists BINARY_INTEGER; ");
		_sb.append("BEGIN ");
		_sb.append("UTL_FILE.FGETATTR(?, ?, v_bool, ?, ?); ");
		_sb.append("IF (v_bool=TRUE) THEN v_exists := 1; ELSE v_exists := 0; END IF; ");
		_sb.append("? := v_exists; ");
		_sb.append("END; ");

		try(CallableStatement _cs = _dbc.prepareCall(_sb.toString()))
		{
			_cs.setString(1, this._dir);
			_cs.setString(2, this._fileName);
			_cs.registerOutParameter(3, Types.INTEGER);
			_cs.registerOutParameter(4, Types.INTEGER);
			_cs.registerOutParameter(5, Types.INTEGER);
			_cs.execute();
			this._length = _cs.getInt(3);
			int _blocksize = _cs.getInt(4);
			int _intExists = _cs.getInt(5);
			if(_intExists > 0)
			{
				this._exists = true;
			}
			LOG.debug("(ex={}, fl={}, bs={}) = UTL_FILE.FGETATTR()", Boolean.toString(this._exists), this._length,
					_blocksize);
		}
	}

	/**
	 * Calls UTL_FILE.FOPEN procedure.
	 * @throws SQLException
	 */
	protected void oraOpen(String _openMode)
			throws SQLException
	{
		// UTL_FILE.FOPEN (
		// location IN VARCHAR2,
		// filename IN VARCHAR2,
		// open_mode IN VARCHAR2,
		// max_linesize IN BINARY_INTEGER)
		// RETURN file_type;

		StringBuilder _sb = new StringBuilder();
		_sb.append("DECLARE ");
		_sb.append("v_fp UTL_FILE.FILE_TYPE; ");
		_sb.append("BEGIN ");
		_sb.append("v_fp := UTL_FILE.FOPEN(?, ?, ?); ");
		_sb.append("? := v_fp.id; ");
		_sb.append("? := v_fp.datatype; ");
		_sb.append("END; ");

		try(CallableStatement _cs = _dbc.prepareCall(_sb.toString()))
		{
			_cs.setString(1, this.getDirectory());
			_cs.setString(2, this.getFileName());
			_cs.setString(3, _openMode);
			_cs.registerOutParameter(4, Types.INTEGER);
			_cs.registerOutParameter(5, Types.INTEGER);

			LOG.debug("UTL_FILE.FOPEN(dir={}, file={}, mode={})", _fileId, _fileType, _openMode);
			_cs.execute();
			_fileId = _cs.getInt(4);
			_fileType = _cs.getInt(5);
		}
	}

	/**
	 * Indicates of this stream has been opened. Calls UTL_FILE.IS_OPEN
	 * procedure.
	 * @return
	 * @throws SQLException
	 */
	public boolean isOpen()
			throws SQLException
	{
		// UTL_FILE.IS_OPEN (
		// file IN FILE_TYPE)
		// RETURN BOOLEAN;

		StringBuilder _sb = new StringBuilder();
		_sb.append("DECLARE ");
		_sb.append("v_bool BOOLEAN; ");
		_sb.append("v_is_open BINARY_INTEGER; ");
		_sb.append("v_fp UTL_FILE.FILE_TYPE; ");
		_sb.append("BEGIN ");
		_sb.append("v_fp.id := ?; ");
		_sb.append("v_fp.datatype := ?; ");
		_sb.append("v_bool := UTL_FILE.IS_OPEN(v_fp); ");
		_sb.append("IF (v_bool=TRUE) THEN v_is_open := 1; ELSE v_is_open := 0; END IF; ");
		_sb.append("? := v_is_open; ");
		_sb.append("END; ");

		boolean _result = false;
		try(OracleCallableStatement _cs = (OracleCallableStatement)_dbc.prepareCall(_sb.toString()))
		{
			_cs.setInt(1, this._fileId);
			_cs.setInt(2, this._fileType);
			_cs.registerOutParameter(3, Types.INTEGER);

			LOG.debug("UTL_FILE.IS_OPEN(id={}, type={})", this._fileId, this._fileType);
			_cs.execute();
			int _isOpen = _cs.getInt(3);
			if(_isOpen > 0)
			{
				_result = true;
			}
		}

		return _result;
	}

	/**
	 * Calls UTL_FILE.FCLOSE procedure.
	 * @throws SQLException
	 */
	protected void close()
			throws SQLException
	{
		// UTL_FILE.FCLOSE (
		// file IN OUT FILE_TYPE);
		StringBuilder _sb = new StringBuilder();
		_sb.append("DECLARE ");
		_sb.append("v_fp UTL_FILE.FILE_TYPE; ");
		_sb.append("BEGIN ");
		_sb.append("v_fp.id := ?; ");
		_sb.append("v_fp.datatype := ?; ");
		_sb.append("UTL_FILE.FCLOSE(v_fp); ");
		_sb.append("END; ");

		try(CallableStatement _cs = _dbc.prepareCall(_sb.toString()))
		{
			_cs.setInt(1, this._fileId);
			_cs.setInt(2, this._fileType);
			LOG.debug("UTL_FILE.FCLOSE(id={}, type={})", _fileId, _fileType);
			_cs.execute();
		}
	}

	/**
	 * This is used in combination with the oracp_list_dir function to retrieve
	 * the contents of a directory object.
	 * @return
	 * @throws SQLException
	 */
	public String[] listFiles()
			throws SQLException
	{
		StringBuilder _sb = new StringBuilder();
		_sb.append("select column_value as file_name ");
		_sb.append("from table(sys.oracp_list_dir(?)) ");

		LOG.debug("SQL: {}", _sb.toString());
		ArrayList<String> _results = new ArrayList<>();

		try(PreparedStatement _stmt = _dbc.prepareStatement(_sb.toString()))
		{
			_stmt.setString(1, this._dir);
			try(ResultSet _rSet = _stmt.executeQuery())
			{
				while(_rSet.next())
				{
					String _dirFile = _rSet.getString(1);
					_results.add(_dirFile);
				}
			}
		}
		return _results.toArray(new String[0]);
	}

	/**
	 * This is used in combination with {@linkplain OraFile#getContents} to give
	 * feedback on the progress of a file copy.
	 */
	public interface Progress
	{
		/**
		 * Called periodically during the copy of a file.
		 * @param _partBytes Partial bytes transferred.
		 * @param _totalBytes Total bytes in the file.
		 */
		void update(int _partBytes, int _totalBytes);
	}

	/**
	 * Copy the contents of this file to an output stream.
	 * @param _os Stream to output to.
	 * @param _progress Optional callback routine for progress.
	 * @throws Exception
	 */
	public void getContents(OutputStream _os, Progress _progress)
			throws Exception
	{
		try(OraInputStream _is = new OraInputStream(this))
		{
			transfer(_is, _os, _progress);
		}
	}

	/**
	 * @param _is Source data stream
	 * @param _progress Optional callback routine for progress.
	 * @throws Exception
	 */
	public void putContents(InputStream _is, Progress _progress)
			throws Exception
	{
		try(OraOutputStream _os = new OraOutputStream(this))
		{
			transfer(_is, _os, _progress);
		}
	}

	/**
	 * Copy the contents of this file to an output stream.
	 * @param _is Source data stream
	 * @param _os Destination for data
	 * @param _progress Optional callback routine for progress.
	 * @throws IOException
	 */
	public void transfer(InputStream _is, OutputStream _os, Progress _progress)
			throws IOException
	{
		int _numTotal = 0;
		long _lastTime = 0;
		int _fileSize = _is.available();
		while(true)
		{
			int _numRead = _is.read(this._buf);
			if(_numRead > 0)
			{
				_numTotal += _numRead;
				_os.write(this._buf, 0, _numRead);
			}

			if(_progress != null)
			{
				long _timeDiff = System.currentTimeMillis() - _lastTime;
				if(_timeDiff > PROGRESS_DELAY_MS || _numRead <= 0)
				{
					_lastTime = System.currentTimeMillis();
					_progress.update(_numTotal, _fileSize);
				}
			}

			if(_numRead <= 0)
			{
				break;
			}
		}
	}
}
