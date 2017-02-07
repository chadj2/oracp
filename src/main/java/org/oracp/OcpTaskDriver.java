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

package org.oracp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Arrays;

import org.oracp.sql.OraFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taskdriver.TaskDefinition;
import org.taskdriver.TaskDriver;
import org.taskdriver.TaskDriverOptions;
import org.taskdriver.demo.TaskDriverDemo;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;

/**
 * <b>ORACP File Utility</b> <br>
 * This is a command line based program to perform file operations over an
 * Oracle JDBC connection. <br>
 * A primary use case is the copy of dump files to and from the database for use
 * with datapump import and export. It can be a simpler alternative to other
 * approaches like SFTP.
 * @author Chad Juliano
 */
public class OcpTaskDriver extends TaskDriver<OcpTaskDriver.OcpTaskEnum>
{
    private static final Logger LOG          = LoggerFactory.getLogger(OcpTaskDriver.class);
    private final DecimalFormat _dFormat     = new DecimalFormat("0.00");
    private String              _optionalOpt = null;
    private String              _requiredOpt = null;
    private boolean             _force       = false;
    private OracleDataSource    _ods         = null;
    private String              _sourceDbDir = null;
    private long                _lastTimeMs;
    private long                _lastBytes;

    enum OcpTaskEnum
    {
        GET,
        LIST,
        PUT;
    };

    /**
     * Constructor.
     */
    public OcpTaskDriver()
    {
        addOption("url", "Oracle JDBC URL (jdbc:oracle:thin:@//hostname:port/service)", null, true);
        addOption("user", "DB username", "u", true);
        addOption("passwd", "DB password", "p", true);
        addOption("db-dir", "DB directory object", "s", true);
        addOption("force", "Force overwrite of destination.", "f", false);

        addTask(OcpTaskEnum.GET, "Transfer a file from the database to a local directory.")
            .addArg("REMOTE-FILE")
            .addArg("LOCAL-DIR");
        addTask(OcpTaskEnum.LIST, "List the contents of an oracle Directory Object.");
        addTask(OcpTaskEnum.PUT, "Transfer a local file to a database directory.")
            .addArg("LOCAL-FILE");
    }

    @Override
    protected void printHelpFooter(PrintWriter _pw)
    {
        _pw.println("This program is a command line utility to execute file operations over an");
        _pw.println("Oracle JDBC connection.");
        _pw.println("Contact Chad Juliano<chadj@dallaslife.net> for feedback or assistance.");
    }

    /**
     * Parse command line arguments into member variables.
     * @param _args
     * @throws Exception
     */
    @Override
    protected void handleGetArgs(TaskDriverOptions _cmdArgs)
            throws Exception
    {
        if(_cmdArgs.hasOption("d"))
        {
            setPackageDebug(this.getClass().getPackage());
            setPackageDebug(OraFile.class.getPackage());
        }

        if(_cmdArgs.hasOption("f"))
        {
            this._force = true;
        }

        // get database options
        _ods = new OracleDataSource();
        _ods.setURL(_cmdArgs.getRequiredOption("url"));
        _ods.setUser(_cmdArgs.getRequiredOption("u"));
        _ods.setPassword(_cmdArgs.getRequiredOption("p"));

        // get database directory object name.
        this._sourceDbDir = _cmdArgs.getRequiredOption("s");
    }

