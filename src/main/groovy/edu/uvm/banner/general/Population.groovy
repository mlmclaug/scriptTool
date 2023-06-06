/*
Auth: mlm - 09/23/2016

Population class- Provides methods for managing the contents
of a banner population.

*/
package edu.uvm.banner;
import groovy.sql.Sql;
import java.sql.SQLException;

class Population{
	String application
	String selection
	String creator
	String user
	// Parent properties.. if need to create population
	String descr = 'Loaded via Scripttool'
	String lock_ind = 'N'
	String type_ind = 'S'

	// internal flag.. first time we insert to a population
	// do make sure master record exists to make population visible
	//        to Banner INB.
	private Boolean doInitProcessing = true

	public void setApplication(String app) {
		if ( application != app ){ doInitProcessing = true }
		application = app
	}
	public void setSelection(String sel) {
		if (selection != sel ){ doInitProcessing = true }
	   selection = sel
	}

	public void setCreator(String cr_id) {
		if (creator != cr_id ){ doInitProcessing = true }
	   creator = cr_id
	}

	public void setUser(String user_id) {
		if (user != user_id ){ doInitProcessing = true }
	   user = user_id
	}

	String toString(){
		"${application} : ${selection} : ${creator} : ${user}"
	}

	Integer count(){
		Sql sql = script.sql
		sql.firstRow(qry_count, this).count
	}

	Integer removeAll(){
		Sql sql = script.sql
		sql.execute(qry_emptyPop, this)
		sql.updateCount
	}

	List fetchAll(){
		List results = []
		Sql sql = script.sql
        sql.eachRow(qry_getPIDM, this.properties) { row -> results << row.pidm }
        results
	}

	String insertPIDM(Integer pidm){
		// inserts pidm into glbextr
		// returns error message if failed. '' on success.
		Sql sql = script.sql
		String r = ''

		if ( doInitProcessing ){
			// make sure parent record exists.. if not add it
			if ('Y' != doesMasterExist()){
				sql.execute(qry_insertMaster, this)
			}
			doInitProcessing = false
		}

	    try{
			sql.execute(qry_insertPIDM, 
				[ application:application, selection:selection, 
				  creator:creator, user:user, descr:descr, pidm:pidm ])
			//r = sql.updateCount
    	}catch(SQLException e){
    		r = "ERROR: Insert failed for ${pidm}.  " + e.getMessage()
    		println r
    	}
		return r
	}

	String doesMasterExist(){
		// returns 'Y' if definition exists, otherwise 'N'
		String found = script.sql.firstRow(qry_doesMasterExist, this)?.found
		return found ?: 'N'
	}

    Integer delete(){
    	// Remove entire population Definition
		Sql sql = script.sql
    	removeAll()
		sql.execute(qry_deleteMaster, this)
		sql.updateCount
    }

// Query definitions
static String qry_emptyPop = """ 
	delete from glbextr
	where glbextr_application = :application
	  and glbextr_selection =   :selection
	  and glbextr_creator_id =  :creator
	  and glbextr_user_id =     :user
"""	

static String qry_insertPIDM = """ 
	insert into glbextr
	(glbextr_application, 
	glbextr_selection,
	glbextr_creator_id,
	glbextr_user_id, 
	glbextr_key,
	glbextr_activity_date,
	glbextr_sys_ind, 
	glbextr_slct_ind)
	VALUES 
	(:application, 
	 :selection, 
	 :creator, 
	 :user,
	 :pidm, 
	 SYSDATE, 
	 'S', 
	 Null)
"""

static String qry_insertMaster = """ 
	insert into glbslct
	(glbslct_application,
	 glbslct_selection,
	 glbslct_creator_id,
	 glbslct_desc,
	 glbslct_lock_ind,
	 glbslct_activity_date,
	 glbslct_type_ind)
	 VALUES 
	   (:application, 
		:selection, 
		:creator, 
		:descr,
		:lock_ind,
		SYSDATE, 
	    :type_ind)
"""

static String qry_doesMasterExist = """ 
	select 'Y' found	from glbslct
	where glbslct_application = :application
	  and glbslct_selection =   :selection
	  and glbslct_creator_id =  :creator
"""

static String qry_count = """ 
	select count(*) count from glbextr
	where glbextr_application = :application
	  and glbextr_selection =   :selection
	  and glbextr_creator_id =  :creator
	  and glbextr_user_id =     :user
"""	

static String qry_getPIDM = """
	select glbextr_key pidm from spriden, glbextr
	where spriden_pidm = to_number(glbextr_key)
  	  and spriden_change_ind is null
      and glbextr_application = :application
	  and glbextr_selection =   :selection
	  and glbextr_creator_id =  :creator
	  and glbextr_user_id =     :user
	  order by lower(spriden_last_name), spriden_first_name
"""	

static String qry_ApplicationList = """ 
	 select GLBAPPL_APPLICATION from GLBAPPL 
	 order by GLBAPPL_APPLICATION
"""	

static String qry_deleteMaster = """ 
	delete from glbslct
	where glbslct_application = :application
	  and glbslct_selection =   :selection
	  and glbslct_creator_id =  :creator
"""

}