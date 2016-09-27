import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool
import edu.uvm.banner.Population;

// Get inputs
Map parms = [:]
parms.application = input('Enter Application, e.g. AR or FINAID', 'AR',
                tr_input(tr.ucase), ck.isInQry(Population.qry_ApplicationList))
parms.selection = input('Enter Selection', ck.required, tr_input(tr.ucase))
parms.creator = input('Enter Creator ID', username.toUpperCase(), 
				ck.required, tr_input(tr.ucase))
parms.user = input('Enter User ID', parms.creator, ck.required, tr_input(tr.ucase))
parms.deleteprior = input('Do you want to delete the prior population? Enter Y or N',
		"Y", tr_input(tr.ucase), ck.isInList.rcurry(['Y','N']))
parms.fnm = input('Enter the file name',ck.required, ck.fileExists)

// seed summary totals
Map counts = [read:0, deleted:0,inserts:0,notfound:0,inserterrors:0]
// instantiate population & bind to script.
Population pop = serviceFactory(Population,
	[application : parms.application, selection : parms.selection, 
	 creator : parms.creator, user : parms.user])

 if ( parms.deleteprior == 'Y' ){
 	// remove any pidms previously added to the population.
 	counts.deleted = pop.removeAll()
 }

new File(parms.fnm).eachLine { line ->
	counts.read += 1
	dbgShow(counts.read + ' - ' + line)
	String stuid = line.split("[ \\t\\n\\,\\?\\;\\.\\:\\!]")[0].trim()
	Integer pidm = tr.findstudent(stuid)
 	dbgShow("   StudentID=${stuid} <> pidm=${pidm}")
 	if (pidm == 0){
	counts.notfound += 1
	} else {
		String errmsg = pop.insertPIDM(pidm)
		if (errmsg){
		counts.inserterrors += 1
		rpt.pl(stuid + ' - ' + errmsg)
		}else{
		counts.inserts += 1
		}
	}
}

// print the report - note: any sql errors have already been printed. 
rpt.pl("Application:  " + parms.application)
rpt.pl("Selection:    " + parms.selection)
rpt.pl("Creator ID:   " + parms.creator)
rpt.pl("User ID:      " + parms.user)
rpt.nl()

rpt.pl("Total records read :      " + counts.read)
rpt.pl("Total records deleted :   " + counts.deleted)
rpt.nl()
rpt.pl("Total records inserted:   " + counts.inserts)
rpt.pl("IDs not found in Banner:  " + counts.notfound)
rpt.pl("Total sql-insert errors:  " + counts.inserterrors)
return 0