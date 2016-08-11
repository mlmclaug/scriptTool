import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println "Simple script to test lists and between"
String c                            

c = input('y or n',ck.isInList.rcurry(['y','n']))
println "c=>${c}"
println ''
def valid_names = ['joe','tom','dick','harry']
c = input('first name',ck.isInList.rcurry(valid_names))
println "c=>${c}"
println ''
c = input('between A & D','D',ck.required, ck.isBetween.rcurry('A','D'))
println "c=>${c}"
println ''
c = input('between 1 & 5',ck.isBetween.rcurry(1,5))
println "c=>${c}"
println ''
c = input('between 1.0 & 3.0',ck.isBetween.rcurry(1.0,3.0))
println "c=>${c}"
