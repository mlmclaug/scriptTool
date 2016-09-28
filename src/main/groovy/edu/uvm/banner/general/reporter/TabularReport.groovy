package edu.uvm.banner.general.reporter

/*
Auth: mlm - 04/11/2014

The is a simple reporter to flat tabular report class.

Output is written to any object that has an append() method
Typically a groovy File instance.  If no outputDest is specified
output will be directed to stdout.


Basic use:
(1) instantiate a TabularReporter instance specifying the lines per page, 
     characters per line and the output destination.For example:

	TabularReporter r = new Reporter(lpp : 55, cpl : 80, outputDest : new File("somefilename.txt")  )

(2) Define the report page headers and page footers with left, centered and right aligned text.

	r.addHead("1/1/2014", "University of Vermont",{"Page " + delegate.pgno})
	r.addHead("", "AR Batch Email","TWREMAL")
	r.addHead("Email Letter Code: AR_HOLD_REMIND", "","")
	r.addFoot("", "*** Confidential ***", "")
   
(3) Define the report columns with 
     r.addColHead( max column width, alignment (L|C|R)
           ,data format exprn, array of column labels one element per row)
	i.e:
	r.addColHead( 10, 'L',"%-10s", ["ID"])
	r.addColHead( 25, 'L',"%-15s",  ["Name"])
	r.addColHead( 10, 'R',"% 10.2f", ["Good","Number"])
(4) Print your report rows using the pl (printline) method.
	Values are passed in as a List w/ one element per report column.
	
	r.pl(["Row 1", "Social, Joe", 22.123])

    or as a variable number of arguments
    
	r.pl("Row 2", "Spade, Sam", 55)

    or as a simple preformated line
    
	r.pl("Row 2 Spade, Sam    55.25")


(5) all done...Close the report.
	
	r.close()

   results in the following sample page:

1/1/2014                     University of Vermont                        Page 3
Test1                            AR Batch Email                          TWREMAL
Email Letter Code: AR_HOLD_REMIND                                               
                                         Good
ID        Name                         Number
992222222 Smith, Jay29                 645.25
993333333 Smith, Jay30                 667.50
993333333 Smith, Jay31                 689.75
993333333 Smith, Jay32                 712.00
993333333 Smith, Jay33                 734.25
993333333 Smith, Jay34                 756.50
993333333 Smith, Jay35                 778.75
993333333 Smith, Jay36                 801.00
993333333 Smith, Jay37                 823.25
993333333 Smith, Jay38                 845.50
993333333 Smith, Jay39                 867.75
994444444 Smith, Jay40                 890.00
994444444 Smith, Jay41                 912.25
994444444 Smith, Jay42                 934.50
                              *** Confidential ***                              	
	

	
To use on other projects make sure tabularreporter.jar is on the class path & Import it
	import edu.uvm.banner.general.reporter.TabularReporter
instantiate one:
	TabularReporter rpt = new TabularReporter(lpp : 25, cpl : 80)
generate random test report:
	rpt.Test3()
	
	==============================================
    Class	
	Properties:
	- lpp - print lines on the page - 0 if continuous print.
	- cpl - characters per print line
	- outputDest - the output File or any object that has an append() method.
	             stdout if undefined
	- pgHeader[] - array of maps 1 element per row in the header
	       Each row has a map keyed L|C|R for left|center|right justified section
	       of the header
	- pgFooter - See Header
	- colHeaders - 
         > width     - Max Number of characters in column  
	     > alignment - L|C|R left|center|right justification
	     > dataFmt   - printf format string for the data values
	     > labels    - array of column header labels (one for each row)

	Misc:
 	- pgFeed = the form feed character "\f"
	- newLine = platform dependent line.separator character
	- dfltColumnWidth = 15 - used when no column with is specified for the  column  
	     
    Internal
    --------	     
	- pgno - internal tracks what page we are on
	- pgrow - internal tracks what row we are on the page.

	- cached_hdr - cached compiled header
	- cached_colhdr - cached compiled column headers
	- cached_ftr - cached compiled footer
	-isCacheable_hdr - is it static for the report?  
	-isCacheable_colhdr - is it static for the report? <-always true right now
	-isCacheable_ftdr - is it static for the report?
	
	
	Methods:
	- addHead(left,center,right)
	- addFoot(left,center,right)
	- addColHead(colwidth, alignment(L|R|C), fmt exprn [header labels])
	- addColHead(colwidth, alignment(L|R|C), "header labels")
	       -fmt exprn= String w/ format expression for the data. see Printf for expressions

	- pl - printline(c1,c2,c3......)
	- pl - printline(string)
	- pl - printline(a,b,c,.....)
	       If 1st line on page...increment pageno, evaluate and print header, print column headers
	- nl() - new line
	- nl(n) - n new lines
	- newpage() - fill remaining lines on page w/ nl.

	- close() - finish last page...
    - linesRemaining() - how many printable lines are still available on the page.
     
     Things to add (maybe):
     -Job Parameter Dump method.
     -create tests
     -create javadoc
     -subtotal/total rows/underline methods??????
     - deal w/ lines that are longer than cpl.  currently just prints em
         may want to optionally fold them... or split to 2 pages. Overflowing as is is good for now.
*/

