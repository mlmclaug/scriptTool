/* ========================================================================
   file : ar_statement_extract.groovy
   desc : Groovy script to extract billing statements for a population.
          Saves each statement in a pdf file n the selected output directory.
          Files are named: billMonth-id.pdf or billMonth-id_1.pdf ...
   date : MLM 11/03/2016
  ========================================================================*/
import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool
import edu.uvm.banner.Population;

// Get inputs.. instantiate population & bind to script.
Map parms = [:]
Population pop = serviceFactory(Population)
pop.application = input('Enter Application, e.g. AR or FINAID', 'AR',
                tr_input(tr.ucase), ck.isInQry(Population.qry_ApplicationList))
pop.selection = input('Enter Selection', ck.required, tr_input(tr.ucase))
pop.creator = input('Enter Creator ID', username.toUpperCase().replaceFirst(/OPS\$/,''), 
				ck.required, tr_input(tr.ucase))
pop.user = input('Enter User ID', pop.creator, ck.required, tr_input(tr.ucase))
parms.billMonth = input('Enter Billing Month', 
				sql.firstRow(qry.bill_months).bill_month,
                tr_input(tr.ucase), ck.isInQry(qry.bill_months))
parms.outputDir = input('Enter output directory', 
				'./statement-extract')
//===========================================================
println "Processing....${pop.toString()} - ${parms.billMonth}\n"

java.sql.Timestamp billDate =  sql.firstRow(qry.getBillDate, [billMonth:parms.billMonth]).bill_date
File dir = getDirectory(parms.outputDir)

dbgShow "Bill Date=${billDate}"
dbgShow "Output Directory =${dir.getCanonicalPath()}"
Number filecount = 0
// Loop on each pidm in the population.. Lookup Statement and send to a file
pop.fetchAll().each{ key ->
	String id = sql.firstRow(qry.getStudentID, [pidm:key]).studentID
	dbgShow "Processing : ${parms.billMonth} - ${id} : ${key}"

	Number mediaID =  sql.firstRow(qry.getMediaID,
						[pidm: key, billDate:billDate])?.TBBSTMT_MEDIA_ID
	java.sql.Blob statement =  sql.firstRow(qry.getStatementBlob,
						[mediaId:mediaID])?.gorblob_blob
	dbgShow "     MediaID = ${mediaID} - Blob size= ${statement?.length()}"

	if (statement){
		File f = getFile(dir, "${parms.billMonth}-${id}", '.pdf')
		f.bytes = statement.getBytes( 1, ( int ) statement.length() );
		filecount++

	} else{
		rpt.pl( "ERROR ${id} - MediaID = ${mediaID} : No statement not found for ${parms.billMonth}.")
	}
}

rpt.pl( "${filecount} files created in ${dir.getCanonicalPath()}")
return 0 //All done....
//===========================================================
//vvvvvv Utility Functions vvvvvvvv
File getDirectory(String pathname){
	// fetch output directory... create if needed.
    File dir = new File(pathname)
    if( !dir.exists() ) {
      dir.mkdirs()
    }
    return dir
}

File getFile(File dir, String pfx, String sfx){
    // Create new file as pfx.sfx
    // if exists creates pfx_1.sfx, pfx_2.sfx... until it finds a free one.
    String fname = "$pfx$sfx"
    File f = new File( dir, fname )

    def x = 0
    while ( f.exists() ) {
        x++
        fname = "${pfx}_${x}${sfx}"
        f = new File( dir, fname )
    }
    return f
}

class qry {
	static String bill_months = """
	select to_char(twvbill_bill_date,'YYYYmm') bill_month from twvbill 
	order by twvbill_bill_date desc
	"""
	static String getStudentID = """
	select uvmfrm_utl.getStuID(:pidm) studentID from dual
	"""
	static String getBillDate = """
	select twvbill_bill_date bill_date from twvbill
	where  to_char(twvbill_bill_date,'YYYYmm') = :billMonth
	"""
	static String getMediaID = """
	SELECT TBBSTMT_MEDIA_ID FROM TBBSTMT
	where TBBSTMT_PIDM = :pidm and TBBSTMT_BILL_DATE = :billDate
	"""
	static String getStatementBlob = """
	SELECT gorblob_blob FROM gorblob
    WHERE gorblob_media_id = :mediaId
	"""
}