    /**
     * Task router.
     * @param _dbc
     * @throws Exception
     */
    @Override
    protected void handleDoTask(OcpTaskEnum _task, TaskDefinition<OcpTaskEnum> _taskDef)
            throws Exception
    {
        LOG.info("Opening Connection...");
        try(OracleConnection _dbc = (OracleConnection)_ods.getConnection())
        {
            String _dbVersion = getDbVersion(_dbc);
            LOG.info("{}", _dbVersion);

            LOG.info("* Starting task: {}", _task);
            _lastTimeMs = System.currentTimeMillis();
            _lastBytes = 0;
            switch(_task)
            {
                case GET:
                    String _remoteFile = _taskDef.takeArg();
                    String _localDir =  _taskDef.takeArg();
                    doGet(_dbc, _remoteFile, _localDir);
                    break;
                case LIST:
                    doList(_dbc);
                    break;
                case PUT:
                    String _localFile =  _taskDef.takeArg();
                    doPut(_dbc, _localFile);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Execute the GET Task.
     * @param _destDirStr
     * @param _sourceFile
     * @param _dbc
     * @throws Exception
     */
    private void doGet(OracleConnection _dbc, String _sourceFile, String _destDirStr)
            throws Exception
    {
        File _destDir = new File(_destDirStr);
        if(!_destDir.isDirectory())
        {
            throw new IOException("Could not find destination dir: " + _destDir.getCanonicalPath());
        }

        OraFile _oraFile = new OraFile(_dbc, _sourceDbDir, _sourceFile);
        double _sizeMb = (double)_oraFile.length() / (double)(1024 * 1024);
        LOG.info("Source: {} ({} MB)", _oraFile, _dFormat.format(_sizeMb));

        File _localFile = new File(_destDir, _sourceFile);
        LOG.info("Destination: <{}>", _localFile.getCanonicalPath());

        try(FileOutputStream _os = new FileOutputStream(_localFile))
        {
            _oraFile.getContents(_os, _progress);
        }
        LOG.info("Transfer Complete!");
    }

    /**
     * Execute the LIST task.
     * @param _dbc
     * @throws SQLException
     */
    private void doList(OracleConnection _dbc)
            throws SQLException
    {
        OraFile _oraFile = new OraFile(_dbc, _sourceDbDir);
        String[] _fileList = _oraFile.listFiles();

        LOG.info("Total {} files in {}:", _fileList.length, _sourceDbDir);
        Arrays.stream(_fileList).sorted().forEach(_file ->
        {
            LOG.info("  {}", _file);
        });
    }

    /**
     * Execute the PUT task.
     * @param _dbc
     * @param _sourceFile
     * @throws Exception
     */
    private void doPut(OracleConnection _dbc, String _sourceFile)
            throws Exception
    {
        File _localFile = new File(_sourceFile);
        if(!_localFile.isFile())
        {
            throw new IOException("Could not find source file: " + _localFile.getCanonicalPath());
        }

        double _sizeMb = (double)_localFile.length() / (double)(1024 * 1024);
        LOG.info("Source: {} ({} MB)", _localFile.getCanonicalPath(), _dFormat.format(_sizeMb));

        OraFile _oraFile = new OraFile(_dbc, _sourceDbDir, _localFile.getName());
        if(_oraFile.exists() && !this._force)
        {
            throw new Exception("Detination file already exists: " + _oraFile);
        }
        else
        {
            LOG.warn("Force overwrite of destination file!");
        }

        LOG.info("Destination: <{}>", _oraFile);

        try(FileInputStream _is = new FileInputStream(_localFile))
        {
            _oraFile.putContents(_is, _progress);
        }

        LOG.info("Transfer Complete!");
    }

    /**
     * Default progress message.
     */
    OraFile.Progress _progress = (_partBytes, _totalBytes) ->
    {
        long _diffMs = System.currentTimeMillis() - this._lastTimeMs;
        long _diffBytes = _partBytes - this._lastBytes;
        LOG.debug("diffBytes=<{}>, diffMs=<{}>", _diffBytes, _diffMs);

        double _pctComplete = ((double)_partBytes / (double)_totalBytes) * 100;
        double _rateKbSec = ((double)_diffBytes / 1024) / ((double)_diffMs / 1000);

        LOG.info("  {}% ({}/{} KB) ({} KB/sec)",
                _dFormat.format(_pctComplete),
                _partBytes / 1024,
                _totalBytes / 1024,
                _dFormat.format(_rateKbSec));

        _lastTimeMs += _diffMs;
        _lastBytes = _partBytes;
    };

    private static String getDbVersion(OracleConnection _dbc)
            throws SQLException
    {
        DatabaseMetaData _props = _dbc.getMetaData();
        String _dbVersion = _props.getDatabaseProductVersion();

        // trim everything after the first line
        _dbVersion = _dbVersion.replaceAll("\\v(.*)", "");
        return _dbVersion;
    }

    /**
     * Program entry point.
     * @param _args
     */
    @SuppressWarnings("unused")
    public static void main(String _args[])
    {
        try
        {
            new OcpTaskDriver().run(_args);
        }
        catch(Exception _ex)
        {
            System.exit(1);
        }
        System.exit(0);
    }
}
