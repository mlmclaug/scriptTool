/*
Auth: mlm - 06/13/2016

ScriptTool provides groovy scripts with the following.
(1) A database connection that optionally applies Banner security
(2) A reporting object to simplify the generation of tabular reports.
(3) Collect runtime parameters. Will collect from the command line or prompt.
     Various check constraints can be applied to the user input.
(4) Provide some commonly used convenience variables and methods.
     dbname, username, and tr (translation routines)
(5) Read/Parse csv files.
(6) Generate and send email.
(7) Execute OS Commands
(8) Bind to service classes via serviceFactory()

TODO: 
(*) Integrate w/ Banner Jobsub oneup (and optionally retain oneup values in the database)???
(*) Indentify and define additional common input constraints.
(*) Indentify and define additional common translation methods.

*/
package edu.uvm.banner;
import groovy.sql.Sql;
import java.sql.*;
import edu.uvm.banner.general.reporter.*;
import java.util.Locale;


abstract class ScriptTool  extends groovy.lang.Script {
	Sql sql           // Groovy Sql object, connected to Database & banner security has been applied.
	TabularReport rpt // reporter instance
	CSV csv
	String dbname     // database name - convenience variable
	String username   // id of the database user  - convenience variable
    def ck  = [:]     // map of validation methods
    def tr  = [:]     // map of translation methods
    // Internal State variables:
	boolean verbose = false  //turned on by -verbose flag.. prints additional info about what is happening.
	boolean dobannersecurity = false  //turned on by -enableBanner flag on command line
	private String[] parmbuf;  //buffer to hold user input parameters entered on the commandline. 
						//get's drained by Input method.
	private String _transformer_buffer = '' //Do Not Use - Property is transient, used during input transformations.

    // Optional provided Input Validations.  
    // Each validation returns true is input is ok.  
    //  False if not.  When false, prints a (hopefully) helpful error message.
    // printIfFalse( expression, errorMessage) is a convenience method to hid the noise.
    void registerValidations(){
		ck['required'] = {it -> printIfFalse( it.size() > 0 , '*** Input is required.') }  
		ck['isNumber'] = {it -> printIfFalse(it.isNumber(), '*** Must be a number.')}  
		ck['isInteger'] = {it -> printIfFalse(it.isInteger(), '*** Must be an integer number.')}  
		ck['isBigDecimal'] = {it -> printIfFalse( it.isBigDecimal() || it.isInteger(), '*** Must be a number.')}  
		ck['isInList'] = {item, list = [] -> 
				printIfFalse( ( !item || list.any { it == item } ), "*** Invalid input: ${item}. must be one of ${list}.")}
		ck['isBetween'] = {item, min=0, max=0 -> 
	        def inp // if min is a number... do a number compare. otherwise to string compare.
	        if (min instanceof java.math.BigDecimal){ inp =  cvt2num(item)
	        } else if (min instanceof java.lang.Integer){inp = cvt2num(item)
	        } else {inp = item //String
	        } 

	        printIfFalse( inp >= min && inp <= max || !item , 
	        	"*** Invalid input: ${item}. must be between ${min} and ${max}.")
		}
		ck['isDate'] = {it, fmt = 'MM/dd/yyyy' -> 
			//Preliminary...probably/surely are better ways to do this....
		     try{
		     	// permit empty response-- use ck_required if needed. 
		     	if (it){
			     	//Java 8, new Date-Time API is probably better... this is ok for now.
		        	date = Date.parse(fmt,it)
		     	}
		        true
		     }catch(Exception e){ 
		     println e.message	
		     println "*** Invalid Date: ${it}.  Enter date as ${fmt}."
		     false 
		     }
		}
		ck['isStudentID'] = {studentID -> 
			def pidm = sql.firstRow('select uvmfrm_utl.get_pidm(?) pidm from dual',[studentID]).pidm
			printIfFalse( !studentID || pidm > 0  , "*** Invalid Student ID: ${studentID}.") 
		}
		ck['isAIDY'] = {aidy -> 
			String aidyear = sql.firstRow('select robinst_aidy_code from robinst where robinst_aidy_code = ?',[aidy])?.robinst_aidy_code
			printIfFalse( !aidy || aidyear , "*** Invalid Aid Year: ${aidy}.") 
		}
		ck['isTermCD'] = {termcd -> 
			String term = sql.firstRow('select stvterm_code from stvterm where stvterm_code = ?',[termcd])?.stvterm_code
			printIfFalse( !termcd || term , "*** Invalid Term Code: ${termcd}.") 
		}
		ck['fileExists'] = {filename ->
			File f = new File(filename)
			printIfFalse( !filename || (f.exists() && f.isFile()), "*** File not found: ${filename}.") 
		}
		ck['isInQry'] = { String qry, String fieldname = null ->
		    // Given a query, constructs a inList check constraint
		    // populated with all values from fieldname.
		    // if fieldname is not supplied it is assumed to be the first column.
		    List cds = [];
		    if (fieldname){
			    sql.rows(qry).each {r -> cds << r.get(fieldname) }
		    } else {
			    sql.rows(qry).each {r -> cds << r.getAt(0) }
		    }
		    ck.isInList.rcurry(cds)
		}
		ck['transform'] = { String data, Closure transformer ->
			_transformer_buffer = transformer(data)
			true
		}
	}

