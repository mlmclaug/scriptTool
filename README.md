# README #

scripttool is used to make groovy scripting easier.


To use:
- Set JAVA_HOME to JDK8 and add to $PATH
- Set GROOVY_HOME to 2.4.6+ and add to $PATH
- Add scripttool.jar, javax.mail.jar and commons-csv-1.4.jar to the class path.
- Add preferred JDBC Driver to the class path.
- Optionally Add Oracle OCI directory to the classpath  and set LD_LIBRARY_PATH if using Oracle SEP's authentication.

- Add the following 2 lines to the top of your {yourscript}.groovy:
      import edu.uvm.banner.ScriptTool;
      @groovy.transform.BaseScript ScriptTool scriptTool

and then call your script as follows:

>groovy {yourscript}.groovy {dbc} {-enableBanner} {-verbose} {-F{xx}} a b c ....

where

dbc = the database connection information such as the following: 
~~~~
      /, /@prod, joe@aist, 
      or joe@jdbc:oracle:thin:@ldap://ldap.uvm.edu:389/AIST, CN=...
~~~~
If the userid or password is not supplied, they will be prompted for.
Note: Normally uses the Oracle JDBC thin client, except when 
connecting w/ OS Authentication using SEPS (/). When OSAuth,
the Oracle OCI client is used to connect to the database.

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

===========================================================
The following objects/variables are made available for use in your script:

sql - a Groovy Sql object, connected to Database & banner security has been 
		optionally applied.

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
          required, isNumber, isInteger, isBigDecimal, isInList, isBetween, 
          isDate, isStudentID, isAIDY, isTermCD

tr       - a map of available translation methods. Contains:
          studentid2pidm, term2aidy
dbgShow(msg) - This method prints msg to stdout if -verbose. debug messages.

tr_input(closure) - generates a ck constraint that can transform/modify
    a users input. For instance tr_input(tr.ucase) can be used to convert
    user input into upper case.

m = os_exec(String cmd) - executes an os command and returns a map.
                  m.returnValue - return code, 0 if ok
                  m.serr - string containing any errors sent to stderr
                  m.sout - string containing the output sent to stdout

Sql s1 = connect(dbc)      - connect to a additional database using the
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


