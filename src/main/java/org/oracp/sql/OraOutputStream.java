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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.jdbc.OracleCallableStatement;

/**
 * This class makes use of the Oracle <a href=
 * "https://docs.oracle.com/database/121/ARPLS/u_file.htm#ARPLS069">DBMS.UTL_FILE</a>
 * package to transfer data to files contained in directory objects.
 * @see OraFile
 * @author cjuliano
 */
public class OraOutputStream extends OutputStream
{
    private static final Logger LOG      = LoggerFactory.getLogger(OraOutputStream.class);
    private final Connection    _dbc;
    private final OraFile       _file;
    private long                _filePos = 0;

    /**
     * Constructor
     */
    OraOutputStream(OraFile _file) throws SQLException
    {
        this._dbc = _file.getConnection();
        this._file = _file;
        this._file.oraOpen("wb");
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

    @Override
    public void write(int arg0)
            throws IOException
    {
        throw new IOException("Not implimented!");
    }

    @Override
    public void write(byte[] _buf, int _offset, int _length)
            throws IOException
    {
        try
        {
            oraWrite(_buf, _offset, _length);
        }
        catch(SQLException _ex)
        {
            throw new IOException(_ex.getMessage(), _ex);
        }
    }

    /**
     * Calls UTL_FILE.PUT_RAW procedure.
     * @param _buf input data
     * @param _offset offset into buffer
     * @param _length number of bytes to write
     */
    protected void oraWrite(byte[] _buf, int _offset, int _length)
            throws SQLException
    {
        //UTL_FILE.PUT_RAW (
        //   file          IN    UTL_FILE.FILE_TYPE,
        //   buffer        IN    RAW, 
        //   autoflush     IN    BOOLEAN DEFAULT FALSE);

        StringBuilder _sb = new StringBuilder();
        _sb.append("DECLARE ");
        _sb.append("v_fp UTL_FILE.FILE_TYPE; ");
        _sb.append("BEGIN ");
        _sb.append("v_fp.id := ?; ");
        _sb.append("v_fp.datatype := ?; ");
        _sb.append("UTL_FILE.PUT_RAW (v_fp, ?); ");
        _sb.append("END; ");

        try(OracleCallableStatement _cs = (OracleCallableStatement)_dbc.prepareCall(_sb.toString()))
        {
            _cs.setInt(1, this._file.getOraId());
            _cs.setInt(2, this._file.getOraType());
            InputStream _is = new ByteArrayInputStream(_buf, _offset, _length);
            _cs.setBinaryStream(3, _is, _length);
            LOG.debug("UTL_FILE.PUT_RAW(id={}, offset={}, len={})", this._file.getOraId(), _offset, _length);
            _cs.execute();
            _filePos += _length;
        }
    }
}