class TabularReport {
	def outputDest
	Integer lpp = 55 
	Integer cpl = 131
	def pgHeader = [] 
	def pgFooter = []
	def colHeaders = []

	Integer pgno = 0
	Integer pgrow = 0
	String pgFeed = "\f"
	String newLine = System.getProperty("line.separator")
	Integer dfltColumnWidth = 15  
	
	private def cached_hdr = null
	private def cached_colhdr = null
	private def cached_ftr = null
	private boolean isCacheable_hdr = true
	private boolean isCacheable_colhdr = true
	private boolean isCacheable_ftr  = true
	
	TabularReport addHead(ljust,centerjust,rjust){
		//If argument is a closure... make this the delegate.
		if (ljust instanceof Closure) {ljust.delegate=this; isCacheable_hdr = false}
		if (centerjust instanceof Closure) {centerjust.delegate=this; isCacheable_hdr = false}
		if (rjust instanceof Closure) {rjust.delegate=this; isCacheable_hdr = false}
		
		pgHeader <<  ["L": ljust ?: "", "C": centerjust ?: "", "R": rjust ?: ""]
		this
	}

	TabularReport removeHeaders(){
		pgHeader = []
		cached_hdr = null
		isCacheable_hdr = true
		this
	}

	TabularReport addFoot(ljust,centerjust,rjust){
		//If argument is a closure... make this the delegate.
		if (ljust instanceof Closure) {ljust.delegate=this; isCacheable_ftr = false}
		if (centerjust instanceof Closure) {centerjust.delegate=this; isCacheable_ftr = false}
		if (rjust instanceof Closure) {rjust.delegate=this; isCacheable_ftr = false}

		pgFooter <<  ["L": ljust ?: "", "C": centerjust ?: "", "R": rjust ?: ""]
		this
	}

	TabularReport removeFooters(){
		pgFooter = []
		cached_ftr = null
		isCacheable_ftr  = true
		this
	}

	TabularReport addColHead(Integer colwidth, String headAlign, String dataFmt, List labels){
		//TODO  when/if decide need dynamic col labels... then
		//      check labels for closures... if present set cache able to false...
		//      for now... col labels are static for the report.
		colHeaders <<  new TabularColumnHeader(width:colwidth ?: dfltColumnWidth, 
									alignment:headAlign, dataFmt: dataFmt ?: "",labels:labels ?: [])
		this
	}

	TabularReport addColHead(Integer colwidth, String headAlign, String dataFmt, String labels){
		addColHead(colwidth, headAlign, dataFmt, (List) labels.split(newLine))
		this
	}

	TabularReport removeColHeaders(){
		colHeaders = []
		cached_colhdr = null
		isCacheable_colhdr = true
		this
	}
 
     TabularReport pl(List rowdata){
        //if this line doesn't fit.. start a new page
		newPageIfNeeded()
		//print report data line and increment row count
		def nprcols = -1 + Math.min(colHeaders.size(), rowdata.size())
		nprcols = Math.max(nprcols, 0)
		String outRow = ""
		for ( i in 0..nprcols){
			//TODO if have extra row data.. dump it out on end?? right now ignores extraneous data.
			outRow += colHeaders[i].fmtDataValue(rowdata[i])
		}
		if(colHeaders.size() < rowdata.size()){
			//here there are additional columns we don't know about
			// you want'em.. you gottem
			rowdata[colHeaders.size()..rowdata.size() - 1].each { outRow += it}
		}
		
		outline(outRow)
		pgrow++
		this
	}

	
	TabularReport pl(String txtline){
		// print the line.
		if ( txtline.contains(newLine) ) {
			//here the string contains multiple lines
			// split it and print each line separately
			txtline.split(newLine).each( { pl( it) })
		} else {
			//if this line doesn't fit.. start a new page
			newPageIfNeeded()
			//print report data line and increment row count
			pgrow++
			outline(txtline)
		}
		this
	}

