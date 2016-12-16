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

package org.oracp.test;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.cli.ParseException;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.oracp.OcpMain;

/**
 * @author cjuliano
 * @date 12/2/16 10:11 AM
 */
@SuppressWarnings("javadoc")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OcpMainTest
{

	private String	_jdbcUrl	= null;
	private String	_jdbcUser	= null;
	private String	_jdbcPass	= null;
	private String	_dbDir		= null;

	@Before
	public void initialize()
	{
		_jdbcUrl = "jdbc:oracle:thin:@//slc03qjw.us.oracle.com:1521/PINDB";
		_jdbcUser = "PIN102";
		_jdbcPass = "PIN102";
		_dbDir = "APP_DUMP_DIR";
	}

	@Test(expected = ParseException.class)
	public void T01_testHelp()
			throws Exception
	{
		ArrayList<String> _argList = new ArrayList<>();
		_argList.add("--help");
		execTest(_argList);
	}

	@Test
	public void T02_testList()
			throws Exception
	{
		ArrayList<String> _argList = new ArrayList<>();
		_argList.addAll(Arrays.asList("--url", _jdbcUrl));
		_argList.addAll(Arrays.asList("--user", _jdbcUser));
		_argList.addAll(Arrays.asList("--passwd", _jdbcPass));
		_argList.addAll(Arrays.asList("--db-dir", _dbDir));
		_argList.add("list");
		execTest(_argList);
	}

	@Test
	public void T03_testGet()
			throws Exception
	{
		ArrayList<String> _argList = new ArrayList<>();
		_argList.addAll(Arrays.asList("--url", _jdbcUrl));
		_argList.addAll(Arrays.asList("--user", _jdbcUser));
		_argList.addAll(Arrays.asList("--passwd", _jdbcPass));
		_argList.addAll(Arrays.asList("--db-dir", _dbDir));
		_argList.add("get");
		_argList.add("expdp_xref116_v4.dmp");
		_argList.add("./bin");
		execTest(_argList);
	}

	@Test
	public void T03_testPut()
			throws Exception
	{
		ArrayList<String> _argList = new ArrayList<>();
		_argList.addAll(Arrays.asList("--url", _jdbcUrl));
		_argList.addAll(Arrays.asList("--user", _jdbcUser));
		_argList.addAll(Arrays.asList("--passwd", _jdbcPass));
		_argList.addAll(Arrays.asList("--db-dir", _dbDir));
		_argList.addAll(Arrays.asList("--force"));
		_argList.add("put");
		//_argList.add("./bin/expdp_chadj_v5.dmp");
		_argList.add("./.gradle/2.14.1/taskArtifacts/fileSnapshots.bin");

		execTest(_argList);
	}

	private static void execTest(ArrayList<String> _argList)
			throws Exception
	{
//		String[] _args = _argMap.entrySet().stream()
//				.flatMap(x -> Stream.of(x.getKey(), x.getValue()))
//				.filter(x -> x != null)
//				.toArray(String[]::new);

		//_argList.add(0, "--debug");
		OcpMain _testClass = new OcpMain();
		_testClass.run(_argList.toArray(new String[0]));
	}
}
