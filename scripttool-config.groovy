// Site defaults for database connections.
// Name of the environment variable containig the default database name
database.env_dbname='ORACLE_SID'
// Default DB name to use when not provided already via 
// the command line, environment or default url
database.dflt_dbname='AIST'
// Default db url to use. 
database.url='jdbc:oracle:thin:@ldap://ldap.uvm.edu:389/AIST,CN=OracleContext,dc=uvm,dc=edu'
// jdbc Driver Name to use. 
database.driver_name = 'oracle.jdbc.OracleDriver'


queries{
	getDBName = "select sys_context('userenv','db_name') from dual"
	ck.isStudentID='select uvmfrm_utl.get_pidm(?) pidm from dual'
	ck.isAIDY='select robinst_aidy_code from robinst where robinst_aidy_code = ?'
	ck.isTermCD='select stvterm_code from stvterm where stvterm_code = ?'
	tr.studentid2pidm='select uvmfrm_utl.get_pidm(?) pidm from dual'
	tr.term2aidy='select uvm_utils.acadyr(?) aidy from dual'
	tr.findstudent='select uvmfrm_utl.findStudentBySomeID(?) pidm from dual'
}