	TabularReport pl(Object... args){
		// to support syntax where can call w/ an arbitrary number of arguments.
		pl( args.flatten())
	}
	
	TabularReport nl(){
		// skip a line
		//if this line doesn't fit.. start a new page
		newPageIfNeeded()
		//print report data line and increment row count
		pgrow++
		outline('')
		this
	}

	TabularReport nl(Integer n){
		// skip n lines
		Math.max(0,n).times( {nl()} )
		this
	}

	TabularReport newPage(){
		//  start a new page.. print nl's to complete the page,
		// calculate lines remaining on the page
		// ignore if doing continuous printing (lpp=0)
		if (lpp > 0){
			nl(linesRemaining())
		}
		this
	}
	Integer linesRemaining(){
		(lpp > 0) ? lpp - pgFooter.size() - pgrow : 0
	}

	TabularReport close(){
		//flush last page & footer
		newPage()
		List f = genFooter()
		f.each { outline(it)}
		out(pgFeed)
		//reset state variables
		pgno = 0
		pgrow = 0
		cached_hdr = null
		cached_colhdr = null
		cached_ftr = null
		isCacheable_hdr = true
		isCacheable_colhdr = true
		isCacheable_ftr  = true
		//Groovy handles the  file close for you... may need this for other writers...
		// close outputDest // responsibility of the program that provided the writer.
		this
	}
	
/*
 *   internal utility functions	
 */
	String[] genHeader(){
		//Convert headers into formated string if not available in cache
		
		if (!cached_hdr || !isCacheable_hdr) {
			cached_hdr =  pgHeader.collect( make_hdr_row)
		}
		return cached_hdr
	}
	 
	String[] genFooter(){
		//Convert footers into formated string if not available in cache
		if (!cached_ftr || !isCacheable_ftr) {
			   cached_ftr = pgFooter.collect( make_hdr_row)
		}
		return cached_ftr
	}
 
   private String[] genColHeaders(){
	   //Make column header as array of strings... one for each row.
	   if (!cached_colhdr || !isCacheable_colhdr) {
		   def numrows = colHeaders.collect({it.labels.size() }).max()
		   String[] res = [""]* (numrows ?: 0)
		   colHeaders.each {
			   //Get col labels for this column
			   def chdr = it.fmtColLabels(numrows)
			   //append to the row(s)
			   for (i in 0..res.size()-1 ) {
				   res[i] += chdr[i]
			   }
		   }
		   cached_colhdr = res
	   }
	   return cached_colhdr
   }

	private Closure make_hdr_row = {it ->
		// run it through for variable expansion
		String l = expand(it["L"])
		String c = expand(it["C"])
		String r = expand(it["R"])
		//put them together
		String f = joinRow(cpl,l,c,r)
		f
	}
	
	private String expand(el){
		//execute the closure and return the result.
		( el instanceof Closure) ? el() : el
	 }
	 
	private String joinRow(Integer cpl, String l, String c, String r){
		Integer mid = cpl/2
		Integer centerstart = mid - c.size()/2
		String h = l + ' '.multiply(Math.max(0,centerstart-l.size())) + c
		h =  h +  ' '.multiply(Math.max(0, cpl - (r.size() + h.size())))
		h = h + r
		h
	}

	private void newPageIfNeeded(){
		// if this row would print in the footer section, print footers and advance to new page first
		if ( lpp > 0 && pgrow >= lpp - pgFooter.size() ) {
			// here print footer and advance to new page
			List f = genFooter()
			f.each { outline(it)}
			out(pgFeed)
			pgrow = 0
		}

		// if first row on the page... print header rows and column heading rows
		if (pgrow == 0){
			pgno++
			List h = genHeader()
			h.each { outline(it)}
			pgrow += h.size()
			
			List ch = genColHeaders()
			ch.each { outline(it)}
			pgrow += ch.size()
		}
	}