    void registerTranslations(){
		tr['studentid2pidm'] = {studentID -> 
			sql.firstRow('select uvmfrm_utl.get_pidm(?) pidm from dual',[studentID]).pidm
		}
		tr['term2aidy'] = {termcd -> 
			sql.firstRow('select uvm_utils.acadyr(?) aidy from dual',[termcd]).aidy
		}
		tr['ucase'] = {input -> 
			input.toUpperCase()
		}
		tr['findstudent'] = {studentID -> 
			sql.firstRow('select uvmfrm_utl.findStudentBySomeID(?) pidm from dual',[studentID]).pidm
		}
	}

	String input(String p, String dflt='',Closure... validations ){
	    // Routine to prompt user for a parameter.. 
	    // unless it finds a response from the command line buffer. If so, use that to satisfy the response.
	    // If there is input in the parameter buffer from the command line.. use that to satisfy the response.
	    // Note: no input validation is performed on items specified on the command line.  
	    // we assume you know what you are doing.
		String r
		boolean isOK = false

		if (parmbuf) {
		    // found item in the buffer... run with it.
		    r =  parmbuf[0]
		    parmbuf = parmbuf.drop(1)

			if (!r && dflt){ r = dflt}

			// Run any validations on the input... if fails abort program.
			isOK = true
			if (validations ){
				validations.each { v -> 
					_transformer_buffer = r
					isOK = isOK && v(r)
					if (_transformer_buffer != r) {r = _transformer_buffer}
				 }
			}
			if ( !isOK ){
				System.exit(1);
			}

		}else{
		    // here we are prompting the user for input.
		    // prompt until all validations are satisfied.

		    // set the prompt... include default value if there is one.
			String p1
			String prompt_suffix = ': '
			if (dflt) {
				p1 = p + ' (Dflt=' + dflt + ')' + prompt_suffix
			}else{
				p1 = p + prompt_suffix
			}  

			isOK = false
			while ( !isOK ) {
				isOK = true
				r = prompt(p1)
				if (!r && dflt){ r = dflt}

				// Run any validations on the input... if fails reprompt.
				// validations return true if ok else false.
				// if false the validation should print a helpful error message.
				// Note: ck['transform'] is special and can be used to perform
				//      a translation/transformation of the input data.
				//      for example it can be used to convert user input into upper case.
				if (validations ){
					validations.each { v -> 
						_transformer_buffer = r
						isOK = isOK && v(r)
						if (_transformer_buffer != r) {r = _transformer_buffer}
					 }
				}
			}
		}
		return r     
	}

