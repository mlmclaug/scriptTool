import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

println "Simple script to test custom validation"
String a                            

Closure isFirstLetterYorN_caseinsensitive = {
        printIfFalse( 'YN'.indexOf(it.take(1).toUpperCase()) > -1, 
            '*** Please enter yes or no (Y,y,N,n,yes,no,Yes,No...).')
    }

a = input("Yes or No (YyNn)",ck.required, isFirstLetterYorN_caseinsensitive)
println "a=>${a}<"

