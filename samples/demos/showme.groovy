import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println ">>> Connected as ${username}  in ${dbname}"

print '    Roles: '
sql.eachRow('select role from session_roles'){ row ->
        print row.role + ', '}
println ''
