import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

    //Get inputs/parameters
    def parms = new parms()
    parms.endDate = input("Enter the End Date as yyyy-MM-dd", LocalDate.now().toString() ,ck.required)
    parms.numDays = input("Enter number of days",'5',ck.required, ck.isInteger)
    println "Processing $parms.endDate and previous $parms.numDays days"
    parms.day = LocalDate.parse(parms.endDate , DateTimeFormatter.ISO_LOCAL_DATE);
    parms.oracleday = java.sql.Date.valueOf(parms.day)

    // get the data
    def xr = populateXref(parms)

    //>>>> Setup Report definition 
    rpt.addHead( "Last weeks RHACOMM counts by counselor.","","")
    .addHead( new Date().format('MM/dd/yyyy h:mm a') + " ${dbname}" , "","")
    .addHead("", "" ,"")
    .addHead(xr.getHeaderRow(), "" ,"")
    .addColHead( 10, 'L',"", ["Counselor","=========="])

    (0..xr.days.size()).each {
        rpt.addColHead( 8, 'R',"", ["Phon","===="])
        rpt.addColHead( 6, 'R',"", ["In P","===="])
    }
    rpt.lpp=58;rpt.cpl=rpt.colHeaders.sum {it.width}

    // print the data
    for ( u in xr.users ) { 
        rpt.pl(xr.getRow(u))
    }
    rpt.pl(xr.getTotalRow())

    println "Done"

//=======================================================================
def populateXref(parms parms){
    // Build/Populate xref Data
    def xr = new xref()
    // set List of Days to report on in ascending order
    for ( n in parms.numDays.toInteger()-1..0 ) {
        xr.days << parms.day.minus(n, ChronoUnit.DAYS)
    }

    String prevUser = ''
    sql.eachRow(queries.rhrcomm,[parms]){ r ->
        String user = r.userid
        if (prevUser != user){
            //first time we see a user, add to the users property 
            //and seed the users data w/ an empty map 
            xr.users << user 
            xr.data << [(user): [:]]
            prevUser = user
        }
        LocalDate day = r.day.toLocalDateTime().toLocalDate()
        def dp = new dataPoint(phone:r.phone, inperson:r.inperson)
        xr.data[user] << [(day):dp] // add data point to the user data.
    }
    return xr
}

class parms {
    def endDate 
    def numDays
    LocalDate day
    java.sql.Date oracleday
}
class queries{
    //Define Queries to be run
    static String rhrcomm = """ 
    select
    rhrcomm_user_id "userid",trunc(rhrcomm_activity_date) "day"
    , count( case when rhrcomm_category_code like 'P%' then rhrcomm_pidm end) "phone"
    , count( case when rhrcomm_category_code like 'I%' then rhrcomm_pidm end) "inperson"
    from rhrcomm
    where (rhrcomm_category_code like 'P%'
    or rhrcomm_category_code like 'I%')
    and rhrcomm_activity_date between :oracleday - :numDays and :oracleday + 1
    group by rhrcomm_user_id, trunc(rhrcomm_activity_date)
    order by rhrcomm_user_id, trunc(rhrcomm_activity_date)
"""
}

class xref{
  List days = []  //list of days to be reported in ascending order
  List users = [] //list of users to be reported in ascending order
  Map data = [:] // map of data for each user as a sparse matrix
                 // 1 row per counselor/one col for each day
                 // the data is a map by day of dataPoints
  List getRow( String user){
    List r = [user]
    def rowdata = data[user]
    def tot = new dataPoint(phone:0,inperson:0)
    
    days.each { d ->
      def dp = rowdata[d] 
      if (dp){
         tot += dp
         r += dp.asList()
      }else{
          r += [0,0]
      }
    }
    r += tot.asList()
  }
  
  List getTotalRow(){
    List r = ["DAY TOTAL"]
    List totalsByDay = []
    def grandtotal = new dataPoint(phone:0,inperson:0)
    
    for ( d in days ) { 
        def daytotal = new dataPoint(phone:0,inperson:0)
        users.each { u ->
            def dp = data[u][d]
            if (dp){ daytotal += dp }
        }
        totalsByDay << daytotal
        grandtotal += daytotal
    }
    totalsByDay << grandtotal
    r + totalsByDay*.asList().flatten()
  }
  String getHeaderRow(){
    String res = "           "
    for ( d in days ) { 
        DateTimeFormatter format = DateTimeFormatter.ofPattern("  dd-MMM-yyy ");
        res += d.format(format);
    }
    return res += "     TOTAL"
  }
}

class dataPoint{
    Integer phone
    Integer inperson
    String toString(){ "phone=${phone} : inperson = ${inperson}"}
    
    def plus(b){ 
       return new dataPoint(phone: this.phone + b.phone, inperson: this.inperson + b.inperson)
    }
    List asList(){ [phone, inperson] }
}