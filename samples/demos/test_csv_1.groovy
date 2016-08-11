import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool


println 'Use default column names & exclude header row:'
csv.fetchAll('sampledata/Test Data.csv')
.eachWithIndex	{it, n -> println ">>>[${n}] ${it}"}

println 'Override column names & include header row:'
csv.setParser( ['ID','Name','Code'], false)
  .fetchAll('sampledata/Test Data.csv')
  .eachWithIndex {it, n -> println ">>>[${n}] ${it}"}

println 'Fetch as list:'
csv.rowFmt = 'L'
csv.fetchAll('sampledata/Test Data.csv')
   .eachWithIndex {it, n -> println ">>>[${n}] ${it}"}


println 'Process Row by Row:'
csv.setParser( [], true)
csv.rowFmt = 'M'
csv.fetchAll('sampledata/Test Data.csv')
  {it -> println ">>>Row=[ ${it}"}