	String showUsage(){
return '''
Add the following 2 lines to the top of your script:

import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

and then call your script as follows:

>groovy {yourscript}.groovy {dbc} {-enableBanner} {-verbose} {-F{xx}} a b c ....

where

dbc = the database connection information such as the following: 
      /, /@prod, joe@aist, 
      or joe@jdbc:oracle:thin:@ldap://ldap.uvm.edu:389/AIST, CN=...
      If the userid or password is not supplied, they will be prompted for.
      If dbc = -nodb - no database will be connected to. See deferred db 
      		connection below.

-enableBanner = if specified, banner security checking and banner role 
                elevation will be performed.

{-F{xx}} = Overrides the default path/name of the output report file.
		The default report file is {current directory}/{yourscript}.lis
		if {xx} is a directory path, the report file will be 
				{xx}/{yourscript}.lis
		if {xx} is not a directory, the report file will be {xx}
		if -F and no other information is provided, the report output is
			sent to STDOUT.

-verbose = Display details about what is happening.

a  b c = If provided will be used as responses to Input values that would 
		otherwise be prompted for.

The following are made available for use in your script:

sql - a Groovy Sql object, connected to Database & banner security has been 
		applied.
rpt - a TabularReport instance.
csv - a CSV instance for reading/parsing csv files.
dbname   - database name
username - id of the database user  - convenience variable

ck       - a map of available validation methods. Contains:
          ''' + ck.collect({key,value->return key}).join(', ') + '''
tr       - a map of available translation methods. Contains:
          ''' + tr.collect({key,value->return key}).join(', ') + '''

dbgShow(msg) - This method prints msg to stdout if -verbose. debug messages.

email(Map settings).send()
	where settings is a map of email properties as follows:
	[to:x@uvm.edu, cc:..., bcc:..., from: ....
	subject:'text', body:'blah, blah, blah',
	attachments['filename1','filename2',...] ]

tr_input(closure) - generates a ck constraint that can transform/modify
    a users input. For instance tr_input(tr.ucase) can be used to convert
    user input into upper case.

m = os_exec(String cmd) - executes an os command and returns a map.
                  m.returnValue - return code, 0 if ok
                  m.serr - string containing any errors sent to stderr
                  m.sout - string containing the output sent to stdout

connect(dbc)	   - connect to a database using the same dbc syntax  
connect(dbc,true)    described above.   Add 'true' to -enableBanner

serviceFactory(class_name {, constructor_args})
	i.e:
	c = serviceFactory(edu.uvm.banner.Population)
	c = serviceFactory(edu.uvm.banner.Population, [p1:'aaa',p2:'bbb'])
	c = serviceFactory(edu.uvm.banner.Population, ['aaa','bbb']  as Object[])

	serviceFactory instantiates an instance of a class and adds a 'script' 
	property. The script property makes all the script properties and methods 
	available for use in to the service class.
	The service class can now call:  script.sql ... script.username  etc...

'''
	}
	Map getDBConnectionInfo(String dbcOverride = null){
	// Gets a map of the database connection info 
	// Uses arg[0] from the command line and/or defaults from the environment.
	// expected formats are one of the following:
	//         ['/@prod', '/', 'mlm@aist', 'mlm/secret@jdbc:oracle:thin:@ldap://joes/garage/BANUPG', '']
	 Map res = [:]
	 if (dbcOverride != null){
		dbc = dbcOverride
	 } else{
		dbc = args.size()>0 ? args[0] : ''
	 }
	 // -verbose or -enableBanner or -F is expected to be after the connection info..
	 // if it's first on the command line then assume connection info not provided.
	 if ( dbc == '-verbose' || dbc == '-enableBanner' || dbc =~ /^-F/ ){dbc = ''}

	 v=dbc.split("@",2)
	 
	if ( v[0] == '/') {
		res = [uid : '', pwd : '' 
			,url : getURL_OCI( v.size() > 1 ? v[1] : '')]
	} else if ( '-NODB'  == v[0].toUpperCase() ||
				'/NOLOG' == v[0].toUpperCase() ) {
		res = [uid : '', pwd : '', url : null]
	} else{
		res = [uid : getUserID(v[0]), pwd : getPassword(v[0])
			, url : getURL_THIN( v.size() > 1 ? v[1] : '')]
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
	  userid = prompt('User ID: ')
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
	  pwrd = promptpw('Password: ')
	}
	pwrd
	}

	String getURL_OCI(String u){
	//Get url for the database. u is right of the @
	// returns url in format of jdbc:oracle:oci:/@AIST
	// u is expected to be AIST, '', or full url to use: jdbc:oracle:oci:/@AIST
	String url = ''

	// if true has one word w/ no special characters... treat as a sid.
	// other wise accept string as is....
	if (u.size()>0 && u ==~  /^[a-zA-Z0-9]*$/ ) { //connection info is supplied and is one word.
	   url = 'jdbc:oracle:oci:/@' + u.toUpperCase()
	} else if (u.size()>0) { // connection info is supplied.. expected to be complete url
		url = u
	} else { // here no connect info supplied.. use Oracle SID if available.
		String sid = System.getenv('ORACLE_SID')
		sid = sid ? sid : 'AIST'
		url = 'jdbc:oracle:oci:/@' + sid
	}
	url
	}

