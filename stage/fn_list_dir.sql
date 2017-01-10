--
-- FUNCTION: oracp_list_dir
-- PURPOSE: List files in a directory.
-- USAGE:
--    select column_value as file_name
--    from table(sys.oracp_list_dir('app_dump_dir'));
-- NOTES: This function requires access to x$krbmsft and should
--    be run as SYS.
--

CREATE OR REPLACE TYPE oracp_file_array AS TABLE OF VARCHAR2(100);
/

CREATE OR REPLACE FUNCTION oracp_list_dir (
        lp_directory IN VARCHAR2 DEFAULT NULL)
        RETURN oracp_file_array PIPELINED
    AS
    lv_pattern VARCHAR2(1024);
    lv_ns VARCHAR2(1024);
    lv_file_name VARCHAR2(1024);
BEGIN
    SELECT directory_path
    INTO lv_pattern
    FROM dba_directories
    WHERE directory_name = upper(lp_directory);

    lv_pattern := sys.dbms_backup_restore.normalizefilename(lv_pattern || '/');
    sys.dbms_backup_restore.searchfiles(lv_pattern, lv_ns);

    FOR file_row IN (
        SELECT fname_krbmsft AS file_name
        FROM x$krbmsft )
    LOOP
        lv_file_name := REPLACE(file_row.file_name, lv_pattern, '');
        PIPE ROW(lv_file_name);
    END LOOP;
END;
/

GRANT EXECUTE ON oracp_list_dir TO PUBLIC;
/