	private void outline(String l){
		// Sends line to the output writer/destination w/ nl
        // currently stdout or a file
		if (outputDest){  //File or anything w/ a append() method.
			outputDest.append(l)
			outputDest.append(newLine)
		} else { // stdout
			println l
		}
		
	}
	private void out(String l){
		// Sends line to the output writer/destination (no nl)
		//TODO -- add other writers?
		if (outputDest){  //File or anything w/ a append() method.
			outputDest.append(l)
		} else { // stdout
			print l
		}
	}

	/*
	 *   Main and some tester methods.
	 */
	
	static main(args) {
		//Sample1()
		//Sample2(null)
		//Sample2( new File("test2.txt"))
		Sample3()
		//Sample3(new File("sample3.txt"))
	}

	
	static void Sample1(){
		//TabularReporter r = new Reporter(lpp : 25, cpl : 80, outputDest : new File("somefilename.txt")  )
		TabularReport r = new TabularReport(lpp : 20, cpl : 80, outputDest : null )
		r.addHead("1/1/2014", "University of Vermont",{"Page " + delegate.pgno})
		.addHead("Test1", "AR Batch Email","TWREMAL")
		.addHead("Email Letter Code: AR_HOLD_REMIND", "","")
		.addFoot("", "*** Confidential ***", "")
		.addColHead( 10, 'L',"%-10s", ["ID"])
		.addColHead( 25, 'L',"%-25s",  ["Name"])
		.addColHead( 10, 'R',"% 10.2f", ["Good","Number"])
		
		for(i in 1..50){
			def id = "99" + i.toString()[0].multiply(7)
			def name = "Smith, Jay" + i.toString()
			def num = i*22.25
			r.pl([id,name,num])
		}
		r.pl(["",null, "=========="])
		r.pl(["TOTAL",null, (1..50).sum() ])
		r.close()
	}
	
	static void Sample2(dest){
		TabularReport r = new TabularReport(lpp : 25, cpl : 80, outputDest : dest ,pgFeed : "")
		r.addHead("2/2/2014", "University of Vermont",{"Page " + delegate.pgno})
		.addHead("", "AR Batch Email","TWREMAL")
		.addHead("Email Letter Code: AR_HOLD_REMIND", "","Test2")
		.addColHead( 10, 'L',"%-10s", ["ID"])
		.addColHead( 25, 'L',"%-25s",  ["Name"])
		.addColHead( 10, 'R',"% 10d", "Another\nNumber")
		.addColHead( 10, 'R',"% 10.2f", "Final\nNumber")
		
		for(i in 1..20){
			def id = "99" + i.toString()[0].multiply(7)
			def name = "Social, Joe (" + i.toString() + ")"
			Integer num = i*22
			r.pl([id,name,num, i * 100.33])
		}
		r.pl(["",null, "=========="])
		r.pl("TOTAL",null, (1..50).sum() )

		r.pl([12.0000, (1..50).sum(), null ])
		r.pl(12.0000, null, (1..50).sum(), 27.25, "comment" )
		r.pl()
		r.pl([])
		r.pl([12.0000, "more stuff", (1..50).sum(),  "<<<<<<" ])
		r.pl(["test big number", "", 12345678901234567890123456789012345678901234567890.12345678901234567890,  12345678901234567890123456789012345678901234567890.12345678901234567890, "<<<<<<" ])
		r.newPage()
		r.nl(1)
		r.pl("!! Message row 1!!")
		r.nl()
		r.pl("!! Message row 2!!")
		r.nl()
		r.pl("Test Multiple Lines 1\nTest Multiple Lines 2\nTest Multiple Lines 3\nTest Multiple Lines 4\n")
		r.close()
	}