	String getURL_THIN(String c){
	//Get url for the database. c is right of the @
	// returns url in format of jdbc:oracle:thin:@ldap://ldap.uvm.edu:389/AIST,CN=OracleContext,dc=uvm,dc=edu
	// c is expected to be AIST, '', or full url to use: jdbc:oracle:thin:@.....
	// if nothing provided get from (1) environment (JDBC_CONNECTION) (2) make from environment ORACLE_SID.
	  String url = System.getenv('JDBC_CONNECTION')
	  String sid = System.getenv('ORACLE_SID')
	  sid = sid ? sid : 'AIST'
	  url = url ? url : 'jdbc:oracle:thin:@ldap://ldap.uvm.edu:389/' + sid + ',CN=OracleContext,dc=uvm,dc=edu'  

	if (c.size()>0) {
	    if  ( c ==~  /^[a-zA-Z0-9]*$/ ) {
	       if (c.toUpperCase() != sid) {
	       // here we have one word after @ and it is different than the SID
	       // so replace SID in the default url.
	       url = url.replace (sid, c.toUpperCase() )
	       }
	    } else{// take connection url as provided
	      url = c
	    }
	}
	url
	}

	void openDBConnection(Map dbc) {
		//dbc = Map [uid, pwd, url, scriptnm]
		if (dbc.url != null){
			dbgShow "Open Connection to Database and set dbname and username."
		    def loglvl = Sql.LOG.level
		    try{
		    	String drivername = System.getenv('ORACLE_DRIVER') ?: 'oracle.jdbc.OracleDriver'  
		    	Sql.LOG.level = java.util.logging.Level.SEVERE
				sql = Sql.newInstance(dbc.url, dbc.uid, dbc.pwd, drivername)
		    	}catch(SQLException e){
		    		println "Datbase Connection failed.\n" + e.getMessage()
		    		System.exit(1);
		    	}
		    Sql.LOG.level = loglvl

			dbname = sql.firstRow("select value from sys.v_\$parameter where name = 'db_name'").value
			username = sql.firstRow("select user from user_users").user

		    // if -enableBanner then do banner security checking/privledge elevating
		    //if (args.any { it == '-enableBanner'}){
		    if (dobannersecurity){
				// Set Banner Secutity - prints message if not permitted and security is not elevated.
				// Reduce log level to hide security code being executed when error is thrown
				String scriptnm = getScriptName().toUpperCase()
				dbgShow "Setting Banner secuity on ${scriptnm}."
			    loglvl = Sql.LOG.level
			    try{
			    	Sql.LOG.level = java.util.logging.Level.SEVERE
			    	sql.call(BannerSecurity.setBanSecr, 
			    		[BannerSecurity.seed1, BannerSecurity.seed3, scriptnm])
			    	}catch(SQLException e){
			    		println "WARNING: Banner Security not enabled on this object.\n" + e.getMessage()
			    		System.exit(1);
			    	}
			    Sql.LOG.level = loglvl
			}
		} else {
			dbgShow "No Database, connection deferred."
		}
	}

	String getScriptName(){
		String scriptFile = getClass().protectionDomain.codeSource.location.path
		String fname = scriptFile.split('/')[-1]
		fname.take(fname.lastIndexOf('.'))
	}

	String prompt(String prompt_text){
	   System.console().readLine(prompt_text)
	}

	String promptpw(String prompt_text){
	   System.console().readPassword(prompt_text).toString()
	}
	void dbgShow(String msg){
		if (verbose){println msg}
	}
	void disp_dbc(Map dbc){
		dbgShow "Connection Info: user= ${dbc.uid}, password= ${'-'.multiply(dbc.pwd.size())}"
		dbgShow "            url: ${dbc.url}"
	}
	String[] fetchParmBuffer(){
		// extracts from the command line any arguments that will be used as responses to 
		// prompts.  Ignores the connection info (assumed in first position) and any 
		// recognized optional -flags
		args.drop(1).findAll { it ->  it != '-verbose' && it != '-enableBanner' &&  (!(it =~ /^-F/)) }
	}

	def cvt2num(String s){
		// converts a string to a number. else returns null
	   def r = null
	    if (s.isNumber()){
	        r = s.isBigDecimal() ? s.toBigDecimal() : s.toInteger()
	    }
	return r
	}

	boolean printIfFalse(boolean b, String message){
		// Convenience method to hide the noise.
		// when false message is printed. returns b
		if (!b){ println message}
		return b
	}

	def getReportDestination(a){
	// Default output is to a file named {scriptname}.lis in the current working directory.
	// -F on the command line can overeride the directory location plus the default file name
	//   if -F is  a directory output defaults to {-Fdir}/{scriptname}.lis
	//   if -F is a full file path that us used as the output file.
	//   if -F and nothing else... use default STDOUT.
	File f
	String path = a.find { it =~ /^-F/ }

	if ( path && path == '-F'){
	    // here -F and nothing else.... just sent to default STDOUT. 
	    f = null 
	}else if (path){ 
	    path = path ? path.drop(2) : path // remove leading '-F'
	    f = new File(path)
	    if (f.exists() && f.isDirectory()){
	        f = new File(f.absolutePath + '/' + getScriptName() + '.lis')
	    }else{ //use file as specified. 
	        f = new File(path)
	    }
	}else{
	    // Here nothing provided.. generate the default in the current directory.
	    f = new File('./' + getScriptName() + '.lis')
	}
	return f
	}

