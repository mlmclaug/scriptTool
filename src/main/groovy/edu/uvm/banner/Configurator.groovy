/*
Auth: mlm - 11/15/2016

This class is used to configure the ScriptTool working environment.
(1) Gets/sets default values
(2) Instantiates and opens a database connection.
(3) Applies Banner Security (optionally)

Defaults come from one or more of the following:

(1) scripttool-config.groovy in the directory where the jar is loaded from.
(2) scripttool-config.groovy in the users home directory ($HOME/.scripttool/).
(3) scripttool-config.groovy in the current directory.

Sample scripttool-config.groovy

========================
//scripttool-config.groovy
// Site defaults for database connections.
// Name of the environment variable containig the default database name
database.env_dbname='ORACLE_SID'
// Default DB name to use when not provided already via 
// the command line, environment or default url
database.dflt_dbname='ORCL'
// Default db url to use. 
database.url='ldap://ldap.myserver.com:310/ORCL,CN=OracleContext,dc=mycom,dc=com' 
// jdbc Driver Name to use. 
database.driver_name = 'oracle.jdbc.OracleDriver'

// Various site specific queries can be overiden here.
queries{
	getDBName = "select sys_context('userenv','db_name') from dual"
	ck.isStudentID='select uvmfrm_utl.get_pidm(?) pidm from dual'
	ck.isAIDY='select robinst_aidy_code from robinst where robinst_aidy_code = ?'
	ck.isTermCD='select stvterm_code from stvterm where stvterm_code = ?'
	tr.studentid2pidm='select uvmfrm_utl.get_pidm(?) pidm from dual'
	tr.term2aidy='select uvm_utils.acadyr(?) aidy from dual'
	tr.findstudent='select uvmfrm_utl.findStudentBySomeID(?) pidm from dual'
}

// constraints{
// 	dirExists = {dirname ->
// 		File f = new File(dirname)
// 		printIfFalse( !dirname || (f.exists() && f.isDirectory()), "*** Directory not found: ${dirname}.") 
// 	}
// }

// translates{
// 	lcase = {input ->
// 		input.toLowerCase()
// 	}
// }
//command_line=['-NODB']
========================

The following environment variables will override defaults that are supplied 
via the configuration file(s)

JDBC_CONNECTION      - database url to use
JDBC_DRIVER          - driver name for the database
defaults.database.env_dbname  - name of the default database

Any connection info supplied on the command line override these settings.

*/
package edu.uvm.banner;
import groovy.sql.Sql;
import java.sql.*;

class Configurator{
	ConfigObject defaults
	// Resolved values.....
	String dbName
	String url
	String driverName


	Configurator resolveDefaults(){
		//import default values from any external config files
		defaults = new ConfigSlurper().parse("""\
		database.env_dbname='ORACLE_SID'
		database.dflt_dbname='ORCL'
		database.url='jdbc:oracle:thin:@localhost:1521:ORCL' 
		database.driver_name = 'oracle.jdbc.OracleDriver'
		""")

		[new File(  getJarPath() + '/scripttool-config.groovy')
		,new File( System.getProperty('user.home')+'/.scripttool/scripttool-config.groovy')
		,new File( './scripttool-config.groovy')].each{
			// merge in any default propery overrides from any configuration file(s)
			if (it.exists()){
				script.dbgShow "Loading configuration from " +  it.toURL()
				ConfigObject t = new ConfigSlurper().parse(it.toURL())
				defaults.merge(t)
			}
		}

		// override w/ values from the environment (if defined)
		script.dbgShow "Checking for environment variables: " +  defaults.database.env_dbname + ', JDBC_CONNECTION, JDBC_DRIVER'
		url =  defaults.database.url
		dbName = defaults.database.dflt_dbname
		String envdb = System.getenv(defaults.database.env_dbname)
		if (envdb != null && envdb.size()>0 && envdb != dbName) {
			// here new DBname is coming from the environment and is different that the default.
			// make sure the dbname stays consistent with dbname in the url like when overriding fom the command line.
			script.dbgShow "  > Overridng default database name: ${dbName} changed to ${envdb}" 
			url = url.replace (dbName, envdb)
			dbName = envdb
		}

		String envu = System.getenv('JDBC_CONNECTION')
		if (envu != null && url != envu){
			script.dbgShow "  > Overridng default url: ${envu}" 
			url = envu
		}

		String envdrv = System.getenv('JDBC_DRIVER')
		driverName =  defaults.database.driver_name
		if (envdrv != null  && driverName != envdrv){
			script.dbgShow "  > Overridng default driver name: ${envdrv}" 
			driverName = envdrv
		}

		return this
	}

	Sql openDatabase(){
        Map dbc = getDBConnectionInfo()
        disp_dbc(dbc)
        openDBConnection(dbc)
	}

