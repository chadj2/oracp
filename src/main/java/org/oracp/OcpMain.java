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
import java.io.StringWriter;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.oracp.sql.OraFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
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
public class OcpMain
{
	enum OcpTask
	{
		LIST("list", "List the contents of an oracle Directory Object."),
		GET("get FILENAME DEST", "Transfer a file from the database to a local directory."),
		PUT("put FILENAME", "Transfer a local file to a database directory.");

		private final String	_usage;
		private final String	_description;

		OcpTask(String _usage, String _description)
		{
			this._usage = _usage;
			this._description = _description;
		}

		public String getUsage()
		{
			return this._usage;
		}

		public String getDescription()
		{
			return this._description;
		}
	};

	private static final Logger	LOG				= LoggerFactory.getLogger(OcpMain.class);

	private final DecimalFormat	_dFormat		= new DecimalFormat("0.00");
	private final Options		_options		= new Options();

	private OracleDataSource	_ods			= null;
	private String				_sourceDbDir	= null;
	private OcpTask				_task			= null;
	private ArrayDeque<String>	_argQueue		= null;
	private long				_lastTimeMs;
	private long				_lastBytes;
	private boolean				_force			= false;

	/**
	 * Program entry point.
	 * @param args
	 */
	@SuppressWarnings("unused")
	public static void main(String args[])
	{
		OcpMain _inst = new OcpMain();
		try
		{
			_inst.run(args);
		}
		catch(Exception _ex)
		{
			System.exit(1);
		}
	}

	/**
	 * Start the process.
	 * @param _args Command line parameters.
	 * @throws Exception
	 */
	public void run(String[] _args)
			throws Exception
	{
		try
		{
			parseArgs(_args);
			LOG.info("Opening Connection...");
			try(OracleConnection _dbc = (OracleConnection)_ods.getConnection())
			{
				DatabaseMetaData _props = _dbc.getMetaData();
				String _dbVersion = _props.getDatabaseProductVersion();
				// trim everything after the first line
				_dbVersion = _dbVersion.replaceAll("\\v(.*)", "");
				LOG.info("{}", _dbVersion);

				doTask(_dbc);
			}
		}
		catch(ParseException _ex)
		{
			LOG.error("Terminaing: {}", _ex.getMessage());
			throw _ex;
		}
		catch(Exception _ex)
		{
			LOG.error("Process Failed: {}", _ex.getMessage(), _ex);
			throw _ex;
		}
	}

	/**
	 * Constructor.
	 */
	public OcpMain()
	{
		_options.addOption("h", "help", false, "print this message");
		_options.addOption(null, "url", true, "Oracle JDBC URL (jdbc:oracle:thin:@//hostname:port/service)");
		_options.addOption("u", "user", true, "DB username");
		_options.addOption("p", "passwd", true, "DB password");
		_options.addOption("s", "db-dir", true, "DB directory object");
		_options.addOption("d", "debug", false, "turn on debug logging");
		_options.addOption("f", "force", false, "Force overwrite of destination.");
		//_opts.addOption(Option.builder("j").longOpt("url")
		//		.desc("Oracle JDBC URL <jdbc:oracle:thin:@//hostname:port/service>").hasArg().build());
	}

	protected void printHelp(PrintWriter _pw)
	{
		String _taskUsage = Arrays.stream(OcpTask.values())
				.map(x -> x.getUsage())
				.collect(Collectors.joining("|", "[", "]"));
		final String _cmdSyntax = String.format("oracp [OPTIONS] %s", _taskUsage);

		final String _header = "This program is a command line utility that can perform " +
				"file operations over an Oracle JDBC connection.";

		String _taskDetails = Arrays.stream(OcpTask.values())
				.map(x -> String.format("   %-18s: %s", x.getUsage(), x.getDescription()))
				.collect(Collectors.joining("\n"));
		String _footer = String.format("\nYou must choose one of the following tasks:\n%s", _taskDetails);

		HelpFormatter _help = new HelpFormatter();
		_help.setWidth(100);

		_help.printHelp(_pw, 100, _cmdSyntax, _header, _options, 3, 5, _footer, false);
	}