	void truncFile( File f) {
		// truncate file if not empty
		if ( f && f.exists() && f.isFile() && f.size()>0 ) {
			f.write('')
		}
	}

	Email email(Map m){
		new Email(m)
	}

	Closure tr_input(Closure transformer ){
		// performs a transformation on the user input.
		// i.e convert to upper case, convert code to an id.......
		// transformer is a closure that is passed user the input
		// and returns modified user input.. i.e convert to upper case.
		ck.transform.rcurry(transformer) 
	}
	/**
	 * helper class to check the operating system this Java VM runs in
	 * please keep the notes below as a pseudo-license
	 * http://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
	 * compare to http://svn.terracotta.org/svn/tc/dso/tags/2.6.4/code/base/common/src/com/tc/util/runtime/Os.java
	 * http://www.docjar.com/html/api/org/apache/commons/lang/SystemUtils.java.html
	 */
	  /** types of Operating Systems  */
	  public enum OSType { Windows, MacOS, Linux, Other };

	  // cached result of OS detection
	  protected static OSType detectedOS;

	  /**
	   * detect the operating system from the os.name System property and cache
	   * the result
	   * 
	   * @returns - the operating system detected
	   */
	  public static OSType getOperatingSystemType() {
	    if (detectedOS == null) {
	      String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
	      if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
	        detectedOS = OSType.MacOS;
	      } else if (OS.indexOf("win") >= 0) {
	        detectedOS = OSType.Windows;
	      } else if (OS.indexOf("nux") >= 0) {
	        detectedOS = OSType.Linux;
	      } else {
	        detectedOS = OSType.Other;
	      }
	    }
	    return detectedOS;
	  }

	Map os_exec (String cmd){
		// Execute an OS level command. inserting OS shell so shell 
		// provided commands and glob expansion works.
		List c = OSType.Windows == getOperatingSystemType() ? ['cmd' , '/c', cmd] : ['sh' , '-c', cmd]
		return os_exec (c)
	}

	Map os_exec (def cmd){
		// Can be called with a string or list (anything w/ a .execute())
		// Note to get commands and globing provided by the shell use
		// list w/ sh -c or cmd /c depending on your howt platform. 
		Map r = [:]
		def sout = new StringBuilder()
		def serr = new StringBuilder()
		def proc = cmd.execute(null, new File("."))
		proc.consumeProcessOutput(sout, serr)
		proc.waitFor()

		r.exitValue = proc.exitValue()
		r.sout = sout.toString()
		r.serr = serr.toString()
		return r
	}
    void connect(String dbconnectstring, boolean dobansecur = false){
    	dobannersecurity = dobansecur
        Map dbc = getDBConnectionInfo(dbconnectstring)
        disp_dbc(dbc)
        openDBConnection(dbc)
    }

	Object serviceFactory( Object cl, def constructormap = [:]){
	    def c =  cl.newInstance(constructormap)
	    c.metaClass.getScript =  { -> this }     
	    return c
	}

	def run() {
		initialize()
		try {
			// Run actually script code.
			final result = runCode()
		dbgShow "Script return value = $result"
		} finally {
			dbgShow '>Clearing state.'
			sql?.close()
			
			if (rpt.pgno != 0 || rpt.pgrow != 0){
				rpt?.close()
			}
			
			dbgShow '>Clearing state is done.'
		}
	}
	 
	private void initialize() {
		verbose =  args.any { it == '-verbose'}
		dobannersecurity = args.any { it == '-enableBanner'}
		dbgShow '>Initializing state.'
		dbgShow "Arguments: ${args}"
		parmbuf = fetchParmBuffer()
        Map dbc = getDBConnectionInfo()
        disp_dbc(dbc)
        openDBConnection(dbc)
        dbgShow 'Setting up report instance.' 
		rpt = new TabularReport() 
		rpt.outputDest = getReportDestination(args)
		truncFile( rpt.outputDest )
		registerValidations()
		registerTranslations()
		csv = new CSV()
		dbgShow '>Initializing state is done.'
	}
	 
	// Abstract method as placeholder for the actual script code to run.
	abstract def runCode() 

	// TODO:remove this when unit & integration tests are written...
   public static testit(){true}

}