	Map getDBConnectionInfo(String dbcOverride = null){
		// Gets a map of the database connection info 
		// Uses arg[0] from the command line and/or defaults from the environment.
		// expected formats are one of the following:
		//         ['/@prod', '/', 'mlm@aist', 'mlm/secret@jdbc:oracle:thin:@ldap://joes/garage/BANUPG', '']
		Map res = [:]
		String dbc

		if (dbcOverride != null){
			dbc = dbcOverride
		} else{
			dbc = script.args.size()>0 ? script.args[0] : ''
		}
		// -verbose or -enableBanner or -F is expected to be after the connection info..
		// if it's first on the command line then assume connection info not provided.
		if ( dbc == '-verbose' || dbc == '-enableBanner' || dbc =~ /^-F/ ){dbc = ''}
		List v=dbc.split("@",2)
		 
		if ( v[0] == '/') {
			res = [uid : '', pwd : '' 
				,url : getURL_OCI( v.size() > 1 ? v[1] : '')
				,drivername : driverName]
		} else if ( '-NODB'  == v[0].toUpperCase() ||
					'/NOLOG' == v[0].toUpperCase() ) {
			res = [uid : '', pwd : '', url : null , drivername : null]
		} else{
			res = [uid : getUserID(v[0]), pwd : getPassword(v[0])
				, url : getURL_THIN( v.size() > 1 ? v[1] : '')
				,drivername : driverName]
		}
		res
	}

	String getUserID(String u){
		// Extract userid from u, if empty prompt
		// u is characters left of the @ on the connect string
		// expected to be in form of '', 'uid', or 'uid/pwd'
		String userid = ''
		userid = u.split('/')[0]

		if ( userid == '') {
		  userid = script.prompt('User ID: ')
		}
		userid
		}

		String getPassword(String u){
		// Extract password from u, if empty prompt
		// u is characters left of the @ on the connect string
		// expected to be in form of '', 'uid', or 'uid/pwd'
		String pwrd = ''
		String[] t = u.split('/')
		pwrd = t.size() > 1 ? t[1] : ''

		if ( pwrd == '') {
		  pwrd = script.promptpw('Password: ')
		}
		pwrd
	}


	String getURL_OCI(String u){
		//Get url for the database. u is right of the @ on the command line
		// returns url in format of jdbc:oracle:oci:/@DBNAME
		// u is expected to be DBNAME, '', or full url to use: jdbc:oracle:oci:/@ORCL
		String url = ''

		// if true has one word w/ no special characters... treat as a sid.
		// other wise accept string as is....
		if (u.size()>0 && u ==~  /^[a-zA-Z0-9]*$/ ) { //connection info is supplied and is one word.
		   url = 'jdbc:oracle:oci:/@' + u.toUpperCase()
		} else if (u.size()>0) { // connection info is supplied.. expected to be complete url
			url = u
		} else { // here no connect info supplied.. use Oracle SID if available.
			url = 'jdbc:oracle:oci:/@' + dbName
		}
		url
	}

	String getURL_THIN(String c){
		//Get url for the database. c is right of the @  on the command line
		// returns url to connect to the database
		// c is expected to be DBNAME, '', or full url to use: jdbc:oracle:thin:@.....
		// if nothing provided get from (1) environment (JDBC_CONNECTION) (2) make from environment ORACLE_SID.
		String dburl = url
		String sid = dbName
		if (c.size()>0) {
		    if  ( c ==~  /^[a-zA-Z0-9]*$/ ) {
		       if (c.toUpperCase() != sid) {
		       // here we have one word after @ and it is different than the SID
		       // so replace SID in the default url.
		       dburl = dburl.replace (sid, c.toUpperCase() )
		       }
		    } else{// take connection url as provided
		      dburl = c
		    }
		}
		dburl
	}

	Sql openDBConnection(Map dbc) {
		// Opens connection and sets script.sql
		// Optionally applies banner security.
		//dbc = Map [uid, pwd, url, drivername]
		Sql sqlObj
		if (dbc.url != null){
			script.dbgShow "Open Connection to Database."
		    def loglvl = Sql.LOG.level
		    try{
		    	Sql.LOG.level = java.util.logging.Level.SEVERE
				sqlObj = Sql.newInstance(dbc.url, dbc.uid, dbc.pwd, dbc.drivername)
		    	}catch(SQLException e){
		    		println "Datbase Connection failed.\n" + e.getMessage()
		    		System.exit(1);
		    	}
		    Sql.LOG.level = loglvl
		} else {
			script.dbgShow "No Database, connection deferred."
		}
		return sqlObj
	}

	void applyBannerSecurity(Sql dbsession) {
		// Set Banner Secutity - prints message if not permitted and security is not elevated.
		// Reduce log level to hide security code being executed when error is thrown
		// dbsession - sql instance to secure.. usually primary but could be additional databases.
		String scriptnm = script.getScriptName().toUpperCase()
		script.dbgShow "Setting Banner secuity on ${scriptnm}."
	    def loglvl = Sql.LOG.level
	    try{
	    	Sql.LOG.level = java.util.logging.Level.SEVERE
	    	edu.uvm.banner.security.BannerSecurity.apply(script, dbsession)
	    	}catch(SQLException e){
	    		println "WARNING: Banner Security not enabled on this object.\n" + e.getMessage()
	    		System.exit(1);
	    	}
	    Sql.LOG.level = loglvl
	}


    String getJarPath(){
    	// find path from which the scriptool.jar was loaded from.
	    String p = new File(this.getClass().protectionDomain.codeSource.location.toURI()).getAbsolutePath()
	    return p.substring(0,p.lastIndexOf(File.separator))
	}

	void disp_dbc(Map dbc){
		script.dbgShow "Connection Info: user= ${dbc.uid}, password= ${'-'.multiply(dbc.pwd.size())}"
		script.dbgShow "            url: ${dbc.url}, drivername : ${dbc.drivername}"
	}


	
}