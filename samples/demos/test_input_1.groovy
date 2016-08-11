import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println "Simple script to test input"
String a = input("Term")  
String b = input("ID")
println "a=>${a}< - b=>${b}<"

println ''
println "Has Defaults (& is Valid):"
a = input("Term",'201609',ck.isTermCD)
b = input("ID",'950824521',ck.isStudentID)
String c = input("aidy",ck.isAIDY)
println "a=>${a}< - b=>${b}< - c=>${c}<"

println ''
println "Required and is number"
a = input("Number",ck.required, ck.isNumber)
b = input("Date as MM/dd/yyyy",ck.isDate)       
println "a=>${a}< - b=>${b}<"

println '\nnumber is required:'
a = input("Enter a number",ck.required, ck.isBigDecimal)
println "a=>${a}< "

