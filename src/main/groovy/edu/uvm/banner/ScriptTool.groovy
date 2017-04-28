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
(*) Do something w/ Dates - some helper to simplify data handling.
(*) Add helper to load external class from file.
*/
package edu.uvm.banner;
import groovy.sql.Sql;
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
	edu.uvm.banner.Configurator config   //Configurator object

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
	        String q = config.defaults.queries.ck.isStudentID ?:
				"select spriden_pidm from spriden where spriden_change_ind is null and spriden_id = rtrim(?)"
			def pidm = sql.firstRow(q,[studentID])?.getAt(0) ?: 0
			printIfFalse( !studentID || pidm > 0  , "*** Invalid Student ID: ${studentID}.") 
		}
		ck['isAIDY'] = {aidy -> 
	        String q = config.defaults.queries.ck.isAIDY ?:
						'select robinst_aidy_code from robinst where robinst_aidy_code = ?'
			String aidyear = sql.firstRow(q,[aidy])?.getAt(0) 
			printIfFalse( !aidy || aidyear , "*** Invalid Aid Year: ${aidy}.") 
		}
		ck['isTermCD'] = {termcd -> 
	        String q = config.defaults.queries.ck.isTermCD ?:
						'select stvterm_code from stvterm where stvterm_code = ?'
			String term = sql.firstRow(q,[termcd])?.getAt(0)
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
	        String q = config.defaults.queries.tr.studentid2pidm ?:
						"select spriden_pidm from spriden where spriden_change_ind is null and spriden_id = rtrim(?)"
			sql.firstRow(q,[studentID])?.getAt(0)  ?: 0
		}
		tr['term2aidy'] = {termcd -> 
	        String q = config.defaults.queries.tr.term2aidy ?:
	        		'select stvterm_fa_proc_yr from stvterm where stvterm_code = ?'
			sql.firstRow(q,[termcd])?.getAt(0)
		}
		tr['ucase'] = {input -> 
			input.toUpperCase()
		}
		tr['findstudent'] = {studentID -> 
	        String q = config.defaults.queries.tr.findstudent ?:
						'''select spriden_pidm from  spriden, spbpers
  							where spriden_change_ind is null
  							and spriden_pidm = spbpers_pidm
  							and (spriden_id = ?1 or spbpers_ssn = ?1)'''
			sql.firstRow(q,[studentID])?.getAt(0) ?: 0
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
dbname   - database name   - convenience variable
username - id of the database user  - convenience variable

rpt - a TabularReport instance.
	.lpp = lines per page (Dflt=55)
	.cpl = characters per line (Dflt=131)
	.addHead("left","center","right") - method to add page heading line(s)
	.addFoot("left","center","right") - method to add page heading line(s)
	.addColHead( width, 'L|C|R',{optional sprintf format}, ["Col label",...])
	.pl(x) - print line where x can be a string or list of column values to print.

csv - a CSV instance for reading/parsing csv files.

email(Map settings).send()
	where settings is a map of email properties as follows:
	[to:x@uvm.edu, cc:..., bcc:..., from: ....
	subject:'text', body:'blah, blah, blah',
	attachments['filename1','filename2',...] ]

ck       - a map of available validation methods. Contains:
''' + wrap_list(ck.collect({key,value->return key}),80,' '*10, ', ') + '''

tr       - a map of available translation methods. Contains:
''' + wrap_list(tr.collect({key,value->return key}),80,' '*10, ', ') + '''

dbgShow(msg) - This method prints msg to stdout if -verbose. debug messages.

tr_input(closure) - generates a ck constraint that can transform/modify
    a users input. For instance tr_input(tr.ucase) can be used to convert
    user input into upper case.

m = os_exec(String cmd) - executes an os command and returns a map.
                  m.returnValue - return code, 0 if ok
                  m.serr - string containing any errors sent to stderr
                  m.sout - string containing the output sent to stdout

Sql s1 = connect(dbc)	   - connect to a additional database using the
Sql s1 = connect(dbc,true)   same dbc syntax described above.
                             Add 'true' to -enableBanner

serviceFactory(class_name {, constructor_args})
	i.e:
	c = serviceFactory(edu.uvm.banner.Population)
	c = serviceFactory(edu.uvm.banner.Population, [p1:'aaa',p2:'bbb'])
	c = serviceFactory(edu.uvm.banner.Population, ['aaa','bbb']  as Object[])
	c = serviceFactory(new File(filename))  // Load class from an external file
	c = serviceFactory(filename)   // Load class from an external file

	serviceFactory instantiates an instance of a class and adds a 'script' 
	property. The script property makes all the script properties and methods 
	available for use in to the service class.
	The service class can now call:  script.sql ... script.username  etc...

'''
	}

	String wrap_list(List l, Integer atPos, String indent, String fsep){
	// Returns a list as a comma seperated string starting a newline atPos 
	// using indent indent to preceed each line and separating each elemetn with fsep
	// Syntax: wrap_list(l,80,' '*5, ', ') << wraps at 80, indents 5 spaces and sep is a ', '
	List lines = []
	String sep = ''
	String line = indent

	if ( l.size() > 0 ) {
	    l.each { it ->
	        if ( (line.size() + it.size() + sep.size()) > atPos ) {
	            lines << line
	            line = indent + it
	        } else {
	            line += sep + it
	        }
	        sep = fsep
	    }
	   lines << line
	}
	return lines.join("\n")
	}

	String getScriptName(){
		String scriptFile = getClass().protectionDomain.codeSource.location.path
		String fname = scriptFile.split('/')[-1]
		fname.take(fname.lastIndexOf('.'))
	}

    String fetchDBName(){
		// Get the Database name from the database.
		// Note: assumes Oracle, but can override the default in the config file.
        String dbn
        String q = config.defaults.queries.getDBName ?:
        			"select sys_context('userenv','db_name') from dual"
        try{
            dbn = delegate.sql.firstRow(q)?.getAt(0)
        }catch( Exception e ){
            dbn = null
        }
    	return dbn
    }

	String extractDBName( String url){
		// tries to extract the database name from the url
		// first pull out everything after the @ if there is one
		pos = url.lastIndexOf("@")
		String u1 = pos>-1 ? url.substring(pos+1) : url

		def pos = u1.lastIndexOf("/")
		if (pos != -1){
			// then get everything after the final slash up to a comma or semi-colon
			u1 = u1.substring(pos + 1)
			',;'.each {it ->
				pos = u1.indexOf(it)
				u1 = pos>-1 ? u1.substring(0,pos) : u1
			}
		}else{
			// Get everything after the final colon (if there is one)
			pos = u1.lastIndexOf(":")
			u1 = pos>-1 ? u1.substring(pos+1) : u1
		}
		return u1.toUpperCase()
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
    Sql connect(String dbconnectstring = null, boolean dobansecur = false){
    	dbgShow 'Opening new database connection.'
        Map dbc = config.getDBConnectionInfo(dbconnectstring)
        config.disp_dbc(dbc)
        Sql s = config.openDBConnection(dbc)

	    if (dobansecur){
	    	config.applyBannerSecurity(s)
	    }

	    if (this.sql == null){
	    	// here deferred connection (1st db) .. set default properties
	    	this.sql = s
	    	username = sql.getConnection().getUserName()
			dbname = fetchDBName() ?:
				extractDBName(sql.getConnection().getMetaData().getURL())
	    }

	    return s
    }

	Object serviceFactory( Object cl, def constructormap = [:]){
	    def c =  cl.newInstance(constructormap)
	    c.metaClass.getScript =  { -> this }     
	    return c
	}
	Object serviceFactory( File f, def constructormap = [:]){
		// Load a class from and external file.
		def gcl = new GroovyClassLoader()
		def clazz = gcl.parseClass(f)
		serviceFactory(clazz,constructormap)
	}
	Object serviceFactory( String filename, def constructormap = [:]){
		// Load a class from and external file. filename is the name of the file.
		serviceFactory( new File(filename), constructormap)
	}

	def run() {
		final result
		initialize()
		try {
			// Run actually script code.
			result = runCode()
		dbgShow "Script return value = $result"
		} finally {
			dbgShow '>Clearing state.'
			sql?.close()
			
			if (rpt.pgno != 0 || rpt.pgrow != 0){
				rpt?.close()
			}
			
			dbgShow '>Clearing state is done.'
		}
		return result
	}
	 
	private void initialize() {
		verbose =  args.any { it == '-verbose'}
		dobannersecurity = args.any { it == '-enableBanner'}
		dbgShow '>Initializing state.'
		dbgShow "Arguments: ${args}"
		parmbuf = fetchParmBuffer()

		config = serviceFactory(edu.uvm.banner.Configurator).resolveDefaults()

		if (args.size()==0){
			if (config.defaults.command_line){
				args = config.defaults.command_line
				verbose =  args.any { it == '-verbose'}
				dobannersecurity = args.any { it == '-enableBanner'}

				List t = args.collect()
				if (t.size()>=0) { t[0] = t[0].replaceAll("(?<=\\/)[^@]*(?=@)",'xxx')}
				dbgShow(">Using command line parameters from config: " + t.toString())
			}else{// here nothing provided... assume no database desired
				args = ['-NODB']
			}
		}

		sql = config.openDatabase()
	    if (dobannersecurity){
	    	config.applyBannerSecurity(sql)
	    }

		if (sql){
			// Set convenience variables username & dbname
			username = sql.getConnection().getUserName()
			dbname = fetchDBName() ?:
				extractDBName(sql.getConnection().getMetaData().getURL())
		}

        dbgShow 'Load any custom constraints and/or translations.' 
		config.defaults?.constraints.each { k, v ->
			dbgShow "   +Adding ck.${k}"
			v.delegate = this
			ck[k] = v
		}
		config.defaults?.translates.each { k, v -> 
			dbgShow "   +Adding tr.${k}"
			v.delegate = this
			tr[k] = v
		}

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