	static void Sample3(dest = null){
		// Set up the Sample Report
		TabularReport r = new TabularReport(lpp : 25, cpl : 80,outputDest : dest)
		r.addHead( new Date().format('MM/dd/yyyy h:mm a') , "Sluggo and Sons",{"Page " + delegate.pgno})
		.addHead("", "Nice Report Title","")
		.addFoot("", {"Page " + delegate.pgno}, "")
		.addColHead( 10, 'L',"%-10s", ["ID"])
		.addColHead( 20, 'L',"%-20s",  ["Product"])
		.addColHead( 12, 'R',"%,12.0f", ["First","Number"])
		.addColHead( 12, 'R',"%,12.2f", ["Second","Number"])
		.addColHead( 12, 'R',"%,12.3f", ["Third","Number"])
		.addColHead( 14, 'R',"%,14.0f", "Total")
		
		// Generate and print some pseudo random data
		Random rand = new Random()
		def max = 10000
		def p = ["Greasy Sandwich","Clam Chowda","Ham & Eggs","Milk & Cookies","Donut","Burger and Fries",
			"Tuna Salad on Rye","Ice Cream Sundae","Philly Cheese Steak", "Italian Hoagie","Ham Grinder"]
		def n1tot = 0; def n2tot = 0; def n3tot = 0;def grandtotal = 0;
		for(i in 1..30){
			def n = rand.nextFloat()
			String id = "99" + String.format("%7.0f", n * 10000000)
			String name = p[rand.nextInt( p.size())]
			def n1 = rand.nextInt((Integer) max)  + rand.nextFloat()
			def n2 = rand.nextInt((Integer) max)  + rand.nextFloat()
			def n3 = rand.nextInt((Integer) max)  + rand.nextFloat()
			def tot = n1 + n2 + n3
			n1tot+= n1;	n2tot+= n2;	n3tot+= n3;	grandtotal+= tot
			r.pl([id,name,n1,n2,n3,tot])
		}
		//print a total line and close the report.
		r.pl(["",null, "==========", "==========", "==========", "=========="])
		r.pl("TOTAL","", n1tot, n2tot, n3tot, grandtotal)
		r.close()
		
/* Which will produce something like this:

04/11/2014 11:50 AM             Sluggo and Sons                           Page 1
                               Nice Report Title                                
                                     First      Second       Third              
ID        Product                   Number      Number      Number         Total
997680382 Philly Cheeese Steak       9,231    3,456.43   6,826.878        19,514
993459371 Philly Cheeese Steak       5,885    2,534.07   5,197.053        13,616
994062968 Donunt                     8,303    5,284.10   9,362.580        22,950
997244741 Ice Cream Sundae           8,711    3,298.60   2,188.136        14,198
992997168 Ham Grinder                8,726    6,920.95   9,133.877        24,781
999868581 Donunt                     7,538      449.56   8,954.915        16,942
997584450 Ice Cream Sundae           5,709    5,193.64   5,082.121        15,985
992318517 Ham & Eggs                 9,059    9,791.33   2,006.727        20,857
992062809 Burger and Fries             156    2,451.94   4,410.448         7,018
993461602 Greasy Sandwich            8,596    2,280.31   3,586.605        14,463
998202875 Ham Grinder                  587    2,611.01   6,158.794         9,357
996601865 Tuna Salad on Rye          6,670    8,217.62   9,613.213        24,501
998364795 Philly Cheeese Steak       4,819    7,764.28   8,341.654        20,925
995686911 Greasy Sandwich            1,546    4,623.72   8,777.513        14,947
991817440 Ham & Eggs                 4,315    3,733.18   2,545.904        10,594
999015129 Italian Hoagie             3,278    5,429.74   3,189.119        11,897
993421507 Ham Grinder                4,653    5,568.43   7,394.217        17,616
99 795072 Ice Cream Sundae           4,517    8,937.74   9,251.486        22,707
996278069 Ham & Eggs                 5,891      643.34     280.890         6,815
993408579 Ham & Eggs                   950      214.24   4,626.379         5,791
                                     Page 1                                     
<NP>04/11/2014 11:50 AM             Sluggo and Sons                           Page 2
                               Nice Report Title                                
                                     First      Second       Third              
ID        Product                   Number      Number      Number         Total
992444149 Ham & Eggs                 4,322    6,619.47   5,316.123        16,258
995729182 Tuna Salad on Rye          4,828    4,026.72   8,019.295        16,874
998316866 Clam Chowda                3,270    5,634.70   4,935.057        13,839
995347387 Italian Hoagie             5,323    4,833.87   5,959.749        16,117
993787731 Burger and Fries           4,309    9,127.27   7,504.118        20,941
995054368 Greasy Sandwich            3,712    1,272.95   2,270.624         7,255
996768637 Italian Hoagie             6,520      214.66   2,353.465         9,088
996435657 Tuna Salad on Rye            816    9,274.40   6,393.991        16,484
997191126 Tuna Salad on Rye          7,945    4,459.20   7,898.605        20,303
995926363 Burger and Fries             182      660.51   5,225.081         6,068
                                ==========  ==========  ==========    ==========
TOTAL                              150,367  135,527.97 172,804.617       458,700








                                     Page 2                                     
 */
		
	}
	
}
