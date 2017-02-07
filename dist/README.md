# ORACP File Transfer Utility

This program is a command line utility that can perform file operations over an 
[Oracle JDBC connection][OJDBC]. It can simplify the transfer of files between a client and 
the database compared to approaches like SFTP.
 
[OJDBC]: <http://docs.oracle.com/cd/E11882_01/java.112/e16548/urls.htm>

See the [ORACP homepage][HOME] for documentation.

[HOME]: <https://github.com/chadj2/oracp>

## Oracle JDBC Driver

Before you can build or run this project you will to download a copy of the 
[ojdbc7.jar][ORACLE-JDBC] driver and place it in the **./lib** directory.

[ORACLE-JDBC]: <http://www.oracle.com/technetwork/database/features/jdbc/index-091264.html>

This driver can't be included with the distribution because 
it is covered under the [Oracle Technology Network License Agreement][ORACLE-OTN].

[ORACLE-OTN]: <http://www.oracle.com/technetwork/licenses/distribution-license-152002.html>

## Author

- [Chad Juliano](https://github.com/chadj2)

## License

This program is licensed under [GNU Lesser General Public License v3.0 only][LGPL-3.0]. 
Some rights reserved. See [LICENSE][].

[LGPL-3.0]: <https://spdx.org/licenses/LGPL-3.0>
[LICENSE]: <LICENSE.md>
