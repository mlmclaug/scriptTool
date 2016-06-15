# README #

scripttool is used to make groovy scripting easier.


To use:
- Add scripttool.jar and tabularreport.jar to the class path.
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
		applied.

rpt - a TabularReport instance.

dbname   - database name

username - id of the database user  - convenience variable

ck       - a map of available validation methods. Contains:
          required, isNumber, isInteger, isBigDecimal, isInList, isBetween, 
          isDate, isStudentID, isAIDY, isTermCD

tr       - a map of available translation methods. Contains:
          studentid2pidm, term2aidy