	/**
	 * Parse command line arguments into member variables.
	 * @param _args
	 * @throws Exception
	 */
	protected void parseArgs(String[] _args)
			throws Exception
	{
		CommandLineParser _parser = new DefaultParser();
		CommandLine _cmd = _parser.parse(_options, _args);

		if(_cmd.hasOption("d"))
		{
			setLoglevel(Level.DEBUG, this.getClass().getPackage().getName());
			setLoglevel(Level.DEBUG, OraFile.class.getPackage().getName());
		}

		if(_cmd.hasOption("f"))
		{
			this._force = true;
		}

		StringJoiner _argDesc = new StringJoiner(" ", "{", "}");
		Arrays.stream(_args).forEach(str -> _argDesc.add(str));
		LOG.debug("ARGS: {}", _argDesc);

		//if(_cmd.hasOption("h") || _cmd.getArgs().length == 0)
		if(_cmd.hasOption("h"))
		{
			StringWriter _sw = new StringWriter();
			printHelp(new PrintWriter(_sw));
			LOG.info(_sw.toString());
			throw new ParseException("Help option requested");
		}

		// get database options
		_ods = new OracleDataSource();
		_ods.setURL(getRequiredOption(_cmd, "url"));
		_ods.setUser(getRequiredOption(_cmd, "u"));
		_ods.setPassword(getRequiredOption(_cmd, "p"));

		// get database directory object name.
		this._sourceDbDir = getRequiredOption(_cmd, "s");

		// some of these args will be processed by the task
		_argQueue = new ArrayDeque<>(_cmd.getArgList());
		String _taskStr = _argQueue.pollFirst();
		if(_taskStr == null)
		{
			throw new MissingArgumentException("Missing argument: task { list, get }");
		}

		this._task = OcpTask.valueOf(_taskStr.toUpperCase());
		LOG.debug("ARG: TASK = <{}>", this._task);
	}

	void setLoglevel(Level _level, String _loggerName)
	{
		ch.qos.logback.classic.Logger _logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(_loggerName);
		_logger.setLevel(_level);
		_logger.debug("Level {} enabled for: <{}>", _level, _loggerName);
	}

	protected String getRequiredOption(CommandLine _cmd, String _smallOpt)
			throws MissingArgumentException
	{
		String _value = _cmd.getOptionValue(_smallOpt);
		Option _optObj = _options.getOption(_smallOpt);
		if(_value == null)
		{
			String _msg = String.format("Missing option: --%s <%s>", _optObj.getLongOpt(), _optObj.getDescription());
			throw new MissingArgumentException(_msg);
		}

		LOG.debug("OPTION: {} = <{}>", _optObj.getLongOpt(), _value);
		return _value;
	}

	/**
	 * Task router.
	 * @param _dbc
	 * @throws Exception
	 */
	protected void doTask(OracleConnection _dbc)
			throws Exception
	{
		LOG.info("* Starting task: {}", this._task);
		_lastTimeMs = System.currentTimeMillis();
		_lastBytes = 0;
		switch(this._task)
		{
			case GET:
				doGet(_dbc);
				break;
			case LIST:
				doList(_dbc);
				break;
			case PUT:
				doPut(_dbc);
				break;
			default:
				break;
		}
	}

	/**
	 * Get the next parameter (not option) that was passed on the command line.
	 * @param _paramName Paramter name used for logging and error descriptions.
	 * @return
	 * @throws MissingArgumentException
	 */
	protected String takeNextParam(String _paramName)
			throws MissingArgumentException
	{
		String _paramVal = _argQueue.pollFirst();
		if(_paramVal == null)
		{
			throw new MissingArgumentException("Missing parameter: " + _paramName);
		}
		LOG.debug("ARG: {} = <{}>", _paramName, _paramVal);
		return _paramVal;
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
		Arrays.stream(_fileList).sorted().forEach(_file -> LOG.info("  {}", _file));
	}

	/**
	 * Default progress message.
	 */
	OraFile.Progress _progress = (_partBytes, _totalBytes) -> {
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

	/**
	 * Execute the GET Task.
	 * @param _dbc
	 * @throws Exception
	 */
	private void doGet(OracleConnection _dbc)
			throws Exception
	{
		String _sourceFile = takeNextParam("Source file");
		String _destDirStr = takeNextParam("Dest Dir");

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
	 * Execute the PUT task.
	 * @param _dbc
	 * @throws Exception
	 */
	private void doPut(OracleConnection _dbc)
			throws Exception
	{
		String _sourceFile = takeNextParam("Source file");
		File _localFile = new File(_sourceFile);

		if(!_localFile.isFile())
		{
			throw new IOException("Could not find source file: " + _localFile.getCanonicalPath());
		}
		double _sizeMb = (double)_localFile.length() / (double)(1024 * 1024);
		LOG.info("Source: {} ({} MB)", _localFile.getCanonicalPath(), _dFormat.format(_sizeMb));

		OraFile _oraFile = new OraFile(_dbc, _sourceDbDir, _localFile.getName());
		LOG.info("Destination: <{}>", _oraFile);

		if(_oraFile.exists() && !this._force)
		{
			throw new Exception("Detination file already exists: " + _oraFile);
		}
		else
		{
			LOG.warn("Force overwrite of destination file!");
		}

		try(FileInputStream _is = new FileInputStream(_localFile))
		{
			_oraFile.putContents(_is, _progress);
		}

		LOG.info("Transfer Complete!");
	}
}
