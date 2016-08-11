import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println "Simple script to translations"
String a = input("Term",'201609')  
String b = input("ID", '950824521')
println "a=>${a}< - b=>${b}<"

String aidy = tr.term2aidy(a)
Number pidm = tr.studentid2pidm(b)
println "aidy=>${aidy}< - pidm=>${pidm}<"
