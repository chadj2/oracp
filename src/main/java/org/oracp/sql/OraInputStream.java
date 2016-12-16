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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.jdbc.OracleCallableStatement;

/**
 * This class makes use of the Oracle <a href=
 * "https://docs.oracle.com/database/121/ARPLS/u_file.htm#ARPLS069">DBMS.UTL_FILE</a>
 * package to transfer data from files contained in directory objects.
 * @see OraFile
 * @author Chad Juliano
 */
public class OraInputStream extends InputStream
{
	private static final Logger	LOG			= LoggerFactory.getLogger(OraInputStream.class);
	private final Connection	_dbc;
	private final OraFile		_file;

	private int					_filePos	= 0;

	/**
	 * Constructor.
	 * @param _file A file contained within an Oracle Directory object.
	 * @throws SQLException
	 */
	public OraInputStream(OraFile _file) throws SQLException
	{
		this._dbc = _file.getConnection();
		this._file = _file;
		this._file.oraOpen("rb");
	}

	@Override
	public int available()
			throws IOException
	{
		return _file.length() - _filePos;
	}

	@Override
	public int read()
			throws IOException
	{
		throw new IOException("Not implimented!");
	}

	@Override
	public int read(byte[] _buf)
			throws IOException
	{
		int _bytesRead;
		try
		{
			_bytesRead = oraRead(_buf);
		}
		catch(SQLException _ex)
		{
			throw new IOException(_ex.getMessage(), _ex);
		}
		return _bytesRead;
	}

	@Override
	public void close()
			throws IOException
	{
		try
		{
			_file.close();
		}
		catch(SQLException _ex)
		{
			throw new IOException(_ex.getMessage(), _ex);
		}
	}

	/**
	 * Calls UTL_FILE.GET_RAW procedure.
	 * @param _buf
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	private int oraRead(byte[] _buf)
			throws SQLException, IOException
	{
		if(_filePos >= _file.length())
		{
			// we are at the end
			return -1;
		}

		// UTL_FILE.GET_RAW (
		// fid IN utl_file.file_type,
		// r OUT NOCOPY RAW,
		// len IN PLS_INTEGER DEFAULT NULL);

		StringBuilder _sb = new StringBuilder();
		_sb.append("DECLARE ");
		_sb.append("v_fp UTL_FILE.FILE_TYPE; ");
		_sb.append("BEGIN ");
		_sb.append("v_fp.id := ?; ");
		_sb.append("v_fp.datatype := ?; ");
		_sb.append("UTL_FILE.GET_RAW (v_fp, ?, ?); ");
		_sb.append("END; ");

		int _numRead = -1;
		try(OracleCallableStatement _cs = (OracleCallableStatement)_dbc.prepareCall(_sb.toString()))
		{
			_cs.setInt(1, this._file.getOraId());
			_cs.setInt(2, this._file.getOraType());
			_cs.registerOutParameter(3, Types.BINARY);
			_cs.setInt(4, _buf.length);
			_cs.execute();
			//LOG.debug("UTL_FILE.GET_RAW(id={}, type={}, len={})", this._fileId, this._fileType, _buf.length);

			try(InputStream _is = _cs.getBinaryStream(3))
			{
				_numRead = _is.read(_buf);
			}
			_filePos += _numRead;
		}

		return _numRead;
	}
}
