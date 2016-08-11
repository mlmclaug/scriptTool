import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

    println ">>> Connected as ${username}  in ${dbname}"
    // Get parms
    def parms = new parms([hold_code : 'FH', allHolds : 'Y', aidy_code : '1112', termCode : '201201'])
    def queries = new queries()
    //Print Report Header
    println "${username} in ${dbname}"
    println "ID        Name                       Acct Bal Hold         Amount Lvl FAF   From_Date   Cls Sts"

    //Main Program Loop
    int acct_cnt = 0, hold_cnt = 0, prev_pidm = 0
    sql.eachRow(queries.sqlMain, [parms]){ row ->
        parms.pidm = row.spriden_pidm
        def holds = sql.rows(queries.sqlHolds, [parms])
        def lvl = sql.firstRow(queries.sqlLevel, [parms])
        def fafsa = sql.firstRow(queries.sqlFafsa, [parms])
        def cls = sql.firstRow(queries.sqlClass, [parms])

        def stuname = row.spriden_last_name + ", " + row.spriden_first_name + " " + (row.spriden_mi !=  null ? row.spriden_mi : "")
        stuname = stuname.substring(0,[24,stuname.size()].min())
        acct_cnt ++
        hold_cnt += holds.size()
        //Output the Row
        holds.each {h -> 
            boolean bRow1 = prev_pidm != row.spriden_pidm
            prev_pidm = row.spriden_pidm
            printf( "%-10s%-24s %10s %-8s %10s %-4s %-4s %-12s %-3s %-3s\n",
                    bRow1 ? row.spriden_id : " ",
                    bRow1 ? stuname : " ",
                    bRow1 ? sprintf("%10.2f",row.balance) : " ",
                    h.sprhold_hldd_code,
                    h.sprhold_amount_owed  != null ? sprintf("%10.2f",h.sprhold_amount_owed) : " ",
                    this.nvl(fafsa?.robusdf_value_29," "),
                    this.nvl(lvl?.sgbstdn_levl_code," "),
                    h.fromdate,
                    this.nvl(cls?.swxclas_code," "),
                    this.nvl(lvl?.sgbstdn_stst_code," "))
        }
    }
    // Print summary Stats
    println "Accounts: ${acct_cnt}"
    println "Holds:    ${hold_cnt}"
static def nvl(val, ifnull) { val !=null ? val : ifnull}

class parms {
    def hold_code 
    def allHolds
    def aidy_code
    def termCode
    def pidm
}
class queries{
    //Define Queries to be run
    def sqlMain = """
        select spriden_pidm, spriden_id, spriden_last_name, spriden_first_name, spriden_mi, sum(tbraccd_balance) balance
         from spriden, tbraccd, sprhold
        where sprhold_pidm = spriden_pidm
          and sprhold_hldd_code = :hold_code
          and sprhold_to_date >= sysdate
          and tbraccd_pidm = sprhold_pidm
          and spriden_change_ind is null
          and spriden_entity_ind = 'P'
          --and spriden_pidm in (247944, 1904161, 389799, 390033)
        group by spriden_pidm, spriden_id, spriden_last_name, spriden_first_name,
          spriden_mi, sprhold_from_date
        order by lower(spriden_last_name), spriden_first_name
    """

    def sqlHolds = """
        select sprhold_hldd_code, sprhold_amount_owed, to_char(sprhold_from_date,'DD-MON-YYYY') fromdate
        from sprhold where sprhold_pidm = :pidm and sprhold_to_date >= sysdate
        and (:allHolds = 'Y' or sprhold_hldd_code = :hold_code)
        order by sprhold_hldd_code
    """

    def sqlLevel = """
    select sgbstdn_levl_code, sgbstdn_stst_code
    from sgbstdn where sgbstdn_pidm = :pidm
      and sgbstdn_term_code_eff = (select max(sgbstdn_term_code_eff) 
                                from sgbstdn where sgbstdn_pidm = :pidm)
    """

    def sqlFafsa = """
        select robusdf_value_29 from robusdf 
        where robusdf_pidm = :pidm and robusdf_aidy_code = :aidy_code
    """

    def sqlClass = """
        select swxclas_code from  swxclas
        where swxclas_code = swf_clas_calc(:pidm, :termCode)
    """
}
