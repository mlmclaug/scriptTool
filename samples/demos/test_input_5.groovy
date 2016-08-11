import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println "Simple script input file name"
String a = input("FileName",ck.required,ck.fileExists)  
println "a=>${a}<"

File f = new File(a)

println 'FileExists: ' + f.exists()
println 'IsFile: ' + f.isFile()
println 'Size = ' + f.size()
